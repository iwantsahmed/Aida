package com.rayluxeo.aida.ui.onboarding

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraAmbientBackground
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraScaffold

/**
 * Everything one pane can ask for, in one holder rather than eleven parameters threaded through
 * three layers. [openVoiceSetup] is null when the host has not wired voice setup into the flow,
 * and the pane then offers no button for it rather than one that goes nowhere.
 */
@Stable
internal class OnboardingCallbacks(
    val next: () -> Unit,
    val back: () -> Unit,
    val onUserNameChange: (String) -> Unit,
    val saveUserName: (String) -> Unit,
    val selectLanguage: (LanguageMode) -> Unit,
    val requestMicrophone: () -> Unit,
    val requestNotifications: () -> Unit,
    val openAppSettings: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val enrollBiometrics: () -> Unit,
    val openAssistantSettings: () -> Unit,
    val openVoiceSetup: (() -> Unit)?,
    val finish: () -> Unit,
)

/**
 * First run, once. Twelve panes that between them explain what Mira is, ask for the two
 * permissions she can actually use today, and are honest about the ones she cannot.
 *
 * [onFinished] is called only after `onboardingComplete` has reached storage, so a kill during
 * the last step costs Abu the last tap rather than the whole flow.
 *
 * [onOpenVoiceSetup] is optional: pass it when `MiraRoutes.VOICE_SETUP` is reachable from the
 * onboarding route, and the voice pane gains a way to go and choose one immediately. Without it
 * the pane simply says that Settings holds the picker, which is equally true.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenVoiceSetup: (() -> Unit)? = null,
) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val microphoneRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onMicrophoneResult(
            granted = granted,
            canAskAgain = activity?.canRequestAgain(Manifest.permission.RECORD_AUDIO) == true,
        )
    }

    val notificationRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationsResult(
            granted = granted,
            canAskAgain = activity?.canRequestAgain(Manifest.permission.POST_NOTIFICATIONS) == true,
        )
    }

    // Re-read on every resume, not only on entry: Abu may have granted any of these from a system
    // screen and walked straight back in.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onMicrophoneChecked(context.hasMicrophonePermission())
        viewModel.onNotificationsChecked(context.notificationsEnabled(), notificationsRequestable)
        viewModel.onBiometricsChecked(context.biometricReadiness())
    }

    BackHandler(enabled = state.canGoBack) { viewModel.back() }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    val callbacks = remember(
        viewModel,
        context,
        activity,
        microphoneRequest,
        notificationRequest,
        onOpenVoiceSetup,
    ) {
        OnboardingCallbacks(
            next = viewModel::next,
            back = viewModel::back,
            onUserNameChange = viewModel::onUserNameChange,
            saveUserName = viewModel::saveUserName,
            selectLanguage = viewModel::setLanguageMode,
            requestMicrophone = { microphoneRequest.launch(Manifest.permission.RECORD_AUDIO) },
            requestNotifications = {
                if (notificationsRequestable) {
                    notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.openNotificationSettings()
                }
            },
            openAppSettings = { context.openAppSettings() },
            openNotificationSettings = { context.openNotificationSettings() },
            enrollBiometrics = { context.openBiometricEnrollment() },
            openAssistantSettings = {
                if (!context.openAssistantSettings()) viewModel.onAssistantSettingsMissing()
            },
            openVoiceSetup = onOpenVoiceSetup,
            finish = viewModel::finish,
        )
    }

    OnboardingContent(state = state, callbacks = callbacks, modifier = modifier)
}

/**
 * Header, pane, actions — and the actions stay put while the pane crosses over, so the way
 * forward never moves under Abu's thumb.
 */
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    callbacks: OnboardingCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraScaffold(
        modifier = modifier,
        background = { MiraAmbientBackground() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OnboardingHeader(
                state = state,
                wording = onboardingWording(state.language),
                onBack = callbacks.back,
            )
            Crossfade(
                targetState = state.step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                animationSpec = MiraMotion.standard(),
                label = "OnboardingPane",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    OnboardingStepBody(step = step, state = state, callbacks = callbacks)
                    Spacer(Modifier.height(spacing.md))
                }
            }
            // Outside the scroll on purpose: an interval that lives inside it is only there when
            // Abu has scrolled to the end, and on a long pane the last line would otherwise sit
            // against the buttons.
            Spacer(Modifier.height(spacing.lg))
            OnboardingStepActions(state = state, callbacks = callbacks)
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

/** The back affordance keeps its row on the first pane too, so the title does not jump. */
@Composable
private fun OnboardingHeader(
    state: OnboardingUiState,
    wording: OnboardingWording,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        // The scaffold hands over the status-bar inset and nothing more, so the row would
        // otherwise begin on the bar itself.
        Spacer(Modifier.height(spacing.xs))
        Box(modifier = Modifier.height(spacing.xxl)) {
            if (state.canGoBack) {
                MiraIconButton(
                    icon = MiraIcons.ArrowBack,
                    contentDescription = wording.back,
                    onClick = onBack,
                )
            }
        }
        Spacer(Modifier.height(spacing.sm))
        OnboardingProgress(step = state.step, wording = wording)
    }
}

@Preview(widthDp = 400, heightDp = 880)
@Composable
private fun OnboardingScreenPreview() {
    MiraTheme {
        OnboardingContent(
            state = OnboardingUiState(
                step = OnboardingStep.MICROPHONE,
                microphone = PermissionState.DENIED,
            ),
            callbacks = OnboardingCallbacks(
                next = {},
                back = {},
                onUserNameChange = {},
                saveUserName = {},
                selectLanguage = {},
                requestMicrophone = {},
                requestNotifications = {},
                openAppSettings = {},
                openNotificationSettings = {},
                enrollBiometrics = {},
                openAssistantSettings = {},
                openVoiceSetup = null,
                finish = {},
            ),
        )
    }
}
