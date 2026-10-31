package com.rayluxeo.aida.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_STEP = "onboarding.step"

/**
 * Drives first-run.
 *
 * The step index lives in [SavedStateHandle] rather than in memory so that a rotation, or the
 * process being killed while Abu is away in a system settings screen granting a permission,
 * brings him back to the pane he left rather than to the welcome.
 *
 * Nothing here touches an Android permission API. The screen owns the launchers and the platform
 * queries and reports their answers back through the `on…` functions, which keeps the policy —
 * what a refusal means, and what may be concluded from a later look — testable without a device.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val userProfile: UserProfileRepository,
    private val clock: MiraClock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stepIndex: StateFlow<Int> = savedState.getStateFlow(KEY_STEP, 0)

    private val runtime = MutableStateFlow(RuntimeState())

    init {
        viewModelScope.launch {
            val currentSettings = settings.current()
            if (currentSettings.onboardingComplete && currentSettings.userName.isBlank() && savedState.get<Int>(KEY_STEP) == null) {
                savedState[KEY_STEP] = OnboardingStep.NAME.ordinal
            }
        }
    }

    val state: StateFlow<OnboardingUiState> =
        combine(stepIndex, settings.settings, runtime) { index, stored, live ->
            OnboardingUiState(
                step = OnboardingStep.atIndex(index),
                userName = live.userName.ifEmpty { stored.userName },
                languageMode = stored.languageMode,
                microphone = live.microphone,
                notifications = live.notifications,
                biometrics = live.biometrics,
                assistantSettingsMissing = live.assistantSettingsMissing,
                finishing = live.finishing,
                finished = live.finished,
            )
        }.stateIn(
            scope = viewModelScope,
            // Eagerly: the flow is one screen's worth of state with nothing expensive upstream,
            // and the first pane must be right on the first frame after a restore.
            started = SharingStarted.Eagerly,
            initialValue = OnboardingUiState(step = OnboardingStep.atIndex(stepIndex.value)),
        )

    /** Read from saved state rather than from [state], which lags it by one emission. */
    private val currentStep: OnboardingStep get() = OnboardingStep.atIndex(stepIndex.value)

    /**
     * Forward. This is also what "Skip for now" calls: skipping an optional step is the same
     * movement, and the button says which of the two Abu is choosing.
     */
    fun next() {
        val step = currentStep
        if (!step.isLast) savedState[KEY_STEP] = step.next().ordinal
    }

    fun back() {
        val step = currentStep
        if (!step.isFirst) savedState[KEY_STEP] = step.previous().ordinal
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch { settings.setLanguageMode(mode) }
    }

    fun onUserNameChange(name: String) {
        runtime.update { it.copy(userName = name) }
    }

    fun saveUserName(name: String) {
        val trimmed = name.trim()
        runtime.update { it.copy(userName = trimmed) }
        viewModelScope.launch {
            settings.setUserName(trimmed)
            val existing = userProfile.current().getOrNull()
            val profile = existing?.copy(displayName = trimmed) ?: UserProfile(
                displayName = trimmed,
                defaultCurrency = Money.BDT,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            )
            userProfile.save(profile)
            if (settings.current().onboardingComplete) {
                runtime.update { it.copy(finished = true) }
            }
        }
    }

    fun onMicrophoneChecked(granted: Boolean) {
        runtime.update { it.copy(microphone = it.microphone.recheckedAs(granted)) }
    }

    fun onMicrophoneResult(granted: Boolean, canAskAgain: Boolean) {
        runtime.update { it.copy(microphone = answeredWith(granted, canAskAgain)) }
    }

    /**
     * [requestable] is the platform's own answer, not a preference: below API 33 there is no
     * notification dialog to show, so "off" there already means "only settings can change it".
     */
    fun onNotificationsChecked(granted: Boolean, requestable: Boolean) {
        runtime.update {
            val next = when {
                granted -> PermissionState.GRANTED
                !requestable -> PermissionState.BLOCKED
                else -> it.notifications.recheckedAs(false)
            }
            it.copy(notifications = next)
        }
    }

    fun onNotificationsResult(granted: Boolean, canAskAgain: Boolean) {
        runtime.update { it.copy(notifications = answeredWith(granted, canAskAgain)) }
    }

    fun onBiometricsChecked(readiness: BiometricReadiness) {
        runtime.update { it.copy(biometrics = readiness) }
    }

    /** The phone had no assistant screen to open, so the step stops offering one. */
    fun onAssistantSettingsMissing() {
        runtime.update { it.copy(assistantSettingsMissing = true) }
    }

    /**
     * The only write that matters: until this lands, a kill at step twelve starts Abu over.
     * [OnboardingUiState.finished] is raised afterwards rather than before, so the screen only
     * leaves once the flag is genuinely stored.
     */
    fun finish() {
        if (runtime.value.finishing || runtime.value.finished) return
        runtime.update { it.copy(finishing = true) }
        viewModelScope.launch {
            if (runtime.value.userName.isNotBlank()) {
                settings.setUserName(runtime.value.userName.trim())
            }
            settings.setOnboardingComplete(true)
            runtime.update { it.copy(finishing = false, finished = true) }
        }
    }

    /** The half of the state that is re-read from the platform rather than restored with it. */
    private data class RuntimeState(
        val userName: String = "",
        val microphone: PermissionState = PermissionState.NOT_GRANTED,
        val notifications: PermissionState = PermissionState.NOT_GRANTED,
        val biometrics: BiometricReadiness = BiometricReadiness.UNKNOWN,
        val assistantSettingsMissing: Boolean = false,
        val finishing: Boolean = false,
        val finished: Boolean = false,
    )
}

/**
 * A plain look at a permission can only ever turn it on or take it away. It must not downgrade
 * [PermissionState.BLOCKED] to [PermissionState.NOT_GRANTED], because the two look identical
 * from outside and Mira would start offering a dialog Android will never show.
 */
private fun PermissionState.recheckedAs(granted: Boolean): PermissionState = when {
    granted -> PermissionState.GRANTED
    this == PermissionState.GRANTED -> PermissionState.NOT_GRANTED
    else -> this
}

private fun answeredWith(granted: Boolean, canAskAgain: Boolean): PermissionState = when {
    granted -> PermissionState.GRANTED
    canAskAgain -> PermissionState.DENIED
    else -> PermissionState.BLOCKED
}
