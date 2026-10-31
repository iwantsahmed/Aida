package com.rayluxeo.aida.domain.salah

/**
 * The prayers Mira tracks. The first five are the obligatory daily prayers; [TAHAJJUD] is a
 * voluntary night prayer and is deliberately listed apart — it is never counted inside the
 * five-prayer completion the home card and the reports show (Abu's own instruction).
 *
 * Sunrise is a prayer-time boundary Mira computes and shows, but it is not a prayer and so is not
 * in this enum.
 */
enum class Prayer {
    FAJR,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA,
    TAHAJJUD,
    ;

    /** True for the five daily obligatory prayers. [TAHAJJUD] is voluntary and never obligatory. */
    val isObligatory: Boolean get() = this != TAHAJJUD

    companion object {
        /** The five obligatory prayers, in the order they fall across the day. */
        val OBLIGATORY: List<Prayer> = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    }
}
