package com.rayluxeo.aida.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraConfirmationCard
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraStatus
import com.rayluxeo.aida.core.design.component.MiraStatusIndicator
import com.rayluxeo.aida.core.design.component.MiraAvatar
import com.rayluxeo.aida.ui.mira.speechFailureWording
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.VoiceSessionState
import com.rayluxeo.aida.voice.toAvatarState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Everything the assistant overlay draws, as one value.
 *
 * [heard] and [answer] are the current turn only — the overlay sits over another app and is not a
 * place to read history, so a turn from before this session opened never reaches here.
 *
 * [screen] carries the two facts about the assist context that are *shown*, and deliberately not
 * the screen text itself: the overlay says what Android shared with Mira, and has no way to put
 * the contents of another app's window on top of that app's window.
 */
data class AssistantOverlayState(
    val voice: VoiceSessionState = VoiceSessionState(),
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val heard: String? = null,
    val answer: String? = null,
    /** Already rendered in Abu's language by the caller; null when nothing is waiting on a yes. */
    val pendingSummary: String? = null,
    val screen: Screen? = null,
    val hapticsEnabled: Boolean = true,
) {
    /** What Android handed the assistant, reduced to what Abu is told about it. */
    data class Screen(val appLabel: String?, val includesScreenshot: Boolean)

    val listening: Boolean get() = voice.state == MiraVoiceState.LISTENING

    /** True while the turn is still moving, which is when a failure from the last one is stale. */
    val busy: Boolean
        get() = when (voice.state) {
            MiraVoiceState.LISTENING,
            MiraVoiceState.PROCESSING,
            MiraVoiceState.EXECUTING,
            MiraVoiceState.SPEAKING,
            -> true

            MiraVoiceState.IDLE, MiraVoiceState.SUCCESS, MiraVoiceState.ERROR -> false
        }

    val failure: SpeechFailure? get() = voice.lastFailure?.takeUnless { busy }

    /**
     * The one line under the status: what Abu is part way through saying while he is saying it,
     * and what Mira committed to having heard once he has stopped.
     */
    val spokenLine: String?
        get() = voice.partialTranscript?.takeIf { listening && it.isNotBlank() } ?: heard
}

/**
 * Mira over another app: a panel at the bottom edge, never a full-screen takeover.
 *
 * It shows one turn and nothing else — no branding, no settings, no scrolling history — because
 * whatever Abu was doing is still underneath it and he asked a question rather than opened an app.
 *
 * Dismissal is deliberately cheap in both directions: anywhere outside the panel, or a downward
 * swipe on it. A session that will not go away is worse than one that never opened.
 */
@Composable
fun AssistantOverlay(
    state: AssistantOverlayState,
    onOrb: () -> Unit,
    onConfirm: () -> Unit,
    onCancelPending: () -> Unit,
    onRetry: () -> Unit,
    onOpenMira: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val language = state.language

    Box(modifier = modifier.fillMaxSize()) {
        // The app behind stays visible and untouched; tapping it puts Abu back where he was.
        //
        // The scrim is what makes this read as one panel rather than two cards adrift on whatever
        // wallpaper happens to be underneath. It is Material's own scrim token, so it dims without
        // hiding: Abu can still see where he was, which is the point of an overlay.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = AssistantOverlayWording.dismiss(language),
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = spacing.md, vertical = spacing.md)
                .then(swipeDownToDismiss(onDismiss)),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            val failure = state.failure
            if (failure != null) {
                MiraInlineNotice(
                    text = speechFailureWording(failure, language),
                    tone = MiraNoticeTone.DANGER,
                    actionText = AssistantOverlayWording.failureAction(failure, language),
                    onAction = if (failure.isRetryable) onRetry else onOpenMira,
                )
            }

            // The gate the pipeline asked for, inline and identical to the one on Mira's own
            // screen. A mutating command never runs from here without it.
            val pending = state.pendingSummary
            if (pending != null) {
                MiraConfirmationCard(
                    question = pending,
                    confirmText = AssistantOverlayWording.confirm(language),
                    onConfirm = onConfirm,
                    cancelText = AssistantOverlayWording.cancel(language),
                    onCancel = onCancelPending,
                )
            }

            AssistantPanel(state = state, onOrb = onOrb)
        }
    }
}

/**
 * Enough to settle the panel onto its own ground without blacking out the app behind it — an
 * overlay that hides where Abu was stops being an overlay.
 */
private const val SCRIM_ALPHA = 0.55f

