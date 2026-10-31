package com.rayluxeo.aida.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.model.TaskSort
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TagUsage
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.usecase.ArchiveTaskUseCase
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import com.rayluxeo.aida.domain.usecase.CancelReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.DeleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.DuplicateTaskUseCase
import com.rayluxeo.aida.domain.usecase.RestoreTaskUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeTaskUseCase
import com.rayluxeo.aida.domain.usecase.UnarchiveTaskUseCase
import com.rayluxeo.aida.domain.usecase.UnsnoozeReminderUseCase
import com.rayluxeo.aida.domain.usecase.WatchPlaceReminderUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/**
 * The task manager behind the Tasks tab.
 *
 * Every list on this screen is one [TaskQuery]: the seven views, the search field and all four
 * filter dimensions set fields on the same object, so a view and the rows in it cannot disagree
 * about what "overdue" means. The by-project and by-client views ask storage to cut the same
 * query up, rather than grouping the rows here, because the headings need names that only
 * storage can join to.
 *
 * Anything that changes a task goes through a use case, including the bulk paths: a selection of
 * twelve tasks is twelve completions, each with whatever a repeat implies, not one shortcut
 * around the rules. What actually changed is kept so the undo restores that and nothing else.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val createTask: CreateTaskUseCase,
    private val setTaskDone: SetTaskDoneUseCase,
    private val archiveTask: ArchiveTaskUseCase,
    private val unarchiveTask: UnarchiveTaskUseCase,
    private val snoozeTask: SnoozeTaskUseCase,
    private val duplicateTask: DuplicateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val restoreTask: RestoreTaskUseCase,
    reminders: ReminderRepository,
    private val places: ReminderPlaceRepository,
    private val createReminder: CreateReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
    private val unsnoozeReminder: UnsnoozeReminderUseCase,
    private val watchPlace: WatchPlaceReminderUseCase,
    projects: ProjectRepository,
    clients: ClientRepository,
    tags: TagRepository,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
    private val renderer: ResponseRenderer,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listings: Flow<AppResult<List<TaskGroup>>> =
        local.map { Listing(it.toQuery(), it.view.grouping) }
            .distinctUntilChanged()
            .flatMapLatest { listing ->
                when (listing.grouping) {
                    TaskGrouping.NONE -> tasks.observe(listing.query).map { ungrouped(it) }
                    // Grouping is a suspend query, so the plain stream over the same query is used
                    // as the change signal: every insert, edit or deletion re-cuts the sections and
                    // a grouped view stays as live as a flat one.
                    TaskGrouping.PROJECT ->
                        tasks.observe(listing.query).map { tasks.groupedByProject(listing.query) }
                    TaskGrouping.CLIENT ->
                        tasks.observe(listing.query).map { tasks.groupedByClient(listing.query) }
                }
            }

    /**
     * The reminders and the places they wait at, read together.
     *
     * Combined into one flow rather than added to the state combine below, which is already at the
     * five sources `combine` types. A place reminder has to be shown with its place — "waiting at
     * the office" is the whole of what a row like that says — so neither half is useful alone.
     */
    private val remindersFeed: Flow<RemindersFeed> =
        combine(reminders.observeUpcoming(), places.observeAll()) { upcoming, watched ->
            RemindersFeed(upcoming, watched.associateBy { it.reminderId })
        }

    private val facets: Flow<Facets> = combine(
        projects.observe(ProjectFilter.ALL),
        clients.observe(ClientFilter.ALL),
        tags.observeUsage(),
    ) { allProjects, allClients, allTags -> Facets(allProjects, allClients, allTags) }

    val uiState: StateFlow<TasksUiState> = combine(
        local,
        listings,
        remindersFeed,
        settings.settings,
        facets,
    ) { state, groups, feed, preferences, available ->
        buildState(state, groups, feed, preferences.languageMode.displayLanguage(), available)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = TasksUiState(),
    )

    fun onSelectTab(tab: TasksTab) = local.update { it.copy(tab = tab) }

    /** Changing view drops the selection: bulk actions must never reach a row Abu cannot see. */
    fun onSelectView(view: TasksView) = local.update {
        it.copy(view = view, isSelecting = false, selected = emptySet())
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onDismissNotice() = local.update { it.copy(notice = null, undo = null) }

    // --- Filtering and sorting -------------------------------------------------------------

    fun onSelectProject(projectId: Long?) = local.update { it.copy(projectId = projectId) }

    fun onSelectClient(clientId: Long?) = local.update { it.copy(clientId = clientId) }

    fun onToggleTag(tagId: Long) = local.update {
        it.copy(tagIds = if (tagId in it.tagIds) it.tagIds - tagId else it.tagIds + tagId)
    }

    fun onTogglePriority(priority: TaskPriority) = local.update {
        it.copy(
            priorities = if (priority in it.priorities) it.priorities - priority else it.priorities + priority,
        )
    }

    fun onClearFilters() = local.update {
        it.copy(projectId = null, clientId = null, tagIds = emptySet(), priorities = emptySet())
    }

    /** Choosing the sort already applied reverses it, which is how every list Abu uses behaves. */
    fun onSelectSort(sort: TaskSort) = local.update {
        if (it.sort == sort) it.copy(ascending = !it.ascending) else it.copy(sort = sort, ascending = true)
    }

    // --- One task at a time ----------------------------------------------------------------

    fun onToggleTask(id: Long, isDone: Boolean) {
        viewModelScope.launch {
            val language = language()
            when (val result = setTaskDone(id, !isDone)) {
                is AppResult.Success -> {
                    // Re-opening a task needs no announcement; completing one is worth a word.
                    val notice = if (result.value.isDone) {
                        TasksNotice(
                            message = renderer.speak(MiraResponse.TaskCompleted(result.value.title), language),
                            tone = NoticeTone.CONFIRM,
                        )
                    } else {
                        null
                    }
                    local.update { it.copy(notice = notice, undo = null) }
                }
                is AppResult.Failure -> showProblem(result.error.code, language)
            }
        }
    }

    fun onOpenActions(id: Long) {
        viewModelScope.launch {
            val language = language()
            when (val found = tasks.byId(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        actions = TaskActionsUiState(
                            taskId = found.value.id,
                            title = found.value.title,
                            isArchived = found.value.archivedAt != null,
                            canSnooze = found.value.dueAt != null,
                        ),
                    )
                }
                is AppResult.Failure -> showProblem(found.error.code, language)
            }
        }
    }

    fun onCloseActions() = local.update { it.copy(actions = null) }

    fun onSnoozeTask(id: Long, option: SnoozeOption) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            when (val moved = snoozeTask(id, option.amount)) {
                // The moment is read back off the saved task rather than recomputed here, so the
                // line Abu reads is the date the task actually carries.
                is AppResult.Success -> when (val movedTo = moved.value.dueAt) {
                    null -> showProblem(MiraErrorCode.UNKNOWN, language)
                    else -> local.update {
                        it.copy(
                            notice = TasksNotice(
                                message = tasksWording(language)
                                    .taskSnoozed(times.momentLabel(movedTo, language)),
                                tone = NoticeTone.CONFIRM,
                            ),
                            undo = null,
                        )
                    }
                }
                is AppResult.Failure -> showProblem(moved.error.code, language)
            }
        }
    }

    fun onSetArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            val result = if (archived) archiveTask(id) else unarchiveTask(id)
            when (result) {
                is AppResult.Success -> {
                    val words = tasksWording(language)
                    local.update {
                        it.copy(
                            notice = TasksNotice(
                                message = if (archived) words.taskArchived else words.taskUnarchived,
                                tone = NoticeTone.CONFIRM,
                                undoLabel = words.undo,
                            ),
                            undo = UndoAction.Archived(listOf(id), restoreTo = !archived),
                        )
                    }
                }
                is AppResult.Failure -> showProblem(result.error.code, language)
            }
        }
    }

    fun onDuplicateTask(id: Long) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            when (val duplicated = duplicateTask(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        notice = TasksNotice(
                            message = tasksWording(language).taskDuplicated,
                            tone = NoticeTone.CONFIRM,
                        ),
                        undo = null,
                    )
                }
                is AppResult.Failure -> showProblem(duplicated.error.code, language)
            }
        }
    }

    /** Soft, so the undo puts back the same row rather than a copy wearing the old title. */
    fun onDeleteTask(id: Long) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            when (val removed = deleteTask(id)) {
                is AppResult.Success -> {
                    val words = tasksWording(language)
                    local.update {
                        it.copy(
                            notice = TasksNotice(
                                message = words.taskDeleted,
                                tone = NoticeTone.CONFIRM,
                                undoLabel = words.undo,
                            ),
                            undo = UndoAction.Deleted(listOf(id)),
                        )
                    }
                }
                is AppResult.Failure -> showProblem(removed.error.code, language)
            }
        }
    }

    /** Puts back exactly what the last action changed, and nothing it did not. */
    fun onUndo() {
        val undo = local.value.undo ?: return
        viewModelScope.launch {
            val language = language()
            val outcome = applyToEach(undo.ids) { id ->
                when (undo) {
                    is UndoAction.Deleted -> restoreTask(id)
                    is UndoAction.Archived -> if (undo.restoreTo) archiveTask(id) else unarchiveTask(id)
                    is UndoAction.Completed -> setTaskDone(id, false)
                }
            }
            val words = tasksWording(language)
            local.update {
                it.copy(
                    undo = null,
                    notice = TasksNotice(
                        message = outcome.error
                            ?.let { code -> renderer.display(MiraResponse.Problem(code), language) }
                            ?: words.undone,
                        tone = if (outcome.error != null) NoticeTone.PROBLEM else NoticeTone.CONFIRM,
                    ),
                )
            }
        }
    }

    // --- Bulk selection --------------------------------------------------------------------

    fun onStartSelection(id: Long) = local.update {
        it.copy(isSelecting = true, selected = it.selected + id)
    }

    fun onToggleSelected(id: Long) = local.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun onSelectAllVisible() {
        val visible = uiState.value.visibleTasks.map { it.id }.toSet()
        local.update { it.copy(isSelecting = true, selected = visible) }
    }

    fun onClearSelection() = local.update { it.copy(selected = emptySet()) }

    /** Leaving selection performs nothing: it is a way out, not a decision. */
    fun onLeaveSelection() = local.update { it.copy(isSelecting = false, selected = emptySet()) }

    fun onBulkComplete() = bulk(
        action = { id -> setTaskDone(id, true) },
        message = { words, count -> words.tasksCompleted(count) },
        undo = { ids -> UndoAction.Completed(ids) },
    )

    fun onBulkArchive() = bulk(
        action = { id -> archiveTask(id) },
        message = { words, count -> words.tasksArchived(count) },
        undo = { ids -> UndoAction.Archived(ids, restoreTo = false) },
    )

    fun onBulkDelete() = bulk(
        action = { id -> deleteTask(id) },
        message = { words, count -> words.tasksDeleted(count) },
        undo = { ids -> UndoAction.Deleted(ids) },
    )

    // --- Reminders and the composer --------------------------------------------------------

    /**
     * Asks the question rather than answering it.
     *
     * A reminder cannot be brought back: [CancelReminderUseCase] disarms the alarm and removes
     * the row, and there is no restore behind it the way [restoreTask] sits behind a deleted
     * task. So this screen cannot offer the undo notice it offers everywhere else, and the
     * second tap has to come before the act instead of after it.
     */
    fun onCancelReminderRequested(id: Long) = local.update { it.copy(cancelReminderId = id) }

    /** Leaving the question performs nothing: it is a way out, not a decision. */
    fun onDismissCancelReminder() = local.update { it.copy(cancelReminderId = null) }

    /** The answer to [onCancelReminderRequested], and the only thing that actually cancels. */
    fun onCancelReminder(id: Long) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(cancelReminderId = null) }
            when (val cancelled = cancelReminder(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        notice = TasksNotice(
                            message = tasksWording(language).reminderCancelled,
                            tone = NoticeTone.CONFIRM,
                        ),
                        undo = null,
                    )
                }
                is AppResult.Failure -> showProblem(cancelled.error.code, language)
            }
        }
    }

    /**
     * Calls off a snooze, putting the reminder back on the moment Abu originally set.
     *
     * Offered only for a reminder that is snoozed, so the use case's own "nothing to do" branch is
     * a guard rather than a path this screen relies on.
     */
    fun onUnsnoozeReminder(id: Long) {
        viewModelScope.launch {
            val language = language()
            when (val restored = unsnoozeReminder(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        notice = TasksNotice(
                            message = remindersWording(language).snoozeCalledOff,
                            tone = NoticeTone.CONFIRM,
                        ),
                        undo = null,
                    )
                }
                is AppResult.Failure -> showProblem(restored.error.code, language)
            }
        }
    }

    /**
     * Puts the place watches back.
     *
     * A reboot drops every proximity alert the app held, and so does the permission being taken
     * away, so this is the ordinary way back rather than a repair for a fault. One refusal is the
     * whole answer: the platform refuses every place for the same reason, so Mira stops at the
     * first and says what it was instead of repeating it once per place.
     */
    fun onWatchPlacesAgain() {
        viewModelScope.launch {
            val language = language()
            val words = remindersWording(language)
            when (val pending = places.pending()) {
                is AppResult.Failure -> showProblem(pending.error.code, language)
                is AppResult.Success -> rewatch(pending.value.filterNot { it.isRegistered }, words, language)
            }
        }
    }

    fun onDismissPlaceNotice() = local.update {
        it.copy(placeDismissed = true, placeOverride = null)
    }

    private suspend fun rewatch(
        unwatched: List<ReminderPlace>,
        words: RemindersWording,
        language: SpokenLanguage,
    ) {
        for (place in unwatched) {
            val watched = watchPlace(
                reminderId = place.reminderId,
                label = place.label,
                latitude = place.latitude,
                longitude = place.longitude,
                radiusMeters = place.radiusMeters,
                trigger = place.trigger,
            )
            when (watched) {
                is AppResult.Failure -> {
                    showProblem(watched.error.code, language)
                    return
                }
                is AppResult.Success -> when (val outcome = watched.value.outcome) {
                    PlaceWatchResult.Watching -> Unit
                    is PlaceWatchResult.Refused -> {
                        local.update {
                            it.copy(placeDismissed = false, placeOverride = outcome.reason.toNotice(words))
                        }
                        return
                    }
                }
            }
        }
        local.update {
            it.copy(
                placeDismissed = false,
                placeOverride = null,
                notice = TasksNotice(message = words.watchingAgain, tone = NoticeTone.CONFIRM),
                undo = null,
            )
        }
    }

    fun onOpenReminderComposer() = local.update { it.copy(showReminderComposer = true) }

    fun onCloseReminderComposer() = local.update { it.copy(showReminderComposer = false) }

    fun onSaveReminder(title: String, date: LocalDate, time: LocalTime) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val triggerAt = date.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant()
        viewModelScope.launch {
            val language = language()
            when (val res = createReminder(clean, triggerAt)) {
                is AppResult.Success -> {
                    local.update {
                        it.copy(
                            showReminderComposer = false,
                            notice = TasksNotice(
                                message = renderer.display(MiraResponse.ReminderCreated(res.value.title, res.value.triggerAt), language),
                                tone = NoticeTone.CONFIRM,
                            ),
                            undo = null,
                        )
                    }
                }
                is AppResult.Failure -> {
                    showProblem(res.error.code, language)
                }
            }
        }
    }

    fun onOpenComposer() = local.update { it.copy(composer = ComposerDraft()) }

    fun onCloseComposer() = local.update { it.copy(composer = null) }

    fun onComposerTitleChange(title: String) = updateComposer { it.copy(title = title) }

    fun onComposerDateChange(date: LocalDate?) = updateComposer {
        // Clearing the date clears the time with it: a time with no day cannot become a due moment.
        if (date == null) it.copy(dueDate = null, dueTime = null) else it.copy(dueDate = date)
    }

    fun onComposerTimeChange(time: LocalTime?) = updateComposer { it.copy(dueTime = time) }

    fun onComposerPriorityChange(priority: TaskPriority) = updateComposer { it.copy(priority = priority) }

    fun onSaveTask() {
        val draft = local.value.composer ?: return
        if (draft.title.isBlank() || draft.isSaving) return
        updateComposer { it.copy(isSaving = true) }
        viewModelScope.launch {
            val language = language()
            val created = createTask(
                title = draft.title,
                dueAt = draft.dueAt(),
                dueAllDay = draft.dueDate != null && draft.dueTime == null,
                priority = draft.priority,
            )
            when (created) {
                is AppResult.Success -> local.update {
                    it.copy(
                        composer = null,
                        undo = null,
                        notice = TasksNotice(
                            message = renderer.speak(
                                MiraResponse.TaskCreated(created.value.title, created.value.dueAt),
                                language,
                            ),
                            tone = NoticeTone.CONFIRM,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    updateComposer { it.copy(isSaving = false) }
                    showProblem(created.error.code, language)
                }
            }
        }
    }

    // --- Plumbing --------------------------------------------------------------------------

    private fun bulk(
        action: suspend (Long) -> AppResult<*>,
        message: (TasksWording, Int) -> String,
        undo: (List<Long>) -> UndoAction,
    ) {
        val ids = local.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val language = language()
            val outcome = applyToEach(ids, action)
            val words = tasksWording(language)
            local.update {
                it.copy(
                    isSelecting = false,
                    selected = emptySet(),
                    // A failure part way through is said plainly and what did land is still
                    // undoable: hiding either half would leave Abu guessing at the state.
                    notice = TasksNotice(
                        message = outcome.error
                            ?.let { code -> renderer.display(MiraResponse.Problem(code), language) }
                            ?: message(words, outcome.changed.size),
                        tone = if (outcome.error != null) NoticeTone.PROBLEM else NoticeTone.CONFIRM,
                        undoLabel = words.undo.takeIf { outcome.changed.isNotEmpty() },
                    ),
                    undo = undo(outcome.changed).takeIf { outcome.changed.isNotEmpty() },
                )
            }
        }
    }

    private suspend fun applyToEach(
        ids: Collection<Long>,
        action: suspend (Long) -> AppResult<*>,
    ): BulkOutcome {
        val changed = mutableListOf<Long>()
        var error: MiraErrorCode? = null
        for (id in ids) {
            when (val result = action(id)) {
                is AppResult.Success -> changed += id
                is AppResult.Failure -> if (error == null) error = result.error.code
            }
        }
        return BulkOutcome(changed, error)
    }

    /** The moment the sheet's date and time add up to, or null when Abu left the date empty. */
    private fun ComposerDraft.dueAt(): Instant? {
        val date = dueDate ?: return null
        return times.toInstant(date, dueTime ?: DEFAULT_DUE_TIME)
    }

    private fun updateComposer(transform: (ComposerDraft) -> ComposerDraft) = local.update { state ->
        state.composer?.let { state.copy(composer = transform(it)) } ?: state
    }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()

    private fun showProblem(code: MiraErrorCode, language: SpokenLanguage) = local.update {
        it.copy(
            notice = TasksNotice(
                message = renderer.display(MiraResponse.Problem(code), language),
                tone = NoticeTone.PROBLEM,
            ),
            undo = null,
        )
    }

    private fun ungrouped(rows: List<Task>): AppResult<List<TaskGroup>> =
        AppResult.Success(listOf(TaskGroup(id = null, name = null, tasks = rows)))

    private fun buildState(
        state: LocalState,
        groups: AppResult<List<TaskGroup>>,
        feed: RemindersFeed,
        language: SpokenLanguage,
        available: Facets,
    ): TasksUiState {
        val words = tasksWording(language)
        val sections = when (groups) {
            is AppResult.Success -> groups.value.toSections(state, language, words, available)
            is AppResult.Failure -> emptyList()
        }
        val visible = sections.flatMap { section -> section.items.map { it.id } }.toSet()
        val reminderRows = feed.reminders.map { it.toRow(language, feed.places[it.id]) }
        return TasksUiState(
            isLoading = false,
            language = language,
            tab = state.tab,
            view = state.view,
            query = state.query,
            sections = sections,
            filters = state.toFilters(words, available),
            sort = TaskSortUiState(sort = state.sort, ascending = state.ascending),
            selection = TaskSelectionUiState(
                isActive = state.isSelecting,
                ids = state.selected,
                allSelected = visible.isNotEmpty() && state.selected.containsAll(visible),
            ),
            reminders = reminderRows,
            placeNotice = state.placeOverride ?: feed.unwatchedNotice(language)
                .takeUnless { state.placeDismissed },
            composer = state.composer?.toUiState(language),
            showReminderComposer = state.showReminderComposer,
            actions = state.actions,
            // Resolved against the rows rather than remembered as a title, so a reminder that
            // fires or is cancelled elsewhere takes its own question down with it instead of
            // leaving Abu agreeing to something that is already gone.
            confirmCancelReminder = state.cancelReminderId
                ?.let { id -> reminderRows.firstOrNull { it.id == id } }
                ?.let { ReminderCancelRequest(id = it.id, title = it.title) },
            notice = state.notice,
            errorMessage = (groups as? AppResult.Failure)
                ?.let { renderer.display(MiraResponse.Problem(it.error.code), language) },
        )
    }

    /**
     * Keeps the group of tasks that belong to nothing, and puts it last.
     *
     * A task with no project is data Abu cannot find any other way round, so dropping the unnamed
     * group would quietly hide it. Empty groups are dropped instead — a heading over nothing is
     * noise, not information.
     */
    private fun List<TaskGroup>.toSections(
        state: LocalState,
        language: SpokenLanguage,
        words: TasksWording,
        available: Facets,
    ): List<TaskSection> = filter { it.tasks.isNotEmpty() }
        .sortedBy { it.id == null }
        .map { group ->
            TaskSection(
                key = "${state.view.name}-${group.id ?: 0L}",
                title = when (state.view.grouping) {
                    TaskGrouping.NONE -> null
                    TaskGrouping.PROJECT -> group.name ?: words.noProjectSection
                    TaskGrouping.CLIENT -> group.name ?: words.noClientSection
                },
                items = group.tasks.map { it.toItem(state, language, words, available) },
            )
        }

    private fun Task.toItem(
        state: LocalState,
        language: SpokenLanguage,
        words: TasksWording,
        available: Facets,
    ): TaskListItem {
        val overdue = !isDone && times.isOverdue(dueAt)
        val details = mutableListOf<String>().apply {
            dueAt?.let { add(times.dueLabel(it, language, overdue)) }
            // The heading already says whose it is, so a grouped row does not repeat it.
            if (state.view.grouping != TaskGrouping.PROJECT) {
                projectId?.let { id -> available.projectName(id)?.let { add(it) } }
            }
            if (state.view.grouping != TaskGrouping.CLIENT) {
                clientId?.let { id -> available.clientName(id)?.let { add(it) } }
            }
            if (subtasks.isNotEmpty()) {
                add(words.stepsProgress(subtasks.count { it.isDone }, subtasks.size))
            }
            if (isRecurring) add(words.repeats)
        }
        return TaskListItem(
            id = id,
            title = title,
            supporting = details.joinToString(DETAIL_SEPARATOR).takeIf { it.isNotEmpty() },
            isOverdue = overdue,
            isDone = isDone,
            isArchived = archivedAt != null,
            isHighPriority = priority == TaskPriority.HIGH,
            isSelected = id in state.selected,
        )
    }

    private fun LocalState.toFilters(words: TasksWording, available: Facets): TaskFiltersUiState =
        TaskFiltersUiState(
            projectId = projectId,
            clientId = clientId,
            tagIds = tagIds,
            priorities = priorities,
            projects = available.projects.map { TaskFacet(id = it.id, label = it.name) },
            clients = available.clients.map { TaskFacet(id = it.id, label = it.name) },
            tags = available.tags.sortedByDescending { it.taskCount }
                .map { TaskFacet(id = it.tag.id, label = it.tag.name) },
            appliedLabels = mutableListOf<String>().apply {
                projectId?.let { id -> available.projectName(id)?.let { add(it) } }
                clientId?.let { id -> available.clientName(id)?.let { add(it) } }
                available.tags.filter { it.tag.id in tagIds }.forEach { add(it.tag.name) }
                TaskPriority.entries.filter { it in priorities }
                    .forEach { add(words.priorityOptions[it.ordinal]) }
            },
        )

    /**
     * One reminder, with the place it waits at when it has one.
     *
     * The three ways of saying when are alternatives rather than a stack: a snooze is where the
     * reminder actually is now, a place reminder has no moment at all, and only what is left is a
     * plain time. Showing two of them would answer the same question twice, differently.
     */
    private fun Reminder.toRow(language: SpokenLanguage, place: ReminderPlace?): ReminderRow {
        val words = remindersWording(language)
        val whenLabel = when {
            snoozedUntil != null -> words.snoozedUntil(times.momentLabel(snoozedUntil, language))
            waitsForPlace && place != null -> {
                val waiting = words.waitingAt(place.label)
                if (place.isRegistered) waiting else "$waiting — ${words.notBeingWatched}"
            }
            else -> words.at(times.momentLabel(armedAt, language))
        }
        return ReminderRow(
            id = id,
            title = title,
            kind = kind,
            kindLabel = words.kindLabel(kind),
            whenLabel = whenLabel,
            isSnoozed = isSnoozed,
            repeats = repeats,
        )
    }

    private fun ComposerDraft.toUiState(language: SpokenLanguage): TaskComposerUiState = TaskComposerUiState(
        title = title,
        dueDate = dueDate,
        dueTime = dueTime,
        dateLabel = dueDate?.let { times.dateLabel(it, language) },
        timeLabel = dueTime?.let { times.timeLabel(it, language) },
        priority = priority,
        isSaving = isSaving,
        pickerDate = dueDate ?: clock.today(),
        pickerTime = dueTime ?: DEFAULT_DUE_TIME,
    )

    /** Everything the screen owns rather than the database: which view, what was typed, what is pending. */
    private data class LocalState(
        val tab: TasksTab = TasksTab.TASKS,
        val view: TasksView = TasksView.TODAY,
        val query: String = "",
        val projectId: Long? = null,
        val clientId: Long? = null,
        val tagIds: Set<Long> = emptySet(),
        val priorities: Set<TaskPriority> = emptySet(),
        val sort: TaskSort = TaskSort.DUE_DATE,
        val ascending: Boolean = true,
        val isSelecting: Boolean = false,
        val selected: Set<Long> = emptySet(),
        val composer: ComposerDraft? = null,
        val showReminderComposer: Boolean = false,
        val actions: TaskActionsUiState? = null,
        /** The reminder whose cancel question is up, held as an id so the row stays the source. */
        val cancelReminderId: Long? = null,
        val undo: UndoAction? = null,
        val notice: TasksNotice? = null,
        /** A refusal Mira has just been given, which replaces the standing count. */
        val placeOverride: RemindersPlaceNotice? = null,
        /** Abu waved the standing notice away. Trying again brings it back. */
        val placeDismissed: Boolean = false,
    ) {
        fun toQuery(): TaskQuery = TaskQuery(
            filter = view.filter,
            sort = sort,
            ascending = ascending,
            text = query.trim().takeIf { it.isNotEmpty() },
            projectId = projectId,
            clientId = clientId,
            tagIds = tagIds,
            priorities = priorities,
        )
    }

    /**
     * The reminders and the places they wait at, keyed by the reminder each place belongs to.
     *
     * `reminder_places.reminderId` is unique, so the map is a faithful index rather than a lossy
     * one: a reminder holds at most one place.
     */
    private data class RemindersFeed(
        val reminders: List<Reminder>,
        val places: Map<Long, ReminderPlace>,
    ) {

        /**
         * The standing line about places with nothing watching for them, or null when every one
         * of them is covered. Derived rather than remembered, so it disappears by itself the
         * moment the watches are back.
         */
        fun unwatchedNotice(language: SpokenLanguage): RemindersPlaceNotice? {
            val scheduled = reminders.filter { it.waitsForPlace }.mapTo(mutableSetOf()) { it.id }
            val unwatched = places.values.count { it.reminderId in scheduled && !it.isRegistered }
            if (unwatched == 0) return null
            val words = remindersWording(language)
            return RemindersPlaceNotice(
                message = words.placesNotWatched(unwatched),
                actionText = words.watchAgain,
            )
        }
    }

    private data class ComposerDraft(
        val title: String = "",
        val dueDate: LocalDate? = null,
        val dueTime: LocalTime? = null,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val isSaving: Boolean = false,
    )

    private data class Listing(val query: TaskQuery, val grouping: TaskGrouping)

    /** The names the rows and the filter sheet need, which the task rows carry only as ids. */
    private data class Facets(
        val projects: List<Project>,
        val clients: List<Client>,
        val tags: List<TagUsage>,
    ) {
        fun projectName(id: Long): String? = projects.firstOrNull { it.id == id }?.name

        fun clientName(id: Long): String? = clients.firstOrNull { it.id == id }?.name
    }

    /** What the last action changed, so the undo puts back that and only that. */
    private sealed interface UndoAction {
        val ids: List<Long>

        data class Deleted(override val ids: List<Long>) : UndoAction

        /** [restoreTo] is the archived state to put back, which is the opposite of what was done. */
        data class Archived(override val ids: List<Long>, val restoreTo: Boolean) : UndoAction

        data class Completed(override val ids: List<Long>) : UndoAction
    }

    private data class BulkOutcome(val changed: List<Long>, val error: MiraErrorCode?)

    private companion object {
        /**
         * A task given only a date still needs a moment. Nine in the morning is the start of Abu's
         * day, so "due Thursday" lands at the top of Thursday rather than at midnight, where it
         * would read as overdue all day.
         */
        val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)

        /** Language-neutral, so the supporting line reads the same in Bengali and in English. */
        const val DETAIL_SEPARATOR = " · "

        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
