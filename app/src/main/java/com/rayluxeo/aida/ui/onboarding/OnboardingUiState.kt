package com.rayluxeo.aida.ui.onboarding

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.tasks.displayLanguage

/**
 * Everything the onboarding flow draws, in one value.
 *
 * The permission fields are what Mira has been *told* by the platform, never what she hopes: a
 * step reports "granted" only after a real check, which is what keeps the flow from asking for
 * something Abu has already allowed.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val userName: String = "",
    val languageMode: LanguageMode = LanguageMode.AUTO,
    val microphone: PermissionState = PermissionState.NOT_GRANTED,
    val notifications: PermissionState = PermissionState.NOT_GRANTED,
    val biometrics: BiometricReadiness = BiometricReadiness.UNKNOWN,
    /** True once this phone has told Mira it has no assistant settings screen to open. */
    val assistantSettingsMissing: Boolean = false,
    val finishing: Boolean = false,
    /** True only after `onboardingComplete` has actually reached storage. */
    val finished: Boolean = false,
) {
    val canGoBack: Boolean get() = !step.isFirst

    /**
     * Which of the two phrasebooks every pane is read from.
     *
     * Derived rather than stored, so it cannot drift from [languageMode]: the language pane writes
     * the mode through to storage and the very next frame of the same flow is drawn from it.
     */
    val language: SpokenLanguage get() = languageMode.displayLanguage()
}
