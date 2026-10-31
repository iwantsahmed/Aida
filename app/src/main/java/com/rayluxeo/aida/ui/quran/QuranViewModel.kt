package com.rayluxeo.aida.ui.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.quran.QuranPageProvider
import com.rayluxeo.aida.domain.quran.QuranProgress
import com.rayluxeo.aida.domain.usecase.BackupQuranProgressUseCase
import com.rayluxeo.aida.domain.usecase.GetQuranProgressUseCase
import com.rayluxeo.aida.domain.usecase.MarkQuranPageReadUseCase
import com.rayluxeo.aida.domain.usecase.RestoreQuranProgressUseCase
import com.rayluxeo.aida.domain.usecase.SaveQuranLastViewedPageUseCase
import com.rayluxeo.aida.domain.usecase.SetQuranCurrentPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class QuranViewModel @Inject constructor(
    private val getProgress: GetQuranProgressUseCase,
    private val markPageRead: MarkQuranPageReadUseCase,
    private val setCurrentPage: SetQuranCurrentPageUseCase,
    private val saveLastViewedPage: SaveQuranLastViewedPageUseCase,
    private val backupProgress: BackupQuranProgressUseCase,
    private val restoreProgress: RestoreQuranProgressUseCase,
    private val clock: MiraClock,
) : ViewModel() {

    private val selectedPage = MutableStateFlow<Int?>(null)
    private val localNotice = MutableStateFlow<String?>(null)

    val uiState: StateFlow<QuranUiState> = combine(
        getProgress.observe(clock.today()),
        selectedPage,
        localNotice,
    ) { progress, customPage, notice ->
        val activePageNum = customPage ?: progress.continueFromPage
        val pageContent = QuranPageProvider.page(activePageNum)
        val isCompleted = progress.pagesReadSet.contains(activePageNum)
        QuranUiState(
            isLoading = false,
            pageNumber = activePageNum,
            totalPages = QuranProgress.TOTAL_PAGES,
            page = pageContent,
            pagesReadToday = progress.pagesReadToday,
            dailyTarget = progress.dailyTarget,
            completedDaysCount = progress.completedDatesCount,
            isPageCompletedToday = isCompleted,
            lastReadPageToday = progress.lastReadPageToday,
            continueFromPage = progress.continueFromPage,
            khatamCount = progress.khatamCount,
            notice = notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = QuranUiState(isLoading = true),
    )

    init {
        viewModelScope.launch {
            val progress = getProgress(clock.today()).getOrNull()
            if (progress != null && selectedPage.value == null) {
                selectedPage.value = progress.continueFromPage
                saveLastViewedPage(progress.continueFromPage, clock.today())
            }
        }
    }

    fun onNextPage() {
        val current = uiState.value.pageNumber
        // Looping at page 604 back to page 1
        val next = if (current < QuranProgress.TOTAL_PAGES) current + 1 else 1
        selectedPage.value = next
        viewModelScope.launch { saveLastViewedPage(next, clock.today()) }
    }

    fun onPreviousPage() {
        val current = uiState.value.pageNumber
        val prev = if (current > 1) current - 1 else QuranProgress.TOTAL_PAGES
        selectedPage.value = prev
        viewModelScope.launch { saveLastViewedPage(prev, clock.today()) }
    }

    fun onSelectPage(page: Int) {
        val clamped = page.coerceIn(1, QuranProgress.TOTAL_PAGES)
        selectedPage.value = clamped
        viewModelScope.launch { saveLastViewedPage(clamped, clock.today()) }
    }

    fun onResumeFromToday() {
        val target = uiState.value.continueFromPage
        onSelectPage(target)
    }

    fun onMarkPageRead() {
        val pageNum = uiState.value.pageNumber
        viewModelScope.launch {
            val result = markPageRead(pageNum, clock.today())
            val updated = result.getOrNull()
            if (updated != null) {
                if (pageNum >= QuranProgress.TOTAL_PAGES) {
                    localNotice.value = "আলহামদুলিল্লাহ! সম্পূর্ণ কুরআনুল কারীম খতম হয়েছে (খতম: ${updated.khatamCount})। পুনরায় পৃষ্ঠা ১ (সূরা আল-ফাতিহা) থেকে শুরু হয়েছে।"
                } else if (updated.targetMet) {
                    localNotice.value = "মাশাআল্লাহ! আজকের ২ পৃষ্ঠা তিলাওয়াতের লক্ষ্য পূরণ হয়েছে।"
                } else {
                    localNotice.value = "পৃষ্ঠা $pageNum সম্পন্ন হয়েছে। আজকের অগ্রগতি: ${updated.pagesReadToday}/${updated.dailyTarget} পৃষ্ঠা।"
                }
                // Automatically update selectedPage to the next continuation page
                val next = updated.continueFromPage
                selectedPage.value = next
                saveLastViewedPage(next, clock.today())
            }
        }
    }

    fun onBackupProgress() {
        viewModelScope.launch {
            when (val res = backupProgress()) {
                is AppResult.Success -> {
                    localNotice.value = "ডিভাইসের স্টোরেজে কুরআনের অগ্রগতি ব্যাকআপ সম্পন্ন হয়েছে।"
                }
                is AppResult.Failure -> {
                    localNotice.value = "ব্যাকআপ সংরক্ষণ করা যায়নি।"
                }
            }
        }
    }

    fun onRestoreProgress() {
        viewModelScope.launch {
            when (val res = restoreProgress(clock.today())) {
                is AppResult.Success -> {
                    val restored = res.value
                    selectedPage.value = restored.continueFromPage
                    localNotice.value = "ডিভাইস ব্যাকআপ থেকে অগ্রগতি রিস্টোর হয়েছে (পৃষ্ঠা ${restored.continueFromPage}, খতম: ${restored.khatamCount})।"
                }
                is AppResult.Failure -> {
                    localNotice.value = "ডিভাইসে কোনো ব্যাকআপ ফাইল পাওয়া যায়নি।"
                }
            }
        }
    }

    fun onDismissNotice() {
        localNotice.value = null
    }
}
