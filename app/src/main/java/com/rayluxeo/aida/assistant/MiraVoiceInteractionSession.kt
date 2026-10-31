package com.rayluxeo.aida.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSession.AssistState
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.collectAsState
import com.rayluxeo.aida.MainActivity
import com.rayluxeo.aida.assistant.di.assistantSessionEntryPoint
import com.rayluxeo.aida.assistant.ui.AssistantOverlay
import com.rayluxeo.aida.assistant.ui.AssistantOverlayState
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.LocalHapticsEnabled
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.VoiceSessionState
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Mira when she is reached by the system assist gesture rather than by opening her app.
 *
 * The turn itself is not implemented here. [com.rayluxeo.aida.voice.VoiceConversationController]
 * already owns pressing, listening, interpreting, executing and speaking — including what a press
 * means in each state, the earcons, the audio focus and the confirmation gate — and a second copy
 * of those rules living in an overlay would drift from the one on Mira's own screen within a
 * week. This class gives that controller a new way in and renders what it reports.
 *
 * Three things are genuinely this class's own:
 *
 * - **The window.** A `VoiceInteractionSession` is not an Activity, so [AssistantSessionHost]
 *   supplies the three view-tree owners Compose needs and this class drives that lifecycle from
 *   the session callbacks.
 * - **The way out.** [hide] when Abu dismisses the panel, [finish] once the turn is genuinely
 *   over. A session sitting over another app is worse than one that never opened.
 * - **The assist context**, which arrives here and nowhere else, is read through
 *   [AssistContextReader] and reduced to the one line the overlay shows about it.
 *
 * Everything runs in the **main process**. AOSP suggests `android:process` for the session
 * service; Mira must not, because a second process gets its own Hilt `SingletonComponent` and a
 * second DataStore over the same file — which this project has already been bitten by.
 */
class MiraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val sessionContext = context
    private val dependencies = context.assistantSessionEntryPoint()
    private val controller = dependencies.voiceController()
    private val renderer = dependencies.responseRenderer()
    private val clock = dependencies.clock()

    private val host = AssistantSessionHost()
    private val scope = CoroutineScope(SupervisorJob() + dependencies.dispatchers().main)

    /**
     * Named overlayState, not overlay: this flow is read from inside a `ComposeView.apply { }`
     * block, where `this` is a View and `overlay` resolves to the platform's own
     * [android.view.ViewOverlay] instead. The collision compiles as a receiver-type error several
     * lines away from the cause.
     */
    private val overlayState = MutableStateFlow(AssistantOverlayState())

    /**
     * The inputs the overlay is built from. They are plain fields rather than combined flows
     * because everything here runs on the main dispatcher, one callback at a time, and a
     * `combine` would buy nothing but a second place for the state to be assembled.
     */
    private var voice = VoiceSessionState()
    private var turns: List<ConversationTurn> = emptyList()
    private var settings = MiraSettings()
    private var snapshot: AssistSnapshot? = null
    private var screenshotArrived = false

    /** Where this turn begins. What was said before it belongs to the app, not to this panel. */
    private var openedAt: Instant = Instant.EPOCH

    private var closeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        host.onCreate()
        openedAt = openingMark()
        scope.launch { controller.state.collect(::onVoice) }
        scope.launch {
            dependencies.conversation().observeRecent(TRANSCRIPT_WINDOW).collect(::onTurns)
        }
        scope.launch { dependencies.settings().settings.collect(::onSettings) }
    }

    /**
     * The owners go on the root **before** the `ComposeView` is added to it: the view tree is
     * searched upwards at attach time, so an owner set afterwards arrives too late and the
     * overlay composes nothing at all.
     */
    override fun onCreateContentView(): View {
        val root = FrameLayout(sessionContext)
        host.attachTo(root)

        val composeView = ComposeView(sessionContext).apply {
            // Tied to the host's lifecycle rather than the window, so the composition goes away
            // with the session instead of outliving it into the next invocation.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Plain collectAsState, not the lifecycle-aware variant: this composition is
                // already tied to the host's lifecycle by the disposal strategy above, so
                // re-deriving the same lifecycle here would only restate it.
                val state by overlayState.collectAsState()
                MiraTheme {
                    CompositionLocalProvider(LocalHapticsEnabled provides state.hapticsEnabled) {
                        AssistantOverlay(
                            state = state,
                            onOrb = ::onOrbPressed,
                            onConfirm = controller::confirmPending,
                            onCancelPending = controller::dismissPending,
                            onRetry = controller::retryLast,
                            onOpenMira = ::openMira,
                            onDismiss = ::dismissPanel,
                        )
                    }
                }
            }
        }

        root.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    /**
     * Mira appears and starts listening, because being invoked by the gesture *is* the press.
     *
     * A turn already under way — one started on her own screen a moment earlier — is left alone
     * and simply rendered: interrupting it here would lose whatever Abu had already said.
     */
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        host.onShow()
        openedAt = openingMark()
        publish()
        when (voice.state) {
            MiraVoiceState.IDLE,
            MiraVoiceState.SUCCESS,
            MiraVoiceState.ERROR,
            -> controller.onOrbPressed()

            MiraVoiceState.LISTENING,
            MiraVoiceState.PROCESSING,
            MiraVoiceState.EXECUTING,
            MiraVoiceState.SPEAKING,
            -> Unit
        }
    }

    /**
     * The panel has gone, so the microphone and the voice go with it: a session Abu waved away
     * must not leave Mira listening to the room or talking over the app he went back to. A
     * command already in flight is deliberately left to finish — it is writing something he asked
     * for, and abandoning it half way is worse than finishing it unobserved.
     *
     * The assist context is cleared here rather than in [onShow] because the platform may deliver
     * it before the session is shown, and clearing it on the way in would throw away what had
     * already arrived.
     */
    override fun onHide() {
        super.onHide()
        closeJob?.cancel()
        closeJob = null
        when (voice.state) {
            MiraVoiceState.LISTENING, MiraVoiceState.SPEAKING -> controller.cancel()
            MiraVoiceState.IDLE,
            MiraVoiceState.PROCESSING,
            MiraVoiceState.EXECUTING,
            MiraVoiceState.SUCCESS,
            MiraVoiceState.ERROR,
            -> Unit
        }
        snapshot = null
        screenshotArrived = false
        host.onHide()
    }

    override fun onDestroy() {
        scope.cancel()
        host.onDestroy()
        super.onDestroy()
    }

    /**
     * What Android was willing to share about the screen Abu was looking at.
     *
     * The first payload that carries anything wins: a task can hand over several activities, and
     * an empty one arriving second must not erase the one that had something in it. Nothing is
     * read by any other route, and nothing here leaves the device.
     */
    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val read = dependencies.assistContext().read(state.assistStructure, state.assistContent)
        val existing = snapshot
        if (existing == null || existing.isEmpty) snapshot = read
        publish()
    }

    /**
     * The bitmap itself is deliberately not kept. Nothing in this overlay draws it, and a picture
     * of whatever Abu had on screen is the last thing that should sit in Mira's memory for longer
     * than the platform holds it. Only the fact that one arrived is recorded, so that what he is
     * told about the context is true rather than assumed from the flags of the invocation.
     */
    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        screenshotArrived = screenshot != null
        publish()
    }

    /**
     * Truncated to the millisecond because that is the resolution a turn is stored at: a mark
     * carrying nanoseconds would sit *after* a turn logged in the very same millisecond, and the
     * panel would show nothing at all for that turn.
     */
    private fun openingMark(): Instant = clock.now().truncatedTo(ChronoUnit.MILLIS)

    private fun onOrbPressed() {
        // Any press is Abu taking part, so the panel stops counting down towards closing itself.
        closeJob?.cancel()
        closeJob = null
        controller.onOrbPressed()
    }

    private fun onVoice(next: VoiceSessionState) {
        val previous = voice.state
        voice = next
        publish()

        when {
            next.pending != null -> cancelClose()
            next.state == MiraVoiceState.SUCCESS && previous != MiraVoiceState.SUCCESS -> closeSoon()
            next.state != MiraVoiceState.SUCCESS && next.state != MiraVoiceState.IDLE -> cancelClose()
        }
    }

    private fun onTurns(recent: List<ConversationTurn>) {
        turns = recent
        publish()
    }

    private fun onSettings(next: MiraSettings) {
        settings = next
        publish()
    }

    private fun publish() {
        val exchange = latestExchange(turns, openedAt)
        val pendingSummary = voice.pending?.let { renderer.display(it.request.summary, it.language) }
        overlayState.value = AssistantOverlayState(
            voice = voice,
            language = assistantLanguage(settings.languageMode, turns.lastOrNull()?.language),
            heard = exchange.heard,
            // A confirmation is logged to the transcript as Mira's reply, so showing both would
            // ask the same question twice. The card is the one that can be answered.
            answer = exchange.answer.takeIf { pendingSummary == null },
            pendingSummary = pendingSummary,
            screen = sharedScreen(),
            hapticsEnabled = settings.hapticFeedback,
        )
    }

    /**
     * What Abu is told the platform handed over, or nothing at all.
     *
     * A screenshot on its own still counts: assist structure and assist screenshot are two
     * separate settings, and a phone with the first switched off can still deliver the second.
     * Saying nothing in that case would understate what arrived.
     */
    private fun sharedScreen(): AssistantOverlayState.Screen? {
        val shared = snapshot
        return when {
            shared != null && !shared.isEmpty ->
                AssistantOverlayState.Screen(shared.appLabel, screenshotArrived)

            screenshotArrived -> AssistantOverlayState.Screen(shared?.appLabel, true)

            else -> null
        }
    }

    /**
     * The turn is answered and Mira has already read it out; the panel leaves of its own accord
     * so that Abu gets his app back without having to dismiss her.
     *
     * Only a turn that ended in [MiraVoiceState.SUCCESS] starts this. A failure keeps the panel
     * up, because the notice under it is the one that offers a way forward, and a question
     * waiting on a yes never starts it at all.
     */
    private fun closeSoon() {
        closeJob?.cancel()
        closeJob = scope.launch {
            delay(ANSWERED_LINGER_MS)
            if (voice.pending == null) finish()
        }
    }

    private fun cancelClose() {
        closeJob?.cancel()
        closeJob = null
    }

    /** Abu waved the panel away. The session stays alive for the next gesture. */
    private fun dismissPanel() {
        hide()
    }

    /**
     * Where the things a session cannot do are done: raising the microphone permission dialog,
     * and typing when speech will not work at all.
     *
     * [startAssistantActivity] is the supported route out of a session and is tried first — a
     * plain start from a service context is subject to Android's background-activity-launch
     * rules, which is exactly the case this API exists for.
     */
    private fun openMira() {
        val intent = Intent(sessionContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startAssistantActivity(intent)
        } catch (unsupported: RuntimeException) {
            sessionContext.startActivity(intent)
        }
        hide()
    }
}

