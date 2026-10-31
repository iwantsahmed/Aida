package com.rayluxeo.aida.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.DateStyle
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.mira.MiraEntryCoordinator
import com.rayluxeo.aida.ui.mira.MiraEntryIntent
import com.rayluxeo.aida.ui.tasks.displayLanguage
import com.rayluxeo.aida.ui.vault.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What the shell needs to know before it can draw a single screen.
 *
 * [Loading] is a real state rather than a default, because either guess is visible. Assuming
 * onboarding is done flashes Home at Abu on a clean install; assuming it is not flashes the
 * welcome screen at him every time he reopens the app. DataStore answers in a few milliseconds,
 * so the shell holds an obsidian ground for that long instead of showing the wrong thing.
 */
sealed interface MiraShellState {

    data object Loading : MiraShellState

    data class Ready(
        val onboardingComplete: Boolean,
        val userName: String = "",
        val hapticsEnabled: Boolean,
        /** Which language the tab strip, the hub and the planned-area screens write in. */
        val language: SpokenLanguage,
        /** The date style every screen formats a date in, provided once at the shell root. */
        val dateStyle: DateStyle = DateStyle.DAY_MONTH_YEAR,
        /** Whether Abu has turned app-lock on. The gate does nothing while this is false. */
        val appLockEnabled: Boolean = false,
        /** Whether leaving the app should re-arm the lock immediately, on top of the delay below. */
        val lockWhenBackgrounded: Boolean = true,
        /** How long away before the lock re-arms on return. 0 is "Immediately". See [AppLockController]. */
        val appLockTimeoutSeconds: Int = 0,
        /** Whether the lock is currently holding him out — see [AppLockController]. */
        val locked: Boolean = false,
        /** Abu's "Require unlock for" choices: a biometric gate in front of these sensitive areas. */
        val protectMoney: Boolean = false,
        val protectMemory: Boolean = false,
        val protectInvoices: Boolean = false,
        val protectBackups: Boolean = false,
    ) : MiraShellState
}

/**
 * The few settings that belong to the whole shell rather than to one screen: whether Abu has
 * been through first-run onboarding, whether haptics are on for every component beneath, and
 * the language the shell's own words are written in.
 *
 * The language is read here rather than in each screen because the bottom bar, the hub and a
 * planned area are one frame with one voice, and the frame outlives any of them.
 */
@HiltViewModel
class MiraShellViewModel @Inject constructor(
    settings: SettingsRepository,
    private val entry: MiraEntryCoordinator,
    private val appLock: AppLockController,
) : ViewModel() {

    val state: StateFlow<MiraShellState> =
        combine(settings.settings, appLock.locked) { stored, locked ->
            MiraShellState.Ready(
                onboardingComplete = stored.onboardingComplete,
                userName = stored.userName,
                hapticsEnabled = stored.hapticFeedback,
                language = stored.languageMode.displayLanguage(),
                dateStyle = stored.dateStyle,
                appLockEnabled = stored.appLockEnabled,
                lockWhenBackgrounded = stored.lockWhenBackgrounded,
                appLockTimeoutSeconds = stored.appLockTimeoutSeconds,
                locked = locked,
                protectMoney = stored.protectMoney,
                protectMemory = stored.protectMemory,
                protectInvoices = stored.protectInvoices,
                protectBackups = stored.protectBackups,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MiraShellState.Loading,
        )

    /**
     * Carry a question onto the Mira tab. The screen reads it when it comes up and drops it into
     * the composer, so a "ask Mira about this" from elsewhere arrives as the words it meant rather
     * than an empty screen.
     */
    fun askMira(prompt: String) {
        entry.request(MiraEntryIntent.Ask(prompt))
    }

    /** Open the Mira tab already listening — the Talk shortcut and the voice tile land here. */
    fun startListening() {
        entry.request(MiraEntryIntent.StartListening)
    }

    /**
     * The app went to the background. The departure is recorded so a later return can honour the
     * Auto-lock delay, and the lock shuts now as well when Abu has turned "Lock when I leave" on.
     * A no-op while the feature is off. Driven by the shell gate's lifecycle observer.
     */
    fun onAppBackgrounded() {
        val s = state.value as? MiraShellState.Ready ?: return
        if (s.appLockEnabled) appLock.onLeftForeground(lockImmediately = s.lockWhenBackgrounded)
    }

    /**
     * The app returned to the foreground. The lock re-arms when Abu was away at least his Auto-lock
     * delay — which is what makes that setting real. A no-op while the feature is off.
     */
    fun onAppForegrounded() {
        val s = state.value as? MiraShellState.Ready ?: return
        if (s.appLockEnabled) appLock.onReturnedToForeground(s.appLockTimeoutSeconds)
    }

    /** Clear the lock, after the prompt succeeds or when the feature is switched off. */
    fun unlockApp() {
        appLock.unlock()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
