package com.rayluxeo.aida.ui.alarms

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSwitch
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.ui.response.WEEKDAY_SET
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.delay

/** How long a notice stays up before it clears itself. Long enough to read. */
private const val NOTICE_MILLIS = 6_000L

/**
 * The alarm clock.
 *
 * A row shows the time, the days it repeats on and, underneath, what is actually true of it — the
 * next ring, a snooze, or that it is switched off. When Android has withheld exact alarms the
 * screen says so at the top rather than leaving every switch looking armed to the minute.
 */
@Composable
fun AlarmsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AlarmsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) { AlarmsCallbacks(viewModel) }

    AlarmsContent(state = state, callbacks = callbacks, onBack = onBack, modifier = modifier)
}

/** Everything the screen can ask the view model to do, as one value. */
@Immutable
internal class AlarmsCallbacks(
    val onAddAlarm: () -> Unit,
    val onEditAlarm: (Long) -> Unit,
    val onCloseEditor: () -> Unit,
    val onLabelChange: (String) -> Unit,
    val onTimeChange: (LocalTime) -> Unit,
    val onToggleRepeatDay: (DayOfWeek) -> Unit,
    val onSelectRepeatPreset: (Set<DayOfWeek>) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onSetEnabled: (Long, Boolean) -> Unit,
    val onDismissNotice: () -> Unit,
) {
    constructor(viewModel: AlarmsViewModel) : this(
        onAddAlarm = viewModel::onAddAlarm,
        onEditAlarm = viewModel::onEditAlarm,
        onCloseEditor = viewModel::onCloseEditor,
        onLabelChange = viewModel::onEditorLabelChange,
        onTimeChange = viewModel::onEditorTimeChange,
        onToggleRepeatDay = viewModel::onToggleRepeatDay,
        onSelectRepeatPreset = viewModel::onSelectRepeatPreset,
        onSave = viewModel::onSaveAlarm,
        onDelete = viewModel::onDeleteAlarm,
        onSetEnabled = viewModel::onSetEnabled,
        onDismissNotice = viewModel::onDismissNotice,
    )
}

@Composable
private fun AlarmsContent(
    state: AlarmsUiState,
    callbacks: AlarmsCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val words = alarmsWording(state.language)
    val context = LocalContext.current

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        when (notice.tone) {
            AlarmsNoticeTone.CONFIRM -> haptics.confirm()
            AlarmsNoticeTone.PROBLEM -> haptics.error()
        }
        delay(NOTICE_MILLIS)
        callbacks.onDismissNotice()
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = words.alarmsTitle,
                onBack = onBack,
                backContentDescription = words.back,
                action = {
                    MiraIconButton(
                        icon = MiraIcons.Add,
                        contentDescription = words.addAlarm,
                        onClick = callbacks.onAddAlarm,
                    )
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            if (state.isLoading) {
                MiraListSkeleton()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!state.canScheduleExact) {
                        ExactAlarmsNotice(
                            words = words,
                            onOpenSettings = { context.openExactAlarmSettings() },
                            modifier = Modifier.padding(top = spacing.xs, bottom = spacing.sm),
                        )
                    }
                    if (state.isEmpty) {
                        // Centred in the space it owns. Mira's screens are empty most of the
                        // time, and an empty state left to float in the top third of the page
                        // reads as a screen that stopped loading rather than as a decision.
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiraEmptyState(
                                icon = MiraIcons.Alarm,
                                message = words.noAlarms,
                                actionText = words.addFirstAlarm,
                                onAction = callbacks.onAddAlarm,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            // The foot of the list clears the notice that settles over it, so a
                            // confirmation never lands on top of the last alarm. The figure is
                            // the bar's own, not this screen's guess at it.
                            contentPadding = PaddingValues(
                                top = spacing.xs,
                                bottom = alarmsNoticeInset,
                            ),
                        ) {
                            itemsIndexed(state.alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                                AlarmListRow(
                                    alarm = alarm,
                                    words = words,
                                    onOpen = { callbacks.onEditAlarm(alarm.id) },
                                    onSetEnabled = { enabled -> callbacks.onSetEnabled(alarm.id, enabled) },
                                )
                                // Between rows, never after the last one: a hairline under the
                                // final alarm rules off a list that has already ended.
                                if (index < state.alarms.lastIndex) MiraDivider()
                            }
                        }
                    }
                }
            }

            AlarmsNoticeBar(
                notice = state.notice,
                words = words,
                onDismiss = callbacks.onDismissNotice,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.editor?.let { editor ->
        AlarmEditorSheet(
            state = editor,
            words = words,
            onLabelChange = callbacks.onLabelChange,
            onTimeChange = callbacks.onTimeChange,
            onToggleRepeatDay = callbacks.onToggleRepeatDay,
            onSelectRepeatPreset = callbacks.onSelectRepeatPreset,
            onSave = callbacks.onSave,
            onDelete = callbacks.onDelete,
            onDismiss = callbacks.onCloseEditor,
        )
    }
}

