package com.rayluxeo.aida.domain.location

import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/**
 * Where Abu is standing, when he is the one who asked.
 *
 * The only way a coordinate enters Mira. There is no geocoder and no map, so saving a place means
 * being at it — which also happens to be the most private possible design: nothing is looked up,
 * nothing is sent, and a fix is taken once, on a tap, rather than watched.
 */
sealed interface PlaceFix {

    data class Found(
        val latitude: Double,
        val longitude: Double,
        /**
         * What the platform claims, shown to Abu so a poor indoor fix is his call, not Mira's.
         *
         * Null when the platform did not say. `Location.getAccuracy()` reads 0 when it was never
         * set, and 0 metres would tell him the fix is perfect — the one reading he must not be
         * given. Absence is a thing the UI can say; a sentinel float is a thing it would print.
         */
        val accuracyMeters: Float?,
    ) : PlaceFix

    /**
     * Reuses [PlaceWatchRefusal] rather than declaring a second list of reasons.
     *
     * Location fails for the same handful of reasons whether Mira is taking one fix or watching
     * for an arrival, and the UI already turns every one of them into the settings screen that
     * answers it. A parallel enum would mean a second mapping to keep in step — the shape §7.12
     * exists to forbid.
     */
    data class Unavailable(val reason: PlaceWatchRefusal) : PlaceFix
}

/**
 * Takes a single location fix.
 *
 * A port so `domain` can ask without importing Android, and so the use cases are testable with a
 * fake that returns a coordinate instead of needing a device with a sky view.
 */
interface CurrentPlaceFinder {

    /**
     * Suspends until the platform answers or gives up.
     *
     * Never throws: a missing permission, a disabled radio and a device with no provider all come
     * back as [PlaceFix.Unavailable], because all three are ordinary states Abu can fix.
     */
    suspend fun here(): PlaceFix
}
