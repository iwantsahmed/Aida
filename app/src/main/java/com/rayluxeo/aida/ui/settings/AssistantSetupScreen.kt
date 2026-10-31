package com.rayluxeo.aida.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.voice.VoiceInteractionService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.assistant.AssistantEntryPoint
import com.rayluxeo.aida.assistant.MiraVoiceInteractionService
import com.rayluxeo.aida.assistant.AssistantRoleState
import com.rayluxeo.aida.assistant.AssistantStatus
import com.rayluxeo.aida.assistant.HotwordSupport
import com.rayluxeo.aida.assistant.HotwordVerdict
import com.rayluxeo.aida.assistant.OemAssistantGuidance
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraStatus
import com.rayluxeo.aida.core.design.component.MiraStatusIndicator
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.ui.mira.tryStartActivity
import com.rayluxeo.aida.ui.onboarding.hasMicrophonePermission

/**
 * Where Abu makes Mira the app Android hands the assistant gesture to — and finds out plainly
 * when something else still holds it.
 *
 * Nothing on this screen grants the role. Android has no supported way for an app to ask for
 * ROLE_ASSISTANT: `RoleManager.createRequestRoleIntent` throws for it on some builds, and the
 * setting is changed only from Android's own screen. So the control here opens that screen and
 * is labelled as doing exactly that, rather than pretending to a power Mira does not have.
 *
 * The state is re-read on every resume because Abu changes it outside Mira and comes straight
 * back, the same way the microphone permission is re-read today.
 */
@Composable
fun AssistantSetupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AssistantSetupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MiraTheme.spacing
    val wording = assistantWording(state.language)

    val microphoneRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // The answer is read back from the platform rather than taken from the dialog's result,
        // and it changes the facts, which is what retires the microphone row of an earlier test.
        viewModel.refresh(context.assistantSystemFacts())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh(context.assistantSystemFacts())
    }

    val openAssistantSettings: () -> Unit = {
        val intent = viewModel.settingsIntent()
        if (intent == null || !context.tryStartActivity(intent)) {
            viewModel.reportProblem(wording.assistantSettingsWouldNotOpen)
        }
    }

    val onFix: (AssistantFix) -> Unit = { fix ->
        when (fix) {
            AssistantFix.OPEN_ASSISTANT_SETTINGS -> openAssistantSettings()
            AssistantFix.GRANT_MICROPHONE ->
                microphoneRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = wording.title,
                subtitle = wording.subtitle,
                onBack = onBack,
                backContentDescription = wording.back,
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MiraListSkeleton()
            }
            return@MiraScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // One opening interval, held whether or not a notice is showing, so the first
            // section does not move up the screen the moment one is dismissed.
            Spacer(Modifier.height(spacing.md))
            val notice = state.notice
            if (notice != null) {
                MiraInlineNotice(
                    text = notice,
                    tone = MiraNoticeTone.DANGER,
                    dismissContentDescription = wording.dismiss,
                    onDismiss = viewModel::dismissNotice,
                )
                Spacer(Modifier.height(spacing.lg))
            }

            RoleSection(
                status = state.status,
                settingsAvailable = state.settingsAvailable,
                onOpenSettings = openAssistantSettings,
                wording = wording,
            )

            Spacer(Modifier.height(spacing.sectionGap))
            TestSection(
                state = state,
                onRunTest = {
                    val facts = context.assistantSystemFacts()
                    viewModel.runTest(facts) { context.openMiraSession() }
                },
                onFix = onFix,
                wording = wording,
            )

            val oem = state.status?.oem
            if (oem != null) {
                Spacer(Modifier.height(spacing.sectionGap))
                OemSection(
                    guidance = oem,
                    onOpenOemSettings = {
                        val intent = oem.settingsIntent
                        if (intent == null || !context.tryStartActivity(intent)) {
                            viewModel.reportProblem(wording.oemSettingWouldNotOpen)
                        }
                    },
                    wording = wording,
                )
            }

            Spacer(Modifier.height(spacing.sectionGap))
            EntryPointSection(hotword = state.hotword, wording = wording)
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

/** What Android says right now, and the one control that can change it. */
@Composable
private fun RoleSection(
    status: AssistantStatus?,
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
    wording: AssistantWording,
) {
    val spacing = MiraTheme.spacing
    MiraSectionHeader(text = wording.rightNow)
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        MiraStatusIndicator(status = status.indicator(), label = status.headline(wording))
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = status.explanation(wording),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))
        if (settingsAvailable) {
            MiraButton(
                text = wording.openAndroidAssistantSetting,
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = wording.settingsScreenAbsent,
                style = MaterialTheme.typography.bodyMedium,
                color = MiraTheme.colors.danger,
            )
        }
    }
}

