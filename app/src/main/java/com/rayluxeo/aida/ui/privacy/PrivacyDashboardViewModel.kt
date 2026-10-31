package com.rayluxeo.aida.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.usecase.EraseAllDataUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyDashboardUiState(
    val totalTasksCount: Int = 0,
    val isEraseConfirmationOpen: Boolean = false,
    val isErasing: Boolean = false,
    val notice: String? = null,
)

@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geminiCredentials: GeminiCredentialStore,
    private val taskRepository: TaskRepository,
    private val eraseAllData: EraseAllDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyDashboardUiState())
    val uiState: StateFlow<PrivacyDashboardUiState> = _uiState.asStateFlow()

    val settings: StateFlow<MiraSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiraSettings())

    init {
        viewModelScope.launch {
            val tasksRes = taskRepository.count(TaskQuery())
            if (tasksRes is AppResult.Success) {
                _uiState.update { it.copy(totalTasksCount = tasksRes.value) }
            }
        }
    }

    fun toggleGeminiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val words = words()
            if (!enabled) {
                geminiCredentials.setApiKey(null)
                _uiState.update { it.copy(notice = words.geminiDisabledNotice) }
            } else {
                _uiState.update { it.copy(notice = words.geminiKeyRequiredNotice) }
            }
        }
    }

    /**
     * Turn the device-wide app-lock on or off. The gate reads [MiraSettings.appLockEnabled] at the
     * shell, so this write is the whole switch — there is no separate runtime flag to keep in step.
     * The "lock when backgrounded" preference is carried through unchanged so a later toggle of this
     * switch does not silently reset it.
     */
    fun toggleAppLock(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            settingsRepository.setAppLock(
                enabled = enabled,
                timeoutSeconds = current.appLockTimeoutSeconds,
                lockWhenBackgrounded = current.lockWhenBackgrounded,
            )
            val words = words()
            _uiState.update {
                it.copy(notice = if (enabled) words.appLockOnNotice else words.appLockOffNotice)
            }
        }
    }

    fun openEraseConfirmation() {
        _uiState.update { it.copy(isEraseConfirmationOpen = true) }
    }

    fun closeEraseConfirmation() {
        _uiState.update { it.copy(isEraseConfirmationOpen = false) }
    }

    /**
     * Actually wipes the device, for real, through [EraseAllDataUseCase]. Abu has already confirmed
     * by the time this runs. On success onboarding is forgotten too, so the shell routes back to
     * first run; the success notice rarely lingers because the screen is replaced underneath it.
     */
    fun confirmEraseAllData() {
        if (_uiState.value.isErasing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isEraseConfirmationOpen = false, isErasing = true) }
            val words = words()
            when (eraseAllData()) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isErasing = false, totalTasksCount = 0, notice = words.dataErasedNotice) }
                is AppResult.Failure ->
                    _uiState.update { it.copy(isErasing = false, notice = words.eraseFailedNotice) }
            }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** Whatever Mira has to say here is said in the language the screen is currently drawn in. */
    private fun words(): PrivacyWording =
        privacyWording(settings.value.languageMode.displayLanguage())
}
