package com.rayluxeo.aida.domain.salah

/**
 * How to place Fajr and Isha where, at high latitudes in summer, the sun never dips far enough
 * below the horizon for true twilight — so the angle-based formula has no solution. Each rule
 * estimates the missing twilight as a portion of the night between sunset and sunrise.
 *
 * Bangladesh is well south of where this ever bites, so [MIDDLE_OF_NIGHT] is a safe default that
 * simply never activates there; it is here so Mira stays correct if Abu travels.
 */
enum class HighLatitudeRule {
    /** The night is split in half: twilight may not run past the midpoint. */
    MIDDLE_OF_NIGHT,

    /** Fajr and Isha are each held within one seventh of the night from sunrise / sunset. */
    SEVENTH_OF_NIGHT,

    /** The twilight portion scales with the method's own angle (angle / 60 of the night). */
    TWILIGHT_ANGLE,

    /** No adjustment — use the raw angle even where it yields no time. */
    NONE,
}
