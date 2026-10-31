package com.rayluxeo.aida.ui.notes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * The six things Mira can do with this note.
 *
 * An action she cannot do right now is shown dimmed with its reason written underneath, rather
 * than hidden. Hiding would leave Abu wondering where "Invoice draft" went; a chip that dims and
 * says "link a client first" tells him what to do next, and the line disappears by itself the
 * moment the reason does.
 */
@Composable
internal fun NoteActionRow(
    actions: List<NoteActionUi>,
    words: NoteDetailWording,
    onAction: (NoteAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            actions.forEach { action ->
                MiraChip(
                    text = action.label,
                    enabled = action.isEnabled,
                    onClick = if (action.isEnabled) {
                        { onAction(action.action) }
                    } else {
                        null
                    },
                )
            }
        }

        val reasons = actions.mapNotNull { action ->
            action.reason?.let { words.unavailable(action.label, it) }
        }
        if (reasons.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            reasons.forEach { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun NoteActionRowPreview() {
    MiraPreviewSurface {
        NoteActionRow(
            actions = listOf(
                NoteActionUi(
                    action = NoteAction.TURN_INTO_TASKS,
                    label = EnglishNoteDetailWording.actionLabel(NoteAction.TURN_INTO_TASKS),
                    isEnabled = false,
                    reason = EnglishNoteDetailWording.needsWords,
                ),
                NoteActionUi(
                    action = NoteAction.ORGANIZE,
                    label = EnglishNoteDetailWording.actionLabel(NoteAction.ORGANIZE),
                    isEnabled = true,
                ),
                NoteActionUi(
                    action = NoteAction.CREATE_INVOICE_DRAFT,
                    label = EnglishNoteDetailWording.actionLabel(NoteAction.CREATE_INVOICE_DRAFT),
                    isEnabled = false,
                    reason = EnglishNoteDetailWording.linkClientFirst,
                ),
            ),
            words = EnglishNoteDetailWording,
            onAction = {},
        )
    }
}
