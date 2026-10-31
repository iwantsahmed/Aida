package com.rayluxeo.aida.ui.quran

import com.rayluxeo.aida.domain.quran.QuranPage
import com.rayluxeo.aida.domain.quran.QuranPageProvider
import com.rayluxeo.aida.domain.quran.QuranProgress

/**
 * State for the Quran recitation and daily 2-page target tracker screen.
 */
data class QuranUiState(
    val isLoading: Boolean = false,
    val pageNumber: Int = 1,
    val totalPages: Int = QuranProgress.TOTAL_PAGES,
    val page: QuranPage = QuranPageProvider.page(1),
    val pagesReadToday: Int = 0,
    val dailyTarget: Int = QuranProgress.DEFAULT_DAILY_TARGET,
    val completedDaysCount: Int = 0,
    val isPageCompletedToday: Boolean = false,
    val lastReadPageToday: Int? = null,
    val continueFromPage: Int = 1,
    val khatamCount: Int = 0,
    val notice: String? = null,
) {
    val targetMet: Boolean get() = pagesReadToday >= dailyTarget
    val progressRatio: Float
        get() = (pagesReadToday.toFloat() / dailyTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val remainingPagesToday: Int get() = (dailyTarget - pagesReadToday).coerceAtLeast(0)
}
