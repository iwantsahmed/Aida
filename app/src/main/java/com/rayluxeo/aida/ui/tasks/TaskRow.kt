package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import kotlin.math.roundToInt

/** How far across the row the thumb has to travel before the action behind it fires. */
private const val SWIPE_THRESHOLD_FRACTION = 0.28f

/** The row never slides further than this, so what is underneath stays readable rather than torn. */
private const val SWIPE_LIMIT_FRACTION = 0.42f

/**
 * How far a pressed row gives under the thumb.
 *
 * A tenth of what [com.rayluxeo.aida.core.design.component.MiraButton] uses, because a button is
 * one object and a list is twenty: the same two percent repeated down a column reads as the whole
 * screen flinching. One percent is felt rather than seen, which is the point — the row answers the
 * finger before it answers the tap.
 */
private const val PRESSED_SCALE = 0.99f

/**
 * One task in the list, with the two gestures Abu's thumb reaches for.
 *
 * Swiping right completes or re-opens the task; swiping left opens the rest of the actions rather
 * than performing one, because delete and archive are not things a thumb should be able to do by
 * accident. Both directions name what they will do while the row is still moving: an unlabelled
 * coloured background is a guess Abu has to make with his thumb already committed.
 *
 * Every gesture has a control beside it — tapping completes, the trailing glyph opens the same
 * actions — because a swipe is invisible to TalkBack and to anyone who has not been told about it.
 */
