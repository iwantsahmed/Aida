package com.rayluxeo.aida.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.automation.AutomationEngine
import com.rayluxeo.aida.domain.automation.AutomationPlaceWatch
import com.rayluxeo.aida.domain.automation.AutomationRunHistory
import com.rayluxeo.aida.domain.automation.AutomationValidator
import com.rayluxeo.aida.domain.automation.CrossAppAccess
import com.rayluxeo.aida.domain.automation.InstalledAppCatalog
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.model.InstalledApp
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.usecase.SyncAutomationSchedulesUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutomationsUiState(
    val selectedAutomationForHistory: Automation? = null,
    val editingAutomation: Automation? = null,
    val isAddSheetOpen: Boolean = false,
    val notice: String? = null,
    val isSuccess: Boolean = false,
    /** Which language the screen's own labels are written in; the notices are worded to match. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

/**
 * Whether the cross-app tier can actually act, read fresh so the create sheet never claims a rule is
 * armed when the grant it needs is off (§5). The toggle is Abu's master opt-in; the two OS grants are
 * read live because he may flip them in system settings and come back.
 */
data class CrossAppReadiness(
    val toggleOn: Boolean = false,
    val accessibilityOn: Boolean = false,
    val notificationAccessOn: Boolean = false,
) {
    /** A global-action rule fires only with the accessibility service on (and the tier switched on). */
    val globalActionsReady: Boolean get() = toggleOn && accessibilityOn

    /** A notification trigger fires only with notification access on (and the tier switched on). */
    val notificationTriggerReady: Boolean get() = toggleOn && notificationAccessOn
}

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val automationEngine: AutomationEngine,
    private val automationValidator: AutomationValidator,
    private val syncSchedules: SyncAutomationSchedulesUseCase,
    private val runHistory: AutomationRunHistory,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val installedAppCatalog: InstalledAppCatalog,
    private val crossAppAccess: CrossAppAccess,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationsUiState())

    /**
     * Abu's language is folded in here rather than stored on [_uiState], so switching it in
     * Settings rewords the screen at once without the view model having to touch every notice.
     */
    val uiState: StateFlow<AutomationsUiState> = combine(_uiState, settings.settings) { state, prefs ->
        state.copy(language = prefs.languageMode.displayLanguage())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationsUiState())

    val automations: StateFlow<List<Automation>> = automationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The places Abu has already saved, offered as the choices for a place trigger. Mira watches a
     * place she has coordinates for; a new one is added on the Places screen, not invented here.
     */
    val savedPlaces: StateFlow<List<SavedPlace>> = savedPlaceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The launchable apps, for the "launch an app" action's picker. Read once, off the main thread. */
    val installedApps: StateFlow<List<InstalledApp>> = flow {
        emit(installedAppCatalog.launchableApps().getOrNull().orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // The master toggle is taken LIVE from the settings flow, so flipping it in Settings is reflected
    // at once; only the two OS grants need an explicit re-read (there is no flow for them), done on
    // sheet-open and on resume so a trip to system settings is picked up.
    private val _osGrants = MutableStateFlow(OsGrants())

    /** Whether the cross-app tier can act, for the create sheet's honest "grant is off" notes (§5). */
    val crossApp: StateFlow<CrossAppReadiness> =
        combine(settings.settings, _osGrants) { prefs, grants ->
            CrossAppReadiness(
                toggleOn = prefs.crossAppAutomationsEnabled,
                accessibilityOn = grants.accessibilityOn,
                notificationAccessOn = grants.notificationAccessOn,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CrossAppReadiness())

    fun refreshCrossAppReadiness() {
        viewModelScope.launch {
            _osGrants.value = OsGrants(
                accessibilityOn = crossAppAccess.isAccessibilityServiceEnabled(),
                notificationAccessOn = crossAppAccess.isNotificationAccessEnabled(),
            )
        }
    }

    val selectedHistoryRuns: StateFlow<List<AutomationRun>> = _uiState
        .flatMapLatest { state ->
            val autoId = state.selectedAutomationForHistory?.id
            if (autoId != null) {
                runHistory.observeHistory(autoId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleEnabled(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            val words = automationsWording(language())
            // Arming follows the stored state, so a rule that will not persist its toggle does not
            // end up with an alarm the list does not show.
            when (val result = automationRepository.setEnabled(automation.id, enabled)) {
                is AppResult.Success -> {
                    // A place rule Abu just enabled may not actually be watchable (a withheld
                    // background-location grant). Tell him rather than leave the toggle looking armed.
                    syncSchedules.sync(result.value).placeRefusal()?.let { refusal ->
                        _uiState.update {
                            it.copy(notice = words.placeWatchRefused(refusal), isSuccess = false)
                        }
                    }
                }
                is AppResult.Failure -> Unit
            }
        }
    }

    fun deleteAutomation(automationId: Long) {
        viewModelScope.launch {
            val words = automationsWording(language())
            when (val result = automationRepository.delete(automationId)) {
                is AppResult.Success -> {
                    syncSchedules.cancel(automationId)
                    _uiState.update { it.copy(notice = words.automationDeleted, isSuccess = true) }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.deleteFailed(result.error.detail)) }
                }
            }
        }
    }

    fun runManually(automationId: Long) {
        viewModelScope.launch {
            val words = automationsWording(language())
            _uiState.update { it.copy(notice = words.runningAutomation) }
            when (val result = automationEngine.triggerAutomation(automationId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            notice = words.runFinished(result.value.outcome),
                            isSuccess = true,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.runFailed(result.error.detail)) }
                }
            }
        }
    }

    fun selectForHistory(automation: Automation?) {
        _uiState.update { it.copy(selectedAutomationForHistory = automation) }
    }

    fun openAddSheet() {
        // Re-read the OS grants as the sheet opens, so a cross-app trigger/action shows its true
        // readiness without waiting for the next Activity resume.
        refreshCrossAppReadiness()
        _uiState.update { it.copy(isAddSheetOpen = true, editingAutomation = null) }
    }

    fun closeAddSheet() {
        _uiState.update { it.copy(isAddSheetOpen = false, editingAutomation = null) }
    }

    fun setEditingAutomation(automation: Automation?) {
        if (automation != null) refreshCrossAppReadiness()
        _uiState.update { it.copy(editingAutomation = automation, isAddSheetOpen = automation != null) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** The two OS grants, re-read on demand — there is no system flow to observe them live. */
    private data class OsGrants(
        val accessibilityOn: Boolean = false,
        val notificationAccessOn: Boolean = false,
    )

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()

    fun saveAutomation(
        name: String,
        description: String?,
        trigger: AutomationTriggerConfig,
        action: AutomationAction,
        isEnabled: Boolean,
    ) {
        val now = clock.now()
        val currentEdit = _uiState.value.editingAutomation
        val automation = Automation(
            id = currentEdit?.id ?: 0L,
            name = name,
            description = description,
            trigger = trigger,
            action = action,
            isEnabled = isEnabled,
            createdAt = currentEdit?.createdAt ?: now,
            updatedAt = now,
        )

        viewModelScope.launch {
            val words = automationsWording(language())
            val valid = automationValidator.validate(automation)
            if (valid is AppResult.Failure) {
                _uiState.update { it.copy(notice = words.validationError(valid.error.detail)) }
                return@launch
            }

            val result = if (currentEdit != null) {
                automationRepository.update(automation)
            } else {
                automationRepository.create(automation)
            }

            when (result) {
                is AppResult.Success -> {
                    // Arm (or tear down) everything the saved rule needs to fire on its own — a
                    // time-based rule starts ringing now, a place rule starts being watched, and an
                    // edit away from either drops what it no longer needs.
                    val refusal = syncSchedules.sync(result.value).placeRefusal()
                    _uiState.update {
                        if (refusal != null) {
                            // The rule is saved, but Mira cannot watch the place yet, so she says so
                            // rather than claiming it is armed (§5).
                            it.copy(
                                isAddSheetOpen = false,
                                editingAutomation = null,
                                notice = words.placeWatchRefused(refusal),
                                isSuccess = false,
                            )
                        } else {
                            it.copy(
                                isAddSheetOpen = false,
                                editingAutomation = null,
                                notice = words.automationSaved(result.value.name),
                                isSuccess = true,
                            )
                        }
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = words.saveFailed(result.error.detail)) }
                }
            }
        }
    }

    /**
     * The one reason a place could not be watched, if the sync came back refused. A time rule, a
     * non-place rule, or a place that armed cleanly all return null — there is nothing to tell Abu.
     */
    private fun AppResult<AutomationPlaceWatch>.placeRefusal(): PlaceWatchRefusal? =
        ((this as? AppResult.Success)?.value as? AutomationPlaceWatch.Refused)?.reason
}
