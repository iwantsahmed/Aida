package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.os.Build
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.ink.InkTool

/**
 * The order the two tools are offered in.
 *
 * Written out rather than read off `InkTool.entries`, because tying the control to the enum's
 * declaration order means reordering the enum silently swaps pen and eraser with nothing failing
 * to compile.
 */
private val INK_TOOLS = listOf(InkTool.PEN, InkTool.ERASER)

/**
 * Quick Ink: a page of Abu's own handwriting, belonging to a note.
 *
 * Obsidian to the edges with as little on it as a working control set allows, because the page is
 * the screen. The canvas itself is a plain `android.view.View` inside [AndroidView] rather than
 * Compose pointer input, and every reason for that is in [InkCanvasView]'s own documentation —
 * batched historical samples, unbuffered dispatch, the platform motion predictor, and the tool
 * type that tells a reversed S Pen from a nib.
 *
 * **Nothing transient is allowed to change the canvas's height.** The notices, the handwriting
 * hint and the recognised-text panel float over the page instead of sitting above it: a canvas
 * that shrinks by a banner's height re-fits Abu's handwriting to the new size and drops his undo
 * stack with it, and three saves in a row would leave the page visibly smaller than he wrote it.
 *
 * The screen owns the two things only it can know: whether this phone's keyboard will read
 * handwriting, and the session that asks it to. Everything else is the view model's.
 *
 * @param noteId the note this page belongs to, or null when Quick Ink was opened from a shortcut
 *   or the widget and no note exists yet. In that case the row is minted on the first save, so
 *   opening the canvas and walking away leaves nothing behind in Abu's list.
 * @param pageId an existing page to reopen, or null for a fresh one.
 */
