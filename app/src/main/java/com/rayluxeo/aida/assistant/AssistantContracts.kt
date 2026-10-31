package com.rayluxeo.aida.assistant

import android.content.Intent
import android.net.Uri

/**
 * Whether Mira is the app Android hands the assistant gesture to.
 *
 * Three states rather than a boolean, because "not the assistant" and "cannot be the assistant"
 * lead to different screens: one offers a way to change it, the other has to say plainly that this
 * phone has nowhere to change it and stop pretending there is a button worth pressing.
 */
enum class AssistantRoleState {
    /** Mira holds the role. The gesture reaches her. */
    HELD,

    /** Something else holds it, and Android offers a screen where that can be changed. */
    AVAILABLE,

    /** This build of Android exposes no way to hand the role to a third-party app. */
    UNSUPPORTED,
}

/**
 * What Android had to say when asked, plus whatever the OEM adds on top.
 *
 * [holder] is the package that currently answers the gesture, read from the platform rather than
 * guessed, and null when nothing does.
 */
data class AssistantStatus(
    val state: AssistantRoleState,
    val holder: String? = null,
    val holderLabel: String? = null,
    val oem: OemAssistantGuidance? = null,
) {
    val isMira: Boolean get() = state == AssistantRoleState.HELD
}

/**
 * The extra steps a manufacturer puts between Abu and the assistant setting.
 *
 * Samsung is the case that matters here: the platform role screen exists, but the side-button
 * gesture is bound separately, so handing Mira the role is not by itself enough to make the button
 * reach her. Steps are rendered as written, in order.
 */
data class OemAssistantGuidance(
    val manufacturer: String,
    val steps: List<String>,
    val settingsIntent: Intent? = null,
)

/**
 * Reading and changing who Android treats as the assistant.
 *
 * The request is deliberately an [Intent] to a settings screen rather than a grant.
 * `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` is not a supported way to ask for this
 * role and throws on some builds, so there is no in-app dialog that can hand it over — the honest
 * control opens the screen where Abu does it himself and says so.
 */
interface AssistantRoleController {

    /** Asked of the platform every time. The role can be changed from outside Mira at any moment. */
    suspend fun status(): AssistantStatus

    /**
     * Where Abu goes to change it, or null when this phone has no such screen — in which case the
     * screen must say so rather than offer a control that does nothing.
     */
    fun settingsIntent(): Intent?
}

/**
 * What Android was willing to tell Mira about the screen Abu was looking at.
 *
 * Only ever built from what the platform hands the assistant. Mira does not read the screen by any
 * other route, and a field is null when the platform withheld it — an app that sets `FLAG_SECURE`,
 * or a system setting Abu turned off, both arrive here as an absence rather than as an error.
 */
data class AssistSnapshot(
    val appPackage: String? = null,
    val appLabel: String? = null,
    val title: String? = null,
    val webUri: Uri? = null,
    /** Flattened visible text, in reading order, capped. Empty when nothing was shared. */
    val text: String = "",
    /** True only when a screenshot actually arrived, never merely because one was asked for. */
    val hasScreenshot: Boolean = false,
) {
    val isEmpty: Boolean get() = text.isBlank() && title.isNullOrBlank() && webUri == null
}

/** Turns the platform's assist payload into the one shape Mira reasons about. */
interface AssistContextReader {
    fun read(structure: android.app.assist.AssistStructure?, content: android.app.assist.AssistContent?): AssistSnapshot
}

/**
 * Why Mira cannot listen for a wake word without being asked.
 *
 * This is discovered at runtime rather than assumed, because the answer is a property of the
 * device and the build, and an assistant that quietly held the microphone open would be the exact
 * thing `AGENTS.md` and `PRIVACY.md` forbid.
 */
enum class HotwordVerdict {
    /** A supported third-party always-on mechanism exists and Mira may use it. */
    AVAILABLE,

    /** The platform keeps always-on hotwording for system-privileged assistants only. */
    SYSTEM_ONLY,

    /** The hardware has no keyphrase/SoundTrigger support to offer. */
    NO_HARDWARE,

    /** Mira does not hold the assistant role, which is a precondition either way. */
    NOT_THE_ASSISTANT,
}

/**
 * [detectedAt] is absent on purpose — this is asked when it is shown, never cached, because the
 * role can change while Mira is running.
 */
data class HotwordSupport(
    val verdict: HotwordVerdict,
    /** The ways Mira can actually be reached instead. Never empty. */
    val alternatives: List<AssistantEntryPoint>,
)

/** Every route that can start a Mira session, so the UI can list exactly what works. */
enum class AssistantEntryPoint {
    SYSTEM_ASSIST_GESTURE,
    VOICE_BUTTON,
    QUICK_SETTINGS_TILE,
    HOME_SCREEN_WIDGET,
    APP_SHORTCUT,
    SHARE_SHEET,
}

/** Asks the platform what it will allow, and never assumes a yes. */
interface HotwordCapabilityDetector {
    suspend fun detect(): HotwordSupport
}

/**
 * Something another app handed to Mira through the share sheet.
 *
 * The distinction between [Text] and [Link] is not cosmetic: a link is the one case where Mira
 * could usefully reach the network, and it is therefore the one case that has to ask first.
 */
sealed interface SharedPayload {
    data class Text(val text: String) : SharedPayload
    data class Link(val url: String, val title: String? = null) : SharedPayload

    /**
     * Images, screenshots, PDFs and documents. The content stays behind its [Uri] — Mira reads it
     * only if she is asked to do something that needs the bytes.
     */
    data class Documents(val uris: List<Uri>, val mimeType: String) : SharedPayload {
        val isImage: Boolean get() = mimeType.startsWith("image/")
    }
}

/**
 * What Mira intends to send off the device, shown to Abu before any of it leaves.
 *
 * Built and displayed even when Gemini is switched off, because the answer to "may I send this"
 * has to be visible at the moment it is asked rather than inferred from a settings screen he set
 * up weeks ago.
 */
data class OutboundDisclosure(
    val appLabel: String?,
    val includesScreenText: Boolean,
    val includesScreenshot: Boolean,
    val characterCount: Int,
)