/**
 * The test, and the four things it actually looked at.
 *
 * Each row reports what the platform answered. Where a row failed it carries the one action that
 * settles it, and where nothing on this phone could answer the question the row says so rather
 * than showing a tick for a check that never ran.
 */
@Composable
private fun TestSection(
    state: AssistantSetupUiState,
    onRunTest: () -> Unit,
    onFix: (AssistantFix) -> Unit,
    wording: AssistantWording,
) {
    val spacing = MiraTheme.spacing
    MiraSectionHeader(text = wording.test)
    Text(
        text = wording.testDescription,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(spacing.md))
    MiraButton(
        style = MiraButtonStyle.SECONDARY,
        text = if (state.testing) wording.testing else wording.runTest,
        onClick = onRunTest,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canRunTest,
    )

    if (state.checks.isEmpty()) return

    // Deliberately not in a card. Every other list in Mira — voices, engines, places, the
    // settings rows — is set on the screen's own gutter with hairlines between its rows, and
    // boxing this one pushed four rows 20dp in from the section label directly above them. A
    // border is not what makes a result matter; the summary line above it is (AGENTS.md §11).
    Spacer(Modifier.height(spacing.lg))
    val summary = state.summary(wording)
    if (summary != null) {
        Text(
            text = summary,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xs))
    }
    state.checks.forEachIndexed { index, check ->
        if (index > 0) MiraDivider()
        CheckRow(check = check, status = state.status, onFix = onFix, wording = wording)
    }
}

@Composable
private fun CheckRow(
    check: AssistantCheck,
    status: AssistantStatus?,
    onFix: (AssistantFix) -> Unit,
    wording: AssistantWording,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        MiraStatusIndicator(status = check.outcome.indicator(), label = check.id.label(wording))
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = check.reason.sentence(status, wording),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fix = check.fix
        if (fix != null) {
            MiraButton(
                style = MiraButtonStyle.TEXT,
                text = fix.label(wording),
                onClick = { onFix(fix) },
            )
        }
    }
}

/**
 * The manufacturer's own extra steps, written out as they were given.
 *
 * Samsung is why this exists: holding the role does not by itself move the side-button gesture,
 * so without these steps Abu sets Mira as the assistant, squeezes the button, gets Bixby and
 * concludes Mira is broken.
 */
