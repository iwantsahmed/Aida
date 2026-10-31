package com.rayluxeo.aida.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.assistant.AssistantRoleController
import com.rayluxeo.aida.assistant.HotwordCapabilityDetector
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Whether Android hands the assistant gesture to Mira, and what is missing when it does not.
 *
 * Nothing here is cached across a visit to the settings screen. Abu changes the assistant app
 * outside Mira, in Android's own settings, so every answer on this screen is asked for again on
 * each resume rather than remembered from the last time the screen was open.
 *
 * The platform facts arrive from the screen instead of being read here, because each one needs a
 * Context: a view model holding one could not be tested without a device, and the decisions worth
 * testing are the ones made from those facts rather than the reading of them.
 */
@HiltViewModel
class AssistantSetupViewModel @Inject constructor(
    private val roles: AssistantRoleController,
    private val hotwords: HotwordCapabilityDetector,
    settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantSetupUiState())
    val state: StateFlow<AssistantSetupUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var testJob: Job? = null

    init {
        // A read path, so it is observed rather than fetched once: the language toggle is one tap
        // away on the settings screen this one is reached from, and a screen that went on
        // speaking English after Abu chose বাংলা would be the defect this wording exists to fix.
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                _state.update { it.copy(language = preferences.languageMode.displayLanguage()) }
            }
        }
    }

    /**
     * Asks Android everything again, and drops a previous test result that has stopped being true.
     *
     * A result is kept only while the phone it described is unchanged. Coming back from the
     * assistant settings screen with the role newly granted has to clear the row that said Mira
     * did not hold it, or the screen would go on contradicting itself until Abu tested again.
     */
    fun refresh(facts: AssistantSystemFacts) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val status = roles.status()
            val settingsAvailable = roles.settingsIntent() != null
            val hotword = hotwords.detect()
            _state.update { current ->
                val unchanged = current.status == status && current.facts == facts
                current.copy(
                    loading = false,
                    status = status,
                    settingsAvailable = settingsAvailable,
                    hotword = hotword,
                    facts = facts,
                    checks = if (unchanged) current.checks else emptyList(),
                )
            }
        }
    }

    /**
     * Runs the real thing rather than describing it.
     *
     * [openSession] is a parameter because starting an activity needs a Context, which must not
     * enter a view model, and because whether a session actually opened has to come back as a
     * fact from the platform rather than be assumed from having asked. It returns false when
     * nothing in this build answered, which is reported as unchecked and never as a failure.
     *
     * The role is read again here rather than taken from [state]: it can be changed from outside
     * Mira between the screen appearing and the button being pressed.
     */
    fun runTest(facts: AssistantSystemFacts, openSession: suspend () -> Boolean) {
        if (_state.value.testing) return
        testJob?.cancel()
        _state.update { it.copy(testing = true, checks = emptyList(), notice = null) }
        testJob = viewModelScope.launch {
            val status = roles.status()
            val settingsAvailable = roles.settingsIntent() != null
            val session = if (shouldOpenSession(status, facts)) {
                if (openSession()) {
                    AssistantSessionProbe.OPENED
                } else {
                    AssistantSessionProbe.NO_ENTRY_POINT
                }
            } else {
                AssistantSessionProbe.NOT_TRIED
            }
            _state.update {
                it.copy(
                    testing = false,
                    status = status,
                    settingsAvailable = settingsAvailable,
                    facts = facts,
                    checks = assistantChecks(
                        status = status,
                        facts = facts,
                        settingsAvailable = settingsAvailable,
                        session = session,
                    ),
                )
            }
        }
    }

    /**
     * Where Abu goes to change the role, or null when this phone has no such screen.
     *
     * This is a settings screen and not a grant. `RoleManager.createRequestRoleIntent` is not a
     * supported way to ask for the assistant role and throws on some builds, so there is no
     * in-app dialog that could hand it over, and the screen must not offer one.
     */
    fun settingsIntent(): Intent? = roles.settingsIntent()

    /** Android may have no screen to send Abu to. A silent no-op would hide that from him. */
    fun reportProblem(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }
}
