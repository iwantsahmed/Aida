package com.rayluxeo.aida.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraAvatar
import com.rayluxeo.aida.core.design.component.MiraAvatarState
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraStatus
import com.rayluxeo.aida.core.design.component.MiraStatusIndicator
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.ui.common.languageLabelsInOrder
import com.rayluxeo.aida.ui.common.languageModesInOrder


/**
 * Mira says hello: she opens on a happy squint and settles to her resting face a beat later, so
 * the first thing Abu sees her do is greet him. A little larger than her usual mark — this is her
 * entrance, and she is the only thing on the first pane.
 */
@Composable
private fun GreetingAvatar() {
    var avatar by remember { mutableStateOf(MiraAvatarState.SUCCESS) }
    LaunchedEffect(Unit) {
        delay(GREETING_HOLD_MS)
        avatar = MiraAvatarState.IDLE
    }
    MiraAvatar(state = avatar, amplitude = 0f, size = 112.dp)
}

private const val GREETING_HOLD_MS = 1300L

/**
 * What one step says.
 *
 * The copy is the feature here: every pane has to be true on this phone, today. Where an area is
 * still to be built, the pane says so rather than implying it already works. Every word of it
 * comes from [OnboardingWording] in the language Abu picked on the second pane.
 */
@Composable
internal fun OnboardingStepBody(
    step: OnboardingStep,
    state: OnboardingUiState,
    callbacks: OnboardingCallbacks,
    modifier: Modifier = Modifier,
) {
    val wording = onboardingWording(state.language)
    when (step) {
        // The mark appears here and on the last pane, and nowhere between: the flow opens on Mira
        // herself and closes on her, and the ten steps in the middle are about this phone rather
        // than about her. Put on every pane it would stop being a signature and become wallpaper.
        OnboardingStep.WELCOME -> OnboardingPane(
            modifier = modifier,
            title = wording.welcomeTitle,
            body = wording.welcomeBody,
            detail = wording.welcomeDetail,
            mark = { GreetingAvatar() },
        )

        OnboardingStep.NAME -> NamePane(
            name = state.userName,
            onNameChange = callbacks.onUserNameChange,
            wording = wording,
            onDone = {
                if (state.userName.trim().isNotEmpty()) {
                    callbacks.saveUserName(state.userName.trim())
                    callbacks.next()
                }
            },
            modifier = modifier,
        )

        OnboardingStep.LANGUAGE -> LanguagePane(
            mode = state.languageMode,
            wording = wording,
            onSelect = callbacks.selectLanguage,
            modifier = modifier,
        )

        OnboardingStep.VOICE -> OnboardingPane(
            modifier = modifier,
            title = wording.voiceTitle,
            body = wording.voiceBody,
            detail = wording.voiceDetail,
        )

        OnboardingStep.MICROPHONE -> OnboardingPane(
            modifier = modifier,
            title = wording.microphoneTitle,
            body = wording.microphoneBody,
            detail = wording.microphoneDetail,
        ) {
            PermissionStatus(
                permission = state.microphone,
                grantedLabel = wording.microphoneGranted,
                wording = wording,
            )
        }

        OnboardingStep.NOTIFICATIONS -> OnboardingPane(
            modifier = modifier,
            title = wording.notificationsTitle,
            body = wording.notificationsBody,
            detail = wording.notificationsDetail,
        ) {
            PermissionStatus(
                permission = state.notifications,
                grantedLabel = wording.notificationsGranted,
                wording = wording,
            )
        }

        OnboardingStep.CALENDAR -> OnboardingPane(
            modifier = modifier,
            title = wording.calendarTitle,
            body = wording.calendarBody,
            detail = wording.calendarDetail,
        )

        OnboardingStep.CONTACTS -> OnboardingPane(
            modifier = modifier,
            title = wording.contactsTitle,
            body = wording.contactsBody,
            detail = wording.contactsDetail,
        )

        // Location explains and defers, but no longer because nothing reads it — place reminders
        // do, as of September 2026. It defers because the screen that needs it is the right place
        // to ask: a dialog here, before Abu has seen what it is for, is not an informed grant.
        // Calendar and contacts already sat here for the same reason.
        OnboardingStep.LOCATION -> OnboardingPane(
            modifier = modifier,
            title = wording.locationTitle,
            body = wording.locationBody,
            detail = wording.locationDetail,
        )

        OnboardingStep.BIOMETRICS -> OnboardingPane(
            modifier = modifier,
            title = wording.biometricsTitle,
            body = wording.biometricsBody,
            detail = wording.biometricsDetail,
        ) {
            BiometricStatus(readiness = state.biometrics, wording = wording)
        }

        // Mira ships a voice interaction service now, so she is genuinely offered in Android's
        // assistant list. The pane used to say she was not, and that stopped being true.
        OnboardingStep.ASSISTANT -> OnboardingPane(
            modifier = modifier,
            title = wording.assistantTitle,
            body = wording.assistantBody,
            detail = wording.assistantDetail,
        ) {
            if (state.assistantSettingsMissing) {
                MiraStatusIndicator(
                    status = MiraStatus.ERROR,
                    label = wording.assistantSettingsMissing,
                )
            }
        }

        OnboardingStep.PRIVACY -> OnboardingPane(
            modifier = modifier,
            title = wording.privacyTitle,
            body = wording.privacyBody,
        ) {
            OnboardingPoints(points = wording.privacyPoints)
            Spacer(Modifier.height(MiraTheme.spacing.md))
            // Set as a detail rather than as a lead: it qualifies the three points above it, and
            // at body-large it out-weighed the card it is a footnote to.
            Text(
                text = wording.privacyFootnote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OnboardingStep.FINISH -> OnboardingPane(
            modifier = modifier,
            title = wording.finishTitle,
            body = wording.finishBody,
            detail = wording.finishDetail,
            mark = { MiraAvatar(state = MiraAvatarState.IDLE, amplitude = 0f) },
        )
    }
}

/**
 * The actions for one step, anchored at the bottom of the screen.
 *
 * Forward is always the primary button. Where a step offers something else — granting a
 * permission, opening a system screen — that is the single alternative beneath it.
 */
@Composable
internal fun OnboardingStepActions(
    state: OnboardingUiState,
    callbacks: OnboardingCallbacks,
    modifier: Modifier = Modifier,
) {
    val wording = onboardingWording(state.language)
    when (state.step) {
        OnboardingStep.WELCOME -> OnboardingActions(
            primaryText = wording.begin,
            onPrimary = callbacks.next,
            modifier = modifier,
        )

        OnboardingStep.NAME -> OnboardingActions(
            primaryText = wording.continueOn,
            primaryEnabled = state.userName.trim().isNotEmpty(),
            onPrimary = {
                callbacks.saveUserName(state.userName.trim())
                callbacks.next()
            },
            modifier = modifier,
        )

        OnboardingStep.LANGUAGE,
        OnboardingStep.LOCATION,
        OnboardingStep.PRIVACY,
        -> OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = callbacks.next,
            modifier = modifier,
        )

        OnboardingStep.VOICE -> OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = callbacks.next,
            modifier = modifier,
            // Offered only when the host wired voice setup up; an unwired button would be a
            // control that does nothing.
            alternativeText = wording.chooseAVoiceNow.takeIf { callbacks.openVoiceSetup != null },
            alternativeStyle = MiraButtonStyle.SECONDARY,
            onAlternative = callbacks.openVoiceSetup,
        )

        OnboardingStep.MICROPHONE -> PermissionActions(
            permission = state.microphone,
            askText = wording.allowTheMicrophone,
            settingsText = wording.openAppSettings,
            wording = wording,
            onAsk = callbacks.requestMicrophone,
            onOpenSettings = callbacks.openAppSettings,
            onContinue = callbacks.next,
            modifier = modifier,
        )

        OnboardingStep.NOTIFICATIONS -> PermissionActions(
            permission = state.notifications,
            askText = wording.allowNotifications,
            settingsText = wording.openNotificationSettings,
            wording = wording,
            onAsk = callbacks.requestNotifications,
            onOpenSettings = callbacks.openNotificationSettings,
            onContinue = callbacks.next,
            modifier = modifier,
        )

        OnboardingStep.CALENDAR -> SystemPermissionActions(
            permissions = calendarPermissions,
            grantedLabel = wording.calendarGranted,
            askText = wording.allowTheCalendar,
            wording = wording,
            onContinue = callbacks.next,
            modifier = modifier,
        )

        OnboardingStep.CONTACTS -> SystemPermissionActions(
            permissions = contactsPermissions,
            grantedLabel = wording.contactsGranted,
            askText = wording.allowContacts,
            wording = wording,
            onContinue = callbacks.next,
            modifier = modifier,
        )

        OnboardingStep.BIOMETRICS -> OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = callbacks.next,
            modifier = modifier,
            alternativeText = wording.setUpAScreenLock
                .takeIf { state.biometrics == BiometricReadiness.NONE_ENROLLED },
            alternativeStyle = MiraButtonStyle.SECONDARY,
            onAlternative = callbacks.enrollBiometrics,
        )

        OnboardingStep.ASSISTANT -> OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = callbacks.next,
            modifier = modifier,
            alternativeText = wording.openAssistantSettings
                .takeIf { !state.assistantSettingsMissing },
            alternativeStyle = MiraButtonStyle.SECONDARY,
            onAlternative = callbacks.openAssistantSettings,
        )

        OnboardingStep.FINISH -> OnboardingActions(
            primaryText = wording.startUsingMira,
            onPrimary = callbacks.finish,
            modifier = modifier,
            primaryEnabled = !state.finishing,
        )
    }
}