/**
 * One alarm.
 *
 * The time is the title because it is what Abu scans for; the label and the repeat sit under it,
 * and the switch is the only thing on the row that is not a way into the editor.
 */
@Composable
private fun AlarmListRow(
    alarm: AlarmRow,
    words: AlarmsWording,
    onOpen: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraListRow(
        title = words.rowTitle(alarm.timeLabel, alarm.label),
        modifier = modifier,
        supporting = words.rowSupporting(alarm.repeatLabel, alarm.supporting),
        onClick = onOpen,
        trailing = {
            MiraSwitch(
                checked = alarm.isEnabled,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.semantics {
                    contentDescription = words.toggleAlarm(alarm.label)
                },
            )
        },
    )
}

/**
 * Adding or changing an alarm.
 *
 * Delete is only offered for an alarm that exists. It sits apart from Save, in the danger colour,
 * because the two are a hand's width from each other on a phone.
 *
 * The sheet opens fully rather than half way. Its content runs from a display-scale time down to
 * Save, and [MiraBottomSheet] does not scroll its content, so a sheet that stopped at the half
 * fold would put the one button that commits the alarm off the bottom of the screen with no way
 * to reach it but a drag Abu has no reason to guess at.
 */
@Composable
private fun AlarmEditorSheet(
    state: AlarmEditorUiState,
    words: AlarmsWording,
    onLabelChange: (String) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onToggleRepeatDay: (DayOfWeek) -> Unit,
    onSelectRepeatPreset: (Set<DayOfWeek>) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val title = if (state.isNew) words.newAlarm else words.editAlarm

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = title,
    ) {
        AlarmEditorBody(
            state = state,
            words = words,
            onLabelChange = onLabelChange,
            onPickTime = { showTimePicker = true },
            onToggleRepeatDay = onToggleRepeatDay,
            onSelectRepeatPreset = onSelectRepeatPreset,
            onSave = onSave,
            onDelete = onDelete,
        )
    }

    if (showTimePicker) {
        AlarmTimeDialog(
            initialTime = state.time,
            words = words,
            onPick = onTimeChange,
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun ColumnScope.AlarmEditorBody(
    state: AlarmEditorUiState,
    words: AlarmsWording,
    onLabelChange: (String) -> Unit,
    onPickTime: () -> Unit,
    onToggleRepeatDay: (DayOfWeek) -> Unit,
    onSelectRepeatPreset: (Set<DayOfWeek>) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = MiraTheme.spacing

    MiraSectionHeader(text = words.alarmTimeTitle)
    // The time is the one value in this sheet worth reading at arm's length, so it is set at
    // display scale and the whole surface is the target. It was a button whose 14sp label was
    // the same size as the word "Save" underneath it, which left the sheet with no subject.
    // Ivory rather than champagne: Save is the sheet's single accent, and a second one would
    // leave it with none.
    //
    // The chevron is what keeps it from reading as one more card of printed information. A card
    // that opens a picker and a card that only states a value are the same rectangle otherwise,
    // and the one thing in this sheet Abu MUST press is the one he would learn to press last.
    MiraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onPickTime,
        enabled = !state.isSaving,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.timeLabel,
                style = MaterialTheme.typography.displayMedium,
                color = if (state.isSaving) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = MiraIcons.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(spacing.lg),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(spacing.lg))

    MiraTextField(
        value = state.label,
        onValueChange = onLabelChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = words.alarmLabelPlaceholder,
        enabled = !state.isSaving,
    )
    Spacer(Modifier.height(spacing.lg))

    MiraSectionHeader(text = words.repeatLabel)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        DayOfWeek.entries.forEach { day ->
            MiraChip(
                text = words.weekdayShort(day),
                selected = day in state.repeatDays,
                enabled = !state.isSaving,
                onClick = { onToggleRepeatDay(day) },
            )
        }
    }
    Spacer(Modifier.height(spacing.xs))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MiraChip(
            text = words.repeatOnce,
            selected = state.repeatDays.isEmpty(),
            enabled = !state.isSaving,
            onClick = { onSelectRepeatPreset(emptySet()) },
        )
        MiraChip(
            text = words.repeatWeekdays,
            selected = state.repeatDays == WEEKDAY_SET,
            enabled = !state.isSaving,
            onClick = { onSelectRepeatPreset(WEEKDAY_SET) },
        )
        MiraChip(
            text = words.repeatEveryDay,
            selected = state.repeatDays == EVERY_DAY,
            enabled = !state.isSaving,
            onClick = { onSelectRepeatPreset(EVERY_DAY) },
        )
    }
    Spacer(Modifier.height(spacing.xl))

    MiraButton(
        text = words.save,
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isSaving,
    )
    if (!state.isNew) {
        // Centred under the full-width Save rather than tucked against the left margin, so the
        // one destructive action in the sheet reads as deliberate instead of as an afterthought.
        // The gap is the whole argument for demoting it: a text button keeps a full 48dp box, so
        // without this the two touch targets abut and the thumb that meant Save can land on
        // Delete. Demoting it and then leaving it touching is worse than not demoting it.
        Spacer(Modifier.height(spacing.md))
        MiraButton(
            style = MiraButtonStyle.TEXT,
            text = words.delete,
            onClick = onDelete,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = !state.isSaving,
            accent = MiraTheme.colors.danger,
        )
    }
}

