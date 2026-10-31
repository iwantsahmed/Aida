package com.rayluxeo.aida.assistant.share

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.MainActivity
import com.rayluxeo.aida.assistant.SharedPayload
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraConfirmationCard
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraTextField
import dagger.hilt.android.AndroidEntryPoint

/**
 * Where "Ask Mira" lands: the share sheet, and the text-selection toolbar.
 *
 * It is deliberately not the app. Abu shared something from inside another app and expects to
 * end up back there, so this is one compact sheet over its own task — a share that opened Mira's
 * home screen would have thrown away what he was doing to show him a hub he did not ask for.
 *
 * Everything shown here came in on the intent. Nothing is read from the device, nothing is
 * copied anywhere, and nothing leaves: the only writes are the ones a command Abu sent makes,
 * through the same pipeline as every other request.
 *
 * `ACTION_PROCESS_TEXT` is answered without a result on purpose. The contract lets a handler
 * return replacement text, and Mira never rewrites what Abu selected in somebody else's app —
 * she is being asked about it, not asked to edit it — so finishing with no result leaves the
 * selection exactly as it was.
 */
@AndroidEntryPoint
class ShareToMiraActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mira is obsidian in every state, so both bars are forced dark rather than following
        // the system theme — the same rule MainActivity applies.
        val obsidian = MiraPalette.Obsidian.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(obsidian),
            navigationBarStyle = SystemBarStyle.dark(obsidian),
        )
        super.onCreate(savedInstanceState)

        setContent {
            MiraTheme {
                AskMiraSheet(
                    shared = intent,
                    onClose = ::finish,
                    onOpenMira = ::openMira,
                )
            }
        }
    }

    /**
     * The one control worth offering over a share Mira cannot use: the app itself, where he can
     * ask her something she can answer.
     */
    private fun openMira() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}

@Composable
private fun AskMiraSheet(
    shared: Intent,
    onClose: () -> Unit,
    onOpenMira: () -> Unit,
) {
    val viewModel: ShareToMiraViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(shared) { viewModel.onShared(shared) }

    val wording = shareWording(state.language)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // A drag or a back press must not take the sheet away mid-command: this activity owns
        // the scope that command is running in, and losing it half way loses a record Abu asked
        // for. The loading line says why nothing is closing.
        confirmValueChange = { state.dismissible },
    )

    MiraBottomSheet(
        onDismissRequest = { if (state.dismissible) onClose() },
        sheetState = sheet,
        title = wording.title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            when (val stage = state.stage) {
                ShareStage.Reading -> MiraLoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))

                is ShareStage.Unsupported -> RefusedShare(
                    stage = stage,
                    wording = wording,
                    onOpenMira = onOpenMira,
                    onClose = onClose,
                )

                is ShareStage.Ready -> AcceptedShare(
                    stage = stage,
                    state = state,
                    wording = wording,
                    onRequestChange = viewModel::onRequestChange,
                    onSend = viewModel::send,
                    onConfirm = viewModel::confirmPending,
                    onCancel = viewModel::cancelPending,
                    onClose = onClose,
                )
            }
        }
    }
}

/**
 * A share Mira will not pretend about: what it was, why she can do nothing with it, and the one
 * way forward that genuinely works.
 */
@Composable
private fun RefusedShare(
    stage: ShareStage.Unsupported,
    wording: ShareWording,
    onOpenMira: () -> Unit,
    onClose: () -> Unit,
) {
    MiraEmptyState(
        icon = MiraIcons.Block,
        message = wording.unsupported(stage.reason, stage.declaredType),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiraButton(text = wording.close, onClick = onClose, style = MiraButtonStyle.TEXT)
        Spacer(Modifier.weight(1f))
        MiraButton(text = wording.openMira, onClick = onOpenMira, style = MiraButtonStyle.SECONDARY)
    }
}

