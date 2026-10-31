package com.rayluxeo.aida.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.memory.MemoryManager
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.MemorySource
import com.rayluxeo.aida.domain.repository.MemoryRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A line Mira shows above the Memory list. It is a structured value, not a sentence, so the words
 * are chosen per language by [MemoryWording] at render time rather than frozen into one language
 * here (§4.6). The subject on [Remembered]/[Updated] is data Abu typed, placed rather than
 * translated.
 */
sealed interface MemoryNotice {
    data class Remembered(val subject: String) : MemoryNotice
    data class Updated(val subject: String) : MemoryNotice
    data object Forgotten : MemoryNotice
    data object RememberFailed : MemoryNotice
    data object UpdateFailed : MemoryNotice
    data object ForgetFailed : MemoryNotice
}

data class MemoryUiState(
    val searchQuery: String = "",
    val selectedCategory: MemoryCategory? = null,
    val selectedMemory: MemoryFact? = null,
    val editingMemory: MemoryFact? = null,
    val isAddDialogOpen: Boolean = false,
    val notice: MemoryNotice? = null,
    /** Which language the screen words itself in, from Abu's settings. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val memoryManager: MemoryManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())

    /**
     * The language is folded in here rather than stored on [_uiState], so switching language in
     * Settings rewords the whole screen without any handler having to copy it across (the same
     * write-through model the Clients and Salah screens use).
     */
    val uiState: StateFlow<MemoryUiState> = combine(
        _uiState,
        settings.settings,
    ) { state, preferences ->
        state.copy(language = preferences.languageMode.displayLanguage())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryUiState())

    val memories: StateFlow<List<MemoryFact>> = combine(
        memoryRepository.observe(),
        _uiState,
    ) { allMemories, state ->
        var list = allMemories.filter { it.deletedAt == null }
        if (state.selectedCategory != null) {
            list = list.filter { it.category == state.selectedCategory }
        }
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.subject.lowercase().contains(q) || it.fact.lowercase().contains(q)
            }
        }
        list.sortedWith(
            compareByDescending<MemoryFact> { it.isPinned }
                .thenByDescending { it.recallCount }
                .thenByDescending { it.updatedAt }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: MemoryCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = true, editingMemory = null) }
    }

    fun closeAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false, editingMemory = null) }
    }

    fun setEditingMemory(fact: MemoryFact?) {
        _uiState.update { it.copy(editingMemory = fact, isAddDialogOpen = fact != null) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun rememberFact(subject: String, fact: String, category: MemoryCategory?) {
        viewModelScope.launch {
            when (val result = memoryManager.remember(subject, fact, source = MemorySource.TYPED, category = category)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAddDialogOpen = false,
                            notice = MemoryNotice.Remembered(result.value.subject),
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = MemoryNotice.RememberFailed) }
                }
            }
        }
    }

    fun updateFact(fact: MemoryFact) {
        viewModelScope.launch {
            when (val result = memoryRepository.update(fact)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAddDialogOpen = false,
                            editingMemory = null,
                            notice = MemoryNotice.Updated(result.value.subject),
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = MemoryNotice.UpdateFailed) }
                }
            }
        }
    }

    fun togglePin(fact: MemoryFact) {
        viewModelScope.launch {
            memoryManager.togglePin(fact.id, !fact.isPinned)
        }
    }

    fun updateCategory(factId: Long, category: MemoryCategory) {
        viewModelScope.launch {
            memoryManager.updateCategory(factId, category)
        }
    }

    fun forget(factId: Long) {
        viewModelScope.launch {
            when (memoryManager.forget(factId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(notice = MemoryNotice.Forgotten) }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(notice = MemoryNotice.ForgetFailed) }
                }
            }
        }
    }
}