/**
 * A [MiraDialog] rather than Material's own, for the reason the task composer's picker gives: on
 * an AMOLED screen Material's container arrives as a lighter grey card behind the clock face.
 */
@Composable
private fun AlarmTimeDialog(
    initialTime: LocalTime,
    words: AlarmsWording,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )
    MiraDialog(
        title = words.alarmTimeTitle,
        onDismissRequest = onDismiss,
        confirmText = words.set,
        onConfirm = {
            onPick(LocalTime.of(pickerState.hour, pickerState.minute))
            onDismiss()
        },
        modifier = modifier,
        dismissText = words.cancel,
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
    )
}

@Preview
@Composable
private fun AlarmListRowPreview() {
    MiraPreviewSurface {
        val words = alarmsWording(SpokenLanguage.ENGLISH)
        AlarmListRow(
            alarm = AlarmRow(
                id = 1L,
                label = "Fajr",
                timeLabel = "5 am",
                repeatLabel = "Every day",
                supporting = "Rings Tomorrow, 5 am",
                isEnabled = true,
            ),
            words = words,
            onOpen = {},
            onSetEnabled = {},
        )
        MiraDivider()
        AlarmListRow(
            alarm = AlarmRow(
                id = 2L,
                label = "Standup",
                timeLabel = "9:30 am",
                repeatLabel = "Mon · Tue · Wed · Thu · Fri",
                supporting = words.alarmOff,
                isEnabled = false,
            ),
            words = words,
            onOpen = {},
            onSetEnabled = {},
        )
    }
}