@Composable
private fun NamePane(
    name: String,
    onNameChange: (String) -> Unit,
    wording: OnboardingWording,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPane(
        modifier = modifier,
        title = wording.nameTitle,
        body = wording.nameBody,
        detail = wording.nameDetail,
    ) {
        MiraTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = wording.namePlaceholder,
            leadingIcon = MiraIcons.Person,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LanguagePane(
    mode: LanguageMode,
    wording: OnboardingWording,
    onSelect: (LanguageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingPane(
        modifier = modifier,
        title = wording.languageTitle,
        body = wording.languageBody,
    ) {
        MiraSegmentedControl(
            options = languageLabelsInOrder,
            selectedIndex = languageModesInOrder.indexOf(mode),
            onSelect = { onSelect(languageModesInOrder[it]) },
        )
        Spacer(Modifier.height(MiraTheme.spacing.sm))
        Text(
            text = wording.languageFootnote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionStatus(
    permission: PermissionState,
    grantedLabel: String,
    wording: OnboardingWording,
    modifier: Modifier = Modifier,
) {
    val status = when (permission) {
        PermissionState.GRANTED -> MiraStatus.SUCCESS
        PermissionState.NOT_GRANTED -> MiraStatus.IDLE
        PermissionState.DENIED, PermissionState.BLOCKED -> MiraStatus.WARNING
    }
    val label = when (permission) {
        PermissionState.GRANTED -> grantedLabel
        PermissionState.NOT_GRANTED -> wording.notGrantedYet
        PermissionState.DENIED -> wording.notGranted
        PermissionState.BLOCKED -> wording.onlySettingsCanGrant
    }
    MiraStatusIndicator(status = status, label = label, modifier = modifier)
}

@Composable
private fun BiometricStatus(
    readiness: BiometricReadiness,
    wording: OnboardingWording,
    modifier: Modifier = Modifier,
) {
    val status = when (readiness) {
        BiometricReadiness.UNKNOWN -> MiraStatus.IDLE
        BiometricReadiness.READY -> MiraStatus.SUCCESS
        BiometricReadiness.NONE_ENROLLED, BiometricReadiness.UNAVAILABLE -> MiraStatus.WARNING
    }
    val label = when (readiness) {
        BiometricReadiness.UNKNOWN -> wording.biometricsChecking
        BiometricReadiness.READY -> wording.biometricsReady
        BiometricReadiness.NONE_ENROLLED -> wording.biometricsNoneEnrolled
        BiometricReadiness.UNAVAILABLE -> wording.biometricsUnavailable
    }
    MiraStatusIndicator(status = status, label = label, modifier = modifier)
}

/**
 * Calendar and contacts ask for themselves.
 *
 * This step holds its own permission state because the launcher's answer and the buttons that
 * change because of it are both here. What Android has already granted is re-read on the way in
 * and on every resume, so the pane is never wrong about the fact. A refusal is remembered only
 * for as long as the step is on screen: stepping away and back offers to ask again, and where
 * Android has stopped asking, that offer comes straight back as a refusal and moves the pane to
 * the settings screen — one tap, and never a button that quietly does nothing.
 */
@Composable
private fun SystemPermissionActions(
    permissions: List<String>,
    grantedLabel: String,
    askText: String,
    wording: OnboardingWording,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permission by remember(permissions) {
        mutableStateOf(
            if (context.hasPermissions(permissions)) {
                PermissionState.GRANTED
            } else {
                PermissionState.NOT_GRANTED
            },
        )
    }

    // Only ever upwards: Android restarts the process when a granted permission is taken away,
    // so a grant that disappears takes this whole screen with it rather than going stale here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (context.hasPermissions(permissions)) permission = PermissionState.GRANTED
    }

    // The map of answers is not read: a half-granted calendar is not a granted calendar, so the
    // platform is asked about the whole set instead.
    val request = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        permission = when {
            context.hasPermissions(permissions) -> PermissionState.GRANTED
            permissions.any { name -> activity?.canRequestAgain(name) == true } ->
                PermissionState.DENIED

            else -> PermissionState.BLOCKED
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PermissionStatus(
            permission = permission,
            grantedLabel = grantedLabel,
            wording = wording,
        )
        Spacer(Modifier.height(MiraTheme.spacing.md))
        PermissionActions(
            permission = permission,
            askText = askText,
            settingsText = wording.openAppSettings,
            wording = wording,
            onAsk = { request.launch(permissions.toTypedArray()) },
            onOpenSettings = { context.openAppSettings() },
            onContinue = onContinue,
        )
    }
}

/**
 * A permission step's actions follow the permission, not the step.
 *
 * Once Android has refused twice it will not show the dialog again, so the pane stops offering to
 * ask and offers the settings screen instead — the difference between a button that works and one
 * that quietly does nothing.
 */
@Composable
private fun PermissionActions(
    permission: PermissionState,
    askText: String,
    settingsText: String,
    wording: OnboardingWording,
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (permission) {
        PermissionState.GRANTED -> OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = onContinue,
            modifier = modifier,
        )

        PermissionState.NOT_GRANTED -> OnboardingActions(
            primaryText = askText,
            onPrimary = onAsk,
            modifier = modifier,
            alternativeText = wording.skipForNow,
            onAlternative = onContinue,
        )

        PermissionState.DENIED -> OnboardingActions(
            primaryText = wording.askAgain,
            onPrimary = onAsk,
            modifier = modifier,
            alternativeText = wording.skipForNow,
            onAlternative = onContinue,
        )

        PermissionState.BLOCKED -> OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = onContinue,
            modifier = modifier,
            alternativeText = settingsText,
            alternativeStyle = MiraButtonStyle.SECONDARY,
            onAlternative = onOpenSettings,
        )
    }
}
