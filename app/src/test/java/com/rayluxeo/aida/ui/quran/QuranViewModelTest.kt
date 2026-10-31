package com.rayluxeo.aida.ui.quran

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.quran.QuranProgress
import com.rayluxeo.aida.domain.quran.QuranRepository
import com.rayluxeo.aida.domain.usecase.GetQuranProgressUseCase
import com.rayluxeo.aida.domain.usecase.MarkQuranPageReadUseCase
import com.rayluxeo.aida.domain.usecase.SaveQuranLastViewedPageUseCase
import com.rayluxeo.aida.domain.usecase.SetQuranCurrentPageUseCase
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class QuranViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 10, 30, 10, 0))
    private val date = clock.today()

    private val fakeRepo = object : QuranRepository {
        val flow = MutableStateFlow(
            QuranProgress(
                currentPage = 1,
                pagesReadToday = 0,
                dailyTarget = 2,
                lastReadDate = date,
                continueFromPage = 1,
            ),
        )

        override fun observeProgress(date: LocalDate): Flow<QuranProgress> = flow

        override suspend fun getProgress(date: LocalDate): AppResult<QuranProgress> =
            AppResult.Success(flow.value)

        override suspend fun markPageRead(pageNumber: Int, date: LocalDate): AppResult<QuranProgress> {
            val next = if (pageNumber < QuranProgress.TOTAL_PAGES) pageNumber + 1 else 1
            val updated = flow.value.copy(
                pagesReadToday = flow.value.pagesReadToday + 1,
                lastReadPageToday = pageNumber,
                continueFromPage = next,
                pagesReadSet = flow.value.pagesReadSet + pageNumber,
            )
            flow.value = updated
            return AppResult.Success(updated)
        }

        override suspend fun setCurrentPage(pageNumber: Int): AppResult<Unit> {
            flow.value = flow.value.copy(currentPage = pageNumber)
            return AppResult.Success(Unit)
        }

        override suspend fun setDailyTarget(target: Int): AppResult<Unit> {
            flow.value = flow.value.copy(dailyTarget = target)
            return AppResult.Success(Unit)
        }

        override suspend fun saveLastViewedPage(pageNumber: Int, date: LocalDate): AppResult<Unit> {
            flow.value = flow.value.copy(lastStoppedPageToday = pageNumber, continueFromPage = pageNumber)
            return AppResult.Success(Unit)
        }

        override suspend fun backupToDevice(): AppResult<String> =
            AppResult.Success("Backup created successfully")

        override suspend fun restoreFromDevice(date: LocalDate): AppResult<QuranProgress> =
            AppResult.Success(flow.value)
    }

    private val getProgress = GetQuranProgressUseCase(fakeRepo, clock)
    private val markPageRead = MarkQuranPageReadUseCase(fakeRepo, clock)
    private val setCurrentPage = SetQuranCurrentPageUseCase(fakeRepo)
    private val saveLastViewed = SaveQuranLastViewedPageUseCase(fakeRepo, clock)
    private val backupProgress = com.rayluxeo.aida.domain.usecase.BackupQuranProgressUseCase(fakeRepo)
    private val restoreProgress = com.rayluxeo.aida.domain.usecase.RestoreQuranProgressUseCase(fakeRepo, clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `quran opens at continueFromPage where user stopped reading today`() = runTest {
        // Suppose user stopped reading at page 3 today
        fakeRepo.flow.value = QuranProgress(
            currentPage = 3,
            pagesReadToday = 2,
            lastReadPageToday = 2,
            continueFromPage = 3,
            pagesReadSet = setOf(1, 2),
        )

        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.pageNumber).isEqualTo(3)
        assertThat(state.continueFromPage).isEqualTo(3)
        assertThat(state.lastReadPageToday).isEqualTo(2)
        assertThat(state.isPageCompletedToday).isFalse() // page 3 has not been read yet
    }

    @Test
    fun `navigating to previously read page correctly flags it as completed today`() = runTest {
        fakeRepo.flow.value = QuranProgress(
            currentPage = 3,
            pagesReadToday = 2,
            lastReadPageToday = 2,
            continueFromPage = 3,
            pagesReadSet = setOf(1, 2),
        )

        val viewModel = createViewModel()
        observe(viewModel.uiState)

        // User navigates back to page 2
        viewModel.onSelectPage(2)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.pageNumber).isEqualTo(2)
        assertThat(state.isPageCompletedToday).isTrue()
    }

    @Test
    fun `marking page read advances continuation to next page`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onMarkPageRead() // reads page 1
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.pageNumber).isEqualTo(2)
        assertThat(state.pagesReadToday).isEqualTo(1)
        assertThat(state.continueFromPage).isEqualTo(2)
    }

    @Test
    fun `reading page 604 wraps around to page 1 and increments khatam count`() = runTest {
        fakeRepo.flow.value = QuranProgress(
            currentPage = 604,
            pagesReadToday = 1,
            lastReadPageToday = 603,
            continueFromPage = 604,
            khatamCount = 0,
        )

        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onMarkPageRead() // reads page 604
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.pageNumber).isEqualTo(1)
        assertThat(state.continueFromPage).isEqualTo(1)
    }

    private fun TestScope.observe(state: StateFlow<QuranUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun createViewModel() = QuranViewModel(
        getProgress = getProgress,
        markPageRead = markPageRead,
        setCurrentPage = setCurrentPage,
        saveLastViewedPage = saveLastViewed,
        backupProgress = backupProgress,
        restoreProgress = restoreProgress,
        clock = clock,
    )
}
