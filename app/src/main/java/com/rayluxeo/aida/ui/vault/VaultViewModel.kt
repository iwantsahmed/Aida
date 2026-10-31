package com.rayluxeo.aida.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VaultRepository
import com.rayluxeo.aida.domain.usecase.CreateVaultItemUseCase
import com.rayluxeo.aida.domain.usecase.DeleteVaultItemUseCase
import com.rayluxeo.aida.domain.usecase.RevealVaultItemUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the vault door.
 *
 * The rule the whole class exists to enforce: nothing that was decrypted, and nothing Abu is
 * part-way through typing, outlives [lock]. Titles and categories are stored in the clear by
 * design, so they may be observed continuously; secrets are fetched one at a time, on demand,
 * and dropped the moment the vault closes.
 *
 * Nothing here is a sentence. A refused unlock and a failed read both leave a value behind —
 * [VaultLockNote], [VaultProblem] — and the screen words it, which is what lets the vault answer
 * in Bengali. Human wording never lives where the decision is made (AGENTS.md §4).
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val revealItem: RevealVaultItemUseCase,
    private val createItem: CreateVaultItemUseCase,
    private val deleteItem: DeleteVaultItemUseCase,
    settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState())

    /**
     * The language is folded in here rather than written into [_state], so that switching it
     * rewords a lock message that is already on screen instead of leaving the last one in the
     * language it was raised in.
     */
    val state: StateFlow<VaultUiState> = combine(_state, settings.settings) { current, preferences ->
        current.copy(language = preferences.languageMode.displayLanguage())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = VaultUiState(),
    )

    init {
        viewModelScope.launch {
            vault.observeSummaries().collect { summaries ->
                _state.update {
                    it.copy(
                        items = if (summaries.isEmpty()) {
                            VaultItems.Empty
                        } else {
                            VaultItems.Content(summaries)
                        },
                    )
                }
            }
        }
    }

    fun onUnlockRequested() {
        _state.update { it.copy(phase = VaultPhase.UNLOCKING, lock = null, needsEnrolment = false) }
    }

    fun onUnlockOutcome(outcome: VaultUnlockOutcome) {
        _state.update { current ->
            when (outcome) {
                VaultUnlockOutcome.Succeeded ->
                    current.copy(phase = VaultPhase.UNLOCKED, lock = null, needsEnrolment = false)

                // The system prompt is still up, so the phase stays where it is.
                VaultUnlockOutcome.NotRecognised ->
                    current.copy(lock = VaultLockNote.Refused(VaultLockReason.NOT_RECOGNISED))

                VaultUnlockOutcome.Cancelled -> current.copy(
                    phase = VaultPhase.LOCKED,
                    lock = VaultLockNote.Refused(VaultLockReason.CANCELLED),
                )

                VaultUnlockOutcome.LockedOut -> current.copy(
                    phase = VaultPhase.LOCKED,
                    lock = VaultLockNote.Refused(VaultLockReason.LOCKED_OUT),
                )

                VaultUnlockOutcome.NoneEnrolled -> current.copy(
                    phase = VaultPhase.LOCKED,
                    needsEnrolment = true,
                    lock = VaultLockNote.Refused(VaultLockReason.NONE_ENROLLED),
                )

                // needsEnrolment is deliberately left where it was: a phone with no screen lock
                // that then fails to open the enrolment settings still needs one, and clearing
                // the flag here would take away the only button that leads anywhere.
                is VaultUnlockOutcome.Refused -> current.copy(
                    phase = VaultPhase.LOCKED,
                    lock = VaultLockNote.Refused(outcome.reason),
                )

                is VaultUnlockOutcome.SystemRefused -> current.copy(
                    phase = VaultPhase.LOCKED,
                    lock = VaultLockNote.System(outcome.message),
                )
            }
        }
    }

    /** Called when Mira leaves the foreground. A vault that survives the app switcher is not a vault. */
    fun lock() {
        _state.update {
            it.copy(
                phase = VaultPhase.LOCKED,
                lock = null,
                needsEnrolment = false,
                revealing = false,
                revealed = null,
                composerOpen = false,
                draft = VaultDraft(),
                confirmingDelete = null,
                problem = null,
            )
        }
    }

    fun reveal(id: Long) {
        if (_state.value.phase != VaultPhase.UNLOCKED || _state.value.revealing) return
        _state.update { it.copy(revealing = true, problem = null) }
        viewModelScope.launch {
            when (val result = revealItem(id)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        revealing = false,
                        revealed = RevealedSecret(
                            id = result.value.id,
                            title = result.value.title,
                            category = result.value.category,
                            secret = result.value.secret,
                        ),
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(revealing = false, problem = result.error.asProblem())
                }
            }
        }
    }

    fun dismissReveal() {
        _state.update { it.copy(revealed = null) }
    }

    fun openComposer() {
        _state.update { it.copy(composerOpen = true, draft = VaultDraft(), problem = null) }
    }

    fun dismissComposer() {
        _state.update { it.copy(composerOpen = false, draft = VaultDraft()) }
    }

    fun onDraftTitleChange(value: String) {
        _state.update { it.copy(draft = it.draft.copy(title = value)) }
    }

    fun onDraftSecretChange(value: String) {
        _state.update { it.copy(draft = it.draft.copy(secret = value)) }
    }

    fun onDraftCategoryChange(category: VaultCategory) {
        _state.update { it.copy(draft = it.draft.copy(category = category)) }
    }

    fun saveDraft() {
        val draft = _state.value.draft
        if (!draft.isComplete || _state.value.saving) return
        _state.update { it.copy(saving = true, problem = null) }
        viewModelScope.launch {
            when (val result = createItem(draft.title, draft.secret, draft.category)) {
                is AppResult.Success -> _state.update {
                    it.copy(saving = false, composerOpen = false, draft = VaultDraft())
                }

                is AppResult.Failure -> _state.update {
                    it.copy(saving = false, problem = result.error.asProblem())
                }
            }
        }
    }

    fun askDelete(id: Long, title: String) {
        _state.update { it.copy(confirmingDelete = VaultDeletion(id, title), revealed = null) }
    }

    fun cancelDelete() {
        _state.update { it.copy(confirmingDelete = null) }
    }

    fun confirmDelete() {
        val pending = _state.value.confirmingDelete ?: return
        _state.update { it.copy(confirmingDelete = null, problem = null) }
        viewModelScope.launch {
            val result = deleteItem(pending.id)
            if (result is AppResult.Failure) {
                _state.update { it.copy(problem = result.error.asProblem()) }
            }
        }
    }

    fun dismissProblem() {
        _state.update { it.copy(problem = null) }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Which of the vault's five explanations a failure is.
 *
 * Kept narrower than [MiraErrorCode] on purpose: a decryption failure and a missing row are the
 * two the vault has to be able to say precisely, because they mean different things about Abu's
 * data, and everything else is honestly "something went wrong".
 */
private fun MiraError.asProblem(): VaultProblem = when (code) {
    MiraErrorCode.AUTHENTICATION_FAILED -> VaultProblem.CANNOT_DECRYPT
    MiraErrorCode.NOT_FOUND -> VaultProblem.GONE
    MiraErrorCode.VALIDATION -> VaultProblem.INCOMPLETE
    MiraErrorCode.STORAGE -> VaultProblem.STORAGE
    else -> VaultProblem.UNKNOWN
}
