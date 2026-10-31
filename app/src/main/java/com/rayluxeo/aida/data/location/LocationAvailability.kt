package com.rayluxeo.aida.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that decides whether this phone can do location work at all, and why not.
 *
 * Two callers ask the same questions of the same platform state — the scheduler before it arms a
 * proximity alert, the finder before it takes one fix — and they must never give Abu two different
 * accounts of the same phone. Holding both sets of checks here is the same lesson as §7.12: the
 * vocabulary that decides *whether* and the one that decides *how* were once two hand-maintained
 * copies, and the narrower one drifted and then answered with confidence.
 *
 * Nothing here reads a position. These are the cheap, permission-free questions about grants,
 * providers and the master switch, so that a refusal Abu can act on comes back before the phone
 * has spent anything on a fix.
 */
@Singleton
class LocationAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * The refusal Abu would hit right now, or null when nothing stands in the way.
     *
     * Ordered so the answer is the first thing he can do something about: a permission he can
     * grant, then hardware he cannot change, then a switch he can flip. Callers add their own
     * further checks after this one — a limit or a battery bucket belongs to whoever has one.
     */
    fun refusalFor(manager: LocationManager, need: Need): PlaceWatchRefusal? {
        when (need) {
            Need.ONE_FIX ->
                if (!hasForegroundGrant()) return PlaceWatchRefusal.PERMISSION_MISSING
            Need.CONTINUOUS_WATCH -> {
                if (!hasFineGrant()) return PlaceWatchRefusal.PERMISSION_MISSING
                if (!hasBackgroundGrant()) return PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING
            }
        }
        if (usableProviders(manager).isEmpty()) return PlaceWatchRefusal.NO_PROVIDER
        if (!manager.isLocationEnabled) return PlaceWatchRefusal.LOCATION_DISABLED
        return null
    }

    /** Proximity alerts need this one specifically; a coarse-only grant will not arm one. */
    fun hasFineGrant(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Either grant is enough to take a fix while Abu is looking at the screen. Coarse is a
     * perfectly good answer to "remember where I am standing" — a place radius is measured in
     * hundreds of metres — and refusing it would make Mira demand more than she needs.
     */
    fun hasForegroundGrant(): Boolean =
        hasFineGrant() || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundGrant(): Boolean = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /**
     * The providers that can produce a position, best first.
     *
     * `getAllProviders` always includes the passive one, which reports only what another app has
     * already asked for: it can neither raise a proximity alert nor answer a fix of Mira's own. So
     * the question is whether a real provider exists, not whether the list is empty.
     *
     * Fused leads because it is the platform's own combination of every sensor on the phone, which
     * is a better answer than Mira second-guessing which radio is worth waking.
     */
    fun usableProviders(manager: LocationManager): List<String> {
        val present = manager.allProviders
        return PREFERRED_PROVIDERS.filter { it in present }
    }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * What the location is wanted for, because the two needs are not the same permission.
     *
     * Taking one fix while Abu is watching needs only foreground location. Watching for an arrival
     * needs the background grant as well, and that difference is load-bearing: treating them alike
     * would mean he could not so much as save a place until he had granted always-on location,
     * which is the opposite of what an app making his privacy its claim should ask for.
     */
    enum class Need {
        ONE_FIX,
        CONTINUOUS_WATCH,
    }

    private companion object {
        val PREFERRED_PROVIDERS: List<String> = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
    }
}
