package com.rayluxeo.aida.domain.quran

import java.time.LocalDate

/**
 * An individual Ayah with authentic Arabic text with tashkeel,
 * Arabic pronunciation written in Bengali (বাংলা উচ্চারণ),
 * and Bengali meaning (বাংলা অর্থ).
 */
data class QuranAyah(
    val numberInSurah: Int,
    val arabic: String,
    val banglaTransliteration: String,
    val banglaMeaning: String,
)

/**
 * A page in the Quran, representing a Madani Mushaf page.
 */
data class QuranPage(
    val pageNumber: Int,
    val juzNumber: Int,
    val surahNameBangla: String,
    val surahNameArabic: String,
    val surahNumber: Int,
    val ayahs: List<QuranAyah>,
    val bismillahPrecedes: Boolean = false,
    val ayahRange: String = "",
)

/**
 * Recitation progress for Quran.
 *
 * Standard target is at least 2 pages per day.
 * [khatamCount] tracks how many complete recitations of the 604-page Quran Abu has accomplished.
 */
data class QuranProgress(
    val currentPage: Int = 1,
    val pagesReadToday: Int = 0,
    val dailyTarget: Int = DEFAULT_DAILY_TARGET,
    val lastReadDate: LocalDate? = null,
    val completedDatesCount: Int = 0,
    val pagesReadSet: Set<Int> = emptySet(),
    val lastReadPageToday: Int? = null,
    val lastStoppedPageToday: Int? = null,
    val continueFromPage: Int = currentPage,
    val khatamCount: Int = 0,
) {
    val targetMet: Boolean get() = pagesReadToday >= dailyTarget
    val progressRatio: Float
        get() = (pagesReadToday.toFloat() / dailyTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val remainingPagesToday: Int get() = (dailyTarget - pagesReadToday).coerceAtLeast(0)

    companion object {
        const val DEFAULT_DAILY_TARGET = 2
        const val TOTAL_PAGES = 604
    }
}
