package com.rayluxeo.aida.ui.vault

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.model.VaultItemSummary

/**
 * The vault, closed by default. Abu sees a door and nothing else until the platform prompt says
 * it is him, and the door shuts again the moment Mira leaves the foreground.
 */
@Composable
fun VaultScreen(modifier: Modifier = Modifier) {
    val viewModel: VaultViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words = vaultWording(state.language)
    val context = LocalContext.current
    val unlock = rememberVaultUnlock(
        promptTitle = words.promptTitle,
        promptSubtitle = words.promptSubtitle,
        onOutcome = viewModel::onUnlockOutcome,
    )

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.lock() }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = words.vaultTitle,
                subtitle = if (state.isUnlocked) words.unlocked else words.locked,
                action = if (state.isUnlocked) {
                    {
                        MiraIconButton(
                            icon = MiraIcons.Add,
                            contentDescription = words.addEntry,
                            onClick = viewModel::openComposer,
                        )
                    }
                } else {
                    null
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            VaultProblemNotice(
                problem = state.problem,
                words = words,
                onDismiss = viewModel::dismissProblem,
            )

            // Opening the vault is the one reveal in Mira worth the long duration — §11 names it
            // as the example of EXPRESSIVE. The cross-fade is also what makes the two states read
            // as one door rather than as two screens that happen to share a bar.
            //
            // A cross-fade is opacity, so it takes `fade` and not `enter`: the entering easing is
            // heavily front-loaded, which left the outgoing panel ghosting under the list for most
            // of the 320ms.
            Crossfade(
                targetState = state.isUnlocked,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                animationSpec = MiraMotion.fade(MiraMotion.EXPRESSIVE_MS),
                label = "VaultDoor",
            ) { unlocked ->
                if (unlocked) {
                    VaultList(
                        items = state.items,
                        words = words,
                        revealing = state.revealing,
                        onOpen = viewModel::reveal,
                        onAdd = viewModel::openComposer,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LockPanel(
                        note = state.lock,
                        words = words,
                        needsEnrolment = state.needsEnrolment,
                        unlocking = state.phase == VaultPhase.UNLOCKING,
                        onUnlock = {
                            viewModel.onUnlockRequested()
                            unlock()
                        },
                        onEnrol = { context.openBiometricEnrolment(viewModel::onUnlockOutcome) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    val revealed = state.revealed
    val deletion = state.confirmingDelete
    when {
        deletion != null -> MiraDialog(
            title = words.deleteTitle(deletion.title),
            onDismissRequest = viewModel::cancelDelete,
            confirmText = words.delete,
            onConfirm = viewModel::confirmDelete,
            message = words.deleteMessage,
            dismissText = words.keepIt,
            destructive = true,
        )

        revealed != null -> RevealSheet(
            revealed = revealed,
            words = words,
            onCopy = { context.copySecret(revealed.title, revealed.secret) },
            onDelete = { viewModel.askDelete(revealed.id, revealed.title) },
            onDismiss = viewModel::dismissReveal,
        )

        state.composerOpen -> ComposerSheet(
            draft = state.draft,
            words = words,
            saving = state.saving,
            problem = state.problem,
            onTitleChange = viewModel::onDraftTitleChange,
            onSecretChange = viewModel::onDraftSecretChange,
            onCategoryChange = viewModel::onDraftCategoryChange,
            onSave = viewModel::saveDraft,
            onDismiss = viewModel::dismissComposer,
        )
    }
}

/**
 * The line about a read or a write of the vault that failed.
 *
 * It fades in and lifts the door down with it rather than arriving between two frames. The same
 * line on the Alarms screens already fades, and a notice that appears instantly — while the panel
 * under it jumps to a new height — reads as something breaking rather than as Mira saying one
 * thing. Motion follows the rule in `MiraMotion`: the height is movement and springs, the ink is
 * opacity and takes a curve.
 *
 * The last problem outlives its own dismissal, because `problem` is already null while the fade
 * out is still running and text that blanks before it leaves reads as a glitch.
 */
@Composable
private fun VaultProblemNotice(
    problem: VaultProblem?,
    words: VaultWording,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    var showing by remember { mutableStateOf(problem) }
    LaunchedEffect(problem) {
        if (problem != null) showing = problem
    }

    // The Box is what keeps this on the plain AnimatedVisibility rather than the ColumnScope
    // overload, so the transition is exactly the one written here.
    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = problem != null,
            enter = fadeIn(MiraMotion.fade()) + expandVertically(MiraMotion.move()),
            exit = fadeOut(MiraMotion.fade(MiraMotion.QUICK_MS)) + shrinkVertically(MiraMotion.move()),
        ) {
            showing?.let { shown ->
                MiraInlineNotice(
                    text = words.problem(shown),
                    tone = MiraNoticeTone.DANGER,
                    dismissContentDescription = words.dismiss,
                    onDismiss = onDismiss,
                    modifier = Modifier.padding(top = spacing.xs, bottom = spacing.sm),
                )
            }
        }
    }
}

/**
 * The door.
 *
 * Closed has to look like a decision rather than like a screen that failed to load, so the panel
 * is one centred composition: a lock at a size worth seeing, the fact in display type, the reason
 * under it, and exactly one way through. The button is sized to its own words rather than run to
 * both margins — a champagne bar across an otherwise empty page reads as a form to fill in, not
 * as a door to open.
 */
@Composable
private fun LockPanel(
    note: VaultLockNote?,
    words: VaultWording,
    needsEnrolment: Boolean,
    unlocking: Boolean,
    onUnlock: () -> Unit,
    onEnrol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    // Vertical only: MiraScaffold has already laid in the screen gutter, and a second horizontal
    // inset here put this panel's words on a 40dp margin while the bar above it sat at 24dp.
    Column(
        modifier = modifier.padding(vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = MiraIcons.Lock,
            contentDescription = null,
            modifier = Modifier.size(spacing.xxl),
            tint = MiraTheme.colors.accentDim,
        )
        Spacer(Modifier.height(spacing.lg))
        Text(
            text = words.lockedHeadline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = words.lockedBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (note != null) {
            Spacer(Modifier.height(spacing.md))
            Text(
                text = words.lockNote(note),
                style = MaterialTheme.typography.bodyMedium,
                color = MiraTheme.colors.danger,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        if (needsEnrolment) {
            MiraButton(
                text = words.setUpScreenLock,
                onClick = onEnrol,
                leadingIcon = MiraIcons.Fingerprint,
            )
        } else {
            MiraButton(
                text = if (unlocking) words.unlocking else words.unlock,
                onClick = onUnlock,
                enabled = !unlocking,
                leadingIcon = MiraIcons.Fingerprint,
            )
        }
    }
}

@Composable
private fun VaultList(
    items: VaultItems,
    words: VaultWording,
    revealing: Boolean,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    when (items) {
        VaultItems.Loading -> MiraListSkeleton(modifier)

        VaultItems.Empty -> Box(modifier, contentAlignment = Alignment.Center) {
            MiraEmptyState(
                icon = MiraIcons.Key,
                message = words.nothingStored,
                actionText = words.addFirstEntry,
                onAction = onAdd,
            )
        }

        is VaultItems.Content -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.xxl),
        ) {
            itemsIndexed(items.items, key = { _, item -> item.id }) { index, item ->
                VaultRow(item = item, words = words, enabled = !revealing, onOpen = onOpen)
                // Between rows only: a hairline under the last one rules off a list that has
                // already ended.
                if (index < items.items.lastIndex) MiraDivider()
            }
        }
    }
}

/**
 * One entry.
 *
 * No padlock glyph on the row. Every row in here is encrypted, so a lock beside each one says
 * nothing and costs the list its one shared left edge — the title, the category under it and the
 * screen's own gutter all line up without it.
 */
@Composable
private fun VaultRow(
    item: VaultItemSummary,
    words: VaultWording,
    enabled: Boolean,
    onOpen: (Long) -> Unit,
) {
    MiraListRow(
        title = item.title,
        supporting = words.category(item.category),
        onClick = { onOpen(item.id) },
        enabled = enabled,
    )
}

/**
 * One secret, open for as long as the sheet is.
 *
 * Copy is the reason Abu opened it, so it is the sheet's single filled action; Delete is a text
 * action in the danger colour and sits apart from it, the way the alarm editor keeps Save and
 * Delete apart. Two equally weighted buttons a thumb's width from each other is how the wrong
 * one gets pressed.
 */
@Composable
private fun RevealSheet(
    revealed: RevealedSecret,
    words: VaultWording,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    // Opened fully: a long secret plus Copy underneath it is taller than the half fold, and
    // MiraBottomSheet does not scroll its content, so Copy would be the part left off screen.
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = revealed.title,
    ) {
        MiraChip(text = words.category(revealed.category))
        Spacer(Modifier.height(spacing.md))
        SelectionContainer {
            Text(
                text = revealed.secret,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(spacing.md),
            )
        }
        Spacer(Modifier.height(spacing.xl))
        MiraButton(
            text = words.copy,
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = MiraIcons.ContentCopy,
        )
        // A text button keeps a full 48dp box, so demoting Delete and then leaving it touching
        // Copy gives the thumb two adjoining targets — which is the very thing the demotion was
        // for. The separation is the argument, not the colour.
        Spacer(Modifier.height(spacing.md))
        MiraButton(
            style = MiraButtonStyle.TEXT,
            text = words.delete,
            onClick = onDelete,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            accent = MiraTheme.colors.danger,
        )
    }
}

@Composable
private fun ComposerSheet(
    draft: VaultDraft,
    words: VaultWording,
    saving: Boolean,
    problem: VaultProblem?,
    onTitleChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onCategoryChange: (VaultCategory) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    // Opened fully, for the same reason: two fields, a note, the chips and Save do not fit the
    // half fold, and the sheet does not scroll.
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        title = words.newEntry,
    ) {
        MiraTextField(
            value = draft.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = words.entryTitleLabel,
            placeholder = words.entryTitlePlaceholder,
            enabled = !saving,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = words.entryTitleNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
        MiraTextField(
            value = draft.secret,
            onValueChange = onSecretChange,
            modifier = Modifier.fillMaxWidth(),
            label = words.secretLabel,
            enabled = !saving,
            singleLine = false,
        )
        Spacer(Modifier.height(spacing.lg))
        MiraSectionHeader(text = words.categoryLabel)
        // Wrapped, not scrolled. Four chips fit a phone in one or two lines, and a scrolling row
        // clipped them at the sheet's own padding with nothing to say more were there. The alarm
        // editor and the timer composer already wrap; one element, one treatment.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            VaultCategory.entries.forEach { category ->
                MiraChip(
                    text = words.category(category),
                    selected = category == draft.category,
                    enabled = !saving,
                    onClick = { onCategoryChange(category) },
                )
            }
        }
        Spacer(Modifier.height(spacing.xl))
        // Inside the sheet, not behind it: a create failure's reason was being drawn on the screen
        // underneath the modal scrim, so the Save button sprang back with nothing said. §12.
        if (problem != null) {
            MiraInlineNotice(
                text = words.problem(problem),
                tone = MiraNoticeTone.DANGER,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.sm))
        }
        MiraButton(
            text = if (saving) words.saving else words.save,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.isComplete && !saving,
        )
    }
}

/**
 * Copies with the sensitive flag set, so Android 13 and later keep the value out of the
 * clipboard preview and out of clipboard history.
 */
private fun Context.copySecret(label: String, secret: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, secret)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

private fun Context.openBiometricEnrolment(onFailure: (VaultUnlockOutcome) -> Unit) {
    try {
        startActivity(biometricEnrollIntent())
    } catch (_: ActivityNotFoundException) {
        onFailure(VaultUnlockOutcome.Refused(VaultLockReason.ENROLMENT_UNAVAILABLE))
    }
}