@Composable
fun InkCanvasScreen(
    noteId: Long?,
    pageId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: InkCanvasViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wording = inkWording(state.language)
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val context = LocalContext.current
    val density = LocalDensity.current

    // Keyed on the view model, which is the thing that latches the page: the ids come from the
    // route, so a different note or page is a different back-stack entry and a different view
    // model. Keying on the ids would claim this effect can reopen the canvas, and it cannot.
    LaunchedEffect(viewModel) { viewModel.openPage(noteId, pageId) }

    // Asked of the keyboard that is installed, once, rather than inferred from the phone's model.
    val handwritingAvailable = remember(context) { context.canReadHandwriting() }
    LaunchedEffect(handwritingAvailable) {
        viewModel.setHandwritingAvailable(handwritingAvailable)
    }

    // The nib and the eraser are taken from the spacing scale rather than typed as pixels, so a
    // pen drawn on this phone is the same weight relative to the writing on any other.
    val nibWidthPx = with(density) { (spacing.hairline * 2).toPx() }
    val eraserRadiusPx = with(density) { spacing.sm.toPx() }
    val hairlinePx = with(density) { spacing.hairline.toPx() }

    // Ivory ink and the champagne ring, read from the theme: the view invents no colour of its
    // own, it is handed the two it draws with.
    val inkColour = MaterialTheme.colorScheme.onBackground.toArgb()
    val accentColour = MaterialTheme.colorScheme.primary.toArgb()
    LaunchedEffect(nibWidthPx, eraserRadiusPx) {
        viewModel.engine.nibWidthPx = nibWidthPx
        viewModel.engine.eraserRadiusPx = eraserRadiusPx
    }

    // The hand is told before the eye: a saved page and a refused one feel different.
    LaunchedEffect(state.notice) {
        when (state.notice?.tone) {
            MiraNoticeTone.CONFIRM -> haptics.confirm()
            MiraNoticeTone.DANGER -> haptics.error()
            MiraNoticeTone.INFO, null -> Unit
        }
    }

    val leave: () -> Unit = { if (viewModel.requestClose()) onClose() }

    BackHandler {
        // Back out of handwriting mode first: it is a mode Abu entered, and leaving the whole
        // page because he wanted to go back to drawing would lose the writing underneath it.
        if (state.handwritingMode) viewModel.stopHandwriting() else leave()
    }

    val startHandwriting: (View) -> Unit = remember(context, viewModel) {
        { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                InkHandwritingSession(context).start(view, viewModel::onHandwriting)
            }
        }
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = wording.title,
                onBack = leave,
                backContentDescription = wording.close,
                // Only while there is something to write. See `InkCanvasUiState.isSaveOffered`.
                action = if (state.isSaveOffered) {
                    {
                        MiraButton(
                            text = when {
                                state.saving -> wording.saving
                                state.saveIsRemoval -> wording.removePage
                                else -> wording.save
                            },
                            onClick = viewModel::save,
                            // The bar leaves a narrow margin sized for a glyph in a 48dp box,
                            // which centres that glyph on the screen gutter. A filled button has
                            // no such air inside it, so its visible edge landed half a gutter
                            // further out than the control bar below and the page had two right
                            // edges. This puts it back on the one everything else uses.
                            modifier = Modifier.padding(end = spacing.sm),
                            enabled = state.canSave,
                        )
                    }
                } else {
                    null
                },
            )
        },
        // The page runs to the edges of the glass, so the scaffold's gutter is not applied here;
        // the few things that need one take it from the spacing scale themselves. The insets are
        // still honoured, which is what keeps the control bar out from under a cutout.
        horizontalPadding = 0.dp,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) {
                    MiraLoadingIndicator()
                } else {
                    AndroidView(
                        factory = { viewContext ->
                            InkCanvasView(
                                context = viewContext,
                                engine = viewModel.engine,
                                inkColor = inkColour,
                                eraserRingColor = accentColour,
                                hairlinePx = hairlinePx,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { canvas ->
                            canvas.inkColor = inkColour
                            canvas.eraserRingColor = accentColour
                            canvas.hairlinePx = hairlinePx
                            canvas.handwritingMode = state.handwritingMode
                            canvas.contentDescription = wording.canvasDescription
                            canvas.onHandwritingRequested = startHandwriting
                        },
                    )
                    // It leaves on the first contact, so it fades rather than blinking out from
                    // under the nib — and it fades rather than moves, which is the rule: there
                    // is nothing travelling here, only light.
                    // A faded alpha rather than AnimatedVisibility: the hint never moves and never
                    // changes the layout, so there is nothing to animate but light — and an
                    // enclosing Column put `ColumnScope.AnimatedVisibility` in scope inside this
                    // Box, which is an ambiguity worth not having for an effect this simple.
                    val hint by animateFloatAsState(
                        targetValue = if (state.showEmptyHint) 1f else 0f,
                        animationSpec = MiraMotion.fade(),
                        label = "InkEmptyHint",
                    )
                    Text(
                        text = wording.emptyHint,
                        modifier = Modifier
                            .padding(horizontal = spacing.xxl)
                            .graphicsLayer { alpha = hint },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                InkOverlays(
                    state = state,
                    wording = wording,
                    onDismissNotice = viewModel::dismissNotice,
                    onUndo = viewModel::undo,
                    onStopHandwriting = viewModel::stopHandwriting,
                    onKeepText = viewModel::keepRecognisedText,
                    onDiscardText = viewModel::discardRecognisedText,
                )
            }

            InkControlBar(
                state = state,
                wording = wording,
                onSelectTool = viewModel::selectTool,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = viewModel::clear,
                onToggleFinger = { viewModel.setFingerDrawing(!state.fingerDrawing) },
                onHandwriting = viewModel::startHandwriting,
            )
        }
    }

    if (state.confirmingLeave) {
        MiraDialog(
            title = wording.leaveTitle,
            onDismissRequest = viewModel::cancelLeave,
            confirmText = wording.leaveConfirm,
            onConfirm = {
                viewModel.confirmLeave()
                onClose()
            },
            message = wording.leaveMessage,
            dismissText = wording.leaveCancel,
            onDismiss = viewModel::cancelLeave,
            destructive = true,
        )
    }
}

/**
 * Everything that has something to say, floating over the page rather than shortening it.
 *
 * A `BoxScope` child cannot change the height of the box it sits in, which is the whole point:
 * the canvas keeps the size it was measured at, so no banner costs Abu a re-fit of his
 * handwriting or the undo stack behind it.
 */
