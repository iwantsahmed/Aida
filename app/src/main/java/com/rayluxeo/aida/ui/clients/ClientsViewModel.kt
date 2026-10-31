package com.rayluxeo.aida.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientsUiState(
    val selectedClient: Client? = null,
    val searchQuery: String = "",
    val isAddSheetOpen: Boolean = false,
    /** The client just binned, so the undo notice can restore the same row. */
    val pendingUndoClientId: Long? = null,
    val notice: String? = null,
    /** Which language the screen words itself in, from Abu's settings. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val invoiceRepository: InvoiceRepository,
    private val settings: SettingsRepository,
    private val renderer: ResponseRenderer,
    private val clock: MiraClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientsUiState())

    /**
     * The language is folded in here rather than stored on [_uiState], so switching language in
     * Settings rewords the whole screen without any handler having to remember to copy it across.
     */
    val uiState: StateFlow<ClientsUiState> = combine(
        _uiState,
        settings.settings,
    ) { state, preferences ->
        state.copy(language = preferences.languageMode.displayLanguage())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClientsUiState())

    val clients: StateFlow<List<Client>> = combine(
        clientRepository.observe(ClientFilter.ALL),
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
    ) { all, query ->
        if (query.isBlank()) {
            all
        } else {
            all.filter { client ->
                client.name.contains(query, ignoreCase = true) ||
                    client.companyName?.contains(query, ignoreCase = true) == true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedClientProjects: StateFlow<List<Project>> = _uiState
        .flatMapLatest { state ->
            val id = state.selectedClient?.id
            if (id != null) projectRepository.observeForClient(id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedClientInvoices: StateFlow<List<Invoice>> = _uiState
        .flatMapLatest { state ->
            val id = state.selectedClient?.id
            if (id != null) invoiceRepository.observeForClient(id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectClient(client: Client?) {
        _uiState.update { it.copy(selectedClient = client) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openAddSheet() {
        _uiState.update { it.copy(isAddSheetOpen = true) }
    }

    fun closeAddSheet() {
        _uiState.update { it.copy(isAddSheetOpen = false) }
    }

    fun createClient(name: String, companyName: String?, email: String?, phone: String?) {
        viewModelScope.launch {
            val language = language()
            val words = clientsWording(language)
            val now = clock.now()
            val client = Client(
                name = name,
                companyName = companyName,
                email = email,
                phone = phone,
                createdAt = now,
                updatedAt = now,
            )
            when (val res = clientRepository.create(client)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAddSheetOpen = false,
                            selectedClient = res.value,
                            notice = words.clientAdded(res.value.name),
                        )
                    }
                }
                is AppResult.Failure -> {
                    // The error's detail is developer-facing and never shown; the code is rendered
                    // per language, so a refusal reads the same way Mira would speak it.
                    _uiState.update {
                        it.copy(notice = renderer.display(MiraResponse.Problem(res.error.code), language))
                    }
                }
            }
        }
    }

    fun deleteClient(id: Long) {
        viewModelScope.launch {
            val words = clientsWording(language())
            clientRepository.softDelete(id)
            _uiState.update {
                it.copy(
                    selectedClient = null,
                    pendingUndoClientId = id,
                    notice = words.clientMovedToBin,
                )
            }
        }
    }

    fun restoreClient(id: Long) {
        viewModelScope.launch {
            clientRepository.restore(id)
            _uiState.update { it.copy(pendingUndoClientId = null, notice = null) }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(pendingUndoClientId = null, notice = null) }
    }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()
}
