package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.assistant.AssistantRoleState
import com.rayluxeo.aida.assistant.AssistantStatus
import com.rayluxeo.aida.assistant.HotwordSupport
import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * What Android answered about this phone at the moment Abu asked.
 *
 * All three are read by the screen rather than by the view model, because each one needs a
 * Context and a view model holding one could not be tested without a device. They are gathered
 * fresh on every resume: Abu can change any of them from outside Mira and walk straight back in.
 */
data class AssistantSystemFacts(
    val microphoneGranted: Boolean,
    /** False means this build ships no `VoiceInteractionService` for Android to bind at all. */
    val serviceDeclared: Boolean,
    /** `VoiceInteractionService.isActiveService` — the platform's own answer, not an inference. */
    val serviceActive: Boolean,
)

/** What happened when the test tried to put a real session in front of Abu. */
enum class AssistantSessionProbe {
    /** Not tried, because something it depends on was not true. */
    NOT_TRIED,

    /** Mira's own assist entry took the request and opened. */
    OPENED,

    /** Nothing in this build answers an assist request, so only the gesture can show a session. */
    NO_ENTRY_POINT,
}

/** The four things that have to be true before the assistant gesture reaches a listening Mira. */
enum class AssistantCheckId {
    ROLE,
    SERVICE,
    MICROPHONE,
    SESSION,
}

/**
 * [UNAVAILABLE] is deliberately not [FAILED]: it marks something this phone gave Mira no way to
 * check, and reporting that as a fault would send Abu hunting for a problem that may not exist.
 */
enum class AssistantCheckOutcome {
    PASSED,
    FAILED,

    /** Not run, because an earlier check has to pass first. */
    SKIPPED,

    /** Nothing here could answer it. The row says so instead of guessing either way. */
    UNAVAILABLE,
}

/**
 * Why a check came out the way it did, as a value rather than as a sentence.
 *
 * The wording lives on the screen and the reason lives here, so a test can assert on what Mira
 * concluded without pinning the prose, and so every sentence the screen can show is one that some
 * reason actually produces. A check carrying a generic outcome and no reason would need the
 * screen to guess at the words, which is how a row ends up saying something that is not true.
 */
enum class AssistantCheckReason {
    ROLE_HELD,
    ROLE_HELD_BY_OTHER,
    ROLE_UNHELD,
    ROLE_UNSUPPORTED,
    SERVICE_ACTIVE,
    SERVICE_NOT_DECLARED,
    SERVICE_NOT_BOUND,
    MICROPHONE_GRANTED,
    MICROPHONE_MISSING,
    SESSION_OPENED,
    SESSION_NO_ENTRY_POINT,
    SESSION_NOT_TRIED,
}

/** The single action that resolves a failed check, where one exists. */
enum class AssistantFix {
    OPEN_ASSISTANT_SETTINGS,
    GRANT_MICROPHONE,
}

data class AssistantCheck(
    val id: AssistantCheckId,
    val outcome: AssistantCheckOutcome,
    val reason: AssistantCheckReason,
    val fix: AssistantFix? = null,
)

/**
 * Whether opening a session would prove anything.
 *
 * Without the role and the bound service, Android would route the request somewhere that is not
 * Mira's session, and a window opening would then mean nothing at all. The microphone is not a
 * precondition: a session opens without it and says so, which is worth Abu seeing.
 */
fun shouldOpenSession(status: AssistantStatus, facts: AssistantSystemFacts): Boolean =
    status.isMira && facts.serviceActive

/**
 * The whole verdict of "Test Mira assistant", computed from what Android actually said.
 *
 * Every row is derived here and nothing is assumed: a check that could not be run reports that,
 * rather than inheriting the outcome of the one before it.
 */
