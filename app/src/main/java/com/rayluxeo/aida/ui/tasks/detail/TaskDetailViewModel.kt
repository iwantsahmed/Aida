package com.rayluxeo.aida.ui.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskStatus
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.task.RecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.domain.usecase.ArchiveTaskUseCase
import com.rayluxeo.aida.domain.usecase.CancelReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.DeleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.EditTaskUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.domain.usecase.UnarchiveTaskUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The editor behind one task: every field the brief names, in Abu's language.
 *
 * Three rules shape the whole class.
 *
 * **The task's own fields are a draft until Abu saves.** Typing a title, moving a date, choosing a
 * repeat — none of it touches the database. That is what makes leaving without saving mean
 * something, and it is why the screen asks before discarding. Its children do not work that way:
 * a step and an attachment belong to the task rather than to the form, they have their own rows
 * and their own ports, and ticking a step writes it immediately.
 *
 * **Status is derived and so it is never edited.** [TaskStatus] reads deleted, archived and done
 * in that order; a dropdown that also set `archivedAt` would be a second way to say one thing.
 * The screen offers the verbs — complete, reopen, archive, delete — and the status follows. Each
 * verb acts on the *stored* task, so it cannot smuggle an unsaved edit in with it.
 *
 * **A reminder is an offset, not a time.** It is held as minutes before the due moment, so moving
 * a task to Friday moves its reminder with it rather than leaving an alarm pointing at a day that
 * no longer means anything. A reminder Mira set by voice at some other offset keeps that offset
 * and is read back as the moment it will actually fire.
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val subtasks: SubtaskRepository,
    private val tags: TagRepository,
    private val files: FileRepository,
    projects: ProjectRepository,
    clients: ClientRepository,
    private val reminders: ReminderRepository,
    private val createTask: CreateTaskUseCase,
    private val editTask: EditTaskUseCase,
    private val setTaskDone: SetTaskDoneUseCase,
    private val archiveTask: ArchiveTaskUseCase,
    private val unarchiveTask: UnarchiveTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val createReminder: CreateReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
    private val recurrences: RecurrenceCalculator,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
    private val renderer: ResponseRenderer,
) : ViewModel() {

    private val taskId: Long = savedState.get<Long>(ARG_TASK_ID) ?: NEW_TASK_ID

    private val isNewTask: Boolean = taskId == NEW_TASK_ID

    private val local = MutableStateFlow(LocalState(isLoading = !isNewTask))

    /**
     * The task's steps, live from their own table.
     *
     * Keyed on the *stored* id rather than on the route's, because a task saved for the first time
     * on this screen gets its id here: a flow pinned to the id the screen opened with would watch
     * task zero for the rest of the session and show none of the steps it then wrote.
     *
     * Started eagerly rather than while-subscribed because the handlers read `.value` to turn the
     * row Abu tapped into the id the port wants, and a cold flow would hand them an empty list in
     * the gap between the screen leaving and coming back.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val steps: StateFlow<List<Subtask>> = local
        .map { it.stored?.id ?: taskId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == NEW_TASK_ID) flowOf(emptyList()) else subtasks.observeForTask(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val catalogue: Flow<Catalogue> = combine(
        projects.observe(ProjectFilter.ALL),
        clients.observe(ClientFilter.ALL),
        tags.observeAll(),
        files.observeAll(),
        steps,
    ) { projectList, clientList, tagList, fileList, stepList ->
        Catalogue(projectList, clientList, tagList, fileList, stepList)
    }

    val uiState: StateFlow<TaskDetailUiState> = combine(
        local,
        catalogue,
        settings.settings,
    ) { state, reference, preferences ->
        buildState(state, reference, preferences.languageMode.displayLanguage())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = TaskDetailUiState(
            isLoading = !isNewTask,
            isNew = isNewTask,
            pickerDate = clock.today(),
            pickerTime = DEFAULT_DUE_TIME,
        ),
    )

    init {
        if (!isNewTask) load(taskId)
    }

    // The task's own fields. Each one edits the draft and nothing else.

    fun onTitleChange(title: String) = edit { it.copy(title = title) }

    fun onDescriptionChange(description: String) = edit { it.copy(description = description) }

    fun onNotesChange(notes: String) = edit { it.copy(notes = notes) }

    fun onPriorityChange(priority: TaskPriority) = edit { it.copy(priority = priority) }

    /** Clearing the date clears the time and the reminder with it: neither is a moment on its own. */
    fun onDueDateChange(date: LocalDate?) = edit { draft ->
        if (date == null) draft.copy(dueDate = null, dueTime = null, reminderMinutes = null)
        else draft.copy(dueDate = date)
    }

    /** A date with no time is an all-day task, which is exactly what `Task.dueAllDay` records. */
    fun onDueTimeChange(time: LocalTime?) = edit { it.copy(dueTime = time) }

    fun onReminderChange(lead: ReminderLead?) = edit { it.copy(reminderMinutes = lead?.minutesBefore) }

    fun onRecurrenceChange(rule: RecurrenceRule?) = edit { it.copy(recurrence = rule) }

    fun onProjectChange(projectId: Long?) = edit { it.copy(projectId = projectId) }

    fun onClientChange(clientId: Long?) = edit { it.copy(clientId = clientId) }

    fun onEstimateChange(minutes: Int?) = edit { it.copy(estimatedMinutes = minutes) }

    fun onAddTag(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        edit { draft ->
            if (draft.tags.any { it.equals(clean, ignoreCase = true) }) draft
            else draft.copy(tags = draft.tags + clean)
        }
    }

    fun onRemoveTag(name: String) = edit { draft ->
        draft.copy(tags = draft.tags.filterNot { it.equals(name, ignoreCase = true) })
    }

    // The steps. A saved task's steps are their own rows and are written as they are touched;
    // an unsaved task has nowhere to put them yet, so they wait in the draft for the create.

    fun onAddSubtask(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val stored = local.value.stored
        if (stored == null) {
            edit { it.copy(newSteps = it.newSteps + clean) }
            return
        }
        viewModelScope.launch { subtasks.add(stored.id, clean).reportFailure() }
    }

    fun onToggleSubtask(index: Int) {
        val stored = local.value.stored
        if (stored == null) return
        val step = steps.value.getOrNull(index) ?: return
        viewModelScope.launch { subtasks.setDone(step.id, !step.isDone).reportFailure() }
    }

    fun onMoveSubtask(index: Int, up: Boolean) {
        val target = if (up) index - 1 else index + 1
        if (local.value.stored == null) {
            edit { draft ->
                if (index !in draft.newSteps.indices || target !in draft.newSteps.indices) draft
                else draft.copy(newSteps = draft.newSteps.swapped(index, target))
            }
            return
        }
        val stored = local.value.stored ?: return
        val current = steps.value
        if (index !in current.indices || target !in current.indices) return
        val ordered = current.map { it.id }.swapped(index, target)
        viewModelScope.launch { subtasks.reorder(stored.id, ordered).reportFailure() }
    }

    fun onRemoveSubtask(index: Int) {
        if (local.value.stored == null) {
            edit { draft ->
                if (index !in draft.newSteps.indices) draft
                else draft.copy(newSteps = draft.newSteps.filterIndexed { at, _ -> at != index })
            }
            return
        }
        val step = steps.value.getOrNull(index) ?: return
        viewModelScope.launch { subtasks.delete(step.id).reportFailure() }
    }

    /** Unlinking leaves the file itself alone: only the link between it and this task goes. */
    fun onUnlinkAttachment(fileId: Long) {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            when (val result = tasks.detachFile(stored.id, fileId)) {
                is AppResult.Success -> local.update { it.copy(stored = result.value) }
                is AppResult.Failure -> showProblem(result.error.code, language())
            }
        }
    }

    // Revealing and hiding the optional fields.

    fun onRevealField(field: TaskDetailField) = local.update { it.copy(revealed = it.revealed + field) }

    /** Hiding a field empties it: a value Abu cannot see is not one he can have meant to keep. */
    fun onClearField(field: TaskDetailField) {
        local.update { it.copy(revealed = it.revealed - field) }
        when (field) {
            TaskDetailField.DESCRIPTION -> edit { it.copy(description = "") }
            TaskDetailField.NOTES -> edit { it.copy(notes = "") }
            TaskDetailField.DUE -> onDueDateChange(null)
            TaskDetailField.REMINDER -> edit { it.copy(reminderMinutes = null) }
            TaskDetailField.RECURRENCE -> edit { it.copy(recurrence = null) }
            TaskDetailField.PROJECT -> edit { it.copy(projectId = null) }
            TaskDetailField.CLIENT -> edit { it.copy(clientId = null) }
            TaskDetailField.TAGS -> edit { it.copy(tags = emptyList()) }
            TaskDetailField.ESTIMATE -> edit { it.copy(estimatedMinutes = null) }
            // Only the steps of a task that has none yet. A saved task's steps are rows of their
            // own and are removed one at a time, with the control that sits on each of them.
            TaskDetailField.SUBTASKS -> edit { it.copy(newSteps = emptyList()) }
        }
    }

    // Modals, notices and leaving.

    fun onOpenSheet(sheet: TaskDetailSheet) = local.update { it.copy(sheet = sheet) }

    fun onCloseSheet() = local.update { it.copy(sheet = TaskDetailSheet.NONE) }

    fun onDismissNotice() = local.update { it.copy(notice = null) }

    fun onBackRequested() = local.update {
        if (it.isDirty) it.copy(confirmDiscard = true) else it.copy(isFinished = true)
    }

    fun onKeepEditing() = local.update { it.copy(confirmDiscard = false) }

    fun onConfirmDiscard() = local.update { it.copy(confirmDiscard = false, isFinished = true) }

    fun onDeleteRequested() = local.update { it.copy(sheet = TaskDetailSheet.NONE, confirmDelete = true) }

    fun onCancelDelete() = local.update { it.copy(confirmDelete = false) }

    /**
     * Writes the whole form through the task use cases, so a task typed here passes exactly the
     * validation a task Mira creates from "আগামী শুক্রবার website complete করার task add করো"
     * passes. Nothing on this screen writes a task through a repository.
     *
     * The saved task is read back into the draft rather than the draft being kept, because the
     * domain may have settled something Abu left open — a repeat with no date is given the first
     * date the rule lands on — and the screen has to show what was actually stored.
     */
    fun onSave() {
        val state = local.value
        val draft = state.draft
        if (draft.title.isBlank() || state.isSaving) return
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val language = language()
            val labels = when (val resolved = resolveTags(draft.tags)) {
                is AppResult.Failure -> {
                    local.update { it.copy(isSaving = false) }
                    showProblem(resolved.error.code, language)
                    return@launch
                }
                is AppResult.Success -> resolved.value
            }
            val saved = when (val stored = state.stored) {
                null -> createTask(
                    title = draft.title,
                    description = draft.description,
                    notes = draft.notes,
                    dueAt = draft.dueAt(),
                    dueAllDay = draft.isAllDay(),
                    estimatedMinutes = draft.estimatedMinutes,
                    priority = draft.priority,
                    projectId = draft.projectId,
                    clientId = draft.clientId,
                    tagIds = labels.map(Tag::id),
                    subtaskTitles = draft.newSteps,
                    recurrence = draft.recurrence,
                    origin = RecordOrigin.MANUAL,
                )
                else -> editTask(draft.applyTo(stored, labels))
            }
            when (saved) {
                is AppResult.Success -> finishSave(saved.value, state, language)
                is AppResult.Failure -> {
                    local.update { it.copy(isSaving = false) }
                    showProblem(saved.error.code, language)
                }
            }
        }
    }

    /** Completing a task disarms its reminder: finished work does not interrupt Abu. */
    fun onSetDone(done: Boolean) {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            when (val result = setTaskDone(stored.id, done)) {
                is AppResult.Success -> {
                    if (done) disarmReminder()
                    applyStored(result.value)
                    announce(
                        if (done) taskDetailWording(language).completedNotice
                        else taskDetailWording(language).reopenedNotice,
                    )
                }
                is AppResult.Failure -> showProblem(result.error.code, language)
            }
        }
    }

    fun onSetArchived(archived: Boolean) {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            val result = if (archived) archiveTask(stored.id) else unarchiveTask(stored.id)
            when (result) {
                is AppResult.Success -> {
                    if (archived) disarmReminder()
                    applyStored(result.value)
                    announce(
                        if (archived) taskDetailWording(language).archivedNotice
                        else taskDetailWording(language).unarchivedNotice,
                    )
                }
                is AppResult.Failure -> showProblem(result.error.code, language)
            }
        }
    }

    fun onConfirmDelete() {
        val stored = local.value.stored
        if (stored == null) {
            local.update { it.copy(confirmDelete = false, isFinished = true) }
            return
        }
        viewModelScope.launch {
            val language = language()
            when (val result = deleteTask(stored.id)) {
                is AppResult.Success -> {
                    disarmReminder()
                    local.update { it.copy(confirmDelete = false, isFinished = true) }
                }
                is AppResult.Failure -> {
                    local.update { it.copy(confirmDelete = false) }
                    showProblem(result.error.code, language)
                }
            }
        }
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val language = language()
            when (val found = tasks.byId(id)) {
                is AppResult.Failure -> local.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = renderer.display(MiraResponse.Problem(found.error.code), language),
                    )
                }
                is AppResult.Success -> {
                    val task = found.value
                    val reminder = linkedReminder(task.id)
                    val draft = draftOf(task, reminder)
                    local.update {
                        it.copy(
                            isLoading = false,
                            stored = task,
                            draft = draft,
                            original = draft,
                            linkedReminder = reminder,
                        )
                    }
                }
            }
        }
    }

    private suspend fun finishSave(task: Task, before: LocalState, language: SpokenLanguage) {
        val outcome = syncReminder(task, before)
        val draft = draftOf(task, outcome.reminder)
        val words = taskDetailWording(language)
        local.update {
            it.copy(
                stored = task,
                draft = draft,
                original = draft,
                linkedReminder = outcome.reminder,
                isSaving = false,
                notice = if (outcome.missedItsMoment) {
                    TaskDetailNotice(words.reminderInThePast, MiraNoticeTone.INFO)
                } else {
                    TaskDetailNotice(words.savedNotice, MiraNoticeTone.CONFIRM)
                },
            )
        }
    }

    /**
     * Turns the names on the form into tags that exist.
     *
     * [EditTaskUseCase] refuses a tag with no id, so the label has to be real before the task can
     * wear it. `ensure` is the port's own word for "this one, creating it only if it is new", and
     * it matches case-insensitively so "Urgent" does not become a second row beside "urgent".
     */
    private suspend fun resolveTags(names: List<String>): AppResult<List<Tag>> {
        val resolved = mutableListOf<Tag>()
        for (name in names) {
            when (val tag = tags.ensure(name)) {
                is AppResult.Failure -> return tag
                is AppResult.Success -> resolved += tag.value
            }
        }
        return AppResult.Success(resolved)
    }

    /**
     * Re-arms the task's reminder when anything it hangs off has moved.
     *
     * The offset is what is kept, not the moment, so a reminder set an hour before stays an hour
     * before. A moment that has already gone is reported rather than scheduled: an alarm in the
     * past never fires, and saying the task saved without saying that would be a small lie.
     */
    private suspend fun syncReminder(task: Task, before: LocalState): ReminderOutcome {
        val draft = before.draft
        val original = before.original
        val existing = before.linkedReminder
        val untouched = draft.reminderMinutes == original.reminderMinutes &&
            draft.dueAt() == original.dueAt() &&
            draft.title.trim() == original.title.trim()
        if (untouched && before.stored != null) return ReminderOutcome(existing, missedItsMoment = false)

        if (existing != null) cancelReminder(existing.id)

        val minutes = draft.reminderMinutes
        val dueAt = task.dueAt
        if (minutes == null || dueAt == null) return ReminderOutcome(null, missedItsMoment = false)

        val triggerAt = dueAt.minus(minutes, ChronoUnit.MINUTES)
        if (!triggerAt.isAfter(clock.now())) return ReminderOutcome(null, missedItsMoment = true)

        val armed = createReminder(
            title = task.title,
            triggerAt = triggerAt,
            origin = RecordOrigin.MANUAL,
            // The link lives on the reminder rather than on the task: one relationship with two
            // homes is a second source of truth, and this is the home that can be scheduled.
            linkedTaskId = task.id,
        )
        return when (armed) {
            is AppResult.Success -> ReminderOutcome(armed.value, missedItsMoment = false)
            is AppResult.Failure -> ReminderOutcome(null, missedItsMoment = true)
        }
    }

    private suspend fun disarmReminder() {
        val existing = local.value.linkedReminder ?: return
        cancelReminder(existing.id)
        local.update { it.copy(linkedReminder = null) }
    }

    private suspend fun linkedReminder(id: Long): Reminder? =
        reminders.observeUpcoming().first().firstOrNull { it.linkedTaskId == id }

    private fun applyStored(task: Task) = local.update { it.copy(stored = task) }

    private fun announce(message: String) = local.update {
        it.copy(sheet = TaskDetailSheet.NONE, notice = TaskDetailNotice(message, MiraNoticeTone.CONFIRM))
    }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()

    private suspend fun AppResult<*>.reportFailure() {
        if (this is AppResult.Failure) showProblem(error.code, language())
    }

    private fun showProblem(code: MiraErrorCode, language: SpokenLanguage) = local.update {
        it.copy(
            notice = TaskDetailNotice(
                message = renderer.display(MiraResponse.Problem(code), language),
                tone = MiraNoticeTone.DANGER,
            ),
        )
    }

    private fun edit(transform: (Draft) -> Draft) = local.update { it.copy(draft = transform(it.draft)) }

    private fun draftOf(task: Task, reminder: Reminder?): Draft {
        val due = task.dueAt?.let { LocalDateTime.ofInstant(it, clock.zone()) }
        return Draft(
            title = task.title,
            description = task.description.orEmpty(),
            notes = task.notes.orEmpty(),
            dueDate = due?.toLocalDate(),
            dueTime = due?.toLocalTime()?.takeUnless { task.dueAllDay },
            reminderMinutes = reminder?.let { armed ->
                task.dueAt?.let { Duration.between(armed.triggerAt, it).toMinutes() }
            },
            recurrence = task.recurrence,
            priority = task.priority,
            projectId = task.projectId,
            clientId = task.clientId,
            tags = task.tags.map(Tag::name),
            estimatedMinutes = task.estimatedMinutes,
        )
    }

    private fun buildState(
        state: LocalState,
        reference: Catalogue,
        language: SpokenLanguage,
    ): TaskDetailUiState {
        val words = taskDetailWording(language)
        val draft = state.draft
        val stored = state.stored
        val visible = state.visibleFields(hasSteps = reference.steps.isNotEmpty())
        return TaskDetailUiState(
            isLoading = state.isLoading,
            isNew = stored == null,
            language = language,
            status = stored?.status ?: TaskStatus.OPEN,
            title = draft.title,
            description = draft.description,
            notes = draft.notes,
            priority = draft.priority,
            dueDate = draft.dueDate,
            dueTime = draft.dueTime,
            dueDateLabel = draft.dueDate?.let { times.dateLabel(it, language) },
            // All-day is what a date with no time is called. With no date at all there is nothing
            // to be all day, so the control reads as the invitation it is.
            dueTimeLabel = draft.dueTime?.let { times.timeLabel(it, language) }
                ?: draft.dueDate?.let { words.allDay },
            pickerDate = draft.dueDate ?: clock.today(),
            pickerTime = draft.dueTime ?: DEFAULT_DUE_TIME,
            reminderLead = ReminderLead.entries.firstOrNull { it.minutesBefore == draft.reminderMinutes },
            reminderLabel = reminderLabel(draft, words, language),
            recurrence = draft.recurrence,
            recurrenceLabel = draft.recurrence?.let { rule ->
                words.repeatLabel(rule, rule.until?.let { times.dateLabel(it, language) })
            },
            recurrencePreview = recurrencePreview(draft, language),
            projectId = draft.projectId,
            projectName = reference.projects.firstOrNull { it.id == draft.projectId }?.name,
            clientId = draft.clientId,
            clientName = reference.clients.firstOrNull { it.id == draft.clientId }?.name,
            tags = draft.tags,
            tagSuggestions = reference.tags.map(Tag::name)
                .filterNot { name -> draft.tags.any { it.equals(name, ignoreCase = true) } },
            estimatedMinutes = draft.estimatedMinutes,
            estimateLabel = draft.estimatedMinutes?.let(words::estimateLabel),
            subtasks = if (stored == null) {
                draft.newSteps.map { SubtaskRow(id = 0L, title = it, isDone = false) }
            } else {
                reference.steps.map { SubtaskRow(it.id, it.title, it.isDone) }
            },
            attachments = stored?.let { task ->
                reference.files.filter { it.id in task.attachmentIds }.map { it.toRow(words) }
            }.orEmpty(),
            projectOptions = reference.projects.map { PickerOption(it.id, it.name) },
            clientOptions = reference.clients.map { PickerOption(it.id, it.name, it.companyName) },
            visibleFields = visible,
            addableFields = state.addableFields(visible),
            createdLabel = stored?.let { times.momentLabel(it.createdAt, language) },
            updatedLabel = stored?.let { times.momentLabel(it.updatedAt, language) },
            completedLabel = stored?.completedAt?.let { times.momentLabel(it, language) },
            sheet = state.sheet,
            isSaving = state.isSaving,
            isDirty = state.isDirty,
            confirmDiscard = state.confirmDiscard,
            confirmDelete = state.confirmDelete,
            notice = state.notice,
            errorMessage = state.errorMessage,
            isFinished = state.isFinished,
        )
    }

    private fun reminderLabel(draft: Draft, words: TaskDetailWording, language: SpokenLanguage): String? {
        val minutes = draft.reminderMinutes ?: return null
        ReminderLead.entries.firstOrNull { it.minutesBefore == minutes }
            ?.let { return words.reminderLeadLabel(it) }
        val triggerAt = draft.dueAt()?.minus(minutes, ChronoUnit.MINUTES) ?: return null
        return times.momentLabel(triggerAt, language)
    }

    /** Counted from the due day, or from today for a repeat Abu set before choosing a date. */
    private fun recurrencePreview(draft: Draft, language: SpokenLanguage): List<String> {
        val rule = draft.recurrence ?: return emptyList()
        return recurrences.preview(rule, draft.dueDate ?: clock.today(), RECURRENCE_PREVIEW_COUNT)
            .map { times.dateLabel(it, language) }
    }

    private fun FileRecord.toRow(words: TaskDetailWording): AttachmentRow =
        AttachmentRow(id = id, name = displayName, detail = words.fileSize(sizeBytes))

    private fun Draft.dueAt(): Instant? {
        val date = dueDate ?: return null
        return times.toInstant(date, dueTime ?: DEFAULT_DUE_TIME)
    }

    private fun Draft.isAllDay(): Boolean = dueDate != null && dueTime == null

    /**
     * The form painted onto the stored task. Only the fields Abu can edit are carried over;
     * completion, archiving and the timestamps are facts about the task that a form may not
     * overwrite, and [EditTaskUseCase] merges against its own read of the row for the same reason.
     */
    private fun Draft.applyTo(base: Task, labels: List<Tag>): Task = base.copy(
        title = title,
        description = description,
        notes = notes,
        dueAt = dueAt(),
        dueAllDay = isAllDay(),
        estimatedMinutes = estimatedMinutes,
        priority = priority,
        projectId = projectId,
        clientId = clientId,
        tags = labels,
        recurrence = recurrence,
    )

    /** Everything the screen owns rather than the database. */
    private data class LocalState(
        val isLoading: Boolean = true,
        val stored: Task? = null,
        val draft: Draft = Draft(),
        val original: Draft = Draft(),
        val linkedReminder: Reminder? = null,
        /**
         * When it is due is the one field nearly every task wants, and its empty state is two
         * compact buttons rather than an empty box, so it starts on screen. The other eight wait
         * behind their chip.
         */
        val revealed: Set<TaskDetailField> = setOf(TaskDetailField.DUE),
        val sheet: TaskDetailSheet = TaskDetailSheet.NONE,
        val isSaving: Boolean = false,
        val confirmDiscard: Boolean = false,
        val confirmDelete: Boolean = false,
        val notice: TaskDetailNotice? = null,
        val errorMessage: String? = null,
        val isFinished: Boolean = false,
    ) {
        val isDirty: Boolean get() = draft != original

        /**
         * A field is on screen once it holds something, or once Abu has asked for it by name.
         *
         * [hasSteps] is passed in because a saved task's steps are rows in their own table rather
         * than anything the draft carries: without it the checklist would go out of sight the
         * moment the task was saved.
         */
        fun visibleFields(hasSteps: Boolean): Set<TaskDetailField> = TaskDetailField.entries
            .filter { it in revealed || draft.holds(it) || (it == TaskDetailField.SUBTASKS && hasSteps) }
            .filter { it != TaskDetailField.REMINDER || draft.dueDate != null }
            .toSet()

        fun addableFields(visible: Set<TaskDetailField>): List<TaskDetailField> = TaskDetailField.entries
            .filterNot { it in visible }
            .filter { it != TaskDetailField.REMINDER || draft.dueDate != null }
    }

    private data class Catalogue(
        val projects: List<Project>,
        val clients: List<Client>,
        val tags: List<Tag>,
        val files: List<FileRecord>,
        val steps: List<Subtask>,
    )

    /** The reminder as it stands after a save, and whether its moment had already gone. */
    private data class ReminderOutcome(val reminder: Reminder?, val missedItsMoment: Boolean)

    /**
     * The form's own copy of the task's editable fields, compared against the copy taken when it
     * loaded. That comparison is the whole of what "unsaved changes" means here.
     */
    private data class Draft(
        val title: String = "",
        val description: String = "",
        val notes: String = "",
        val dueDate: LocalDate? = null,
        val dueTime: LocalTime? = null,
        val reminderMinutes: Long? = null,
        val recurrence: RecurrenceRule? = null,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val projectId: Long? = null,
        val clientId: Long? = null,
        val tags: List<String> = emptyList(),
        val estimatedMinutes: Int? = null,
        /** Steps for a task that has no id yet. Once it has one they live in their own table. */
        val newSteps: List<String> = emptyList(),
    ) {
        fun holds(field: TaskDetailField): Boolean = when (field) {
            TaskDetailField.DESCRIPTION -> description.isNotBlank()
            TaskDetailField.DUE -> dueDate != null
            TaskDetailField.REMINDER -> reminderMinutes != null
            TaskDetailField.RECURRENCE -> recurrence != null
            TaskDetailField.PROJECT -> projectId != null
            TaskDetailField.CLIENT -> clientId != null
            TaskDetailField.TAGS -> tags.isNotEmpty()
            TaskDetailField.ESTIMATE -> estimatedMinutes != null
            TaskDetailField.SUBTASKS -> newSteps.isNotEmpty()
            TaskDetailField.NOTES -> notes.isNotBlank()
        }
    }

    private companion object {
        /** The nav argument. Zero is the task that does not exist yet. */
        const val ARG_TASK_ID = "taskId"

        const val NEW_TASK_ID = 0L

        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** How many upcoming dates the repeat picker shows back to Abu. */
        const val RECURRENCE_PREVIEW_COUNT = 3

        /**
         * A task given only a date still needs a moment. Nine in the morning is the start of Abu's
         * day, so "due Thursday" lands at the top of Thursday rather than at midnight, where it
         * would read as overdue all day. The same hour the composer sheet uses.
         */
        val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** Two entries traded, without disturbing the rest of the order. */
private fun <T> List<T>.swapped(first: Int, second: Int): List<T> = toMutableList().also {
    val held = it[first]
    it[first] = it[second]
    it[second] = held
}
