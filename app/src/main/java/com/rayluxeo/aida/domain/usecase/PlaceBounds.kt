package com.rayluxeo.aida.domain.usecase

/**
 * The limits every place in Mira is held to, kept in one copy.
 *
 * A place Abu saves and the place a reminder waits at are the same thing measured the same way —
 * [com.rayluxeo.aida.domain.model.SavedPlace] says its radius is "bounded by the same limits a
 * reminder's own radius is" — so the numbers live here rather than beside each use case. Two
 * hand-maintained sets would drift, and the shape that drift takes is a place Abu saved at forty
 * metres that the reminder he then sets on it quietly refuses. AGENTS.md §7.12 is the same rule
 * about a different list.
 */
internal object PlaceBounds {

    /**
     * Below fifty metres the platform's own accuracy decides whether an alert fires, so a tighter
     * radius would not be a tighter promise. The ceiling is a sanity bound: past it Abu is
     * describing a city rather than a place.
     */
    const val MIN_RADIUS_METERS = 50f
    const val MAX_RADIUS_METERS = 20_000f

    fun isOnGlobe(latitude: Double, longitude: Double): Boolean =
        latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE

    fun holdsRadius(radiusMeters: Float): Boolean =
        radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS

    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
}
