package com.rayluxeo.aida.ui.tasks.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.rememberMiraHaptics

/**
 * The steps inside a task: add, tick, reorder, remove.
 *
 * Reordering is a pair of arrows rather than a drag. A drag handle on a 6.8-inch screen competes
 * with the list's own scroll, and arrows are the only version of this that TalkBack can drive at
 * all — each one says which step it moves and where.
 *
 * Ticking every step deliberately does not tick the task. A task can be finished with steps left
 * undone, and the reverse would take the decision out of Abu's hands.
 */
@Composable
internal fun SubtaskList(
    steps: List<SubtaskRow>,
    words: TaskDetailWording,
    onAdd: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onMove: (Int, Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    var draft by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        if (steps.isNotEmpty()) {
            Text(
                text = words.subtaskProgress(steps.count { it.isDone }, steps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xxs))
        }

        steps.forEachIndexed { index, step ->
            SubtaskRowItem(
                step = step,
                words = words,
                // With one step there is no order to change, so the arrows are not there to be
                // greyed out: two dead controls on the only row is 96dp of the title's width
                // spent saying nothing. They appear with the second step, which is the moment
                // reordering starts to mean something.
                canReorder = steps.size > 1,
                canMoveUp = index > 0,
                canMoveDown = index < steps.lastIndex,
                onToggle = {
                    haptics.tap()
                    onToggle(index)
                },
                onMoveUp = { onMove(index, true) },
                onMoveDown = { onMove(index, false) },
                onRemove = { onRemove(index) },
            )
        }

        Spacer(Modifier.height(spacing.xs))
        MiraTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = words.subtaskPlaceholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onAdd(draft)
                    draft = ""
                },
            ),
            trailing = {
                if (draft.isNotBlank()) {
                    MiraIconButton(
                        icon = MiraIcons.CheckCircle,
                        contentDescription = words.addSubtask,
                        onClick = {
                            onAdd(draft)
                            draft = ""
                        },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}

/** The id a step carries before it has been written, which is no id at all. */
private const val UNSAVED_STEP_ID = 0L

@Composable
private fun SubtaskRowItem(
    step: SubtaskRow,
    words: TaskDetailWording,
    canReorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A step on a task that has not been saved has no row of its own to tick yet, so it carries
    // no tick control rather than one that would do nothing.
    val saved = step.id != UNSAVED_STEP_ID
    MiraListRow(
        title = step.title,
        modifier = modifier,
        onClick = if (saved) onToggle else null,
        leading = {
            // The column is held open either way. Dropping it for an unsaved step pulled that
            // one title 64 points left of every other, which reads as a layout fault rather than
            // as "this cannot be ticked yet" — and an empty space says the latter without
            // drawing a control that would not work.
            Box(
                modifier = Modifier.size(MiraTheme.spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                if (saved) {
                    Icon(
                        imageVector = if (step.isDone) {
                            MiraIcons.CheckCircle
                        } else {
                            MiraIcons.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (step.isDone) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canReorder) {
                    MiraIconButton(
                        icon = MiraIcons.ArrowUpward,
                        contentDescription = words.moveSubtaskUp(step.title),
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MiraIconButton(
                        icon = MiraIcons.ArrowDownward,
                        contentDescription = words.moveSubtaskDown(step.title),
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiraIconButton(
                    icon = MiraIcons.Close,
                    contentDescription = words.removeSubtask(step.title),
                    onClick = onRemove,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Preview
@Composable
private fun SubtaskListPreview() {
    MiraPreviewSurface {
        SubtaskList(
            steps = listOf(
                SubtaskRow(id = 1L, title = "Draft the homepage copy", isDone = true),
                SubtaskRow(id = 2L, title = "Send it to Ahmed", isDone = false),
            ),
            words = EnglishTaskDetailWording,
            onAdd = {},
            onToggle = {},
            onMove = { _, _ -> },
            onRemove = {},
        )
    }
}
