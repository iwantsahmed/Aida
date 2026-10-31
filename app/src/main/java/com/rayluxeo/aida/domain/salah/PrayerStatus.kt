package com.rayluxeo.aida.domain.salah

/**
 * Where an obligatory prayer stands for Abu on a given day. The language around these is never
 * judgemental (his instruction): a prayer that has not been logged is [NOT_YET] or [NOT_RECORDED],
 * never a failure, and only [MISSED] — which Abu sets himself, or which Mira infers once the whole
 * day has passed unlogged — carries that meaning.
 */
enum class PrayerStatus {
    /** Nothing known yet — the default for a day Abu has not touched. */
    NOT_RECORDED,

    /** Its time has not arrived. */
    UPCOMING,

    /** Its time is in, but it has not been logged; the gentle in-between state. */
    NOT_YET,

    PRAYED,

    /** Prayed, but after its time had passed. Still counts as completed. */
    PRAYED_LATE,

    MISSED,
    ;

    /** Counts toward the five-prayer completion the card and the reports show. */
    val isCompleted: Boolean get() = this == PRAYED || this == PRAYED_LATE
}

/**
 * Tahajjud's own, smaller set of states. It is a voluntary night prayer, so it is never "missed" —
 * only prayed or not — and it is never counted inside the obligatory five.
 */
enum class TahajjudStatus {
    NOT_RECORDED,
    PRAYED,
    NOT_PRAYED,
}

/** How a tracking entry came to be, so a voice log and a tap can be told apart later. */
enum class TrackingSource {
    MANUAL,
    VOICE,
    CHECK_IN,
    AUTOMATIC,
}

/**
 * When Mira offers to remind Abu for Tahajjud. The last third of the night is the default and the
 * most virtuous window; none of these is presented as a religious requirement (his instruction).
 */
enum class TahajjudReminderMode {
    /** At the start of the last third of the night. */
    LAST_THIRD,

    /** Fifteen minutes into the last third. */
    FIFTEEN_AFTER_LAST_THIRD,

    /** Thirty minutes into the last third. */
    THIRTY_AFTER_LAST_THIRD,

    /** A specific minute Abu chose, inside the night window. */
    CUSTOM,

    DISABLED,
}
