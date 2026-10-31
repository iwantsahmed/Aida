package com.rayluxeo.aida.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.search.UniversalSearchResults
import com.rayluxeo.aida.domain.search.UniversalSearchUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UniversalSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: UniversalSearchResults? = null,
    /**
     * Which language the screen words itself in. Defaults to English, exactly as the text
     * normalizer settles AUTO with no Bengali signal to go on — the search field has none either.
     */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

@HiltViewModel
class UniversalSearchViewModel @Inject constructor(
    private val universalSearchUseCase: UniversalSearchUseCase,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UniversalSearchUiState())
    val uiState: StateFlow<UniversalSearchUiState> = _uiState.asStateFlow()

    init {
        settings.settings
            .map { it.languageMode.displayLanguage() }
            .distinctUntilChanged()
            .onEach { language -> _uiState.update { it.copy(language = language) } }
            .launchIn(viewModelScope)
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        performSearch(query)
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, results = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val searchResults = universalSearchUseCase(query)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    results = searchResults,
                )
            }
        }
    }
}
