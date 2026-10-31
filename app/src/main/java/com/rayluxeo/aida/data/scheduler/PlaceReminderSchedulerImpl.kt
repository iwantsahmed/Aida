package com.rayluxeo.aida.data.scheduler

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.PowerManager
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.data.location.LocationAvailability
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Place reminders on the platform's own proximity alerts.
 *
 * `LocationManager.addProximityAlert` is deprecated in the documentation and present in the SDK,
 * and it is what Mira uses: the alternative is Play Services geofencing, which would put a Google
 * dependency inside an app whose whole claim is that Abu's records stay on his phone. That choice
 * is recorded in `docs/ZERO_COST_AUDIT.md`.
 *
 * What it costs is honesty work. The older API gives no callback when the platform quietly stops
 * honouring an alert, so everything that could stand between Abu and his reminder is checked
 * before Mira promises anything, and every answer is a [PlaceWatchResult] rather than an
 * exception. **Location is never required**: nothing else in the app reads any of this, and a
 * phone with location switched off runs every other kind of reminder unchanged.
 */
@Singleton
class PlaceReminderSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val places: ReminderPlaceRepository,
    private val availability: LocationAvailability,
    private val dispatchers: DispatcherProvider,
) : PlaceReminderScheduler {

    /**
     * Mira's ceiling, not the platform's — Android publishes no per-app number for proximity
     * alerts, and each one is a standing location request that an OEM may throttle long before a
     * limit is reached. Small on purpose: a person has a handful of places, and refusing the next
     * one out loud is better than registering it and finding out later nothing was listening.
     */
    override val capacity: Int = MAX_WATCHED_PLACES

    /** The permission is checked by [refusalFor] before the call; lint cannot see that far. */
    @SuppressLint("MissingPermission")
    override suspend fun watch(place: ReminderPlace): AppResult<PlaceWatchResult> {
        if (place.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot watch a place that has not been saved")
        }
        return withContext(dispatchers.io) {
            val manager = context.getSystemService(LocationManager::class.java)
                ?: return@withContext record(place.id, PlaceWatchResult.Refused(PlaceWatchRefusal.NO_PROVIDER))
            val refusal = refusalFor(place, manager)
            if (refusal != null) {
                return@withContext record(place.id, PlaceWatchResult.Refused(refusal))
            }
            try {
                manager.addProximityAlert(
                    place.latitude,
                    place.longitude,
                    place.radiusMeters,
                    NO_EXPIRY,
                    arrivalIntent(place),
                )
            } catch (revoked: SecurityException) {
                // The grant can disappear between the check above and the call itself, and on some
                // builds a background-only refusal surfaces here rather than as a missing grant.
                return@withContext record(place.id, PlaceWatchResult.Refused(missingGrant()))
            }
            record(place.id, PlaceWatchResult.Watching)
        }
    }

    /** Releasing an alert needs the same grant that armed it; the catch below is the honest path. */
    @SuppressLint("MissingPermission")
    override suspend fun stopWatching(placeId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        val armed = PendingIntent.getBroadcast(
            context,
            requestCode(placeId),
            Intent(context, PlaceReceiver::class.java).setAction(PlaceReceiver.ACTION_ARRIVED),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (armed != null) {
            try {
                context.getSystemService(LocationManager::class.java)?.removeProximityAlert(armed)
            } catch (denied: SecurityException) {
                // Without the permission the platform is no longer holding the alert anyway, so
                // there is nothing left to release and nothing Abu needs telling about.
            }
            armed.cancel()
        }
        // Best effort, and the same reasoning as in `record`: the alert is released either way, and
        // the row may already have gone if the reminder was deleted first.
        places.setRegistered(placeId, false)
        AppResult.Success(Unit)
    }

    /**
     * A reboot drops every proximity alert the app held, and so does a permission being withdrawn
     * and given back. Each place is re-offered individually so one Mira cannot hold — a limit
     * reached, a row whose coordinates the platform rejects — does not cost her the others.
     */
    override suspend fun rewatchAll(): AppResult<Unit> = when (val pending = places.pending()) {
        is AppResult.Failure -> pending
        is AppResult.Success -> {
            for (place in pending.value) {
                watch(place)
            }
            AppResult.Success(Unit)
        }
    }

    /**
     * The flag mirrors what the platform is holding. If the write fails the registration itself is
     * unaffected, and claiming otherwise would be the worse answer; [rewatchAll] restates it after
     * the next reboot.
     */
    private suspend fun record(placeId: Long, outcome: PlaceWatchResult): AppResult<PlaceWatchResult> {
        places.setRegistered(placeId, outcome is PlaceWatchResult.Watching)
        return AppResult.Success(outcome)
    }

    /**
     * The grants, the providers and the master switch are [LocationAvailability]'s answers, asked
     * for with `CONTINUOUS_WATCH` because a place reminder that only fires while Mira is open in
     * front of Abu is not a place reminder. What is left below is this scheduler's own: a register
     * only it keeps, and a battery state only background work has to care about. They come last
     * because they are the part Abu can do least about.
     */
    private suspend fun refusalFor(place: ReminderPlace, manager: LocationManager): PlaceWatchRefusal? {
        availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH)
            ?.let { return it }
        // A count Mira cannot read is not a reason to refuse a reminder, so a storage failure
        // reads as nothing registered rather than as a full register.
        val held = places.countRegisteredOtherThan(place.id).getOrNull() ?: 0
        if (held >= capacity) return PlaceWatchRefusal.LIMIT_REACHED
        if (batteryWillNotDeliver()) return PlaceWatchRefusal.BATTERY_RESTRICTED
        return null
    }

    /** Which grant a refusal at the call itself was about, so the two lead to different wording. */
    private fun missingGrant(): PlaceWatchRefusal =
        if (availability.hasFineGrant()) {
            PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING
        } else {
            PlaceWatchRefusal.PERMISSION_MISSING
        }

    /**
     * Being subject to battery optimisation is the default for every app and means nothing on its
     * own, so it is only the first half of the question. The second half is the standby bucket:
     * RESTRICTED is the one state where the platform says outright that it will curtail Mira's
     * background work, and a place alert is exactly that. Mira reports it and stops there — she
     * does not ask Abu to exempt her from battery optimisation, unprompted or otherwise.
     */
    private fun batteryWillNotDeliver(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        if (power.isIgnoringBatteryOptimizations(context.packageName)) return false
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return false
        return usage.appStandbyBucket >= UsageStatsManager.STANDBY_BUCKET_RESTRICTED
    }

    /**
     * Mutable, and it has to be: the platform delivers which edge was crossed by filling
     * `KEY_PROXIMITY_ENTERING` into this intent as it sends it, and an immutable PendingIntent
     * discards everything the sender fills in. The component is already set, so a fill-in can add
     * extras and nothing else — it cannot redirect the broadcast anywhere but [PlaceReceiver].
     */
    private fun arrivalIntent(place: ReminderPlace): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(place.id),
        Intent(context, PlaceReceiver::class.java).apply {
            action = PlaceReceiver.ACTION_ARRIVED
            putExtra(PlaceReceiver.EXTRA_PLACE_ID, place.id)
            putExtra(PlaceReceiver.EXTRA_REMINDER_ID, place.reminderId)
            putExtra(PlaceReceiver.EXTRA_ON_ENTER, place.trigger == PlaceTrigger.ENTER)
        },
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The place's own id. Extras are not part of a PendingIntent's identity, so without a distinct
     * request code every place would collide onto one registration and only the newest would live.
     */
    private fun requestCode(placeId: Long): Int = placeId.toInt()

    private companion object {
        const val MAX_WATCHED_PLACES = 20

        /** `addProximityAlert` reads -1 as "no expiry". A place reminder waits until Abu arrives. */
        const val NO_EXPIRY = -1L
    }
}
