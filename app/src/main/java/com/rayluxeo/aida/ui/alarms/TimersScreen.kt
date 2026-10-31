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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import java.time.Duration
import kotlinx.coroutines.delay

/** How long a notice stays up before it clears itself. Long enough to read. */
private const val NOTICE_MILLIS = 6_000L

/**
 * The timers.
 *
 * Every countdown on screen is derived from its timer's end instant on each tick, so closing Mira
 * and coming back shows the right number rather than one that stopped while she was away.
 */
@Composable
fun TimersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TimersViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) { TimersCallbacks(viewModel) }

    TimersContent(state = state, callbacks = callbacks, onBack = onBack, modifier = modifier)
}

/** Everything the screen can ask the view model to do, as one value. */
@Immutable
internal class TimersCallbacks(
    val onOpenComposer: () -> Unit,
    val onCloseComposer: () -> Unit,
    val onLabelChange: (String) -> Unit,
    val onMinutesChange: (String) -> Unit,
    val onSelectPreset: (Long) -> Unit,
    val onStart: () -> Unit,
    val onPause: (Long) -> Unit,
    val onResume: (Long) -> Unit,
    val onCancel: (Long) -> Unit,
    val onDismissNotice: () -> Unit,
) {
    constructor(viewModel: TimersViewModel) : this(
        onOpenComposer = viewModel::onOpenComposer,
        onCloseComposer = viewModel::onCloseComposer,
        onLabelChange = viewModel::onComposerLabelChange,
        onMinutesChange = viewModel::onComposerMinutesChange,
        onSelectPreset = viewModel::onSelectPreset,
        onStart = viewModel::onStartTimer,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onCancel = viewModel::onCancel,
        onDismissNotice = viewModel::onDismissNotice,
    )
}

@Composable
private fun TimersContent(
    state: TimersUiState,
    callbacks: TimersCallbacks,
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
                title = words.timersTitle,
                onBack = onBack,
                backContentDescription = words.back,
                action = {
                    MiraIconButton(
                        icon = MiraIcons.Add,
                        contentDescription = words.addTimer,
                        onClick = callbacks.onOpenComposer,
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
                        // A screen with no timer running is the ordinary state of this screen,
                        // so it is centred in the space it owns rather than left hanging under
                        // the top bar.
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiraEmptyState(
                                icon = MiraIcons.HourglassEmpty,
                                message = words.noTimers,
                                actionText = words.addFirstTimer,
                                onAction = callbacks.onOpenComposer,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            // The foot of the list clears the notice that settles over it. The
                            // figure is the bar's own, not this screen's guess at it.
                            contentPadding = PaddingValues(
                                top = spacing.xs,
                                bottom = alarmsNoticeInset,
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            items(state.timers, key = { it.id }) { timer ->
                                TimerCard(
                                    timer = timer,
                                    words = words,
                                    onPause = { callbacks.onPause(timer.id) },
                                    onResume = { callbacks.onResume(timer.id) },
                                    onCancel = { callbacks.onCancel(timer.id) },
                                )
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

    state.composer?.let { composer ->
        TimerComposerSheet(
            state = composer,
            words = words,
            onLabelChange = callbacks.onLabelChange,
            onMinutesChange = callbacks.onMinutesChange,
            onSelectPreset = callbacks.onSelectPreset,
            onStart = callbacks.onStart,
            onDismiss = callbacks.onCloseComposer,
        )
    }
}

/**
 * One countdown.
 *
 * The number is the card, and the card is built around it: the name above it is muted and the
 * length below it is smaller still, so the only bright thing on a carbon surface is the figure
 * Abu is actually watching. A timer is read from across a desk, which is what the display scale
 * is for — at body size it was just another line of text.
 *
 * That figure is IVORY, and the card's brightest thing is the one Abu is watching rather than the
 * one wearing the accent. Champagne marks what to press and what is chosen; a countdown is
 * neither. It was champagne here, which put three accents on a screen with three timers and no
 * accented action anywhere on it — the KDoc claimed the opposite of what the code did. Paused
 * drops to muted, so a stopped clock is visibly not a running one before the button is read.
 *
 * Neither control is the screen's primary either: starting a timer is the accented step and it
 * lives in the sheet.
 */
@Composable
private fun TimerCard(
    timer: TimerRow,
    words: AlarmsWording,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = timer.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = timer.countdown,
            style = MaterialTheme.typography.displayLarge,
            color = if (timer.isRunning) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = timer.durationLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            MiraButton(
                style = MiraButtonStyle.SECONDARY,
                text = if (timer.isRunning) words.pause else words.resume,
                onClick = if (timer.isRunning) onPause else onResume,
            )
            MiraButton(
                style = MiraButtonStyle.TEXT,
                text = words.cancelTimer,
                onClick = onCancel,
                accent = MiraTheme.colors.danger,
            )
        }
    }
}

/**
 * Starting a timer by hand.
 *
 * The presets fill the minutes field rather than starting a timer of their own, so there is one
 * value on screen and it is the one that runs.
 *
 * Opened fully, for the reason the alarm editor gives: [MiraBottomSheet] does not scroll its
 * content, so Start must never be the part that falls below the fold.
 */
@Composable
private fun TimerComposerSheet(
    state: TimerComposerUiState,
    words: AlarmsWording,
    onLabelChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSelectPreset: (Long) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = words.newTimer,
    ) {
        TimerComposerBody(
            state = state,
            words = words,
            onLabelChange = onLabelChange,
            onMinutesChange = onMinutesChange,
            onSelectPreset = onSelectPreset,
            onStart = onStart,
        )
    }
}

@Composable
private fun ColumnScope.TimerComposerBody(
    state: TimerComposerUiState,
    words: AlarmsWording,
    onLabelChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSelectPreset: (Long) -> Unit,
    onStart: () -> Unit,
) {
    val spacing = MiraTheme.spacing

    MiraTextField(
        value = state.label,
        onValueChange = onLabelChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = words.timerLabelPlaceholder,
        enabled = !state.isSaving,
    )
    Spacer(Modifier.height(spacing.lg))

    // The shortcuts and the field are one group and sit at one interval apart, because they are
    // two ways of setting the same number rather than two separate decisions.
    MiraSectionHeader(text = words.durationLabel)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        TIMER_PRESET_MINUTES.forEach { minutes ->
            MiraChip(
                text = words.durationPreset(Duration.ofMinutes(minutes)),
                selected = state.minutes == minutes.toString(),
                enabled = !state.isSaving,
                onClick = { onSelectPreset(minutes) },
            )
        }
    }
    Spacer(Modifier.height(spacing.sm))

    MiraTextField(
        value = state.minutes,
        onValueChange = onMinutesChange,
        modifier = Modifier.fillMaxWidth(),
        label = words.minutesLabel,
        enabled = !state.isSaving,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Spacer(Modifier.height(spacing.xl))

    MiraButton(
        text = words.start,
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canStart,
    )
}

@Preview
@Composable
private fun TimerCardPreview() {
    MiraPreviewSurface {
        val words = alarmsWording(SpokenLanguage.ENGLISH)
        TimerCard(
            timer = TimerRow(
                id = 1L,
                label = "Tea",
                countdown = "04:12",
                durationLabel = "5 minutes",
                isRunning = true,
            ),
            words = words,
            onPause = {},
            onResume = {},
            onCancel = {},
        )
        TimerCard(
            timer = TimerRow(
                id = 2L,
                label = "Timer",
                countdown = "24:00",
                durationLabel = words.timerPaused,
                isRunning = false,
            ),
            words = words,
            onPause = {},
            onResume = {},
            onCancel = {},
        )
    }
}
