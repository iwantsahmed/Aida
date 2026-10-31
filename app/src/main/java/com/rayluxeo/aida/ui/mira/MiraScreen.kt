package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rayluxeo.aida.ui.invoices.delivery.InvoiceDeliveryConfirmationDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraSpacing
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraConfirmationCard
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.VoiceSessionState
import com.rayluxeo.aida.voice.toAvatarState
import java.time.Instant
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rayluxeo.aida.core.design.component.MiraAvatar
import com.rayluxeo.aida.core.design.component.MiraAvatarState
import com.rayluxeo.aida.core.design.component.MiraBarIcon
import com.rayluxeo.aida.core.design.rememberMiraHaptics

/**
 * Mira's own screen: the transcript, the mark, and a text field that can do everything the mark
 * can. Nothing here is gated behind speech — a denied or missing microphone costs Abu
 * convenience, never capability.
 *
 * The avatar reports a press and draws what the voice controller says is happening. It decides
 * nothing: the one thing this layer does first is ask Android for the microphone, because that
 * is a dialog and a dialog can only be raised from here.
 *
 * It is also where what Mira prepares becomes something he can press. The dialler, the messaging
 * app and every permission dialog are launched from here and nowhere else: the view model holds
 * the offer and hears the answer, and only this layer touches Android.
 */
