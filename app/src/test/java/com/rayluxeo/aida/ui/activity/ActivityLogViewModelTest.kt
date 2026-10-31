package com.rayluxeo.aida.ui.activity

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Activity screen's view model, assembled from hand-written fakes. Hilt's local-test transform
 * does not run under AGP 9 (§7.4), so the collaborators are constructed directly.
 */
class ActivityLogViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 10, 10, 12, 0))
    private val now: Instant = clock.now()
    private val log = FakeActivityLogRepository()
    private val settings = FakeSettingsRepository()
    private val times = TaskTimeFormatter(clock)

    private val fiveMinutesAgo: Instant = now.minus(Duration.ofMinutes(5))
    private val twoHoursAgo: Instant = now.minus(Duration.ofHours(2))

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `entries reach the state, most recent first`() = runTest {
        log.seed(
            entry(id = 1L, category = ActivityCategory.TASK, summary = "Marked a task done", at = twoHoursAgo),
            entry(id = 2L, category = ActivityCategory.MONEY, summary = "Logged 500 taka", at = fiveMinutesAgo),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            val loaded = expectMostRecentItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.selectedCategory).isNull()
            assertThat(loaded.entries.map { it.summary })
                .containsExactly("Logged 500 taka", "Marked a task done")
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a category switches the observed source and filters the rows`() = runTest {
        log.seed(
            entry(id = 1L, category = ActivityCategory.TASK, summary = "Marked a task done", at = twoHoursAgo),
            entry(id = 2L, category = ActivityCategory.MONEY, summary = "Logged 500 taka", at = fiveMinutesAgo),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().entries).hasSize(2)

            viewModel.selectCategory(ActivityCategory.MONEY)

            val filtered = expectMostRecentItem()
            assertThat(filtered.selectedCategory).isEqualTo(ActivityCategory.MONEY)
            assertThat(filtered.entries.map { it.summary }).containsExactly("Logged 500 taka")
            cancelAndIgnoreRemainingEvents()
        }

        // The switch is a real change of source, not a view-side filter over one list.
        assertThat(log.observeByCategoryCalls).isAtLeast(1)
        assertThat(log.lastCategory).isEqualTo(ActivityCategory.MONEY)
    }

    @Test
    fun `the language follows settings and words the row in it`() = runTest {
        settings.setLanguage(LanguageMode.BANGLA)
        log.seed(
            entry(id = 1L, category = ActivityCategory.MONEY, summary = "৫০০ টাকা লেখা হলো", at = fiveMinutesAgo),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            val loaded = expectMostRecentItem()
            assertThat(loaded.language).isEqualTo(SpokenLanguage.BENGALI)
            // The category name and the relative time, both in Bengali, with Bengali digits.
            assertThat(loaded.entries.single().supporting).isEqualTo("টাকা · ৫ মিনিট আগে")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel() = ActivityLogViewModel(
        activityLog = log,
        clock = clock,
        times = times,
        settings = settings,
    )

    private fun entry(id: Long, category: ActivityCategory, summary: String, at: Instant) =
        ActivityEntry(
            id = id,
            category = category,
            action = "test.$id",
            summary = summary,
            occurredAt = at,
        )
}

/**
 * The log in memory. [observeByCategoryCalls] and [lastCategory] record that a category filter
 * reached storage, so a test can tell a real source switch from a filter applied over one list.
 */
private class FakeActivityLogRepository : ActivityLogRepository {

    private val all = MutableStateFlow<List<ActivityEntry>>(emptyList())

    var observeByCategoryCalls = 0
        private set
    var lastCategory: ActivityCategory? = null
        private set

    fun seed(vararg entries: ActivityEntry) {
        all.value = entries.toList()
    }

    override fun observeRecent(limit: Int): Flow<List<ActivityEntry>> =
        all.map { rows -> rows.sortedByDescending { it.occurredAt }.take(limit) }

    override fun observeByCategory(category: ActivityCategory, limit: Int): Flow<List<ActivityEntry>> {
        observeByCategoryCalls++
        lastCategory = category
        return all.map { rows ->
            rows.filter { it.category == category }.sortedByDescending { it.occurredAt }.take(limit)
        }
    }

    override fun observeForSubject(subject: ActivitySubject): Flow<List<ActivityEntry>> =
        all.map { rows -> rows.filter { it.subject == subject } }

    override suspend fun between(from: Instant, to: Instant): AppResult<List<ActivityEntry>> =
        AppResult.Success(all.value.filter { !it.occurredAt.isBefore(from) && it.occurredAt.isBefore(to) })

    override suspend fun record(entry: ActivityEntry): AppResult<ActivityEntry> {
        all.update { it + entry }
        return AppResult.Success(entry)
    }
}

/** Settings in memory; only the language is exercised here. */
private class FakeSettingsRepository : SettingsRepository {

    private val stored = MutableStateFlow(MiraSettings())
    override val settings: Flow<MiraSettings> = stored.asStateFlow()

    override suspend fun current(): MiraSettings = stored.value

    override suspend fun setLanguageMode(mode: LanguageMode) = setLanguage(mode)

    override suspend fun setActivationSound(enabled: Boolean) = Unit

    override suspend fun setHapticFeedback(enabled: Boolean) = Unit

    override suspend fun setOnboardingComplete(complete: Boolean) = Unit

    fun setLanguage(mode: LanguageMode) {
        stored.update { it.copy(languageMode = mode) }
    }
}
