package com.rayluxeo.aida.ui.meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.repository.MeetingRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class MeetingsUiState(
    /** Which language the screen's own labels are written in. Follows Abu's setting. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val selectedMeeting: Meeting? = null,
    val isAddSheetOpen: Boolean = false,
    /** The date and time the Add sheet opens on — the next hour, from [MiraClock], never the wall
     *  clock read in the UI (§4). It is set when the sheet opens, so the placeholder is never shown. */
    val defaultStart: LocalDateTime = LocalDateTime.of(2026, 1, 1, 9, 0),
    /** Already worded in Abu's language when it was raised; the screen shows it as-is. */
    val notice: String? = null,
)

/**
 * Meetings as a calendar: schedule one, see what is coming up. There is no in-app recorder or
 * transcript extraction — the previous version analysed a hardcoded sample transcript and invented
 * tasks and decisions regardless of what was said (a §12 fabrication), so it was removed rather
 * than left pretending. Real meeting-note extraction, from a transcript Abu actually has, is
 * tracked in docs/FEATURE_CHECKLIST.md.
 */
@HiltViewModel
class MeetingsViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) : ViewModel() {

    /** Everything the screen owns rather than the settings store: the sheet, the notice. */
    private val local = MutableStateFlow(MeetingsUiState())

    /**
     * The screen's state with Abu's language laid over it, so a label and the data beside it are
     * never in two languages. The notice is worded when it is raised (it names a meeting whose
     * title does not change with language), so only [MeetingsUiState.language] is folded in here.
     */
    val uiState: StateFlow<MeetingsUiState> = combine(
        local,
        settings.settings,
    ) { state, preferences ->
        state.copy(language = preferences.languageMode.displayLanguage())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = MeetingsUiState(),
    )

    val meetings: StateFlow<List<Meeting>> = meetingRepository.observeUpcoming()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), emptyList())

    fun openNewMeetingSheet() {
        // The sheet opens on the next whole hour — a meeting default Abu is likely to keep, and a
        // real starting time instead of "now this instant".
        val start = clock.localNow().plusHours(1).truncatedTo(ChronoUnit.HOURS)
        local.update { it.copy(isAddSheetOpen = true, selectedMeeting = null, defaultStart = start) }
    }

    fun closeAddSheet() {
        local.update { it.copy(isAddSheetOpen = false) }
    }

    fun createMeeting(
        title: String,
        location: String?,
        clientId: Long?,
        projectId: Long?,
        startsAt: LocalDateTime,
    ) {
        viewModelScope.launch {
            val language = language()
            val now = clock.now()
            val meeting = Meeting(
                title = title,
                location = location,
                clientId = clientId,
                projectId = projectId,
                // The time Abu chose, resolved to an instant in his own zone — a meeting can be set
                // for later, which is the one thing a scheduler must be able to do.
                startsAt = startsAt.atZone(clock.zone()).toInstant(),
                createdAt = now,
                updatedAt = now,
            )

            when (val result = meetingRepository.create(meeting)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        isAddSheetOpen = false,
                        notice = meetingsWording(language).meetingScheduled(result.value.title),
                    )
                }
                // The sheet stays open on failure so Abu can try again, and the notice says the save
                // did not take — rather than closing in silence on a meeting that was never stored.
                is AppResult.Failure -> local.update {
                    it.copy(notice = meetingsWording(language).saveFailed(result.error.detail))
                }
            }
        }
    }

    fun dismissNotice() {
        local.update { it.copy(notice = null) }
    }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