@Composable
fun MiraScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MiraViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val focusManager = LocalFocusManager.current
    var showHistorySheet by remember { mutableStateOf(false) }
    val threads by viewModel.observeThreads().collectAsStateWithLifecycle(emptyList())

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onMicrophonePermissionResult(
            granted = granted,
            canAskAgain = activity
                ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true,
        )
    }

    // Re-read on every resume, not only on entry: Abu may have granted the microphone on a system
    // screen and walked straight back in, and the mark has to come back to life when he does.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onMicrophoneChecked(context.hasMicrophonePermission())
    }

    // The controller lives as long as the application, so a listening session left open when Abu
    // walks away stays open. Nothing else would close it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onScreenLeft()
    }

    // Asked of the platform rather than read out of the result map: a calendar is two permissions,
    // and a grant Abu made in a settings screen a moment ago counts just as much as this dialog's.
    val capabilityRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val grant = state.action as? MiraAction.Grant ?: return@rememberLauncherForActivityResult
        viewModel.onCapabilityResult(
            capability = grant.capability,
            granted = context.hasGranted(grant.capability),
            canAskAgain = results.keys.any {
                activity?.shouldShowRequestPermissionRationale(it) == true
            },
        )
    }

    val capabilitySettings = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val grant = state.action as? MiraAction.Grant ?: return@rememberLauncherForActivityResult
        viewModel.onCapabilityResult(
            capability = grant.capability,
            granted = context.hasGranted(grant.capability),
            // A settings screen that came back without the grant leaves the settings screen as
            // the only route there still is.
            canAskAgain = false,
        )
    }

    // What the tab was opened to do, honoured once on arrival. A question from Files or the
    // scanner drops into the composer for Abu to read and send; the Talk shortcut and the voice
    // tile start a listening turn as if he had pressed the mark himself.
    val entryIntent by viewModel.entryIntent.collectAsStateWithLifecycle()
    LaunchedEffect(entryIntent) {
        when (val intent = entryIntent) {
            null -> Unit
            is MiraEntryIntent.Ask -> {
                viewModel.onDraftChange(intent.prompt)
                viewModel.onEntryHandled()
            }
            MiraEntryIntent.StartListening -> {
                if (state.voice.state == MiraVoiceState.IDLE && !state.speaking) {
                    if (context.hasMicrophonePermission()) {
                        viewModel.onOrbPressed()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                viewModel.onEntryHandled()
            }
        }
    }

    MiraScreenContent(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onSend = {
            focusManager.clearFocus()
            viewModel.sendDraft()
        },
        onOrb = {
            focusManager.clearFocus()
            // The only decision made here is whether Android has to be asked first. What the
            // press itself means is the controller's, in every state.
            if (state.pressWouldOnlyInterrupt || context.hasMicrophonePermission()) {
                viewModel.onOrbPressed()
            } else {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onConfirm = viewModel::confirmPending,
        onCancelPending = viewModel::cancelPending,
        onDismissNotice = viewModel::dismissNotice,
        onNoticeOffer = { offer ->
            when (offer) {
                NoticeOffer.RETRY -> viewModel.retryVoice()

                NoticeOffer.ALLOW_MICROPHONE ->
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)

                NoticeOffer.MICROPHONE_SETTINGS -> {
                    val opened = MiraCapability.MICROPHONE.settingsIntents(context)
                        .any { intent -> context.tryStartActivity(intent) }
                    if (!opened) viewModel.onActionUnavailable()
                }
            }
        },
        onOpenSettings = onOpenSettings,
        onOpenHistory = { showHistorySheet = true },
        onAction = { action ->
            focusManager.clearFocus()
            when (action) {
                is MiraAction.Call ->
                    if (!context.tryStartActivity(dialIntent(action.number))) {
                        viewModel.onActionUnavailable()
                    }

                is MiraAction.Message -> {
                    val intent = if (action.app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) {
                        whatsAppIntent(action.number, action.body)
                    } else {
                        messageIntent(action.number, action.body)
                    }
                    if (!context.tryStartActivity(intent)) {
                        if (action.app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) {
                            val fallback = Intent(Intent.ACTION_VIEW, intent.data)
                            if (!context.tryStartActivity(fallback)) {
                                viewModel.onActionUnavailable()
                            }
                        } else {
                            viewModel.onActionUnavailable()
                        }
                    }
                }

                is MiraAction.Grant -> {
                    val permissions = action.capability.permissions()
                    if (action.viaSettings || permissions.isEmpty()) {
                        val opened = action.capability.settingsIntents(context).any { intent ->
                            capabilitySettings.tryLaunch(intent)
                        }
                        if (!opened) viewModel.onActionUnavailable()
                    } else {
                        capabilityRequest.launch(permissions.toTypedArray())
                    }
                }

                is MiraAction.DeliverInvoice -> Unit
            }
        },
        modifier = modifier,
    )

    (state.action as? MiraAction.DeliverInvoice)?.let { action ->
        var selectedMethod by remember(action.draft) { mutableStateOf(action.draft.method) }
        InvoiceDeliveryConfirmationDialog(
            draft = action.draft.copy(method = selectedMethod),
            language = state.language,
            onMethodSelect = { selectedMethod = it },
            onConfirm = {
                viewModel.onInvoiceDeliveryConfirmed(
                    context = context,
                    draft = action.draft.copy(method = selectedMethod),
                )
            },
            onDismiss = { viewModel.onInvoiceDeliveryDismissed() },
        )
    }

    if (showHistorySheet) {
        ConversationHistorySheet(
            threads = threads,
            language = state.language,
            onDismiss = { showHistorySheet = false },
            onStartNewConversation = {
                viewModel.startNewConversation()
                showHistorySheet = false
            },
            onDeleteConversation = { id -> viewModel.deleteConversation(id) },
            onDeleteAllConversations = {
                viewModel.deleteAllConversations()
                showHistorySheet = false
            },
            onSearch = { query -> viewModel.searchConversations(query) },
            onObserveThreadMessages = { id -> viewModel.observeThreadMessages(id) },
        )
    }
}

/**
 * One screen: her presence at the top, the exchange in the middle, and the one bar that never
 * scrolls at the foot. The wordmark, the hero, the transcript and the suggestions share a single
 * scroll, so a conversation grows past the greeting the way a real one does — she is what he sees
 * first, and then what she is doing for him.
 */
@Composable
private fun MiraScreenContent(
    state: MiraUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOrb: () -> Unit,
    onConfirm: () -> Unit,
    onCancelPending: () -> Unit,
    onDismissNotice: () -> Unit,
    onNoticeOffer: (NoticeOffer) -> Unit,
    onAction: (MiraAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val wording = miraWording(state.language)
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val turns = (state.transcript as? TranscriptState.Content)?.turns.orEmpty()

    // The last thing said stays in view: a new turn, or the live partial growing, pulls the column
    // to its foot so the answer is never left below the ask bar.
    LaunchedEffect(turns.size, state.partial) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    MiraScaffold(
        modifier = modifier,
        // Transparent so the shell's aurora reads through — behind the transcript and behind the
        // translucent nav bar, the Gemini-like backdrop Abu asked for (16 Oct 2026).
        transparent = true,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
            ) {
                item {
                    // Siri-like: no wordmark, no owner chip — Mira's face below is the identity, and
                    // Abu knows whose phone it is. Just two quiet glyphs: the conversation history and
                    // the way to settings.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.md),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiraBarIcon(
                            icon = MiraIcons.Article,
                            contentDescription = wording.conversationHistory,
                            onClick = onOpenHistory,
                        )
                        MiraBarIcon(
                            icon = MiraIcons.Settings,
                            contentDescription = wording.settings,
                            onClick = onOpenSettings,
                        )
                    }
                }
                item {
                    MiraListeningHero(
                        state = state,
                        wording = wording,
                        onOrb = onOrb,
                        modifier = Modifier.padding(top = spacing.md),
                    )
                }
                if (state.transcript is TranscriptState.Loading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) { MiraLoadingIndicator() }
                    }
                }
                itemsIndexed(items = turns, key = { _, turn -> turn.id }) { index, turn ->
                    MiraTurnCard(
                        turn = turn,
                        // Delivering an invoice is confirmed in its own dialog (shown above the
                        // transcript), so the turn card shows no button for it — the card's button
                        // would be a second, unlocalized control with nothing behind it (§12).
                        action = state.action.takeIf {
                            turn.id == state.actionTurnId && it !is MiraAction.DeliverInvoice
                        },
                        wording = wording,
                        onAction = onAction,
                        modifier = Modifier.padding(
                            top = if (index == 0) {
                                spacing.lg
                            } else {
                                spacing.gapBefore(turn.role, turns.getOrNull(index - 1)?.role)
                            },
                        ),
                    )
                }
                // Starter prompts belong to a blank slate: once a conversation is under way they
                // would sit beneath the exchange and pull the scroll anchor off the latest answer.
                if (state.transcript is TranscriptState.Empty) {
                    item {
                        MiraSuggestions(
                            wording = wording,
                            onSuggestion = { draft ->
                                onDraftChange(draft)
                                focusRequester.requestFocus()
                            },
                            modifier = Modifier.padding(top = spacing.xl),
                        )
                    }
                }
                item { Spacer(Modifier.height(spacing.md)) }
            }

            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = notice.text,
                    tone = MiraNoticeTone.DANGER,
                    dismissContentDescription = wording.dismiss,
                    onDismiss = onDismissNotice,
                    actionText = notice.offer?.label(wording),
                    onAction = notice.offer?.let { offer -> { onNoticeOffer(offer) } },
                )
                Spacer(Modifier.height(spacing.sm))
            }

            if (state.pending != null) {
                MiraConfirmationCard(
                    question = state.pending.summary,
                    confirmText = wording.confirmYes,
                    onConfirm = onConfirm,
                    cancelText = wording.confirmNo,
                    onCancel = onCancelPending,
                )
                Spacer(Modifier.height(spacing.sm))
            }

            MiraAskBar(
                state = state,
                wording = wording,
                focusRequester = focusRequester,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onOrb = onOrb,
                modifier = Modifier.padding(bottom = spacing.md),
            )
        }
    }
}

