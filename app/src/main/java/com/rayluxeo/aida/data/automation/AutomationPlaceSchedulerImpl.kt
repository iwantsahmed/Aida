package com.rayluxeo.aida.data.automation

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
import com.rayluxeo.aida.data.location.LocationAvailability
import com.rayluxeo.aida.domain.automation.AutomationPlaceScheduler
import com.rayluxeo.aida.domain.automation.AutomationPlaceWatch
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Place automations on the platform's own proximity alerts — the exact primitive
 * [com.rayluxeo.aida.data.scheduler.PlaceReminderSchedulerImpl] uses, and for the same reason:
 * `LocationManager.addProximityAlert` keeps Play Services (and a Google dependency) out of an app
 * whose whole claim is that Abu's records stay on his phone (recorded in `docs/ZERO_COST_AUDIT.md`).
 *
 * The honesty work is the same too: the old API gives no callback when the platform quietly stops
 * honouring an alert, so everything that could stand between Abu and his automation is checked
 * before Mira promises anything, and every answer is an [AutomationPlaceWatch] rather than an
 * exception. The alert is armed with **no expiry** because a place automation is recurring.
 */
@Singleton
class AutomationPlaceSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val availability: LocationAvailability,
    private val automationRepository: AutomationRepository,
    private val dispatchers: DispatcherProvider,
) : AutomationPlaceScheduler {

    override val capacity: Int = MAX_WATCHED_PLACE_AUTOMATIONS

    @SuppressLint("MissingPermission")
    override suspend fun sync(automation: Automation): AppResult<AutomationPlaceWatch> {
        if (automation.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot watch an unsaved automation")
        }
        return withContext(dispatchers.io) {
            val place = automation.trigger as? AutomationTriggerConfig.Place
            if (!automation.isEnabled || place == null) {
                cancelAlert(automation.id)
                return@withContext AppResult.Success(AutomationPlaceWatch.NotWatched)
            }
            val manager = context.getSystemService(LocationManager::class.java)
                ?: return@withContext AppResult.Success(AutomationPlaceWatch.Refused(PlaceWatchRefusal.NO_PROVIDER))
            refusalFor(automation.id, manager)?.let {
                cancelAlert(automation.id)
                return@withContext AppResult.Success(AutomationPlaceWatch.Refused(it))
            }
            try {
                manager.addProximityAlert(
                    place.latitude,
                    place.longitude,
                    place.radiusMetres.toFloat(),
                    NO_EXPIRY,
                    arrivalIntent(automation.id),
                )
            } catch (revoked: SecurityException) {
                cancelAlert(automation.id)
                return@withContext AppResult.Success(AutomationPlaceWatch.Refused(missingGrant()))
            }
            AppResult.Success(AutomationPlaceWatch.Watching)
        }
    }

    override suspend fun cancel(automationId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        cancelAlert(automationId)
        AppResult.Success(Unit)
    }

    override suspend fun rewatchAll(): AppResult<Unit> = withContext(dispatchers.io) {
        val enabled = runCatching { automationRepository.observeEnabled().first() }.getOrDefault(emptyList())
        enabled.filter { it.trigger is AutomationTriggerConfig.Place }.forEach { sync(it) }
        AppResult.Success(Unit)
    }

    @SuppressLint("MissingPermission")
    private fun cancelAlert(automationId: Long) {
        val armed = PendingIntent.getBroadcast(
            context,
            requestCode(automationId),
            Intent(context, AutomationPlaceReceiver::class.java).setAction(AutomationPlaceReceiver.ACTION_ARRIVED),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (armed != null) {
            try {
                context.getSystemService(LocationManager::class.java)?.removeProximityAlert(armed)
            } catch (denied: SecurityException) {
                // Without the permission the platform is no longer holding the alert anyway.
            }
            armed.cancel()
        }
    }

    private suspend fun refusalFor(automationId: Long, manager: LocationManager): PlaceWatchRefusal? {
        availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH)?.let { return it }
        val held = runCatching { automationRepository.observeEnabled().first() }.getOrDefault(emptyList())
            .count { it.id != automationId && it.trigger is AutomationTriggerConfig.Place }
        if (held >= capacity) return PlaceWatchRefusal.LIMIT_REACHED
        if (batteryWillNotDeliver()) return PlaceWatchRefusal.BATTERY_RESTRICTED
        return null
    }

    private fun missingGrant(): PlaceWatchRefusal =
        if (availability.hasFineGrant()) {
            PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING
        } else {
            PlaceWatchRefusal.PERMISSION_MISSING
        }

    private fun batteryWillNotDeliver(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        if (power.isIgnoringBatteryOptimizations(context.packageName)) return false
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return false
        return usage.appStandbyBucket >= UsageStatsManager.STANDBY_BUCKET_RESTRICTED
    }

    private fun arrivalIntent(automationId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(automationId),
        Intent(context, AutomationPlaceReceiver::class.java).apply {
            action = AutomationPlaceReceiver.ACTION_ARRIVED
            putExtra(AutomationPlaceReceiver.EXTRA_AUTOMATION_ID, automationId)
        },
        // Mutable so the platform can fill KEY_PROXIMITY_ENTERING; the component is fixed, so a
        // fill-in can add extras and nothing else.
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Offset away from the reminder-place request codes so the two pools cannot collide. */
    private fun requestCode(automationId: Long): Int = (AUTOMATION_PLACE_REQUEST_OFFSET + automationId).toInt()

    private companion object {
        const val MAX_WATCHED_PLACE_AUTOMATIONS = 20
        const val NO_EXPIRY = -1L
        const val AUTOMATION_PLACE_REQUEST_OFFSET = 950_000L
    }
}