@Composable
private fun BoxScope.InkOverlays(
    state: InkCanvasUiState,
    wording: InkWording,
    onDismissNotice: () -> Unit,
    onUndo: () -> Unit,
    onStopHandwriting: () -> Unit,
    onKeepText: () -> Unit,
    onDiscardText: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val notice = state.notice
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (notice != null) {
            MiraInlineNotice(
                text = wording.notice(notice),
                tone = notice.tone,
                dismissContentDescription = wording.dismiss,
                onDismiss = onDismissNotice,
                actionText = wording.noticeAction(notice),
                onAction = if (notice.isUndoable) onUndo else null,
            )
        }
        if (state.handwritingMode) {
            MiraInlineNotice(
                text = wording.handwritingHint,
                dismissContentDescription = wording.handwritingStop,
                onDismiss = onStopHandwriting,
            )
        }
    }

    val recognised = state.recognised
    if (recognised != null) {
        InkRecognisedPanel(
            text = recognised,
            wording = wording,
            onKeep = onKeepText,
            onDiscard = onDiscardText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        )
    }
}

/**
 * What the keyboard read, before any of it reaches the note.
 *
 * Shown rather than applied, because recognition guesses: a word put straight into Abu's note on
 * a guess would be Mira editing his writing for him.
 */
@Composable
private fun InkRecognisedPanel(
    text: String,
    wording: InkWording,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = wording.recognisedTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = spacing.xxl * 3)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraButton(
                text = wording.discardText,
                onClick = onDiscard,
                style = MiraButtonStyle.TEXT,
            )
            MiraButton(text = wording.keepText, onClick = onKeep)
        }
    }
}

/**
 * The only chrome on the page.
 *
 * Undo and redo are disabled rather than hidden when there is nothing to take back — a control
 * that moves as the page changes is harder to find than one that dims. The handwriting control
 * is the opposite: it disappears entirely on a phone whose keyboard cannot do it, because an
 * offer Mira cannot keep is worse than no offer.
 */
@Composable
private fun InkControlBar(
    state: InkCanvasUiState,
    wording: InkWording,
    onSelectTool: (InkTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onToggleFinger: () -> Unit,
    onHandwriting: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        contentPadding = PaddingValues(spacing.sm),
    ) {
        MiraSegmentedControl(
            options = INK_TOOLS.map(wording::toolName),
            selectedIndex = INK_TOOLS.indexOf(state.tool).coerceAtLeast(0),
            onSelect = { index -> onSelectTool(INK_TOOLS[index]) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.handwritingMode,
        )
        // Which implement, and what to do with it, are two groups: a step apart, not a hairline.
        Spacer(Modifier.height(spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraIconButton(
                icon = MiraIcons.Undo,
                contentDescription = wording.undo,
                onClick = onUndo,
                enabled = state.canUndo,
            )
            MiraIconButton(
                icon = MiraIcons.Redo,
                contentDescription = wording.redo,
                onClick = onRedo,
                enabled = state.canRedo,
            )
            MiraIconButton(
                icon = MiraIcons.DeleteOutline,
                contentDescription = wording.clear,
                onClick = onClear,
                enabled = state.canClear,
            )
            MiraIconButton(
                icon = MiraIcons.TouchApp,
                contentDescription = wording.fingerDrawing(state.fingerDrawing),
                onClick = onToggleFinger,
                // The tint says which way this is set, and the tint is a thing only the eye can
                // read. TalkBack is told the state as a state.
                modifier = Modifier.semantics {
                    stateDescription = wording.fingerDrawingState(state.fingerDrawing)
                },
                tint = if (state.fingerDrawing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
            if (state.handwritingAvailable) {
                // No champagne branch here: the control is disabled for exactly as long as
                // handwriting mode is on, and a disabled `MiraIconButton` draws itself muted
                // whatever tint it is handed — so a "tint it when the mode is on" case could
                // never once have reached the screen. What says the mode is on is the notice
                // over the page, which can also turn it off.
                MiraIconButton(
                    icon = MiraIcons.HistoryEdu,
                    contentDescription = wording.handwriting,
                    onClick = onHandwriting,
                    enabled = !state.handwritingMode,
                )
            }
        }
    }
}

@Preview
@Composable
private fun InkControlBarPreview() {
    MiraPreviewSurface {
        InkControlBar(
            state = InkCanvasUiState(
                loading = false,
                strokeCount = 3,
                canUndo = true,
                handwritingAvailable = true,
            ),
            wording = EnglishInkWording,
            onSelectTool = {},
            onUndo = {},
            onRedo = {},
            onClear = {},
            onToggleFinger = {},
            onHandwriting = {},
        )
        InkRecognisedPanel(
            text = "Call the landlord about the lease on Tuesday",
            wording = EnglishInkWording,
            onKeep = {},
            onDiscard = {},
        )
    }
}
