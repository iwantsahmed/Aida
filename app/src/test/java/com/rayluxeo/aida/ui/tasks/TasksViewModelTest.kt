package com.rayluxeo.aida.ui.tasks

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskSort
import com.rayluxeo.aida.domain.repository.TagUsage
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.usecase.ArchiveTaskUseCase
import com.rayluxeo.aida.domain.usecase.CancelReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.CompleteTaskByIdUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.DeleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.DuplicateTaskUseCase
import com.rayluxeo.aida.domain.usecase.ReopenTaskUseCase
import com.rayluxeo.aida.domain.usecase.RestoreTaskUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeTaskUseCase
import com.rayluxeo.aida.domain.usecase.UnarchiveTaskUseCase
import com.rayluxeo.aida.domain.usecase.UnsnoozeReminderUseCase
import com.rayluxeo.aida.domain.usecase.WatchPlaceReminderUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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
 * The Tasks screen built out of hand-written fakes. Hilt's test transform does not run under
 * AGP 9, so the view model is assembled here exactly as Dagger would assemble it.
 */
class TasksViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val tasks = FakeTaskRepository(clock)
    private val subtasks = FakeSubtaskRepository()
    private val tags = FakeTagRepository()
    private val projects = FakeProjectRepository()
    private val clients = FakeClientRepository()
    private val reminders = FakeReminderRepository()
    private val places = FakeReminderPlaceRepository()
    private val scheduler = FakeReminderScheduler()
    private val placeScheduler = FakePlaceReminderScheduler(places)
    private val settings = FakeSettingsRepository()
    private val recurrences = DefaultRecurrenceCalculator()
    private val renderer = MiraResponseRenderer(clock)
    private val times = TaskTimeFormatter(clock)

    private val yesterday = LocalDateTime.of(2026, 3, 4, 9, 0).toInstant()
    private val todayNoon = LocalDateTime.of(2026, 3, 5, 12, 0).toInstant()
    private val tonight = LocalDateTime.of(2026, 3, 5, 20, 0).toInstant()
    private val nextWeek = LocalDateTime.of(2026, 3, 12, 9, 0).toInstant()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    // --- The seven views -------------------------------------------------------------------

    @Test
    fun `the list opens on today and each view asks for its own tasks`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Renew the lease", dueAt = yesterday, createdAt = yesterday),
            testTask(id = 2L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday),
            testTask(id = 3L, title = "Book the flight", dueAt = nextWeek, createdAt = yesterday),
            testTask(
                id = 4L,
                title = "Pay the bill",
                isDone = true,
                completedAt = yesterday,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.view).isEqualTo(TasksView.TODAY)
        assertThat(titles(viewModel)).containsExactly("Call Ahmed")

        viewModel.onSelectView(TasksView.OVERDUE)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Renew the lease")

        viewModel.onSelectView(TasksView.UPCOMING)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Book the flight")

        viewModel.onSelectView(TasksView.COMPLETED)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Pay the bill")

        viewModel.onSelectView(TasksView.ALL)
        runCurrent()
        assertThat(titles(viewModel))
            .containsExactly("Renew the lease", "Call Ahmed", "Book the flight", "Pay the bill")
    }

    /**
     * The group with no project is the one Abu cannot reach any other way, so it has to be there
     * and it has to be last.
     */
    @Test
    fun `by project groups under names and keeps the unassigned work last`() = runTest {
        projects.seed(project(1L, "Rayluxeo"), project(2L, "Homepage"))
        tasks.projectNames[1L] = "Rayluxeo"
        tasks.projectNames[2L] = "Homepage"
        tasks.seed(
            testTask(id = 1L, title = "Write the brief", projectId = 1L, dueAt = tonight, createdAt = yesterday),
            testTask(id = 2L, title = "Fix the header", projectId = 2L, dueAt = nextWeek, createdAt = yesterday),
            testTask(id = 3L, title = "Tidy the study", createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectView(TasksView.BY_PROJECT)
        runCurrent()

        val sections = viewModel.uiState.value.sections
        assertThat(sections.map { it.title })
            .containsExactly("Homepage", "Rayluxeo", "No project")
            .inOrder()
        assertThat(sections.last().items.map { it.title }).containsExactly("Tidy the study")
    }

    @Test
    fun `by client groups the same way and names the group in Abu's language`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        clients.seed(client(9L, "Ahmed"))
        tasks.clientNames[9L] = "Ahmed"
        tasks.seed(
            testTask(id = 1L, title = "Send the proposal", clientId = 9L, dueAt = tonight, createdAt = yesterday),
            testTask(id = 2L, title = "Tidy the study", createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectView(TasksView.BY_CLIENT)
        runCurrent()

        assertThat(viewModel.uiState.value.sections.map { it.title })
            .containsExactly("Ahmed", "ক্লায়েন্ট নেই")
            .inOrder()
    }

    // --- Searching, filtering and sorting ---------------------------------------------------

    @Test
    fun `a search narrows the list and keeps following the table`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Call Ahmed", createdAt = yesterday),
            testTask(id = 2L, title = "Book the flight", createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectView(TasksView.ALL)
        viewModel.onQueryChange("call")
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Call Ahmed")

        tasks.create(testTask(title = "Call the bank", createdAt = yesterday))
        runCurrent()

        assertThat(titles(viewModel)).containsExactly("Call Ahmed", "Call the bank")
    }

    @Test
    fun `filtering by project, tag and priority narrows the list and says what is applied`() = runTest {
        projects.seed(project(1L, "Rayluxeo"))
        tags.seed(TagUsage(Tag(10L, "urgent"), taskCount = 1), TagUsage(Tag(11L, "later"), taskCount = 1))
        tasks.seed(
            testTask(
                id = 1L,
                title = "Write the brief",
                projectId = 1L,
                priority = TaskPriority.HIGH,
                tags = listOf(Tag(10L, "urgent")),
                createdAt = yesterday,
            ),
            testTask(id = 2L, title = "Fix the header", projectId = 1L, createdAt = yesterday),
            testTask(id = 3L, title = "Tidy the study", tags = listOf(Tag(11L, "later")), createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectView(TasksView.ALL)
        runCurrent()
        assertThat(viewModel.uiState.value.filters.isActive).isFalse()

        viewModel.onSelectProject(1L)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Write the brief", "Fix the header")

        viewModel.onToggleTag(10L)
        viewModel.onTogglePriority(TaskPriority.HIGH)
        runCurrent()

        assertThat(titles(viewModel)).containsExactly("Write the brief")
        assertThat(viewModel.uiState.value.filters.appliedLabels)
            .containsExactly("Rayluxeo", "urgent", "High")

        viewModel.onClearFilters()
        runCurrent()
        assertThat(viewModel.uiState.value.filters.appliedLabels).isEmpty()
        assertThat(titles(viewModel)).hasSize(3)
    }

    @Test
    fun `choosing the sort already applied reverses it and a different one starts over`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Alpha", dueAt = tonight, createdAt = yesterday),
            testTask(id = 2L, title = "Zulu", dueAt = todayNoon, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(titles(viewModel)).containsExactly("Zulu", "Alpha").inOrder()

        viewModel.onSelectSort(TaskSort.DUE_DATE)
        runCurrent()
        assertThat(viewModel.uiState.value.sort.ascending).isFalse()
        assertThat(titles(viewModel)).containsExactly("Alpha", "Zulu").inOrder()

        viewModel.onSelectSort(TaskSort.TITLE)
        runCurrent()
        assertThat(viewModel.uiState.value.sort.sort).isEqualTo(TaskSort.TITLE)
        assertThat(viewModel.uiState.value.sort.ascending).isTrue()
        assertThat(titles(viewModel)).containsExactly("Alpha", "Zulu").inOrder()
    }

    // --- Acting on one task ------------------------------------------------------------------

    @Test
    fun `completing a task is announced in Abu's language`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onToggleTask(1L, isDone = false)
        runCurrent()

        assertThat(viewModel.uiState.value.notice?.message).isEqualTo(
            renderer.speak(MiraResponse.TaskCompleted("Call Ahmed"), SpokenLanguage.BENGALI),
        )
        assertThat(tasks.snapshot().single().isDone).isTrue()
    }

    @Test
    fun `re-opening a finished task says nothing, because nothing needs saying`() = runTest {
        tasks.seed(
            testTask(
                id = 1L,
                title = "Call Ahmed",
                dueAt = tonight,
                isDone = true,
                completedAt = yesterday,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onToggleTask(1L, isDone = true)
        runCurrent()

        assertThat(viewModel.uiState.value.notice).isNull()
        assertThat(tasks.snapshot().single().isDone).isFalse()
    }

    @Test
    fun `deleting offers an undo that puts the same task back`() = runTest {
        tasks.seed(
            testTask(
                id = 5L,
                title = "Renew the lease",
                dueAt = tonight,
                priority = TaskPriority.HIGH,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onDeleteTask(5L)
        runCurrent()

        assertThat(tasks.snapshot()).isEmpty()
        assertThat(viewModel.uiState.value.notice?.undoLabel).isEqualTo("Undo")

        viewModel.onUndo()
        runCurrent()

        val restored = tasks.snapshot().single()
        assertThat(restored.id).isEqualTo(5L)
        assertThat(restored.dueAt).isEqualTo(tonight)
        assertThat(restored.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(viewModel.uiState.value.notice?.undoLabel).isNull()
    }

    /**
     * Archiving must not be a one-way door: the task leaves Today and stays findable in All, and
     * the undo puts it straight back.
     */
    @Test
    fun `archiving takes a task out of Today, leaves it in All and is undoable`() = runTest {
        tasks.seed(testTask(id = 5L, title = "Renew the lease", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSetArchived(5L, archived = true)
        runCurrent()

        assertThat(titles(viewModel)).isEmpty()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Task archived.")

        viewModel.onSelectView(TasksView.ALL)
        runCurrent()
        assertThat(viewModel.uiState.value.visibleTasks.single().isArchived).isTrue()

        viewModel.onUndo()
        runCurrent()
        assertThat(tasks.snapshot().single().archivedAt).isNull()
    }

    @Test
    fun `snoozing moves the due moment and names where it went`() = runTest {
        tasks.seed(testTask(id = 5L, title = "Renew the lease", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSnoozeTask(5L, SnoozeOption.ONE_DAY)
        runCurrent()

        assertThat(tasks.snapshot().single().dueAt)
            .isEqualTo(LocalDateTime.of(2026, 3, 6, 20, 0).toInstant())
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Moved to Tomorrow, 8 pm.")
        assertThat(viewModel.uiState.value.notice?.undoLabel).isNull()
    }

    @Test
    fun `a task with no due date is not offered a snooze it cannot take`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Tidy the study", createdAt = yesterday),
            testTask(id = 2L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onOpenActions(1L)
        runCurrent()
        assertThat(viewModel.uiState.value.actions?.canSnooze).isFalse()

        viewModel.onOpenActions(2L)
        runCurrent()
        assertThat(viewModel.uiState.value.actions?.canSnooze).isTrue()

        viewModel.onCloseActions()
        runCurrent()
        assertThat(viewModel.uiState.value.actions).isNull()
    }

    @Test
    fun `duplicating adds a second copy rather than editing the first`() = runTest {
        tasks.seed(testTask(id = 5L, title = "Weekly review", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onDuplicateTask(5L)
        runCurrent()

        assertThat(tasks.snapshot().map { it.title }).containsExactly("Weekly review", "Weekly review")
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Copy added.")
    }

    // --- Bulk selection -----------------------------------------------------------------------

    @Test
    fun `a long press starts selection and select all takes every visible row`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday),
            testTask(id = 2L, title = "Write the brief", dueAt = todayNoon, createdAt = yesterday),
            testTask(id = 3L, title = "Book the flight", dueAt = nextWeek, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onStartSelection(1L)
        runCurrent()
        assertThat(viewModel.uiState.value.selection.isActive).isTrue()
        assertThat(viewModel.uiState.value.selection.count).isEqualTo(1)
        assertThat(viewModel.uiState.value.selection.allSelected).isFalse()

        viewModel.onSelectAllVisible()
        runCurrent()
        // Today holds two of the three; a bulk action must not reach the one off screen.
        assertThat(viewModel.uiState.value.selection.ids).containsExactly(1L, 2L)
        assertThat(viewModel.uiState.value.selection.allSelected).isTrue()

        viewModel.onToggleSelected(1L)
        runCurrent()
        assertThat(viewModel.uiState.value.selection.ids).containsExactly(2L)
        assertThat(viewModel.uiState.value.selection.isActive).isTrue()
    }

    @Test
    fun `a bulk completion completes each task and the undo re-opens them`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday),
            testTask(id = 2L, title = "Write the brief", dueAt = todayNoon, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onStartSelection(1L)
        viewModel.onSelectAllVisible()
        viewModel.onBulkComplete()
        runCurrent()

        assertThat(tasks.snapshot().map { it.isDone }).containsExactly(true, true)
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("2 tasks completed.")
        assertThat(viewModel.uiState.value.selection.isActive).isFalse()

        viewModel.onUndo()
        runCurrent()
        assertThat(tasks.snapshot().map { it.isDone }).containsExactly(false, false)
    }

    @Test
    fun `leaving selection changes nothing about the tasks in it`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onStartSelection(1L)
        viewModel.onLeaveSelection()
        runCurrent()

        assertThat(viewModel.uiState.value.selection.isActive).isFalse()
        assertThat(viewModel.uiState.value.selection.ids).isEmpty()
        assertThat(tasks.snapshot().single().isDone).isFalse()
        assertThat(tasks.snapshot().single().deletedAt).isNull()
    }

    @Test
    fun `changing view drops the selection so no bulk action reaches a hidden row`() = runTest {
        tasks.seed(testTask(id = 1L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onStartSelection(1L)
        viewModel.onSelectView(TasksView.ALL)
        runCurrent()

        assertThat(viewModel.uiState.value.selection.isActive).isFalse()
        assertThat(viewModel.uiState.value.selection.ids).isEmpty()
    }

    // --- Failures -----------------------------------------------------------------------------

    @Test
    fun `a storage failure becomes a problem notice rather than a silent no-op`() = runTest {
        tasks.seed(testTask(id = 5L, title = "Renew the lease", dueAt = tonight, createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        tasks.failWith = MiraErrorCode.STORAGE
        viewModel.onDeleteTask(5L)
        runCurrent()

        val notice = viewModel.uiState.value.notice
        assertThat(notice?.tone).isEqualTo(NoticeTone.PROBLEM)
        assertThat(notice?.undoLabel).isNull()
        assertThat(tasks.snapshot()).hasSize(1)
    }

    /**
     * A bulk action that half-worked is the one Abu has no way to check. It says so, and what did
     * land is still undoable — hiding either half would leave him guessing at the state.
     */
    @Test
    fun `a partly failed bulk action says so and still undoes what landed`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday),
            testTask(id = 2L, title = "Write the brief", dueAt = todayNoon, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onStartSelection(1L)
        viewModel.onSelectAllVisible()
        tasks.failWith = MiraErrorCode.STORAGE
        viewModel.onBulkDelete()
        runCurrent()

        val notice = viewModel.uiState.value.notice
        assertThat(notice?.tone).isEqualTo(NoticeTone.PROBLEM)
        assertThat(notice?.undoLabel).isEqualTo("Undo")
        assertThat(tasks.snapshot()).hasSize(1)

        viewModel.onUndo()
        runCurrent()
        assertThat(tasks.snapshot()).hasSize(2)
    }

    // --- Wording ------------------------------------------------------------------------------

    /**
     * The overdue word used to be an English literal glued on by the composable, so a Bengali row
     * read "Overdue — গতকাল, সকাল ৯টা". It has to come from the same language as the moment.
     */
    @Test
    fun `an overdue row is worded in the same language as the moment beside it`() = runTest {
        tasks.seed(
            testTask(id = 1L, title = "Renew the lease", dueAt = yesterday, createdAt = yesterday),
            testTask(id = 2L, title = "Call Ahmed", dueAt = tonight, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectView(TasksView.ALL)
        runCurrent()

        val english = viewModel.uiState.value.visibleTasks.associateBy { it.title }
        assertThat(english.getValue("Renew the lease").supporting).isEqualTo("Overdue · Yesterday, 9 am")
        assertThat(english.getValue("Call Ahmed").supporting).isEqualTo("Today, 8 pm")

        settings.setLanguageMode(LanguageMode.BANGLA)
        runCurrent()

        val bengali = viewModel.uiState.value.visibleTasks.associateBy { it.title }
        assertThat(bengali.getValue("Renew the lease").supporting).isEqualTo("সময় পেরিয়েছে · গতকাল, সকাল ৯টা")
        assertThat(bengali.getValue("Call Ahmed").supporting).isEqualTo("আজ, রাত ৮টা")
    }

    @Test
    fun `a row carries its project and how far its steps have got`() = runTest {
        projects.seed(project(1L, "Rayluxeo"))
        tasks.seed(
            testTask(
                id = 1L,
                title = "Write the brief",
                dueAt = tonight,
                projectId = 1L,
                subtasks = listOf(
                    testSubtask(1L, taskId = 1L, title = "Outline", isDone = true),
                    testSubtask(2L, taskId = 1L, title = "Draft", position = 1),
                ),
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.visibleTasks.single().supporting)
            .isEqualTo("Today, 8 pm · Rayluxeo · 1 of 2 steps")
    }

    @Test
    fun `the screen's own labels follow the language the rows are worded in`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.language).isEqualTo(SpokenLanguage.ENGLISH)

        settings.setLanguageMode(LanguageMode.BANGLA)
        runCurrent()

        assertThat(viewModel.uiState.value.language).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(tasksWording(SpokenLanguage.BENGALI).viewLabel(TasksView.OVERDUE))
            .isEqualTo("সময় পেরিয়েছে")
    }

    // --- The composer and the reminders tab ---------------------------------------------------

    @Test
    fun `the sheet creates a real task at the chosen date and time`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onOpenComposer()
        viewModel.onComposerTitleChange("Call Ahmed")
        viewModel.onComposerDateChange(LocalDate.of(2026, 3, 6))
        viewModel.onComposerTimeChange(LocalTime.of(18, 30))
        viewModel.onComposerPriorityChange(TaskPriority.HIGH)
        runCurrent()

        assertThat(viewModel.uiState.value.composer?.dateLabel).isEqualTo("6 March")
        assertThat(viewModel.uiState.value.composer?.timeLabel).isEqualTo("6:30 pm")

        viewModel.onSaveTask()
        runCurrent()

        val created = tasks.snapshot().single()
        assertThat(created.title).isEqualTo("Call Ahmed")
        assertThat(created.dueAt).isEqualTo(LocalDateTime.of(2026, 3, 6, 18, 30).toInstant())
        assertThat(created.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(created.dueAllDay).isFalse()
        assertThat(viewModel.uiState.value.composer).isNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo(
            renderer.speak(MiraResponse.TaskCreated("Call Ahmed", created.dueAt), SpokenLanguage.ENGLISH),
        )
    }

    @Test
    fun `a date with no time is due at nine in the morning and reads back as a day`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onOpenComposer()
        viewModel.onComposerTitleChange("Renew the lease")
        viewModel.onComposerDateChange(LocalDate.of(2026, 3, 9))
        viewModel.onSaveTask()
        runCurrent()

        val created = tasks.snapshot().single()
        assertThat(created.dueAt).isEqualTo(LocalDateTime.of(2026, 3, 9, 9, 0).toInstant())
        assertThat(created.dueAllDay).isTrue()
    }

    @Test
    fun `clearing the date clears the time with it`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onOpenComposer()
        viewModel.onComposerTitleChange("Renew the lease")
        viewModel.onComposerDateChange(LocalDate.of(2026, 3, 9))
        viewModel.onComposerTimeChange(LocalTime.of(18, 30))
        viewModel.onComposerDateChange(null)
        viewModel.onSaveTask()
        runCurrent()

        assertThat(tasks.snapshot().single().dueAt).isNull()
    }

    @Test
    fun `cancelling a reminder disarms the alarm as well as the row`() = runTest {
        reminders.seed(
            Reminder(id = 3L, title = "Leave for the airport", triggerAt = tonight, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.reminders.single().whenLabel).isEqualTo("At Today, 8 pm")

        viewModel.onCancelReminderRequested(3L)
        runCurrent()

        assertThat(viewModel.uiState.value.confirmCancelReminder)
            .isEqualTo(ReminderCancelRequest(id = 3L, title = "Leave for the airport"))
        assertThat(scheduler.cancelled).isEmpty()

        viewModel.onCancelReminder(3L)
        runCurrent()

        assertThat(scheduler.cancelled).containsExactly(3L)
        assertThat(viewModel.uiState.value.reminders).isEmpty()
        assertThat(viewModel.uiState.value.confirmCancelReminder).isNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Reminder cancelled.")
    }

    /**
     * The gate is the whole of Abu's protection here: there is no undo behind a cancelled
     * reminder, so backing out of the question has to leave the alarm exactly where it was.
     */
    @Test
    fun `backing out of the cancel question leaves the reminder armed`() = runTest {
        reminders.seed(
            Reminder(id = 9L, title = "Leave for the airport", triggerAt = tonight, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onCancelReminderRequested(9L)
        runCurrent()
        assertThat(viewModel.uiState.value.confirmCancelReminder?.title)
            .isEqualTo("Leave for the airport")

        viewModel.onDismissCancelReminder()
        runCurrent()

        assertThat(viewModel.uiState.value.confirmCancelReminder).isNull()
        assertThat(scheduler.cancelled).isEmpty()
        assertThat(reminders.snapshot()).hasSize(1)
        assertThat(viewModel.uiState.value.reminders).hasSize(1)
        assertThat(viewModel.uiState.value.notice).isNull()
    }

    /**
     * A question about a row that is no longer there is a question Abu cannot answer honestly,
     * so it is derived from the rows rather than remembered beside them.
     */
    @Test
    fun `the cancel question disappears with the reminder it is about`() = runTest {
        reminders.seed(
            Reminder(id = 10L, title = "Collect the parcel", triggerAt = tonight, createdAt = yesterday),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onCancelReminderRequested(10L)
        runCurrent()
        assertThat(viewModel.uiState.value.confirmCancelReminder).isNotNull()

        reminders.seed()
        runCurrent()

        assertThat(viewModel.uiState.value.confirmCancelReminder).isNull()
    }

    @Test
    fun `a reminder row names its kind, and a snoozed one shows where it was pushed to`() = runTest {
        reminders.seed(
            Reminder(
                id = 4L,
                title = "Chase invoice INV-0142",
                triggerAt = tonight,
                kind = ReminderKind.INVOICE,
                createdAt = yesterday,
            ),
            Reminder(
                id = 5L,
                title = "Wish Ahmed",
                triggerAt = todayNoon,
                kind = ReminderKind.BIRTHDAY,
                snoozedUntil = tonight,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val rows = viewModel.uiState.value.reminders.associateBy { it.id }
        assertThat(rows.getValue(4L).kindLabel).isEqualTo("Invoice")
        assertThat(rows.getValue(4L).whenLabel).isEqualTo("At Today, 8 pm")
        assertThat(rows.getValue(5L).isSnoozed).isTrue()
        assertThat(rows.getValue(5L).whenLabel).isEqualTo("Snoozed until Today, 8 pm")
    }

    /**
     * The state a reboot leaves behind: Mira still holds the place, the platform holds nothing.
     * The row has to say so and the tab has to offer the way back.
     */
    @Test
    fun `a place reminder with nothing watching for it says so, and can be watched again`() = runTest {
        reminders.seed(
            Reminder(
                id = 6L,
                title = "Buy printer ink",
                triggerAt = tonight,
                kind = ReminderKind.PLACE,
                createdAt = yesterday,
            ),
        )
        places.seed(
            ReminderPlace(
                id = 1L,
                reminderId = 6L,
                label = "the office",
                latitude = 23.78,
                longitude = 90.40,
                radiusMeters = 150f,
                trigger = PlaceTrigger.ENTER,
                isRegistered = false,
                createdAt = yesterday,
                updatedAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.reminders.single().whenLabel)
            .isEqualTo("Waiting at the office \u2014 not being watched")
        assertThat(viewModel.uiState.value.placeNotice?.message)
            .isEqualTo("One place reminder has nothing watching for it.")

        viewModel.onWatchPlacesAgain()
        runCurrent()

        assertThat(placeScheduler.watched).containsExactly(1L)
        assertThat(viewModel.uiState.value.placeNotice).isNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Watching those places again.")
    }

    /** Each refusal has its own sentence, and only the ones Abu can act on carry a button. */
    @Test
    fun `a refused place watch is reported with the sentence for that refusal`() = runTest {
        reminders.seed(
            Reminder(
                id = 7L,
                title = "Buy printer ink",
                triggerAt = tonight,
                kind = ReminderKind.PLACE,
                createdAt = yesterday,
            ),
        )
        places.seed(
            ReminderPlace(
                id = 2L,
                reminderId = 7L,
                label = "the office",
                latitude = 23.78,
                longitude = 90.40,
                radiusMeters = 150f,
                trigger = PlaceTrigger.ENTER,
                isRegistered = false,
                createdAt = yesterday,
                updatedAt = yesterday,
            ),
        )
        placeScheduler.refuseWith = PlaceWatchRefusal.NO_PROVIDER
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onWatchPlacesAgain()
        runCurrent()

        val notice = viewModel.uiState.value.placeNotice
        assertThat(notice?.message).isEqualTo(
            "This phone has no location provider Aida can use, so place reminders cannot work here.",
        )
        // Nothing Abu could press would change a phone with no provider, so no button is offered.
        assertThat(notice?.actionText).isNull()
        assertThat(notice?.settingsFor).isNull()
    }

    @Test
    fun `calling off a snooze puts the reminder back on its own moment`() = runTest {
        reminders.seed(
            Reminder(
                id = 8L,
                title = "Call the landlord",
                triggerAt = todayNoon,
                snoozedUntil = tonight,
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onUnsnoozeReminder(8L)
        runCurrent()

        assertThat(reminders.snapshot().single().snoozedUntil).isNull()
        assertThat(viewModel.uiState.value.reminders.single().whenLabel).isEqualTo("At Today, 12 pm")
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Back on its own time.")
    }

    @Test
    fun `manual reminder creation schedules reminder and updates ui state`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onOpenReminderComposer()
        assertThat(viewModel.uiState.value.showReminderComposer).isTrue()

        viewModel.onSaveReminder("Call Ahmed", LocalDate.of(2026, 3, 6), LocalTime.of(10, 0))
        runCurrent()

        assertThat(viewModel.uiState.value.showReminderComposer).isFalse()
        val scheduled = reminders.snapshot()
        assertThat(scheduled).hasSize(1)
        assertThat(scheduled.single().title).isEqualTo("Call Ahmed")
    }

    // --- Plumbing ------------------------------------------------------------------------------

    private fun createViewModel() = TasksViewModel(
        tasks = tasks,
        createTask = CreateTaskUseCase(tasks, subtasks, clock),
        setTaskDone = SetTaskDoneUseCase(
            CompleteTaskByIdUseCase(tasks, recurrences, clock),
            ReopenTaskUseCase(tasks),
        ),
        archiveTask = ArchiveTaskUseCase(tasks),
        unarchiveTask = UnarchiveTaskUseCase(tasks),
        snoozeTask = SnoozeTaskUseCase(tasks, reminders, scheduler, clock),
        duplicateTask = DuplicateTaskUseCase(tasks, subtasks, clock),
        deleteTask = DeleteTaskUseCase(tasks),
        restoreTask = RestoreTaskUseCase(tasks),
        reminders = reminders,
        places = places,
        createReminder = CreateReminderUseCase(reminders, scheduler, clock),
        cancelReminder = CancelReminderUseCase(reminders, scheduler),
        unsnoozeReminder = UnsnoozeReminderUseCase(reminders, scheduler),
        watchPlace = WatchPlaceReminderUseCase(places, placeScheduler, clock),
        projects = projects,
        clients = clients,
        tags = tags,
        settings = settings,
        clock = clock,
        times = times,
        renderer = renderer,
    )

    private fun titles(viewModel: TasksViewModel): List<String> =
        viewModel.uiState.value.visibleTasks.map { it.title }

    private fun project(id: Long, name: String) =
        Project(id = id, name = name, createdAt = yesterday, updatedAt = yesterday)

    private fun client(id: Long, name: String) =
        Client(id = id, name = name, createdAt = yesterday, updatedAt = yesterday)

    /** Keeps the `WhileSubscribed` state flow hot for the length of the test. */
    private fun TestScope.observe(state: StateFlow<TasksUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun LocalDateTime.toInstant() = atZone(FakeMiraClock.DHAKA).toInstant()
}