/** What the overlay says about this turn: Abu's own words, and Mira's answer to them. */
internal data class AssistantExchange(val heard: String? = null, val answer: String? = null)

/**
 * The current turn, and only the current turn.
 *
 * The transcript is the whole conversation and outlives every session, so a panel that sits over
 * another app must not reach into it: [since] is when this session opened, and anything older
 * than that belongs to Mira's own screen. Kept out of the session class so that the rule can be
 * tested without a `VoiceInteractionSession`, which cannot be built in a local unit test.
 */
internal fun latestExchange(turns: List<ConversationTurn>, since: Instant): AssistantExchange {
    val thisTurn = turns.filterNot { it.createdAt.isBefore(since) }
    return AssistantExchange(
        heard = thisTurn.lastOrNull { it.role == TurnRole.ABU }?.text,
        answer = thisTurn.lastOrNull { it.role == TurnRole.MIRA }?.text,
    )
}

/**
 * Which language the panel's own words are in.
 *
 * The overlay's wording belongs to no particular utterance, so AUTO follows whichever language
 * the last exchange happened in, and English is the answer on a transcript with nothing in it.
 * This is the same rule Mira's own screen applies to a notice, deliberately.
 */
internal fun assistantLanguage(
    mode: LanguageMode,
    lastTurn: SpokenLanguage?,
): SpokenLanguage = when (mode) {
    LanguageMode.BANGLA -> SpokenLanguage.BENGALI
    LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
    LanguageMode.AUTO -> lastTurn ?: SpokenLanguage.ENGLISH
}

/**
 * Enough of the transcript to find this turn in, and no more. The panel shows one exchange, so
 * reading a hundred rows on every change would be work done to throw away.
 */
private const val TRANSCRIPT_WINDOW = 12

/**
 * How long the answer stays up before the panel closes itself.
 *
 * Long enough to read a sentence Mira has just finished speaking, short enough that she is not
 * still sitting over another app when Abu's attention has moved on. Any press cancels it.
 */
private const val ANSWERED_LINGER_MS = 4_000L