@Composable
private fun OemSection(
    guidance: OemAssistantGuidance,
    onOpenOemSettings: () -> Unit,
    wording: AssistantWording,
) {
    val spacing = MiraTheme.spacing
    MiraSectionHeader(text = guidance.manufacturer)
    Text(
        text = wording.oemIntro,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    guidance.steps.forEachIndexed { index, step ->
        Spacer(Modifier.height(spacing.md))
        Row(modifier = Modifier.fillMaxWidth()) {
            // A fixed column for the ordinal, so every step's sentence begins on the same edge
            // whatever the digit is — and muted, because a numbered list needs a marker, not a
            // fourth place to spend the accent.
            Text(
                text = wording.stepNumber(index + 1),
                modifier = Modifier.width(spacing.lg),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (guidance.settingsIntent != null) {
        Spacer(Modifier.height(spacing.lg))
        MiraButton(
            style = MiraButtonStyle.SECONDARY,
            text = wording.openThatSetting,
            onClick = onOpenOemSettings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Every way Mira can actually be started, and one plain sentence about the wake word.
 *
 * Android reserves an always-on keyphrase for assistants shipped with the system, so Mira has
 * none. That is stated once, without apology, beside the routes that do work.
 */
@Composable
private fun EntryPointSection(hotword: HotwordSupport?, wording: AssistantWording) {
    val spacing = MiraTheme.spacing
    if (hotword == null) return

    MiraSectionHeader(text = wording.howToReachHer)
    Text(
        text = hotword.verdict.sentence(wording),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Set as text rather than as MiraListRow, for the same reason the version block on the
    // Settings screen is: not one of these six is a control *here*. The gesture, the orb, the
    // tile, the widget, the shortcut and the share sheet are all reached from somewhere else, and
    // a row with nothing to tap reads as a control that does not work (AGENTS.md §12). Six of
    // them read as a list of six broken ones.
    hotword.alternatives.forEach { entryPoint ->
        Spacer(Modifier.height(spacing.md))
        Text(
            text = entryPoint.label(wording),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = entryPoint.description(wording),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AssistantSetupUiState.summary(wording: AssistantWording): String? = when {
    allChecksPassed -> wording.everythingInPlace
    failedChecks.size == 1 -> wording.oneThingInTheWay
    failedChecks.size > 1 -> wording.thingsInTheWay(failedChecks.size)
    else -> null
}

private fun AssistantStatus?.indicator(): MiraStatus = when (this?.state) {
    AssistantRoleState.HELD -> MiraStatus.SUCCESS
    AssistantRoleState.AVAILABLE -> MiraStatus.IDLE
    AssistantRoleState.UNSUPPORTED -> MiraStatus.WARNING
    null -> MiraStatus.IDLE
}

private fun AssistantStatus?.headline(wording: AssistantWording): String = when (this?.state) {
    AssistantRoleState.HELD -> wording.headlineHeld
    AssistantRoleState.AVAILABLE -> wording.headlineNotAssistant
    AssistantRoleState.UNSUPPORTED -> wording.headlineCannotChange
    null -> wording.headlineAsking
}

private fun AssistantStatus?.explanation(wording: AssistantWording): String {
    val status = this ?: return wording.explanationAsking
    return when {
        // Deliberately does not name the side button. Holding the role is not the same as owning
        // that gesture: One UI binds the button separately, so on Abu's own phone this could sit
        // directly above the OEM steps saying the opposite. What is true everywhere is that the
        // assist gesture now resolves to Mira; which gesture that is belongs to the OEM section.
        status.state == AssistantRoleState.HELD -> wording.explanationHeld

        status.state == AssistantRoleState.UNSUPPORTED -> wording.explanationUnsupported

        status.holder != null -> wording.explanationHeldByOther(status.holderName(wording))

        else -> wording.explanationNothingHolds
    }
}

/** The label Android gave the holder, falling back to its package: never a guess at its name. */
private fun AssistantStatus.holderName(wording: AssistantWording): String =
    holderLabel?.takeIf { it.isNotBlank() } ?: holder ?: wording.anotherApp

private fun AssistantCheckOutcome.indicator(): MiraStatus = when (this) {
    AssistantCheckOutcome.PASSED -> MiraStatus.SUCCESS
    AssistantCheckOutcome.FAILED -> MiraStatus.ERROR
    AssistantCheckOutcome.SKIPPED -> MiraStatus.IDLE
    AssistantCheckOutcome.UNAVAILABLE -> MiraStatus.WARNING
}

private fun AssistantCheckId.label(wording: AssistantWording): String = when (this) {
    AssistantCheckId.ROLE -> wording.checkRole
    AssistantCheckId.SERVICE -> wording.checkService
    AssistantCheckId.MICROPHONE -> wording.checkMicrophone
    AssistantCheckId.SESSION -> wording.checkSession
}

private fun AssistantCheckReason.sentence(
    status: AssistantStatus?,
    wording: AssistantWording,
): String = when (this) {
    AssistantCheckReason.ROLE_HELD -> wording.roleHeld
    AssistantCheckReason.ROLE_HELD_BY_OTHER ->
        wording.roleHeldByOther(status?.holderName(wording) ?: wording.anotherApp)
    AssistantCheckReason.ROLE_UNHELD -> wording.roleUnheld
    AssistantCheckReason.ROLE_UNSUPPORTED -> wording.roleUnsupported
    AssistantCheckReason.SERVICE_ACTIVE -> wording.serviceActive
    AssistantCheckReason.SERVICE_NOT_DECLARED -> wording.serviceNotDeclared
    AssistantCheckReason.SERVICE_NOT_BOUND -> wording.serviceNotBound
    AssistantCheckReason.MICROPHONE_GRANTED -> wording.microphoneGranted
    AssistantCheckReason.MICROPHONE_MISSING -> wording.microphoneMissing
    AssistantCheckReason.SESSION_OPENED -> wording.sessionOpened
    AssistantCheckReason.SESSION_NO_ENTRY_POINT -> wording.sessionNoEntryPoint
    AssistantCheckReason.SESSION_NOT_TRIED -> wording.sessionNotTried
}

private fun AssistantFix.label(wording: AssistantWording): String = when (this) {
    AssistantFix.OPEN_ASSISTANT_SETTINGS -> wording.openAssistantSettings
    AssistantFix.GRANT_MICROPHONE -> wording.grantMicrophone
}

private fun HotwordVerdict.sentence(wording: AssistantWording): String = when (this) {
    HotwordVerdict.AVAILABLE -> wording.hotwordAvailable
    HotwordVerdict.SYSTEM_ONLY -> wording.hotwordSystemOnly
    HotwordVerdict.NO_HARDWARE -> wording.hotwordNoHardware
    // Deliberately not "grant the role and you get a wake word": the role is a precondition, and
    // Android reserves always-on hotwording for system assistants on top of it.
    HotwordVerdict.NOT_THE_ASSISTANT -> wording.hotwordNotTheAssistant
}

private fun AssistantEntryPoint.label(wording: AssistantWording): String = when (this) {
    AssistantEntryPoint.SYSTEM_ASSIST_GESTURE -> wording.entryGesture
    AssistantEntryPoint.VOICE_BUTTON -> wording.entryVoiceButton
    AssistantEntryPoint.QUICK_SETTINGS_TILE -> wording.entryQuickSettings
    AssistantEntryPoint.HOME_SCREEN_WIDGET -> wording.entryWidget
    AssistantEntryPoint.APP_SHORTCUT -> wording.entryShortcut
    AssistantEntryPoint.SHARE_SHEET -> wording.entryShareSheet
}

private fun AssistantEntryPoint.description(wording: AssistantWording): String = when (this) {
    AssistantEntryPoint.SYSTEM_ASSIST_GESTURE -> wording.entryGestureDetail
    AssistantEntryPoint.VOICE_BUTTON -> wording.entryVoiceButtonDetail
    AssistantEntryPoint.QUICK_SETTINGS_TILE -> wording.entryQuickSettingsDetail
    AssistantEntryPoint.HOME_SCREEN_WIDGET -> wording.entryWidgetDetail
    AssistantEntryPoint.APP_SHORTCUT -> wording.entryShortcutDetail
    AssistantEntryPoint.SHARE_SHEET -> wording.entryShareSheetDetail
}

/**
 * What Android will say about this phone, asked fresh each time.
 *
 * None of it is cached: the role, the bound service and the microphone can all be changed from
 * outside Mira while this screen is open.
 */
private fun Context.assistantSystemFacts(): AssistantSystemFacts {
    val component = voiceInteractionServiceComponent()
    return AssistantSystemFacts(
        microphoneGranted = hasMicrophonePermission(),
        serviceDeclared = component != null,
        serviceActive = component != null && VoiceInteractionService.isActiveService(this, component),
    )
}

/**
 * Mira's own voice interaction service, found by its documented intent rather than by naming the
 * class, so this screen does not break the day that class moves.
 *
 * The query is restricted to Mira's own package, which needs no `<queries>` entry: an app can
 * always see itself.
 */
private fun Context.voiceInteractionServiceComponent(): ComponentName? {
    val intent = Intent(VoiceInteractionService.SERVICE_INTERFACE).setPackage(packageName)
    val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentServices(intent, 0)
    }
    return matches.firstOrNull()?.serviceInfo?.let { ComponentName(it.packageName, it.name) }
}

/**
 * Opens a real Mira session, through the bound assistant service itself.
 *
 * Not `ACTION_ASSIST`: that action is resolved by the system against whoever holds the assistant
 * role, so firing it would either open somebody else's assistant or nothing at all, and in neither
 * case would a result tell Abu anything about Mira. Going through
 * [MiraVoiceInteractionService.showSessionNow] means a true here is the session actually opening
 * in front of him.
 */
private fun Context.openMiraSession(): Boolean = MiraVoiceInteractionService.showSessionNow()
