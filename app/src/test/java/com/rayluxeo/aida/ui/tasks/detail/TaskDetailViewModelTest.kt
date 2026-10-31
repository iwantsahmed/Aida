package com.rayluxeo.aida.ui.tasks.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.ScanRecord
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskStatus
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TagUsage
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.domain.usecase.ArchiveTaskUseCase
import com.rayluxeo.aida.domain.usecase.CancelReminderUseCase
import com.rayluxeo.aida.domain.usecase.CompleteTaskByIdUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.DeleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.EditTaskUseCase
import com.rayluxeo.aida.domain.usecase.ReopenTaskUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.domain.usecase.UnarchiveTaskUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.tasks.FakeClientRepository
import com.rayluxeo.aida.ui.tasks.FakeProjectRepository
import com.rayluxeo.aida.ui.tasks.FakeReminderRepository
import com.rayluxeo.aida.ui.tasks.FakeReminderScheduler
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import com.rayluxeo.aida.ui.tasks.FakeTaskRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.testTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The task editor, assembled the way Dagger would assemble it.
 *
 * Hilt's test transform does not run under AGP 9 (google/dagger#5261), so every collaborator here
 * is hand-written or real. The recurrence arithmetic is the real [DefaultRecurrenceCalculator]:
 * a fake that agreed with the view model about what next Friday is would prove nothing.
 */
class TaskDetailViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 10, 9, 0))
    private val tasks = FakeTaskRepository(clock)
    private val subtasks = EditorSubtaskRepository()
    private val tags = EditorTagRepository()
    private val files = EditorFileRepository()
    private val projects = FakeProjectRepository()
    private val clients = FakeClientRepository()
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()
    private val settings = FakeSettingsRepository()
    private val recurrences = DefaultRecurrenceCalculator()
    private val times = TaskTimeFormatter(clock)
    private val renderer = MiraResponseRenderer(clock)

    private val yesterday = LocalDateTime.of(2026, 3, 9, 9, 0).toInstant()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    // Creating.

    @Test
    fun `a new task saves every field through the create use case`() = runTest {
        projects.seed(testProject(id = 7L, name = "Rayluxeo"))
        clients.seed(testClient(id = 3L, name = "Ahmed"))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("Finish the homepage")
        viewModel.onDescriptionChange("Hero, pricing and the contact form")
        viewModel.onNotesChange("Ahmed wants it before Eid")
        viewModel.onPriorityChange(TaskPriority.HIGH)
        viewModel.onDueDateChange(LocalDate.of(2026, 3, 13))
        viewModel.onDueTimeChange(LocalTime.of(17, 0))
        viewModel.onEstimateChange(120)
        viewModel.onProjectChange(7L)
        viewModel.onClientChange(3L)
        viewModel.onAddTag("urgent")
        viewModel.onAddSubtask("Draft the copy")
        viewModel.onRecurrenceChange(RecurrenceRule.Weekly(days = setOf(DayOfWeek.FRIDAY)))
        runCurrent()

        viewModel.onSave()
        runCurrent()

        val saved = tasks.snapshot().single()
        assertThat(saved.title).isEqualTo("Finish the homepage")
        assertThat(saved.description).isEqualTo("Hero, pricing and the contact form")
        assertThat(saved.notes).isEqualTo("Ahmed wants it before Eid")
        assertThat(saved.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(saved.dueAt).isEqualTo(LocalDateTime.of(2026, 3, 13, 17, 0).toInstant())
        assertThat(saved.dueAllDay).isFalse()
        assertThat(saved.estimatedMinutes).isEqualTo(120)
        assertThat(saved.projectId).isEqualTo(7L)
        assertThat(saved.clientId).isEqualTo(3L)
        // Storage names a tag by its own row, so the label is checked where it was created.
        assertThat(saved.tags.map { it.id }).containsExactly(tags.all().single().id)
        assertThat(tags.all().single().name).isEqualTo("urgent")
        assertThat(saved.recurrence).isEqualTo(RecurrenceRule.Weekly(days = setOf(DayOfWeek.FRIDAY)))
        assertThat(subtasks.forTask(saved.id).map { it.title }).containsExactly("Draft the copy")
        assertThat(viewModel.uiState.value.notice?.tone).isEqualTo(MiraNoticeTone.CONFIRM)
    }

    @Test
    fun `a date with no time is an all-day task and a time clears the flag`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("Pay the bill")
        viewModel.onDueDateChange(LocalDate.of(2026, 3, 13))
        runCurrent()
        viewModel.onSave()
        runCurrent()

        assertThat(tasks.snapshot().single().dueAllDay).isTrue()

        viewModel.onDueTimeChange(LocalTime.of(11, 30))
        runCurrent()
        viewModel.onSave()
        runCurrent()

        val saved = tasks.snapshot().single()
        assertThat(saved.dueAllDay).isFalse()
        assertThat(saved.dueAt).isEqualTo(LocalDateTime.of(2026, 3, 13, 11, 30).toInstant())
    }

    @Test
    fun `a blank title is never saved`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("   ")
        runCurrent()

        assertThat(viewModel.uiState.value.canSave).isFalse()
        viewModel.onSave()
        runCurrent()
        assertThat(tasks.snapshot()).isEmpty()
    }

    // Editing.

    @Test
    fun `an existing task loads into the form and is not dirty until something changes`() = runTest {
        tasks.seed(
            testTask(
                id = 1L,
                title = "Call Ahmed",
                description = "About the retainer",
                dueAt = LocalDateTime.of(2026, 3, 12, 15, 0).toInstant(),
                tags = listOf(Tag(id = 4L, name = "client-work")),
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.isNew).isFalse()
        assertThat(state.title).isEqualTo("Call Ahmed")
        assertThat(state.description).isEqualTo("About the retainer")
        assertThat(state.dueDate).isEqualTo(LocalDate.of(2026, 3, 12))
        assertThat(state.dueTime).isEqualTo(LocalTime.of(15, 0))
        assertThat(state.tags).containsExactly("client-work")
        assertThat(state.isDirty).isFalse()

        viewModel.onTitleChange("Call Ahmed back")
        runCurrent()
        assertThat(viewModel.uiState.value.isDirty).isTrue()
    }

    @Test
    fun `a task that cannot be read says so instead of showing an empty form`() = runTest {
        val viewModel = createViewModel(taskId = 99L)
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNotEmpty()
        assertThat(state.title).isEmpty()
    }

    @Test
    fun `leaving with unsaved work asks first and leaving with none simply goes`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        viewModel.onBackRequested()
        runCurrent()
        assertThat(viewModel.uiState.value.isFinished).isTrue()
        assertThat(viewModel.uiState.value.confirmDiscard).isFalse()

        val second = createViewModel(taskId = 1L)
        observe(second.uiState)
        second.onTitleChange("Call Ahmed back")
        second.onBackRequested()
        runCurrent()

        assertThat(second.uiState.value.confirmDiscard).isTrue()
        assertThat(second.uiState.value.isFinished).isFalse()

        second.onConfirmDiscard()
        runCurrent()
        assertThat(second.uiState.value.isFinished).isTrue()
        assertThat(tasks.snapshot().single().title).isEqualTo("Call Ahmed")
    }

    // The verbs, and the status that follows from them.

    @Test
    fun `status follows the verbs rather than being chosen`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.status).isEqualTo(TaskStatus.OPEN)

        viewModel.onSetDone(true)
        runCurrent()
        assertThat(viewModel.uiState.value.status).isEqualTo(TaskStatus.DONE)

        viewModel.onSetDone(false)
        runCurrent()
        assertThat(viewModel.uiState.value.status).isEqualTo(TaskStatus.OPEN)

        viewModel.onSetArchived(true)
        runCurrent()
        assertThat(viewModel.uiState.value.status).isEqualTo(TaskStatus.ARCHIVED)

        viewModel.onSetArchived(false)
        runCurrent()
        assertThat(viewModel.uiState.value.status).isEqualTo(TaskStatus.OPEN)
    }

    /** A verb reads the stored task, so an edit still on screen is not written by the back door. */
    @Test
    fun `completing a task does not save what is still being typed`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        viewModel.onTitleChange("Something else entirely")
        viewModel.onSetDone(true)
        runCurrent()

        val stored = tasks.snapshot().single()
        assertThat(stored.title).isEqualTo("Call Ahmed")
        assertThat(stored.isDone).isTrue()
        assertThat(viewModel.uiState.value.title).isEqualTo("Something else entirely")
    }

    @Test
    fun `deleting asks first and then leaves the screen`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        viewModel.onDeleteRequested()
        runCurrent()
        assertThat(viewModel.uiState.value.confirmDelete).isTrue()
        assertThat(tasks.snapshot()).isNotEmpty()

        viewModel.onCancelDelete()
        runCurrent()
        assertThat(viewModel.uiState.value.confirmDelete).isFalse()

        viewModel.onDeleteRequested()
        viewModel.onConfirmDelete()
        runCurrent()

        assertThat(tasks.snapshot()).isEmpty()
        assertThat(viewModel.uiState.value.isFinished).isTrue()
    }

    // Reminders.

    @Test
    fun `a reminder is armed at the chosen lead before the due moment`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("Send the proposal")
        viewModel.onDueDateChange(LocalDate.of(2026, 3, 13))
        viewModel.onDueTimeChange(LocalTime.of(17, 0))
        viewModel.onReminderChange(ReminderLead.ONE_HOUR)
        runCurrent()
        viewModel.onSave()
        runCurrent()

        val armed = reminders.snapshot().single { it.state == ReminderState.SCHEDULED }
        assertThat(armed.triggerAt).isEqualTo(LocalDateTime.of(2026, 3, 13, 16, 0).toInstant())
        assertThat(armed.linkedTaskId).isEqualTo(tasks.snapshot().single().id)
        assertThat(scheduler.scheduled).contains(armed.id)
    }

    @Test
    fun `moving the due date moves the reminder with it`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("Send the proposal")
        viewModel.onDueDateChange(LocalDate.of(2026, 3, 13))
        viewModel.onDueTimeChange(LocalTime.of(17, 0))
        viewModel.onReminderChange(ReminderLead.ONE_HOUR)
        runCurrent()
        viewModel.onSave()
        runCurrent()

        viewModel.onDueDateChange(LocalDate.of(2026, 3, 20))
        runCurrent()
        viewModel.onSave()
        runCurrent()

        val scheduled = reminders.snapshot().filter { it.state == ReminderState.SCHEDULED }
        assertThat(scheduled).hasSize(1)
        assertThat(scheduled.single().triggerAt)
            .isEqualTo(LocalDateTime.of(2026, 3, 20, 16, 0).toInstant())
    }

    @Test
    fun `a reminder whose moment has already gone is reported rather than armed`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("Send the proposal")
        viewModel.onDueDateChange(LocalDate.of(2026, 3, 10))
        viewModel.onDueTimeChange(LocalTime.of(9, 30))
        viewModel.onReminderChange(ReminderLead.ONE_HOUR)
        runCurrent()
        viewModel.onSave()
        runCurrent()

        assertThat(tasks.snapshot()).hasSize(1)
        assertThat(reminders.snapshot().none { it.state == ReminderState.SCHEDULED }).isTrue()
        assertThat(viewModel.uiState.value.notice?.message)
            .isEqualTo(EnglishTaskDetailWording.reminderInThePast)
    }

    @Test
    fun `completing a task disarms the reminder that was waiting for it`() = runTest {
        tasks.seed(
            testTask(
                id = 1L,
                title = "Send the proposal",
                dueAt = LocalDateTime.of(2026, 3, 13, 17, 0).toInstant(),
                createdAt = yesterday,
            ),
        )
        reminders.seed(
            Reminder(
                id = 5L,
                title = "Send the proposal",
                triggerAt = LocalDateTime.of(2026, 3, 13, 16, 0).toInstant(),
                linkedTaskId = 1L,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.reminderLead).isEqualTo(ReminderLead.ONE_HOUR)

        viewModel.onSetDone(true)
        runCurrent()

        assertThat(reminders.snapshot().single().state).isEqualTo(ReminderState.CANCELLED)
        assertThat(scheduler.cancelled).contains(5L)
    }

    /** A reminder Mira set by voice can sit anywhere; it is read back rather than snapped. */
    @Test
    fun `a reminder at an offset the chips do not carry is read back as its moment`() = runTest {
        tasks.seed(
            testTask(
                id = 1L,
                title = "Send the proposal",
                dueAt = LocalDateTime.of(2026, 3, 13, 17, 0).toInstant(),
                createdAt = yesterday,
            ),
        )
        reminders.seed(
            Reminder(
                id = 5L,
                title = "Send the proposal",
                triggerAt = LocalDateTime.of(2026, 3, 13, 13, 20).toInstant(),
                linkedTaskId = 1L,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.reminderLead).isNull()
        assertThat(state.reminderLabel).isEqualTo(times.momentLabel(reminders.snapshot().single().triggerAt, state.language))
    }

    // Steps.

    @Test
    fun `steps on a saved task are written as they are touched`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Ship the site", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        viewModel.onAddSubtask("Draft the copy")
        viewModel.onAddSubtask("Send it over")
        runCurrent()
        assertThat(viewModel.uiState.value.subtasks.map { it.title })
            .containsExactly("Draft the copy", "Send it over").inOrder()

        viewModel.onToggleSubtask(0)
        runCurrent()
        assertThat(viewModel.uiState.value.subtasks.first().isDone).isTrue()

        viewModel.onMoveSubtask(1, up = true)
        runCurrent()
        assertThat(viewModel.uiState.value.subtasks.map { it.title })
            .containsExactly("Send it over", "Draft the copy").inOrder()

        viewModel.onRemoveSubtask(0)
        runCurrent()
        assertThat(viewModel.uiState.value.subtasks.map { it.title }).containsExactly("Draft the copy")
    }

    /** Steps are rows of their own, so the checklist must survive not being in the draft. */
    @Test
    fun `a saved task's steps are on screen without being asked for`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Ship the site", createdAt = yesterday))
        subtasks.add(1L, "Draft the copy")
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.visibleFields).contains(TaskDetailField.SUBTASKS)
        assertThat(state.addableFields).doesNotContain(TaskDetailField.SUBTASKS)
        assertThat(state.subtasks.map { it.title }).containsExactly("Draft the copy")
    }

    @Test
    fun `ticking every step does not tick the task`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Ship the site", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        viewModel.onAddSubtask("Draft the copy")
        runCurrent()
        viewModel.onToggleSubtask(0)
        runCurrent()

        assertThat(tasks.snapshot().single().isDone).isFalse()
        assertThat(viewModel.uiState.value.status).isEqualTo(TaskStatus.OPEN)
    }

    // Tags.

    @Test
    fun `a tag already in use is reused rather than spelled a second way`() = runTest {
        tags.seed(Tag(id = 4L, name = "client-work"))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onTitleChange("Call Ahmed")
        viewModel.onAddTag("Client-Work")
        viewModel.onAddTag("client-work")
        runCurrent()

        assertThat(viewModel.uiState.value.tags).hasSize(1)

        viewModel.onSave()
        runCurrent()

        assertThat(tasks.snapshot().single().tags.map { it.id }).containsExactly(4L)
        assertThat(tags.all()).hasSize(1)
    }

    @Test
    fun `the tags already in use are offered and the ones on the task are not offered twice`() = runTest {
        tags.seed(Tag(id = 4L, name = "client-work"), Tag(id = 5L, name = "admin"))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onAddTag("admin")
        runCurrent()

        assertThat(viewModel.uiState.value.tagSuggestions).containsExactly("client-work")
    }

    // Revealing, clearing and the repeat preview.

    @Test
    fun `an empty field is offered rather than shown and revealing puts it on screen`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val before = viewModel.uiState.value
        assertThat(before.visibleFields).doesNotContain(TaskDetailField.NOTES)
        assertThat(before.addableFields).contains(TaskDetailField.NOTES)
        // A reminder hangs off a due moment, so it is not even offered until there is one.
        assertThat(before.addableFields).doesNotContain(TaskDetailField.REMINDER)

        viewModel.onRevealField(TaskDetailField.NOTES)
        runCurrent()

        val after = viewModel.uiState.value
        assertThat(after.visibleFields).contains(TaskDetailField.NOTES)
        assertThat(after.addableFields).doesNotContain(TaskDetailField.NOTES)
    }

    @Test
    fun `clearing a field empties it and takes it off screen`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onDescriptionChange("Hero and pricing")
        runCurrent()
        assertThat(viewModel.uiState.value.visibleFields).contains(TaskDetailField.DESCRIPTION)

        viewModel.onClearField(TaskDetailField.DESCRIPTION)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.description).isEmpty()
        assertThat(state.visibleFields).doesNotContain(TaskDetailField.DESCRIPTION)
    }

    @Test
    fun `clearing the due date takes the reminder with it`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onDueDateChange(LocalDate.of(2026, 3, 13))
        viewModel.onReminderChange(ReminderLead.ONE_DAY)
        runCurrent()
        assertThat(viewModel.uiState.value.reminderLead).isEqualTo(ReminderLead.ONE_DAY)

        viewModel.onClearField(TaskDetailField.DUE)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.dueDate).isNull()
        assertThat(state.reminderLead).isNull()
        assertThat(state.visibleFields).doesNotContain(TaskDetailField.REMINDER)
    }

    @Test
    fun `the repeat preview shows the days the rule actually lands on`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onDueDateChange(LocalDate.of(2026, 3, 13))
        viewModel.onRecurrenceChange(RecurrenceRule.Weekly(days = setOf(DayOfWeek.FRIDAY)))
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.recurrenceLabel).isEqualTo("Every week on Fri")
        assertThat(state.recurrencePreview).containsExactly("13 March", "20 March", "27 March").inOrder()
    }

    // Attachments.

    @Test
    fun `unlinking an attachment leaves the file itself alone`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Send the invoice", createdAt = yesterday)
                .copy(attachmentIds = listOf(2L)),
        )
        files.seed(testFile(id = 2L, name = "invoice.pdf"))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.attachments.map { it.name }).containsExactly("invoice.pdf")

        viewModel.onUnlinkAttachment(2L)
        runCurrent()

        assertThat(viewModel.uiState.value.attachments).isEmpty()
        assertThat(files.all()).hasSize(1)
    }

    // Wording and the repeat editor's own arithmetic, which are pure.

    @Test
    fun `the screen is worded in Bengali when Abu is working in Bengali`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", createdAt = yesterday))
        val viewModel = createViewModel(taskId = 1L)
        observe(viewModel.uiState)

        viewModel.onEstimateChange(90)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.estimateLabel).isEqualTo("১ ঘণ্টা ৩০ মিনিট")
        assertThat(BengaliTaskDetailWording.statusLabel(state.status)).isEqualTo("বাকি")
    }

    @Test
    fun `a repeat reads the same way in both languages`() {
        val rule = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY), interval = 2)
        assertThat(EnglishTaskDetailWording.repeatLabel(rule, null)).isEqualTo("Every 2 weeks on Mon")
        assertThat(BengaliTaskDetailWording.repeatLabel(rule, null)).isEqualTo("প্রতি ২ সপ্তাহে সোম-বারে")
        assertThat(EnglishTaskDetailWording.repeatLabel(RecurrenceRule.Monthly(dayOfMonth = 21), null))
            .isEqualTo("Every month on the 21st")
        assertThat(EnglishTaskDetailWording.repeatLabel(RecurrenceRule.Weekdays(), "30 June"))
            .isEqualTo("Every weekday, until 30 June")
    }

    @Test
    fun `switching the kind of repeat keeps what still applies`() {
        val anchor = LocalDate.of(2026, 3, 13)
        val weekly = RecurrenceRule.Weekly(
            days = setOf(DayOfWeek.FRIDAY),
            interval = 2,
            until = LocalDate.of(2026, 6, 30),
        )

        val monthly = RecurrenceKind.MONTHLY.ruleFrom(weekly, anchor)
        assertThat(monthly).isEqualTo(RecurrenceRule.Monthly(13, 2, LocalDate.of(2026, 6, 30)))

        val none = RecurrenceKind.NONE.ruleFrom(weekly, anchor)
        assertThat(none).isNull()

        // Weekdays has no interval of its own, and every-N-days starts where daily stops.
        assertThat(RecurrenceKind.WEEKDAYS.ruleFrom(weekly, anchor))
            .isEqualTo(RecurrenceRule.Weekdays(LocalDate.of(2026, 6, 30)))
        assertThat(RecurrenceKind.EVERY_N_DAYS.ruleFrom(RecurrenceRule.Daily(), anchor)?.interval)
            .isEqualTo(2)
    }

    @Test
    fun `the interval stepper stays inside what a repeat can mean`() {
        assertThat(RecurrenceRule.Daily().withInterval(0).interval).isEqualTo(1)
        assertThat(RecurrenceRule.EveryNDays(5).withInterval(1).interval).isEqualTo(2)
        assertThat(RecurrenceRule.Yearly().withInterval(99).interval).isEqualTo(30)
        assertThat(RecurrenceRule.Weekdays().withInterval(4).interval).isEqualTo(1)
        assertThat(RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)).withWeekdayToggled(DayOfWeek.MONDAY))
            .isEqualTo(RecurrenceRule.Weekly(days = emptySet()))
        assertThat(RecurrenceRule.Weekly().editorKind()).isEqualTo(RecurrenceKind.WEEKLY)
    }

    private fun createViewModel(taskId: Long = 0L) = TaskDetailViewModel(
        savedState = SavedStateHandle(mapOf("taskId" to taskId)),
        tasks = tasks,
        subtasks = subtasks,
        tags = tags,
        files = files,
        projects = projects,
        clients = clients,
        reminders = reminders,
        createTask = CreateTaskUseCase(tasks, subtasks, clock),
        editTask = EditTaskUseCase(tasks, clock),
        setTaskDone = SetTaskDoneUseCase(
            complete = CompleteTaskByIdUseCase(tasks, recurrences, clock),
            reopen = ReopenTaskUseCase(tasks),
        ),
        archiveTask = ArchiveTaskUseCase(tasks),
        unarchiveTask = UnarchiveTaskUseCase(tasks),
        deleteTask = DeleteTaskUseCase(tasks),
        createReminder = CreateReminderUseCase(reminders, scheduler, clock),
        cancelReminder = CancelReminderUseCase(reminders, scheduler),
        recurrences = recurrences,
        settings = settings,
        clock = clock,
        times = times,
        renderer = renderer,
    )

    /** Keeps the `WhileSubscribed` state flow hot for the length of the test. */
    private fun TestScope.observe(state: StateFlow<TaskDetailUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun LocalDateTime.toInstant(): Instant = atZone(FakeMiraClock.DHAKA).toInstant()

    private fun testProject(id: Long, name: String): Project =
        Project(id = id, name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun testClient(id: Long, name: String): Client =
        Client(id = id, name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun testFile(id: Long, name: String): FileRecord = FileRecord(
        id = id,
        displayName = name,
        relativePath = "files/$name",
        mimeType = "application/pdf",
        sizeBytes = 2_048L,
        source = FileSource.IMPORT,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

/**
 * The steps, live and writable.
 *
 * `ui/tasks`'s shared double leaves ticking, reordering and removing unimplemented because the
 * list never does any of them. The editor does all three, so it brings its own.
 */
private class EditorSubtaskRepository : SubtaskRepository {

    private val rows = MutableStateFlow<List<Subtask>>(emptyList())
    private var nextId = 1L

    fun forTask(taskId: Long): List<Subtask> =
        rows.value.filter { it.taskId == taskId }.sortedBy { it.position }

    override fun observeForTask(taskId: Long): Flow<List<Subtask>> =
        rows.map { list -> list.filter { it.taskId == taskId }.sortedBy { it.position } }

    override suspend fun byId(id: Long): AppResult<Subtask> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")

    override suspend fun add(taskId: Long, title: String): AppResult<Subtask> {
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Subtask title was blank")
        val step = Subtask(
            id = nextId++,
            taskId = taskId,
            title = clean,
            position = forTask(taskId).size,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        rows.value = rows.value + step
        return AppResult.Success(step)
    }

    override suspend fun rename(id: Long, title: String): AppResult<Subtask> =
        change(id) { it.copy(title = title.trim()) }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Subtask> =
        change(id) { it.copy(isDone = done) }

    override suspend fun reorder(taskId: Long, orderedIds: List<Long>): AppResult<Unit> {
        val mine = forTask(taskId).map { it.id }.toSet()
        if (mine != orderedIds.toSet()) {
            return failure(MiraErrorCode.VALIDATION, "Reorder did not name every step of task $taskId")
        }
        rows.value = rows.value.map { step ->
            val at = orderedIds.indexOf(step.id)
            if (at >= 0) step.copy(position = at) else step
        }
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        if (rows.value.none { it.id == id }) return failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
        rows.value = rows.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun change(id: Long, edit: (Subtask) -> Subtask): AppResult<Subtask> {
        val existing = rows.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
        val saved = edit(existing)
        rows.value = rows.value.map { if (it.id == id) saved else it }
        return AppResult.Success(saved)
    }
}

/**
 * The labels, with [ensure] behaving the way the port describes it: one row per name, matched
 * without regard to case, so "Urgent" never becomes a second "urgent".
 */
private class EditorTagRepository : TagRepository {

    private val rows = MutableStateFlow<List<Tag>>(emptyList())
    private var nextId = 1L

    fun seed(vararg tags: Tag) {
        rows.value = tags.toList()
        nextId = (tags.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    fun all(): List<Tag> = rows.value

    override fun observeAll(): Flow<List<Tag>> = rows

    override fun observeUsage(): Flow<List<TagUsage>> = rows.map { list -> list.map { TagUsage(it, 0) } }

    override suspend fun byId(id: Long): AppResult<Tag> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")

    override suspend fun ensure(name: String): AppResult<Tag> {
        val clean = name.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Tag name was blank")
        rows.value.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            ?.let { return AppResult.Success(it) }
        val saved = Tag(id = nextId++, name = clean)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun rename(id: Long, name: String): AppResult<Tag> =
        failure(MiraErrorCode.UNKNOWN, "The task editor does not rename tags")

    override suspend fun delete(id: Long): AppResult<Unit> =
        failure(MiraErrorCode.UNKNOWN, "The task editor does not delete tags")
}

/** Only the index the editor reads: the linked files, and nothing that writes one. */
private class EditorFileRepository : FileRepository {

    private val rows = MutableStateFlow<List<FileRecord>>(emptyList())

    fun seed(vararg files: FileRecord) {
        rows.value = files.toList()
    }

    fun all(): List<FileRecord> = rows.value

    override fun observeAll(): Flow<List<FileRecord>> = rows

    override fun observeForClient(clientId: Long): Flow<List<FileRecord>> =
        rows.map { list -> list.filter { it.clientId == clientId } }

    override fun observeForProject(projectId: Long): Flow<List<FileRecord>> =
        rows.map { list -> list.filter { it.projectId == projectId } }

    override fun observeBySource(source: FileSource): Flow<List<FileRecord>> =
        rows.map { list -> list.filter { it.source == source } }

    override suspend fun byId(id: Long): AppResult<FileRecord> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No file with id $id")

    override suspend fun search(query: String?, limit: Int): AppResult<List<FileRecord>> =
        AppResult.Success(rows.value)

    override suspend fun register(file: FileRecord): AppResult<FileRecord> = unusedByTheEditor()

    override suspend fun registerScan(file: FileRecord, pageCount: Int): AppResult<FileRecord> =
        unusedByTheEditor()

    override suspend fun update(file: FileRecord): AppResult<FileRecord> = unusedByTheEditor()

    override suspend fun recordRecognisedText(fileId: Long, text: String?): AppResult<FileRecord> =
        unusedByTheEditor()

    override suspend fun scanFor(fileId: Long): AppResult<ScanRecord?> = unusedByTheEditor()

    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedByTheEditor()

    override suspend fun restore(id: Long): AppResult<FileRecord> = unusedByTheEditor()

    override suspend fun listReclaimable(): AppResult<List<FileRecord>> = unusedByTheEditor()

    override suspend fun purge(id: Long): AppResult<Unit> = unusedByTheEditor()
}

/**
 * Fails loudly rather than returning something plausible.
 *
 * A double that quietly answers a call the editor was never meant to make is how a test ends up
 * proving the fake rather than the code.
 */
private fun unusedByTheEditor(): Nothing = error("The task editor does not use this")
