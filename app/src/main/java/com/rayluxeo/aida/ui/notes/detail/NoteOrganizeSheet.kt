package com.rayluxeo.aida.ui.notes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSwitch
import com.rayluxeo.aida.ui.notes.NoteSheetList

/**
 * What Mira would change about the note, before any of it is written.
 *
 * Every part carries its own switch because a proposal is not all-or-nothing: the title can be
 * right and the client wrong, and one answer for all four is how a wrong link gets accepted
 * alongside three right ones. Nothing here writes — the sheet is a question, and Apply is the
 * answer.
 *
 * Once Apply is running the whole sheet stops taking answers, the rows included. A row that stayed
 * live while its own switch was dimmed would let Abu change what is being written while it is
 * being written, and the write already has its copy.
 *
 * The proposal scrolls inside a [NoteSheetList]; Cancel and Apply do not, so a note that proposed
 * a dozen labels cannot push the answer off the bottom of the sheet.
 */
@Composable
internal fun NoteOrganizeSheet(
    state: NoteOrganizeUiState,
    words: NoteDetailWording,
    onToggleTitle: () -> Unit,
    onToggleTag: (Long) -> Unit,
    onToggleClient: () -> Unit,
    onToggleProject: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = words.organizeTitle) {
        NoteSheetList {
            // A hairline goes BETWEEN two rows and never under the last of them. Which rows are
            // present depends on what Mira found, so each one rules above itself when something
            // came before it rather than closing itself off below.
            val hasTitle = state.title != null
            val hasClient = state.client != null

            state.title?.let { title ->
                ProposalRow(
                    title = title,
                    supporting = words.organizeTitleLabel,
                    accepted = state.acceptTitle,
                    isApplying = state.isApplying,
                    onToggle = onToggleTitle,
                )
            }

            state.client?.let { client ->
                if (hasTitle) MiraDivider()
                ProposalRow(
                    title = client.label,
                    supporting = words.clientLabel,
                    accepted = state.acceptClient,
                    isApplying = state.isApplying,
                    onToggle = onToggleClient,
                )
            }

            state.project?.let { project ->
                if (hasTitle || hasClient) MiraDivider()
                ProposalRow(
                    title = project.label,
                    supporting = words.projectLabel,
                    accepted = state.acceptProject,
                    isApplying = state.isApplying,
                    onToggle = onToggleProject,
                )
            }

            if (state.tags.isNotEmpty()) {
                Spacer(Modifier.height(spacing.md))
                MiraSectionHeader(text = words.organizeTagsLabel)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    state.tags.forEach { tag ->
                        MiraChip(
                            text = tag.name,
                            selected = tag.isAccepted,
                            enabled = !state.isApplying,
                            onClick = { onToggleTag(tag.id) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.lg))
        Row(modifier = Modifier.fillMaxWidth()) {
            MiraButton(
                text = words.cancel,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = MiraButtonStyle.SECONDARY,
                enabled = !state.isApplying,
            )
            Spacer(Modifier.width(spacing.sm))
            MiraButton(
                text = words.organizeApply,
                onClick = onApply,
                modifier = Modifier.weight(1f),
                enabled = state.canApply,
            )
        }
    }
}

/** One thing Mira proposes, with the switch that accepts it. Three of these, all the same shape. */
@Composable
private fun ProposalRow(
    title: String,
    supporting: String,
    accepted: Boolean,
    isApplying: Boolean,
    onToggle: () -> Unit,
) {
    MiraListRow(
        title = title,
        supporting = supporting,
        onClick = onToggle.takeIf { !isApplying },
        enabled = !isApplying,
        trailing = {
            MiraSwitch(
                checked = accepted,
                onCheckedChange = { onToggle() },
                enabled = !isApplying,
            )
        },
    )
}

@Preview
@Composable
private fun NoteOrganizeSheetPreview() {
    MiraPreviewSurface {
        ProposalRow(
            title = "Lease renewal — what to ask",
            supporting = EnglishNoteDetailWording.organizeTitleLabel,
            accepted = true,
            isApplying = false,
            onToggle = {},
        )
        MiraDivider()
        MiraChip(text = "rent", selected = true, onClick = {})
    }
}
