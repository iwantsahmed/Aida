package com.rayluxeo.aida.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.usecase.CreateProjectUseCase
import com.rayluxeo.aida.domain.usecase.RestoreProjectUseCase
import com.rayluxeo.aida.domain.usecase.SetProjectStatusUseCase
import com.rayluxeo.aida.domain.usecase.SoftDeleteProjectUseCase
import com.rayluxeo.aida.domain.usecase.UpdateProjectUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One project as the list draws it: the record, and the client's name resolved for the row. */
data class ProjectListItem(
    val project: Project,
    /** Null means the project is Abu's own — the screen reads this as "Personal". */
    val clientName: String?,
)

data class ProjectsUiState(
    val filter: ProjectFilter = ProjectFilter.OPEN,
    val searchQuery: String = "",
    val selectedProject: Project? = null,
    /** Open tasks under the selected project, loaded once when its detail opens. */
    val selectedProjectOpenTasks: Int = 0,
    val isFormOpen: Boolean = false,
    /** The project the form is editing; null while the form is creating a new one. */
    val editing: Project? = null,
    /** The project just binned, so the undo notice can restore the same row. */
    val pendingUndoProjectId: Long? = null,
    val notice: String? = null,
    /** Which language the screen words itself in, from Abu's settings. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val taskRepository: TaskRepository,
    private val createProject: CreateProjectUseCase,
    private val updateProject: UpdateProjectUseCase,
    private val setProjectStatus: SetProjectStatusUseCase,
    private val softDeleteProject: SoftDeleteProjectUseCase,
    private val restoreProjectUseCase: RestoreProjectUseCase,
    private val settings: SettingsRepository,
    private val renderer: ResponseRenderer,
    private val clock: MiraClock,
) : ViewModel() {

    /** Today, for a date picker's initial highlight — from the injected clock, never the wall clock (§4). */
    fun today(): LocalDate = clock.today()

    private val _uiState = MutableStateFlow(ProjectsUiState())

    /**
     * Language is folded in here rather than stored on [_uiState], so switching language in Settings
     * rewords the whole screen without any handler having to remember to copy it across.
     */
    val uiState: StateFlow<ProjectsUiState> = combine(
        _uiState,
        settings.settings,
    ) { state, preferences ->
        state.copy(language = preferences.languageMode.displayLanguage())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectsUiState())

    private val filteredProjects = _uiState
        .map { it.filter }
        .distinctUntilChanged()
        .flatMapLatest { filter -> projectRepository.observe(filter) }

    /**
     * The list, with each project's client name joined in from the live client set. `null` is the
     * first-load state (the shell shows a skeleton); an empty list is the genuine empty state.
     */
    val projects: StateFlow<List<ProjectListItem>?> = combine(
        filteredProjects,
        clientRepository.observe(ClientFilter.ALL),
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
    ) { projects, clients, query ->
        val names = clients.associate { it.id to it.name }
        // Typed nullable so the StateFlow can carry `null` as its first-load (skeleton) state while
        // every real emission is a concrete list — an empty list is the genuine empty state.
        val rows: List<ProjectListItem>? = projects
            .filter { project ->
                query.isBlank() ||
                    project.name.contains(query, ignoreCase = true) ||
                    project.description?.contains(query, ignoreCase = true) == true
            }
            // Soonest due first, undated last, then by name — the order a list of work wants.
            .sortedWith(compareBy({ it.dueOn == null }, { it.dueOn }, { it.name.lowercase() }))
            .map { project -> ProjectListItem(project, project.clientId?.let { names[it] }) }
        rows
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The client set, for the picker in the create/edit sheet. */
    val clients: StateFlow<List<Client>> = clientRepository
        .observe(ClientFilter.ALL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFilter(filter: ProjectFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectProject(project: Project?) {
        _uiState.update { it.copy(selectedProject = project, selectedProjectOpenTasks = 0) }
        if (project != null) {
            viewModelScope.launch {
                // The one honest per-project count the data layer exposes: a one-shot grouping on
                // open, not a fake badge and not a reactive query the repo does not support.
                val count = when (
                    val res = taskRepository.groupedByProject(
                        TaskQuery(filter = TaskFilter.OPEN, projectId = project.id),
                    )
                ) {
                    is AppResult.Success -> res.value.firstOrNull()?.tasks?.size ?: 0
                    is AppResult.Failure -> 0
                }
                _uiState.update {
                    if (it.selectedProject?.id == project.id) {
                        it.copy(selectedProjectOpenTasks = count)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun openNewSheet() {
        _uiState.update { it.copy(isFormOpen = true, editing = null) }
    }

    fun openEditSheet(project: Project) {
        _uiState.update { it.copy(isFormOpen = true, editing = project, selectedProject = null) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(isFormOpen = false, editing = null) }
    }

    /**
     * The one entry point for the create/edit sheet's Save. Which write runs is decided by whether
     * the form was opened on an existing project, so the screen never has to know the difference.
     */
    fun saveProject(
        name: String,
        clientId: Long?,
        description: String?,
        status: ProjectStatus,
        startedOn: LocalDate?,
        dueOn: LocalDate?,
        budget: Money?,
    ) {
        viewModelScope.launch {
            val language = language()
            val words = projectsWording(language)
            val editing = _uiState.value.editing
            val result = if (editing == null) {
                createProject(name, clientId, description, status, startedOn, dueOn, budget)
            } else {
                updateProject(
                    editing.copy(
                        clientId = clientId,
                        name = name,
                        description = description,
                        status = status,
                        startedOn = startedOn,
                        dueOn = dueOn,
                        budget = budget,
                        currency = budget?.currency ?: editing.currency,
                    ),
                )
            }
            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isFormOpen = false,
                        editing = null,
                        selectedProject = if (editing != null) result.value else it.selectedProject,
                        notice = if (editing == null) words.projectAdded(result.value.name) else null,
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    // The detail is developer-facing; the code is rendered per language, so a refusal
                    // reads the way Mira would speak it.
                    it.copy(notice = renderer.display(MiraResponse.Problem(result.error.code), language))
                }
            }
        }
    }

    fun changeStatus(id: Long, status: ProjectStatus) {
        viewModelScope.launch {
            val language = language()
            when (val res = setProjectStatus(id, status)) {
                is AppResult.Success -> _uiState.update {
                    if (it.selectedProject?.id == id) it.copy(selectedProject = res.value) else it
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(notice = renderer.display(MiraResponse.Problem(res.error.code), language))
                }
            }
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            val words = projectsWording(language())
            softDeleteProject(id)
            _uiState.update {
                it.copy(
                    selectedProject = null,
                    pendingUndoProjectId = id,
                    notice = words.projectMovedToBin,
                )
            }
        }
    }

    fun restoreProject(id: Long) {
        viewModelScope.launch {
            restoreProjectUseCase(id)
            _uiState.update { it.copy(pendingUndoProjectId = null, notice = null) }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(pendingUndoProjectId = null, notice = null) }
    }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()
}