@Composable
internal fun TaskRow(
    task: TaskListItem,
    words: TasksWording,
    selecting: Boolean,
    onToggleDone: () -> Unit,
    onOpen: () -> Unit,
    onOpenActions: () -> Unit,
    onToggleSelected: () -> Unit,
    onStartSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }

    var rowWidth by remember { mutableIntStateOf(0) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // A released row is travelling, so it comes back on a spring rather than on a duration: let go
    // of it twice in quick succession and the second throw carries the first one's velocity
    // instead of restarting from nothing (MiraMotion, "anything that MOVES uses a spring").
    val offsetPx by animateFloatAsState(
        targetValue = dragPx,
        animationSpec = if (dragging) snap() else MiraMotion.move<Float>(),
        label = "taskRowSwipe",
    )
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = MiraMotion.move(),
        label = "taskRowPress",
    )

    // A swipe left half-open when selection starts would sit under the bulk bar with no way back.
    LaunchedEffect(selecting) {
        if (selecting) dragPx = 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width },
    ) {
        when {
            offsetPx > 0f -> SwipeAffordance(
                alignment = Alignment.CenterStart,
                icon = MiraIcons.CheckCircle,
                label = if (task.isDone) words.reopen else words.complete,
                tint = MiraTheme.colors.positive,
            )
            offsetPx < 0f -> SwipeAffordance(
                alignment = Alignment.CenterEnd,
                icon = MiraIcons.MoreHoriz,
                label = words.moreActions,
                tint = MiraTheme.colors.accentDim,
            )
        }

        MiraListRow(
            title = task.title,
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                // Before the background, so the row's own obsidian gives with it rather than
                // staying behind as a rectangle the content has shrunk away from.
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    role = Role.Button,
                    onLongClickLabel = words.selectTask,
                    onLongClick = {
                        haptics.confirm()
                        onStartSelection()
                    },
                    // A tap OPENS the task. It used to complete it, which left no way to read one
                    // and made finishing a task the easiest thing to do by accident. Completion
                    // now lives on the glyph and on the swipe, both of which are deliberate.
                    onClick = {
                        haptics.tap()
                        if (selecting) onToggleSelected() else onOpen()
                    },
                )
                .draggable(
                    state = rememberDraggableState { delta ->
                        val limit = rowWidth * SWIPE_LIMIT_FRACTION
                        dragPx = (dragPx + delta).coerceIn(-limit, limit)
                    },
                    orientation = Orientation.Horizontal,
                    enabled = !selecting,
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        val released = dragPx
                        val threshold = rowWidth * SWIPE_THRESHOLD_FRACTION
                        dragPx = 0f
                        when {
                            released >= threshold -> {
                                haptics.confirm()
                                onToggleDone()
                            }
                            released <= -threshold -> {
                                haptics.tap()
                                onOpenActions()
                            }
                        }
                    },
                ),
            supporting = task.supporting,
            leading = {
                TaskRowLeading(
                    task = task,
                    selecting = selecting,
                    onToggleDone = onToggleDone,
                    words = words,
                )
            },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Archived keeps its word: the leading glyph changes too, but a filing-box
                    // icon is a guess and this is the only place the state is spelled out. It is
                    // rare — only the All view shows archived work — so it costs the row nothing.
                    if (task.isArchived) {
                        MiraChip(text = words.archivedLabel)
                    }
                    // High priority is a mark, not a pill — and a dim one. Trading a filled
                    // champagne chip for a solid champagne glyph moved the accent's area down
                    // and left the count alone: a screenful with the segmented control, the
                    // selected view chip, an active Filters chip and two urgent rows still put
                    // champagne in five places, which is the same as putting it in none. The
                    // dim tone reads as urgent beside four muted glyphs without competing with
                    // the one control on the screen that is actually champagne.
                    if (task.isHighPriority && !task.isDone) {
                        Icon(
                            imageVector = MiraIcons.PriorityHigh,
                            contentDescription = words.highPriority,
                            tint = MiraTheme.colors.accentDim,
                        )
                    }
                    if (!selecting) {
                        MiraIconButton(
                            icon = MiraIcons.MoreHoriz,
                            contentDescription = words.moreActionsFor(task.title),
                            onClick = onOpenActions,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun TaskRowLeading(
    task: TaskListItem,
    selecting: Boolean,
    onToggleDone: () -> Unit,
    words: TasksWording,
) {
    val icon = when {
        selecting && task.isSelected -> MiraIcons.CheckBox
        selecting -> MiraIcons.CheckBoxOutlineBlank
        task.isArchived -> MiraIcons.Inventory2
        task.isDone -> MiraIcons.CheckCircle
        else -> MiraIcons.RadioButtonUnchecked
    }
    // Selection is the one champagne here, and it earns it: it is a mode Abu deliberately
    // entered and the tint is the whole of what says so. A completed task used to carry it too,
    // which made the Completed view a solid column of the accent — a finished task does not need
    // emphasis, it needs to recede, and the filled glyph already says it is done. Overdue keeps
    // danger, which is a state rather than an accent and is the only thing on the row that Abu
    // has to notice without reading it.
    val tint = when {
        selecting && task.isSelected -> MaterialTheme.colorScheme.primary
        task.isOverdue -> MiraTheme.colors.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // While selecting, the glyph shows the selection and the row owns the tap; the rest of the
    // time it is the control that completes the task, in a target big enough to hit.
    //
    // Either way it occupies the same 48dp, so entering selection does not shove every title on
    // the screen sideways — a list that re-lays itself out under the thumb reads as a glitch.
    if (selecting) {
        Box(
            modifier = Modifier.size(MiraTheme.spacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
    } else {
        MiraIconButton(
            icon = icon,
            contentDescription = if (task.isDone) words.reopen else words.complete,
            onClick = onToggleDone,
            tint = tint,
        )
    }
}

/** What sits behind the row while it moves: the glyph and the word for what letting go will do. */
@Composable
private fun BoxScope.SwipeAffordance(
    alignment: Alignment,
    icon: ImageVector,
    label: String,
    tint: Color,
) {
    val spacing = MiraTheme.spacing
    Row(
        modifier = Modifier
            .matchParentSize()
            // Half of the 48dp control column, so a 24dp glyph revealed underneath lands on the
            // same centre as the glyph sitting on top of it: the tick Abu uncovers by swiping
            // and the tick he taps are the same tick, and a third left edge under a moving row
            // reads as the row failing to line up rather than as an affordance.
            .padding(horizontal = spacing.sm),
        horizontalArrangement = if (alignment == Alignment.CenterStart) {
            Arrangement.Start
        } else {
            Arrangement.End
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(spacing.xs))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/**
 * Everything a row can do apart from completing it.
 *
 * Deleting sits here rather than on a swipe of its own because it is the one action Abu could not
 * take back if the undo were missed, and a sheet asks for a second, deliberate tap. Snooze is left
 * out for a task with no due date: there is nothing to move, and
 * [com.rayluxeo.aida.domain.usecase.SnoozeTaskUseCase] would refuse it.
 */
@Composable
internal fun TaskActionsSheet(
    state: TaskActionsUiState,
    words: TasksWording,
    onSnooze: (SnoozeOption) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, title = state.title) {
        if (state.canSnooze) {
            MiraSectionHeader(text = words.snoozeTitle)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                SnoozeOption.entries.forEach { option ->
                    MiraChip(text = words.snoozeOption(option), onClick = { onSnooze(option) })
                }
            }
            Spacer(Modifier.height(spacing.md))
        }

        MiraListRow(
            title = words.duplicate,
            onClick = onDuplicate,
            leading = {
                Icon(
                    imageVector = MiraIcons.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        MiraListRow(
            title = if (state.isArchived) words.unarchive else words.archive,
            onClick = { onSetArchived(!state.isArchived) },
            leading = {
                Icon(
                    imageVector = MiraIcons.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        MiraListRow(
            title = words.delete,
            onClick = onDelete,
            leading = {
                Icon(
                    imageVector = MiraIcons.DeleteOutline,
                    contentDescription = null,
                    tint = MiraTheme.colors.danger,
                )
            },
        )
        Spacer(Modifier.height(spacing.md))
    }
}

@Preview
@Composable
private fun TaskRowPreview() {
    MiraPreviewSurface {
        TaskRow(
            task = TaskListItem(
                id = 1L,
                title = "Send Ahmed the proposal",
                supporting = "Overdue — Yesterday, 9 am · Rayluxeo · 1 of 3 steps",
                isOverdue = true,
                isDone = false,
                isArchived = false,
                isHighPriority = true,
                isSelected = false,
            ),
            words = EnglishTasksWording,
            selecting = false,
            onToggleDone = {},
            onOpen = {},
            onOpenActions = {},
            onToggleSelected = {},
            onStartSelection = {},
        )
    }
}
