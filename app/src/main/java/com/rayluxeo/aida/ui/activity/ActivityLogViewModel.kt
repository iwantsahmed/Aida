package com.rayluxeo.aida.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Activity screen: what Mira has done, most recent first, optionally narrowed to one category.
 *
 * The log is a read-only port, so it is observed here directly rather than wrapped in a
 * pass-through use case that would add a file and no meaning (§4). Nothing on this screen writes.
 * The chosen category switches which flow is observed; [flatMapLatest] tears down the old
 * subscription so a stale list can never arrive after Abu changes the filter.
 *
 * The clock is injected because every row carries a "when" (§4.4). Recent moments are worded
 * relative to now here in the view model; older ones defer to [TaskTimeFormatter], the one place
 * Mira words a day and a time.
 */
@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val activityLog: ActivityLogRepository,
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
    settings: SettingsRepository,
) : ViewModel() {

    /** The category Abu has filtered to, or null for everything. */
    private val selected = MutableStateFlow<ActivityCategory?>(null)

    private val language = settings.settings.map { it.languageMode.displayLanguage() }

    // The chosen category travels with its own rows rather than as a second input to the combine:
    // feeding both `selected` and a flow derived from it would briefly pair a new filter with the
    // previous filter's rows.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val listing: Flow<CategoryListing> = selected.flatMapLatest { category ->
        val source =
            if (category == null) activityLog.observeRecent() else activityLog.observeByCategory(category)
        source.map { rows -> CategoryListing(category, rows) }
    }

    val state: StateFlow<ActivityLogUiState> =
        combine(listing, language) { listing, language ->
            ActivityLogUiState(
                entries = listing.entries.map { it.toItem(language) },
                selectedCategory = listing.category,
                language = language,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ActivityLogUiState(),
        )

    /** Narrow to one category, or pass null for "All". Re-selecting the same filter is a no-op. */
    fun selectCategory(category: ActivityCategory?) {
        selected.value = category
    }

    private fun ActivityEntry.toItem(language: SpokenLanguage): ActivityLogItem {
        val words = activityLogWording(language)
        val now = clock.now()
        // Within a day, "5 minutes ago" reads at a glance; past that, the day and time say more.
        val moment = if (Duration.between(occurredAt, now) < Duration.ofDays(1)) {
            words.relativeTime(occurredAt, now)
        } else {
            times.momentLabel(occurredAt, language)
        }
        return ActivityLogItem(
            id = id,
            summary = summary,
            supporting = "${words.categoryLabel(category)}$DETAIL_SEPARATOR$moment",
        )
    }

    /** One filter's rows, kept together so the state can never show a filter beside stale rows. */
    private data class CategoryListing(
        val category: ActivityCategory?,
        val entries: List<ActivityEntry>,
    )

    private companion object {
        /** Language-neutral, so the supporting line reads the same in Bengali and in English. */
        const val DETAIL_SEPARATOR = " · "

        const val STOP_TIMEOUT_MS = 5_000L
    }
}
