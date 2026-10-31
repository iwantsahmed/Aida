package com.rayluxeo.aida.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.location.CurrentPlaceFinder
import com.rayluxeo.aida.domain.location.PlaceFix
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One fix, on the platform's own `LocationManager`.
 *
 * No Play Services and no geocoder: the coordinate is read from the phone, used to fill in a place
 * Abu is naming, and written to Room. Nothing is sent anywhere, which is what lets `docs/PRIVACY.md`
 * say what it says.
 *
 * **This asks for less than a place reminder does, deliberately.** Taking a fix while Abu is
 * looking at the screen needs only `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`; watching for
 * an arrival additionally needs `ACCESS_BACKGROUND_LOCATION`. So a missing background grant is not
 * a refusal here. Treating the two alike would mean Abu could not save a place at all until he had
 * granted always-on location — asking for the widest permission at the first, mildest moment,
 * which is exactly the pattern a private assistant should not follow.
 */
@Singleton
class CurrentPlaceFinderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val availability: LocationAvailability,
    private val dispatchers: DispatcherProvider,
) : CurrentPlaceFinder {

    /** [LocationAvailability] checks the grant before the call below; lint cannot see that far. */
    @SuppressLint("MissingPermission")
    override suspend fun here(): PlaceFix = withContext(dispatchers.io) {
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@withContext PlaceFix.Unavailable(PlaceWatchRefusal.NO_PROVIDER)
        availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX)?.let {
            return@withContext PlaceFix.Unavailable(it)
        }
        val provider = availability.usableProviders(manager)
            .firstOrNull { manager.isProviderEnabled(it) }
            ?: return@withContext PlaceFix.Unavailable(PlaceWatchRefusal.LOCATION_DISABLED)

        val location = try {
            // Bounded, because `getCurrentLocation` is documented to give up on its own and does
            // not always do so — on a cold provider it can simply never call back, and the screen
            // is left saying "taking a fix" with nothing behind it and no way out. Abu waiting
            // forever is worse than Abu being told to try again, which is a thing he can act on.
            withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { awaitFix(manager, provider) }
        } catch (revoked: SecurityException) {
            // The grant can disappear between the check above and the call itself. A
            // SecurityException from a location call is about a permission and nothing else.
            return@withContext PlaceFix.Unavailable(PlaceWatchRefusal.PERMISSION_MISSING)
        }
        location?.toFix() ?: PlaceFix.Unavailable(noFixReason(manager, provider))
    }

    /**
     * Bridges the platform's one-shot callback onto the coroutine.
     *
     * The [CancellationSignal] is cancelled with the coroutine, so closing the screen closes the
     * location request with it. A fix left running after nobody is waiting for it is precisely the
     * kind of thing that drains a battery without anyone ever seeing why.
     *
     * The consumer does nothing but resume, so it runs on the calling thread rather than costing a
     * hop to the main looper.
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, CALLBACK_EXECUTOR) { location ->
                continuation.resume(location)
            }
        }

    /**
     * Why a fix that was asked for properly came back empty.
     *
     * The conditions are re-read rather than assumed, because the wait is long enough for Abu to
     * have switched location off or for the grant to have gone; if one of them has, that is the
     * true reason and it is what he is told.
     *
     * When nothing has changed, the answer is [PlaceWatchRefusal.NO_FIX_YET] and not
     * [PlaceWatchRefusal.NO_PROVIDER]. The two are easy to conflate and must not be: a phone with
     * no provider is a fact Abu can do nothing about, while a fix that did not arrive is usually a
     * roof, and the only difference he can act on is whether Mira suggests trying again.
     */
    private fun noFixReason(manager: LocationManager, provider: String): PlaceWatchRefusal {
        availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX)?.let { return it }
        if (!manager.isProviderEnabled(provider)) return PlaceWatchRefusal.LOCATION_DISABLED
        return PlaceWatchRefusal.NO_FIX_YET
    }

    private companion object {
        val CALLBACK_EXECUTOR: Executor = Executor { command -> command.run() }

        /**
         * Long enough for a cold fix, short enough that Abu is not stranded.
         *
         * He is standing still, looking at the screen, having just tapped a button — which is the
         * best case a one-shot fix ever gets. If fifteen seconds of that produces nothing, waiting
         * longer is not going to help; going nearer a window will.
         */
        const val FIX_TIMEOUT_MILLIS = 15_000L
    }
}

/**
 * `Location.getAccuracy()` reads 0 when the provider never set one, and 0 metres would tell Abu
 * the fix is perfect when the truth is that it is unknown. Null says unknown, which is a thing the
 * UI can phrase; a sentinel float is a thing it would print.
 */
private fun Location.toFix(): PlaceFix.Found = PlaceFix.Found(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
)