@Composable
private fun AssistantPanel(
    state: AssistantOverlayState,
    onOrb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val language = state.language

    MiraCard(modifier = modifier.fillMaxWidth()) {
        val screen = state.screen
        if (screen != null) {
            Text(
                text = AssistantOverlayWording.screenNote(screen, language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.sm))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Deliberately never disabled. The controller's own answer about the microphone can
            // be a moment behind a permission just granted, and a press that cannot listen is
            // answered with a notice that offers a way forward — which beats a dead orb.
            MiraAvatar(
                state = state.voice.state.toAvatarState(),
                amplitude = state.voice.amplitude,
                onClick = onOrb,
                modifier = Modifier.size(spacing.xxl + spacing.md),
                contentDescription = AssistantOverlayWording.orb(state.voice.state, language),
            )
            Column(modifier = Modifier.weight(1f)) {
                MiraStatusIndicator(
                    status = state.voice.state.asStatus(),
                    label = AssistantOverlayWording.status(state.voice.state, language),
                )
                val line = state.spokenLine
                if (!line.isNullOrBlank()) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = HEARD_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val answer = state.answer
        if (!answer.isNullOrBlank()) {
            Spacer(Modifier.height(spacing.md))
            // A long answer scrolls inside the panel rather than being cut off: it was spoken
            // once and Abu must still be able to read all of it without opening the app.
            Box(
                modifier = Modifier
                    .heightIn(max = spacing.xxl * ANSWER_MAX_HEIGHT_UNITS)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * A downward drag past the panel's own height puts it away.
 *
 * The threshold is measured rather than fixed, so the gesture means the same thing whether the
 * panel is one line of status or a confirmation as well.
 */
@Composable
private fun swipeDownToDismiss(onDismiss: () -> Unit): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val minimumTravel = with(LocalDensity.current) { MiraTheme.spacing.xxl.toPx() }
    val height = remember { mutableFloatStateOf(0f) }

    return Modifier
        .onSizeChanged { height.floatValue = it.height.toFloat() }
        .offset { IntOffset(0, offset.value.roundToInt()) }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    val enough = maxOf(minimumTravel, height.floatValue * DISMISS_TRAVEL_FRACTION)
                    if (offset.value >= enough) {
                        onDismiss()
                    } else {
                        scope.launch { offset.animateTo(0f, MiraMotion.settle()) }
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, MiraMotion.settle()) } },
            ) { change, dragAmount ->
                change.consume()
                // Upward drags do nothing: there is nothing above the panel to pull into view.
                scope.launch { offset.snapTo((offset.value + dragAmount).coerceAtLeast(0f)) }
            }
        }
}

private fun MiraVoiceState.asStatus(): MiraStatus = when (this) {
    MiraVoiceState.IDLE -> MiraStatus.IDLE
    MiraVoiceState.LISTENING,
    MiraVoiceState.PROCESSING,
    MiraVoiceState.EXECUTING,
    MiraVoiceState.SPEAKING,
    -> MiraStatus.ACTIVE

    MiraVoiceState.SUCCESS -> MiraStatus.SUCCESS
    MiraVoiceState.ERROR -> MiraStatus.ERROR
}

/**
 * Every word the overlay says, in both of Abu's languages.
 *
 * It is an object beside the one screen that uses it for the same reason `speechFailureWording`
 * sits beside Mira's screen: none of this is a `MiraResponse`, so there is nothing for a
 * phrasebook entry to hang on — and a one-language sentence on a path he actually reads is a
 * defect whichever layer it is in.
 */
internal object AssistantOverlayWording {

    fun status(state: MiraVoiceState, language: SpokenLanguage): String = when (language) {
        SpokenLanguage.ENGLISH -> when (state) {
            MiraVoiceState.IDLE -> "Ready"
            MiraVoiceState.LISTENING -> "Listening"
            MiraVoiceState.PROCESSING -> "Thinking"
            MiraVoiceState.EXECUTING -> "Working on it"
            MiraVoiceState.SPEAKING -> "Speaking"
            MiraVoiceState.SUCCESS -> "Done"
            MiraVoiceState.ERROR -> "Couldn't do that"
        }

        SpokenLanguage.BENGALI -> when (state) {
            MiraVoiceState.IDLE -> "প্রস্তুত"
            MiraVoiceState.LISTENING -> "শুনছি"
            MiraVoiceState.PROCESSING -> "ভাবছি"
            MiraVoiceState.EXECUTING -> "করছি"
            MiraVoiceState.SPEAKING -> "বলছি"
            MiraVoiceState.SUCCESS -> "হয়ে গেছে"
            MiraVoiceState.ERROR -> "পারলাম না"
        }
    }

    /** The orb carries no glyph, so what a press would do has to be said to TalkBack here. */
    fun orb(state: MiraVoiceState, language: SpokenLanguage): String = when (state) {
        MiraVoiceState.LISTENING -> when (language) {
            SpokenLanguage.ENGLISH -> "Stop listening"
            SpokenLanguage.BENGALI -> "শোনা বন্ধ করুন"
        }

        MiraVoiceState.SPEAKING -> when (language) {
            SpokenLanguage.ENGLISH -> "Stop Aida"
            SpokenLanguage.BENGALI -> "আইদাকে থামান"
        }

        MiraVoiceState.IDLE,
        MiraVoiceState.PROCESSING,
        MiraVoiceState.EXECUTING,
        MiraVoiceState.SUCCESS,
        MiraVoiceState.ERROR,
        -> when (language) {
            SpokenLanguage.ENGLISH -> "Speak to Aida"
            SpokenLanguage.BENGALI -> "আইদাকে বলুন"
        }
    }

    /**
     * What Android shared, said plainly at the moment it is shared rather than left to a settings
     * screen Abu read weeks ago. It describes what *arrived*; nothing has left the phone.
     */
    fun screenNote(screen: AssistantOverlayState.Screen, language: SpokenLanguage): String {
        val app = screen.appLabel?.takeIf { it.isNotBlank() }
        return when (language) {
            SpokenLanguage.ENGLISH -> when {
                app != null && screen.includesScreenshot ->
                    "Android shared $app's screen and a screenshot"
                app != null -> "Android shared $app's screen"
                screen.includesScreenshot -> "Android shared this screen and a screenshot"
                else -> "Android shared this screen"
            }

            SpokenLanguage.BENGALI -> when {
                app != null && screen.includesScreenshot ->
                    "অ্যান্ড্রয়েড $app-এর স্ক্রিন ও একটি স্ক্রিনশট দিয়েছে"
                app != null -> "অ্যান্ড্রয়েড $app-এর স্ক্রিন দিয়েছে"
                screen.includesScreenshot -> "অ্যান্ড্রয়েড এই স্ক্রিন ও একটি স্ক্রিনশট দিয়েছে"
                else -> "অ্যান্ড্রয়েড এই স্ক্রিন দিয়েছে"
            }
        }
    }

    fun confirm(language: SpokenLanguage): String = when (language) {
        SpokenLanguage.ENGLISH -> "Yes, do it"
        SpokenLanguage.BENGALI -> "হ্যাঁ, করুন"
    }

    fun cancel(language: SpokenLanguage): String = when (language) {
        SpokenLanguage.ENGLISH -> "No"
        SpokenLanguage.BENGALI -> "না"
    }

    fun dismiss(language: SpokenLanguage): String = when (language) {
        SpokenLanguage.ENGLISH -> "Close Aida"
        SpokenLanguage.BENGALI -> "আইদা বন্ধ করুন"
    }

    /**
     * One way forward, and only where it is a real one. Saying the same thing again is offered
     * where it could genuinely work; everything else opens Mira, where the microphone can be
     * granted and where typing does the whole job anyway.
     */
    fun failureAction(failure: SpeechFailure, language: SpokenLanguage): String = when {
        failure.isRetryable -> when (language) {
            SpokenLanguage.ENGLISH -> "Try again"
            SpokenLanguage.BENGALI -> "আবার চেষ্টা করুন"
        }

        else -> when (language) {
            SpokenLanguage.ENGLISH -> "Open Aida"
            SpokenLanguage.BENGALI -> "আইদা খুলুন"
        }
    }
}

/** Abu's own words are an echo, not the answer: three lines is enough to see she heard him. */
private const val HEARD_MAX_LINES = 3

/** In `spacing.xxl` units, so the answer's ceiling moves with the rhythm rather than beside it. */
private const val ANSWER_MAX_HEIGHT_UNITS = 3

/** Past a third of the panel's own height the gesture is unambiguous. */
private const val DISMISS_TRAVEL_FRACTION = 0.33f

/** The previews sit on obsidian because the panel is judged against what is behind it. */
@Composable
private fun AssistantOverlayPreviewSurface(content: @Composable () -> Unit) {
    MiraTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}

@Preview(widthDp = 400, heightDp = 420)
@Composable
private fun AssistantOverlayListeningPreview() {
    AssistantOverlayPreviewSurface {
        AssistantOverlay(
            state = AssistantOverlayState(
                voice = VoiceSessionState(
                    state = MiraVoiceState.LISTENING,
                    amplitude = 0.64f,
                    partialTranscript = "kal shokal dosh tay",
                    canListen = true,
                ),
                screen = AssistantOverlayState.Screen(appLabel = "Chrome", includesScreenshot = true),
            ),
            onOrb = {},
            onConfirm = {},
            onCancelPending = {},
            onRetry = {},
            onOpenMira = {},
            onDismiss = {},
        )
    }
}

@Preview(widthDp = 400, heightDp = 420)
@Composable
private fun AssistantOverlayAnsweredPreview() {
    AssistantOverlayPreviewSurface {
        AssistantOverlay(
            state = AssistantOverlayState(
                voice = VoiceSessionState(state = MiraVoiceState.SUCCESS, canListen = true),
                heard = "kalker kaj gulo bolo",
                answer = "Three things tomorrow: call the landlord at 10:00, " +
                    "send the Rahman quote, and pick up the passport.",
            ),
            onOrb = {},
            onConfirm = {},
            onCancelPending = {},
            onRetry = {},
            onOpenMira = {},
            onDismiss = {},
        )
    }
}

@Preview(widthDp = 400, heightDp = 420)
@Composable
private fun AssistantOverlayConfirmingPreview() {
    AssistantOverlayPreviewSurface {
        AssistantOverlay(
            state = AssistantOverlayState(
                voice = VoiceSessionState(canListen = true),
                heard = "4500 taka income likhe rakho",
                pendingSummary = "Record 4,500 BDT as income?",
            ),
            onOrb = {},
            onConfirm = {},
            onCancelPending = {},
            onRetry = {},
            onOpenMira = {},
            onDismiss = {},
        )
    }
}
