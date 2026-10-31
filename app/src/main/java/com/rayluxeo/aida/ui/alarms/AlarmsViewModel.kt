package com.rayluxeo.aida.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.repository.AlarmRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.usecase.DeleteAlarmUseCase
import com.rayluxeo.aida.domain.usecase.SetAlarmUseCase
import com.rayluxeo.aida.domain.usecase.ToggleAlarmUseCase
import com.rayluxeo.aida.domain.usecase.UpdateAlarmUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The alarm clock.
 *
 * Two things here are not presentation decisions dressed up as ones.
 *
 * The first is [AlarmsUiState.canScheduleExact]. Android withdraws exact alarms at any moment and
 * cancels every future one it was holding when it does, so the answer is watched rather than read
 * once: a row must never sit there looking armed when nothing is armed behind it.
 *
 * The second is that a row's supporting line is built from what the alarm actually carries — a
 * snooze, a next moment, or neither. A switched-off alarm says so in words rather than being left
 * to be inferred from a switch that TalkBack reads separately from the line above it.
 */
@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarms: AlarmRepository,
    private val setAlarm: SetAlarmUseCase,
    private val updateAlarm: UpdateAlarmUseCase,
    private val toggleAlarm: ToggleAlarmUseCase,
    private val deleteAlarm: DeleteAlarmUseCase,
    private val exactAlarms: ExactAlarmAccess,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
    private val renderer: ResponseRenderer,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    /**
     * The permission, starting from what it is now: [ExactAlarmAccess.changes] reports only
     * changes, so without the opening value the combine below would never produce a first state.
     */
    private val exactAccess: Flow<Boolean> =
        exactAlarms.changes.onStart { emit(exactAlarms.canScheduleExact()) }

    val uiState: StateFlow<AlarmsUiState> = combine(
        local,
        alarms.observeAll(),
        settings.settings,
        exactAccess,
    ) { state, stored, preferences, canScheduleExact ->
        val language = preferences.languageMode.displayLanguage()
        AlarmsUiState(
            isLoading = false,
            language = language,
            alarms = stored.map { it.toRow(language) },
            // Worded here rather than held in the editor, so switching language while the sheet
            // is open rewords the time along with everything else behind it.
            editor = state.editor?.let { it.copy(timeLabel = times.timeLabel(it.time, language)) },
            canScheduleExact = canScheduleExact,
            notice = state.notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AlarmsUiState(),
    )

    /** Opens the sheet on the next round hour, which is what Abu is usually reaching for. */
    fun onAddAlarm() = local.update {
        it.copy(editor = AlarmEditorUiState(time = nextRoundHour()))
    }

    fun onEditAlarm(id: Long) {
        viewModelScope.launch {
            when (val found = alarms.byId(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        editor = AlarmEditorUiState(
                            id = found.value.id,
                            label = found.value.label,
                            time = found.value.at,
                            repeatDays = found.value.repeatDays,
                        ),
                    )
                }
                is AppResult.Failure -> showProblem(found.error.code)
            }
        }
    }

    fun onCloseEditor() = local.update { it.copy(editor = null) }

    fun onEditorLabelChange(label: String) = updateEditor { it.copy(label = label) }

    fun onEditorTimeChange(time: LocalTime) = updateEditor { it.copy(time = time) }

    fun onToggleRepeatDay(day: DayOfWeek) = updateEditor { editor ->
        val days = if (day in editor.repeatDays) editor.repeatDays - day else editor.repeatDays + day
        editor.copy(repeatDays = days)
    }

    /** The three shortcuts under the weekday chips: once, every day, and Monday to Friday. */
    fun onSelectRepeatPreset(days: Set<DayOfWeek>) = updateEditor { it.copy(repeatDays = days) }

    fun onSaveAlarm() {
        val editor = local.value.editor ?: return
        local.update { it.copy(editor = editor.copy(isSaving = true)) }
        viewModelScope.launch {
            val language = language()
            // An alarm with no label is ordinary — Abu's seven o'clock is not "for" anything — so
            // the blank is STORED as a blank and named at the point it is read, the way
            // TimersViewModel names an untitled timer. Writing the word in here froze one
            // language into the row: an alarm saved in English still read "Alarm" after Abu
            // switched to বাংলা, and it disagreed with what Mira said when she set the same alarm
            // by voice. The row rendering below already does this correctly.
            val label = editor.label.trim()
            val saved = if (editor.id == null) {
                setAlarm(label, editor.time, editor.repeatDays)
            } else {
                updateAlarm(editor.id, label, editor.time, editor.repeatDays)
            }
            when (saved) {
                is AppResult.Success -> local.update {
                    it.copy(
                        editor = null,
                        notice = AlarmsNotice(
                            message = alarmsWording(language).alarmSaved,
                            tone = AlarmsNoticeTone.CONFIRM,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    local.update { it.copy(editor = editor.copy(isSaving = false)) }
                    showProblem(saved.error.code)
                }
            }
        }
    }

    fun onDeleteAlarm() {
        val id = local.value.editor?.id ?: return
        viewModelScope.launch {
            val language = language()
            when (val removed = deleteAlarm(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        editor = null,
                        notice = AlarmsNotice(
                            message = alarmsWording(language).alarmDeleted,
                            tone = AlarmsNoticeTone.CONFIRM,
                        ),
                    )
                }
                is AppResult.Failure -> showProblem(removed.error.code)
            }
        }
    }

    /**
     * Nothing is said when the switch lands: the row's own line changes under Abu's thumb and
     * says it. A refusal is different — the switch would otherwise appear to have done nothing.
     */
    fun onSetEnabled(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            val switched = toggleAlarm(id, isEnabled)
            if (switched is AppResult.Failure) showProblem(switched.error.code)
        }
    }

    fun onDismissNotice() = local.update { it.copy(notice = null) }

    private fun updateEditor(transform: (AlarmEditorUiState) -> AlarmEditorUiState) =
        local.update { state -> state.editor?.let { state.copy(editor = transform(it)) } ?: state }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()

    private suspend fun showProblem(code: MiraErrorCode) {
        val language = language()
        local.update {
            it.copy(
                notice = AlarmsNotice(
                    message = renderer.display(MiraResponse.Problem(code), language),
                    tone = AlarmsNoticeTone.PROBLEM,
                ),
            )
        }
    }

    private fun nextRoundHour(): LocalTime =
        clock.localNow().toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1)

    private fun Alarm.toRow(language: SpokenLanguage): AlarmRow {
        val words = alarmsWording(language)
        // The order is the whole point. A switch Abu turned off says so; an alarm he left on
        // that the OS refused to hold says THAT, because reading "Off" would blame him for a
        // permission Android withdrew, and reading a ring time would promise one it will not keep.
        val supporting = when {
            !isEnabled -> words.alarmOff
            snoozedUntil != null -> words.snoozedUntil(times.momentLabel(snoozedUntil, language))
            nextTriggerAt == null -> words.alarmNotArmed
            else -> words.ringsAt(times.momentLabel(nextTriggerAt, language))
        }
        return AlarmRow(
            id = id,
            label = label.ifBlank { words.untitledAlarm },
            timeLabel = times.timeLabel(at, language),
            repeatLabel = words.repeatSummary(repeatDays),
            supporting = supporting,
            isEnabled = isEnabled,
        )
    }

    private data class LocalState(
        val editor: AlarmEditorUiState? = null,
        val notice: AlarmsNotice? = null,
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