/**
 * Mira herself — her mark, with what she is doing under it.
 *
 * The sphere is the control: a mark that answers his voice is a better invitation than a sentence
 * telling him he may speak. It is her face through the whole turn — breathing while she listens,
 * thinking, speaking — so the listening state has a presence rather than a graph. The one line
 * beneath appears only while something is actually happening, so a quiet screen is just the mark,
 * the way Apple leaves a voice control alone until it has something to say.
 */
@Composable
private fun MiraListeningHero(
    state: MiraUiState,
    wording: MiraWording,
    onOrb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val voiceLabel = state.voiceDescription(wording)
        MiraAvatar(
            // A reply to a typed line is spoken with the controller idle, so speaking is read off
            // its own flag; otherwise the turn's own state names her expression.
            state = if (state.speaking) MiraAvatarState.SPEAKING else state.voice.state.toAvatarState(),
            amplitude = state.voice.amplitude,
            onClick = onOrb,
            contentDescription = voiceLabel,
            size = spacing.xxl * HERO_AVATAR_UNITS,
            modifier = Modifier.padding(vertical = spacing.xs),
        )
        Spacer(Modifier.height(spacing.sm))
        // While she is hearing him, the line under her face becomes the words themselves — the live
        // partial, the way Home does it — so the recognised speech has one home on the screen rather
        // than a second banner repeating it. Otherwise it is the state, or the quiet invitation.
        val status = state.partial ?: state.statusLine(wording) ?: wording.tapToSpeak
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            color = if (state.listening || state.speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = PARTIAL_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One turn, as a card.
 *
 * Abu's is graphite with a plain glyph; hers carries the champagne edge and the spark, and — when
 * the turn actually did something — a chip naming what, drawn straight under the sentence that
 * explains it. The two are told apart by the accent and the icon, not by which side of the screen
 * they hug, so the transcript stays on one left edge and never reads as a chat app's wall of boxes.
 */
@Composable
private fun MiraTurnCard(
    turn: ConversationTurn,
    action: MiraAction?,
    wording: MiraWording,
    onAction: (MiraAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val fromAbu = turn.role == TurnRole.ABU
    // No surface, no border, no bubble: a conversation read down one left edge rather than a chat
    // app's wall of boxes (the thing Abu asked to be rid of). The speaker is told by the mark — his
    // quiet person dot, her mascot — and by the colour of the words, not by a box around them.
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (fromAbu) {
            Box(
                modifier = Modifier
                    .size(spacing.lg)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiraIcons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(spacing.sm + spacing.xxs),
                )
            }
        } else {
            MiraAvatar(
                state = MiraAvatarState.IDLE,
                amplitude = 0f,
                size = spacing.lg,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                // His words recede, hers carry: the one contrast that separates the two speakers now
                // that neither sits in a box.
                color = if (fromAbu) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            )
            if (!fromAbu) {
                turn.commandName?.let { command ->
                    wording.commandResult(command)?.let { label ->
                        Spacer(Modifier.height(spacing.xs))
                        MiraResultChip(label = label, command = command)
                    }
                }
                action?.let {
                    Spacer(Modifier.height(spacing.sm))
                    MiraButton(
                        text = action.label(wording),
                        onClick = { onAction(action) },
                        style = MiraButtonStyle.SECONDARY,
                        leadingIcon = action.icon(),
                    )
                }
            }
        }
    }
}

/** The 'what she did' chip inside a Mira turn: a glyph for the kind of thing, and the words for it. */
@Composable
private fun MiraResultChip(label: String, command: String, modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    // A quiet line, not a filled pill with a chevron: it says what she did, and it does not pretend
    // to be tappable (it never had a handler). One small champagne glyph, the words beside it.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = command.resultIcon(),
            contentDescription = null,
            tint = MiraTheme.colors.accentDim,
            modifier = Modifier.size(spacing.md),
        )
        Spacer(Modifier.width(spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MiraTheme.colors.accentDim,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun String.resultIcon() = when (this) {
    "CreateReminder", "CreatePlaceReminder", "CreateAlarm" -> MiraIcons.Alarm
    "CreateTimer" -> MiraIcons.Timer
    "CreateTask", "UpdateTask", "CompleteTask" -> MiraIcons.CheckCircle
    "CreateNote" -> MiraIcons.Description
    "RecordPayment", "RecordExpense", "RecordIncome", "CreateInvoiceDraft" -> MiraIcons.ReceiptLong
    "CreateClient" -> MiraIcons.Person
    "CreateCalendarEvent", "UpdateCalendarEvent", "DeleteCalendarEvent" -> MiraIcons.CalendarMonth
    else -> MiraIcons.CheckCircle
}

/**
 * Starter prompts, two by two.
 *
 * Tapping one FILLS the field rather than firing it, so a prompt can be edited or thought better of
 * — and it never sends a command blind that Mira might then have to say she did not understand.
 */
@Composable
private fun MiraSuggestions(
    wording: MiraWording,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val icons = listOf(
        MiraIcons.Article,
        MiraIcons.CheckCircle,
        MiraIcons.Alarm,
        MiraIcons.WbSunny,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = wording.alsoTry,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(spacing.sm))
        wording.suggestions.chunked(2).forEachIndexed { rowIndex, pair ->
            if (rowIndex > 0) Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                pair.forEachIndexed { i, suggestion ->
                    val icon = icons.getOrElse(rowIndex * 2 + i) { MiraIcons.CheckCircle }
                    SuggestionChip(
                        label = suggestion.label,
                        icon = icon,
                        onClick = { onSuggestion(suggestion.draft) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val shape = MaterialTheme.shapes.medium
    val haptics = rememberMiraHaptics()
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                role = Role.Button,
                onClickLabel = label,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(spacing.md),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How much air a turn gets above it, which is the whole of the transcript's rhythm.
 *
 * Related things sit closer than unrelated ones: Mira's answer hugs the question it answers, a
 * speaker carrying on gets an ordinary line's worth, and a new question from Abu opens a new
 * exchange with real space in front of it. Three intervals, each double the last, repeating all
 * the way down — so the shape of the conversation is legible before a word of it is read, and a
 * question and its answer read as one thing even where the type between them is quiet.
 *
 * Not private, so the doubling can be asserted without standing a screen up. It is arithmetic
 * over spacing tokens and nothing else, which makes it the one part of this layout that a local
 * test can hold on to.
 */
internal fun MiraSpacing.gapBefore(role: TurnRole, previous: TurnRole?): Dp = when {
    previous == null -> xxs
    previous == role -> md
    role == TurnRole.MIRA -> xs
    else -> xl
}

/**
 * A dedicated, real-time voice status bar positioned directly above the input field.
 *
 * When Abu speaks, this strip immediately shows:
 * - A live pulsing indicator
 * - Live partial speech words as they are being recognized
 * - Language mode pill ("বাংলা · EN")
 * - Processing state ("Thinking...")
 * - Speaking state ("Speaking... (Tap button to stop)")
 * - Immediate feedback if speech timed out or was not heard.
 */
@Composable
private fun MiraVoiceStatusBar(
    state: MiraUiState,
    wording: MiraWording,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val isBangla = state.language == SpokenLanguage.BENGALI

    val statusText = when {
        state.listening -> state.partial ?: if (isBangla) "শুনছি... বাংলা বা ইংরেজিতে বলুন" else "Listening... Speak in Bangla or English"
        state.voice.state == MiraVoiceState.PROCESSING || state.voice.state == MiraVoiceState.EXECUTING || state.working ->
            if (isBangla) "প্রসেস করা হচ্ছে..." else "Thinking..."
        state.speaking -> if (isBangla) "উত্তর দেওয়া হচ্ছে... (থামাতে বাটনে চাপুন)" else "Speaking... (Tap button to stop)"
        state.voice.state == MiraVoiceState.ERROR -> if (isBangla) "শুনতে পাইনি, আবার বলুন" else "Didn't catch that, tap mic to try again"
        else -> null
    } ?: return

    val statusColor = when {
        state.voice.state == MiraVoiceState.ERROR -> MiraTheme.colors.danger
        state.listening -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(spacing.hairline, statusColor.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.listening) {
                Box(
                    modifier = Modifier
                        .size(spacing.xs + spacing.xxs)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(spacing.xs))
            } else if (state.working || state.voice.state == MiraVoiceState.PROCESSING || state.voice.state == MiraVoiceState.EXECUTING) {
                MiraLoadingIndicator(modifier = Modifier.size(spacing.sm))
                Spacer(Modifier.width(spacing.xs))
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.listening) {
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = "বাংলা · EN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The bottom command bar: always accessible text input on the left and a prominent,
 * dedicated circular Champagne Voice Command button on the right.
 *
 * One tap on the voice button immediately toggles listening and provides real-time haptics.
 */
@Composable
private fun MiraAskBar(
    state: MiraUiState,
    wording: MiraWording,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOrb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val micButtonSize = spacing.xxl + spacing.xs
    val haptics = rememberMiraHaptics()

    Column(modifier = modifier.fillMaxWidth()) {
        val isVoiceActive = state.listening ||
            state.voice.state == MiraVoiceState.PROCESSING ||
            state.voice.state == MiraVoiceState.EXECUTING ||
            state.voice.state == MiraVoiceState.ERROR ||
            state.speaking

        AnimatedVisibility(
            visible = isVoiceActive,
            enter = fadeIn(MiraMotion.standard()) + expandVertically(MiraMotion.standard()),
            exit = fadeOut(MiraMotion.standard()) + shrinkVertically(MiraMotion.standard()),
        ) {
            MiraVoiceStatusBar(
                state = state,
                wording = wording,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.xs),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = wording.askAnything,
                enabled = !state.working,
                singleLine = false,
                maxLines = COMPOSER_MAX_LINES,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                leadingIcon = MiraIcons.GraphicEq,
                trailing = {
                    if (state.canSend) {
                        MiraIconButton(
                            icon = MiraIcons.Send,
                            contentDescription = wording.send,
                            onClick = onSend,
                            enabled = true,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )

            Spacer(Modifier.width(spacing.sm))

            val isListening = state.listening
            val isSpeaking = state.speaking
            val isError = state.voice.state == MiraVoiceState.ERROR

            val buttonBackground by animateColorAsState(
                targetValue = when {
                    isListening || isSpeaking -> MaterialTheme.colorScheme.primary
                    isError -> MiraTheme.colors.danger.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                },
                animationSpec = MiraMotion.standard(),
                label = "micBackground",
            )
            val buttonBorderColor by animateColorAsState(
                targetValue = when {
                    isListening || isSpeaking -> MaterialTheme.colorScheme.primary
                    isError -> MiraTheme.colors.danger
                    else -> MiraTheme.colors.hairlineStrong
                },
                animationSpec = MiraMotion.standard(),
                label = "micBorder",
            )
            val iconTint by animateColorAsState(
                targetValue = when {
                    isListening || isSpeaking -> MaterialTheme.colorScheme.onPrimary
                    isError -> MiraTheme.colors.danger
                    else -> MaterialTheme.colorScheme.primary
                },
                animationSpec = MiraMotion.standard(),
                label = "micIconTint",
            )

            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) MIC_PRESS_SCALE else 1f,
                animationSpec = MiraMotion.settle(),
                label = "micPress",
            )
            val active = state.listening || state.speaking
            val haloTarget = if (active) {
                HALO_FLOOR + HALO_VOICE * state.voice.amplitude.coerceIn(0f, 1f)
            } else {
                0f
            }
            val halo by animateFloatAsState(
                targetValue = haloTarget,
                animationSpec = MiraMotion.track(),
                label = "micHalo",
            )
            val haloColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .size(micButtonSize)
                    .drawBehind {
                        if (halo > 0f) {
                            drawCircle(
                                color = haloColor,
                                radius = size.minDimension / 2f * (1f + halo * HALO_REACH),
                                alpha = halo * HALO_ALPHA,
                            )
                        }
                    }
                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                    .clip(CircleShape)
                    .background(buttonBackground)
                    .border(spacing.hairline, buttonBorderColor, CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, color = MaterialTheme.colorScheme.primary),
                        role = Role.Button,
                        onClickLabel = state.voiceDescription(wording),
                        enabled = state.canListen || state.pressWouldOnlyInterrupt,
                    ) {
                        haptics.tap()
                        onOrb()
                    }
                    .semantics { contentDescription = state.voiceDescription(wording) },
                contentAlignment = Alignment.Center,
            ) {
                val face = when {
                    state.working -> AskButtonFace.WORKING
                    isSpeaking -> AskButtonFace.STOP
                    else -> AskButtonFace.MIC
                }
                Crossfade(targetState = face, animationSpec = MiraMotion.fade(), label = "micFace") { current ->
                    when (current) {
                        AskButtonFace.WORKING -> MiraLoadingIndicator(modifier = Modifier.size(spacing.md))
                        AskButtonFace.STOP -> Icon(
                            imageVector = MiraIcons.Stop,
                            contentDescription = if (state.listening) wording.stopListening else wording.stopMira,
                            tint = iconTint,
                            modifier = Modifier.size(spacing.lg),
                        )
                        AskButtonFace.MIC -> Icon(
                            imageVector = MiraIcons.Mic,
                            contentDescription = state.voiceDescription(wording),
                            tint = iconTint,
                            modifier = Modifier.size(spacing.lg),
                        )
                    }
                }
            }
        }
    }
}

/** The three faces the ask-bar button crossfades between: the spinner, the stop square, the mic. */
private enum class AskButtonFace { WORKING, STOP, MIC }

/**
 * The seven voice states, one to one. Written out rather than mapped by name so that a state
 * added to either enum stops compiling here instead of silently drawing the wrong thing.
 */

/**
 * The status line doubles as the engine indicator, which is as loud as it needs to be.
 *
 * A spoken turn is reported from the controller's own state; [MiraUiState.working] is the typed
 * one, which reaches the same pipeline by a different door. Nothing here claims there is no
 * speech service: that is a thing only a real attempt can establish, and when one fails the
 * notice says it in Abu's own language.
 */
private fun MiraUiState.statusLine(wording: MiraWording): String? = when {
    voice.state == MiraVoiceState.LISTENING -> wording.listening
    voice.state == MiraVoiceState.PROCESSING || working -> wording.thinking
    voice.state == MiraVoiceState.EXECUTING -> wording.carryingOut
    voice.state == MiraVoiceState.SPEAKING || speaking -> wording.speaking
    microphone == MicrophoneAccess.SETTINGS_ONLY -> wording.microphoneOff
    lastEngine == EngineId.LOCAL -> wording.answeredOnDevice
    lastEngine == EngineId.GEMINI -> wording.answeredByGemini
    else -> null
}

/**
 * The mark carries no glyph, so everything it means — including why it is dead — has to be said
 * to TalkBack here instead.
 *
 * Not private, because this is the one thing on the screen Abu cannot check for himself: the
 * branches are read off state and nothing else, so a test can hold them where an eye cannot.
 */
internal fun MiraUiState.voiceDescription(wording: MiraWording): String = when {
    voice.state == MiraVoiceState.LISTENING -> wording.stopListening
    // Not a button at all while a typed command is in flight, so the only honest thing to say
    // about it is what it is showing. "Speak to Mira" over a mark that will not answer a press
    // is the one mistake here TalkBack gives Abu no way to catch.
    working -> wording.thinking
    // The same test the press itself makes, rather than the controller's state alone: a reply to
    // a typed line is read out from outside the controller's turn, and a press stops her all the
    // same. Announcing "Speak to Mira" there described the wrong half of the screen.
    pressWouldOnlyInterrupt -> wording.stopMira
    microphone == MicrophoneAccess.SETTINGS_ONLY -> wording.speechOff
    else -> wording.speakToMira
}

/** Each of these is something that can genuinely be done; a notice with nothing to do has none. */
private fun NoticeOffer.label(wording: MiraWording): String = when (this) {
    NoticeOffer.RETRY -> wording.tryAgain
    NoticeOffer.ALLOW_MICROPHONE -> wording.allowMicrophone
    NoticeOffer.MICROPHONE_SETTINGS -> wording.openMicrophoneSettings
}

/**
 * What the control says it will do, which is exactly what it does. Each of these opens something
 * and hands it over; not one of them finishes the act, and the words must not suggest otherwise.
 */
private fun MiraAction.label(wording: MiraWording): String = when (this) {
    is MiraAction.Call -> wording.openDialler
    is MiraAction.Message ->
        if (app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) wording.openWhatsApp
        else wording.openMessage
    is MiraAction.Grant ->
        if (viaSettings) wording.openAppSettings else wording.grant(capability)
    is MiraAction.DeliverInvoice -> "Deliver Invoice"
}

@Composable
private fun MiraAction.icon(): ImageVector = when (this) {
    is MiraAction.Call -> MiraIcons.Call
    is MiraAction.Message -> MiraIcons.Message
    is MiraAction.Grant -> MiraIcons.Lock
    is MiraAction.DeliverInvoice -> MiraIcons.Send
}

/**
 * A phone whose OEM has dropped a settings screen throws on the way there rather than returning,
 * so the candidates are tried in turn until one opens.
 *
 * @return false when none of them exists on this phone.
 */
private fun ActivityResultLauncher<Intent>.tryLaunch(intent: Intent): Boolean = try {
    launch(intent)
    true
} catch (missing: ActivityNotFoundException) {
    false
}

private fun Context.hasMicrophonePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The mark on the empty screen, in units of the spacing scale rather than as a loose dp: three
 * touch targets across, which is large enough to be the subject of the composition and small
 * enough to survive the keyboard being open under it.
 */
private const val HERO_AVATAR_UNITS = 2

// The mic button's press and its listening halo, as fractions rather than loose dp so the whole
// gesture is tuned in one place. The halo floor keeps a presence while she listens in silence; the
// voice term lifts it with Abu's live amplitude; reach and alpha are how far and how brightly it
// bleeds past the button's edge.
private const val MIC_PRESS_SCALE = 0.9f
private const val HALO_FLOOR = 0.22f
private const val HALO_VOICE = 0.5f
private const val HALO_REACH = 0.45f
private const val HALO_ALPHA = 0.4f

/** The invitation is set to a measure rather than to the gutter, so it reads as a line, not a bar. */

private const val COMPOSER_MAX_LINES = 5

/**
 * How much of what Abu is part way through saying is held on screen.
 *
 * The recogniser's partial is the only element here that grows without anyone asking it to, and it
 * sits between the transcript and the field — so left uncapped, a long sentence squeezes the
 * conversation a line at a time while he is still speaking.
 */
private const val PARTIAL_MAX_LINES = 3

@Preview(widthDp = 400, heightDp = 880)
@Composable
private fun MiraScreenPreview() {
    MiraTheme {
        MiraScreenContent(
            state = MiraUiState(
                transcript = TranscriptState.Content(
                    listOf(
                        ConversationTurn(
                            id = 1L,
                            role = TurnRole.ABU,
                            text = "Ahmed ke call koro",
                            language = SpokenLanguage.ENGLISH,
                            source = InputSource.TEXT,
                            createdAt = Instant.EPOCH,
                        ),
                        ConversationTurn(
                            id = 2L,
                            role = TurnRole.MIRA,
                            text = "Call Ahmed Rahman\n+880 1712 345678",
                            language = SpokenLanguage.ENGLISH,
                            source = InputSource.SYSTEM,
                            commandName = "PrepareCall",
                            createdAt = Instant.EPOCH,
                        ),
                    ),
                ),
                voice = VoiceSessionState(canListen = true),
                microphone = MicrophoneAccess.GRANTED,
                action = MiraAction.Call("+8801712345678"),
                lastEngine = EngineId.LOCAL,
            ),
            onDraftChange = {},
            onSend = {},
            onOrb = {},
            onConfirm = {},
            onCancelPending = {},
            onDismissNotice = {},
            onNoticeOffer = {},
            onAction = {},
            onOpenSettings = {},
        )
    }
}

@Preview(widthDp = 400, heightDp = 880)
@Composable
private fun MiraScreenListeningPreview() {
    MiraTheme {
        MiraScreenContent(
            state = MiraUiState(
                transcript = TranscriptState.Empty,
                voice = VoiceSessionState(
                    state = MiraVoiceState.LISTENING,
                    amplitude = 0.62f,
                    partialTranscript = "kal shokal dosh tay",
                    canListen = true,
                ),
                microphone = MicrophoneAccess.GRANTED,
            ),
            onDraftChange = {},
            onSend = {},
            onOrb = {},
            onConfirm = {},
            onCancelPending = {},
            onDismissNotice = {},
            onNoticeOffer = {},
            onAction = {},
            onOpenSettings = {},
        )
    }
}