/** What arrived, what Mira can honestly do with it, and the field that asks her to. */
@Composable
private fun AcceptedShare(
    stage: ShareStage.Ready,
    state: ShareToMiraUiState,
    wording: ShareWording,
    onRequestChange: (String) -> Unit,
    onSend: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val shown = present(stage, wording)

    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = shown.icon,
                contentDescription = null,
                tint = MiraTheme.colors.accentDim,
            )
            Spacer(Modifier.width(spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shown.kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = shown.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = HEADLINE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shown.detail != null) {
                    Spacer(Modifier.height(spacing.xxs))
                    Text(
                        text = shown.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (shown.caveat != null) {
        Text(
            text = shown.caveat,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (state.canSuggest) {
        // Three chips with glyphs and a Bengali label do not always fit a phone's width, and a
        // clipped suggestion is a control Abu cannot reach.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraChip(
                text = wording.noteSuggestion,
                leadingIcon = MiraIcons.Notes,
                onClick = { onRequestChange(wording.noteRequest(state.suggestionSeed)) },
            )
            MiraChip(
                text = wording.taskSuggestion,
                leadingIcon = MiraIcons.TaskAlt,
                onClick = { onRequestChange(wording.taskRequest(state.suggestionSeed)) },
            )
            MiraChip(
                text = wording.rememberSuggestion,
                leadingIcon = MiraIcons.Bookmark,
                onClick = { onRequestChange(wording.rememberRequest(state.suggestionSeed)) },
            )
        }
    }

    MiraTextField(
        value = state.request,
        onValueChange = onRequestChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = wording.requestPlaceholder,
        enabled = !state.working,
        singleLine = false,
        maxLines = REQUEST_LINES,
    )

    if (state.pending != null) {
        MiraConfirmationCard(
            question = state.pending.summary,
            confirmText = wording.confirm,
            onConfirm = onConfirm,
            cancelText = wording.cancel,
            onCancel = onCancel,
        )
    }

    if (state.reply != null) {
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.reply,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.replyIsProblem) {
                    MiraTheme.colors.danger
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiraButton(
            text = if (state.reply != null) wording.done else wording.close,
            onClick = onClose,
            style = MiraButtonStyle.TEXT,
            enabled = state.dismissible,
        )
        Spacer(Modifier.weight(1f))
        if (state.working) {
            MiraLoadingIndicator()
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = wording.working,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MiraButton(text = wording.ask, onClick = onSend, enabled = state.canSend)
        }
    }
}

/** One payload, worded and given a glyph, so the sheet itself holds no `when` over the payload. */
private data class SharePresentation(
    val icon: ImageVector,
    val kind: String,
    val headline: String,
    val detail: String?,
    /** The honest line about what Mira cannot do with this, or null when there is nothing to say. */
    val caveat: String?,
)

@Composable
private fun present(stage: ShareStage.Ready, wording: ShareWording): SharePresentation =
    when (val payload = stage.payload) {
        is SharedPayload.Text -> SharePresentation(
            icon = MiraIcons.Subject,
            kind = wording.textKind,
            headline = payload.text,
            detail = wording.textLength(payload.text.length),
            caveat = null,
        )

        is SharedPayload.Link -> SharePresentation(
            icon = MiraIcons.Link,
            kind = wording.linkKind,
            headline = payload.title ?: SharedText.hostOf(payload.url),
            detail = payload.url,
            caveat = wording.linkCaveat,
        )

        is SharedPayload.Documents -> SharePresentation(
            icon = if (payload.isImage) MiraIcons.Image else MiraIcons.Description,
            kind = if (payload.isImage) wording.imageKind else wording.fileKind,
            headline = stage.names.filter { it.isNotBlank() }.joinToString(separator = ", ")
                .ifEmpty { wording.itemCount(payload.uris.size) },
            detail = if (payload.uris.size > 1) wording.itemCount(payload.uris.size) else payload.mimeType,
            caveat = if (payload.isImage) wording.imageCaveat else wording.fileCaveat,
        )
    }

/** Enough of a long share to recognise it, without the sheet growing into a reading app. */
private const val HEADLINE_LINES = 4

private const val REQUEST_LINES = 5