fun assistantChecks(
    status: AssistantStatus,
    facts: AssistantSystemFacts,
    settingsAvailable: Boolean,
    session: AssistantSessionProbe,
): List<AssistantCheck> {
    val settingsFix = AssistantFix.OPEN_ASSISTANT_SETTINGS.takeIf { settingsAvailable }

    val role = when {
        status.isMira -> AssistantCheck(
            id = AssistantCheckId.ROLE,
            outcome = AssistantCheckOutcome.PASSED,
            reason = AssistantCheckReason.ROLE_HELD,
        )

        status.state == AssistantRoleState.UNSUPPORTED -> AssistantCheck(
            id = AssistantCheckId.ROLE,
            outcome = AssistantCheckOutcome.FAILED,
            reason = AssistantCheckReason.ROLE_UNSUPPORTED,
        )

        status.holder != null -> AssistantCheck(
            id = AssistantCheckId.ROLE,
            outcome = AssistantCheckOutcome.FAILED,
            reason = AssistantCheckReason.ROLE_HELD_BY_OTHER,
            fix = settingsFix,
        )

        else -> AssistantCheck(
            id = AssistantCheckId.ROLE,
            outcome = AssistantCheckOutcome.FAILED,
            reason = AssistantCheckReason.ROLE_UNHELD,
            fix = settingsFix,
        )
    }

    val service = when {
        facts.serviceActive -> AssistantCheck(
            id = AssistantCheckId.SERVICE,
            outcome = AssistantCheckOutcome.PASSED,
            reason = AssistantCheckReason.SERVICE_ACTIVE,
        )

        // A missing declaration is a fault in the build, not something Abu can settle from a
        // settings screen, so no fix is offered for it.
        !facts.serviceDeclared -> AssistantCheck(
            id = AssistantCheckId.SERVICE,
            outcome = AssistantCheckOutcome.FAILED,
            reason = AssistantCheckReason.SERVICE_NOT_DECLARED,
        )

        else -> AssistantCheck(
            id = AssistantCheckId.SERVICE,
            outcome = AssistantCheckOutcome.FAILED,
            reason = AssistantCheckReason.SERVICE_NOT_BOUND,
            fix = settingsFix,
        )
    }

    val microphone = if (facts.microphoneGranted) {
        AssistantCheck(
            id = AssistantCheckId.MICROPHONE,
            outcome = AssistantCheckOutcome.PASSED,
            reason = AssistantCheckReason.MICROPHONE_GRANTED,
        )
    } else {
        AssistantCheck(
            id = AssistantCheckId.MICROPHONE,
            outcome = AssistantCheckOutcome.FAILED,
            reason = AssistantCheckReason.MICROPHONE_MISSING,
            fix = AssistantFix.GRANT_MICROPHONE,
        )
    }

    val sessionCheck = when (session) {
        AssistantSessionProbe.OPENED -> AssistantCheck(
            id = AssistantCheckId.SESSION,
            outcome = AssistantCheckOutcome.PASSED,
            reason = AssistantCheckReason.SESSION_OPENED,
        )

        AssistantSessionProbe.NO_ENTRY_POINT -> AssistantCheck(
            id = AssistantCheckId.SESSION,
            outcome = AssistantCheckOutcome.UNAVAILABLE,
            reason = AssistantCheckReason.SESSION_NO_ENTRY_POINT,
        )

        AssistantSessionProbe.NOT_TRIED -> AssistantCheck(
            id = AssistantCheckId.SESSION,
            outcome = AssistantCheckOutcome.SKIPPED,
            reason = AssistantCheckReason.SESSION_NOT_TRIED,
        )
    }

    return listOf(role, service, microphone, sessionCheck)
}

/**
 * Everything the assistant setup screen draws.
 *
 * [status] is null only before Android has been asked once. The screen shows nothing about the
 * role until then, because both guesses — "Mira has it" and "Mira does not" — are wrong half the
 * time and one of them sends Abu to a settings screen he did not need.
 */
data class AssistantSetupUiState(
    val loading: Boolean = true,
    /**
     * Which of Abu's two languages the screen speaks, carried here for the same reason Home
     * carries it: the wording is chosen once, from his stored setting, rather than guessed per
     * composable. The Samsung steps on [AssistantStatus.oem] are worded by the controller from
     * the same setting, so both halves of this screen always agree.
     */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val status: AssistantStatus? = null,
    /** False when this phone has no screen where the role can be changed, so no button is drawn. */
    val settingsAvailable: Boolean = false,
    val hotword: HotwordSupport? = null,
    val facts: AssistantSystemFacts? = null,
    val testing: Boolean = false,
    /** Empty until a test has been run, and emptied again as soon as its answer goes stale. */
    val checks: List<AssistantCheck> = emptyList(),
    /** One line above the content when something did not work. Never a raw exception. */
    val notice: String? = null,
) {
    val failedChecks: List<AssistantCheck>
        get() = checks.filter { it.outcome == AssistantCheckOutcome.FAILED }

    /**
     * True only when every single check passed. A check that could not be run keeps this false,
     * which is what stops the screen congratulating Abu on something it never verified.
     */
    val allChecksPassed: Boolean
        get() = checks.isNotEmpty() && checks.all { it.outcome == AssistantCheckOutcome.PASSED }

    /** Testing needs Android's answer first; the button stays inert until it has one. */
    val canRunTest: Boolean get() = !testing && status != null && facts != null
}
