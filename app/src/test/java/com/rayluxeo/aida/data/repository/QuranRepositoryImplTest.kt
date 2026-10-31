package com.rayluxeo.aida.data.repository

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.quran.QuranProgress
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QuranRepositoryImplTest {

    private val fakeSettings = object : AppSettingRepository {
        private val map = mutableMapOf<String, String>()
        private val flows = mutableMapOf<String, MutableStateFlow<String?>>()

        override fun observe(key: String): Flow<String?> =
            flows.getOrPut(key) { MutableStateFlow(map[key]) }

        override suspend fun get(key: String): AppResult<String?> =
            AppResult.Success(map[key])

        override suspend fun put(key: String, value: String): AppResult<Unit> {
            map[key] = value
            flows.getOrPut(key) { MutableStateFlow(null) }.value = value
            return AppResult.Success(Unit)
        }

        override suspend fun remove(key: String): AppResult<Unit> {
            map.remove(key)
            flows[key]?.value = null
            return AppResult.Success(Unit)
        }

        override suspend fun takeNextCounterValue(key: String, startAt: Long): AppResult<Long> =
            AppResult.Success(1L)
    }

    private val repository = QuranRepositoryImpl(fakeSettings)
    private val date = LocalDate.of(2026, 10, 29)

    @Test
    fun `default progress starts at page 1 with 2-page daily target`() = runTest {
        val progress = (repository.getProgress(date) as AppResult.Success).value
        assertThat(progress.currentPage).isEqualTo(1)
        assertThat(progress.dailyTarget).isEqualTo(2)
        assertThat(progress.pagesReadToday).isEqualTo(0)
        assertThat(progress.targetMet).isFalse()
    }

    @Test
    fun `marking pages read updates count, advances bookmark, and reaches daily target at 2 pages`() = runTest {
        // Mark page 1 read
        val step1 = (repository.markPageRead(1, date) as AppResult.Success).value
        assertThat(step1.pagesReadToday).isEqualTo(1)
        assertThat(step1.currentPage).isEqualTo(2) // Advanced bookmark
        assertThat(step1.targetMet).isFalse()

        // Mark page 2 read
        val step2 = (repository.markPageRead(2, date) as AppResult.Success).value
        assertThat(step2.pagesReadToday).isEqualTo(2)
        assertThat(step2.currentPage).isEqualTo(3) // Advanced bookmark
        assertThat(step2.targetMet).isTrue() // Daily 2-page target completed!
        assertThat(step2.completedDatesCount).isEqualTo(1)
    }

    @Test
    fun `reading the exact same page twice on the same day is idempotent`() = runTest {
        repository.markPageRead(1, date)
        val secondRead = (repository.markPageRead(1, date) as AppResult.Success).value

        assertThat(secondRead.pagesReadToday).isEqualTo(1)
    }

    @Test
    fun `continueFromPage continues from where the user stopped reading today`() = runTest {
        // User reads page 1 and page 2 today
        repository.markPageRead(1, date)
        val step2 = (repository.markPageRead(2, date) as AppResult.Success).value

        assertThat(step2.lastReadPageToday).isEqualTo(2)
        assertThat(step2.continueFromPage).isEqualTo(3)

        // If user views page 5 today, lastViewed updates continueFromPage to 5
        repository.saveLastViewedPage(5, date)
        val progress = (repository.getProgress(date) as AppResult.Success).value
        assertThat(progress.lastStoppedPageToday).isEqualTo(5)
        assertThat(progress.continueFromPage).isEqualTo(5)
    }

    @Test
    fun `marking page 604 read wraps bookmark around to page 1 and increments khatam count`() = runTest {
        val result = (repository.markPageRead(604, date) as AppResult.Success).value
        assertThat(result.currentPage).isEqualTo(1)
        assertThat(result.continueFromPage).isEqualTo(1)
        assertThat(result.khatamCount).isEqualTo(1)
    }
}
