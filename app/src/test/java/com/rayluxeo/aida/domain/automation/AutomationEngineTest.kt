package com.rayluxeo.aida.domain.automation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.brief.EveningReviewEngine
import com.rayluxeo.aida.domain.brief.EveningReviewReport
import com.rayluxeo.aida.domain.command.CommandInterpreter
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.command.RoutedCommand
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.domain.usecase.GetDailyBriefUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AutomationEngineTest {

    private val clock = FixedMiraClock()
    private val now = clock.now()

    private class FakeAutomationRepo : AutomationRepository {
        val automations = mutableListOf<Automation>()
        val runs = mutableListOf<AutomationRun>()

        override fun observeAll(): Flow<List<Automation>> = flowOf(automations)
        override fun observeEnabled(): Flow<List<Automation>> = flowOf(automations.filter { it.isEnabled })
        override fun observeRuns(automationId: Long): Flow<List<AutomationRun>> = flowOf(runs.filter { it.automationId == automationId })
        override suspend fun byId(id: Long) = automations.first { it.id == id }.asSuccess()
        override suspend fun create(automation: Automation) = automation.copy(id = 1L).also { automations.add(it) }.asSuccess()
        override suspend fun update(automation: Automation) = automation.asSuccess()
        override suspend fun setEnabled(id: Long, enabled: Boolean) = automations.first().copy(isEnabled = enabled).asSuccess()
        override suspend fun delete(id: Long) = Unit.asSuccess()
        override suspend fun startRun(automationId: Long): AppResult<AutomationRun> {
            val run = AutomationRun(id = runs.size + 1L, automationId = automationId, startedAt = Instant.now())
            runs.add(run)
            return run.asSuccess()
        }
        override suspend fun finishRun(runId: Long, outcome: AutomationOutcome, detail: String?): AppResult<AutomationRun> {
            val idx = runs.indexOfFirst { it.id == runId }
            val updated = runs[idx].copy(finishedAt = Instant.now(), outcome = outcome, detail = detail)
            runs[idx] = updated
            return updated.asSuccess()
        }
        override suspend fun inFlightRuns() = emptyList<AutomationRun>().asSuccess()
    }

    private class FakeTaskRepo : TaskRepository {
        val tasks = mutableListOf<Task>()
        override fun observe(filter: com.rayluxeo.aida.domain.model.TaskFilter) = flowOf(tasks)
        override fun observe(query: com.rayluxeo.aida.domain.model.TaskQuery) = flowOf(tasks)
        override suspend fun list(query: com.rayluxeo.aida.domain.model.TaskQuery) = tasks.asSuccess()
        override suspend fun count(query: com.rayluxeo.aida.domain.model.TaskQuery) = tasks.size.asSuccess()
        override suspend fun groupedByProject(query: com.rayluxeo.aida.domain.model.TaskQuery) = emptyList<TaskGroup>().asSuccess()
        override suspend fun groupedByClient(query: com.rayluxeo.aida.domain.model.TaskQuery) = emptyList<TaskGroup>().asSuccess()
        override fun observeCounts() = flowOf(TaskCounts(0, 0, 0, 0))
        override suspend fun byId(id: Long) = tasks.first().asSuccess()
        override suspend fun search(query: String?, filter: com.rayluxeo.aida.domain.model.TaskFilter) = tasks.asSuccess()
        override suspend fun create(task: Task) = task.copy(id = tasks.size + 1L).also { tasks.add(it) }.asSuccess()
        override suspend fun update(task: Task) = task.asSuccess()
        override suspend fun setDone(id: Long, isDone: Boolean) = tasks.first().asSuccess()
        override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean) = ids.size.asSuccess()
        override suspend fun snooze(id: Long, until: Instant) = tasks.first().asSuccess()
        override suspend fun archive(id: Long) = tasks.first().asSuccess()
        override suspend fun unarchive(id: Long) = tasks.first().asSuccess()
        override suspend fun archiveAll(ids: Collection<Long>) = ids.size.asSuccess()
        override suspend fun delete(id: Long) = Unit.asSuccess()
        override suspend fun restore(id: Long) = tasks.first().asSuccess()
        override suspend fun deleteAll(ids: Collection<Long>) = ids.size.asSuccess()
        override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant) = TaskOccurrence(tasks.first(), tasks.first()).asSuccess()
        override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> = tasks.first().asSuccess()
        override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = tasks.first().asSuccess()
        override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> = tasks.first().asSuccess()
        override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> = tasks.first().asSuccess()
        override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> = tasks.first().asSuccess()
    }

    private class FakeReminderRepo : ReminderRepository {
        val reminders = mutableListOf<Reminder>()
        override fun observeUpcoming() = flowOf(reminders)
        override fun observeByKind(kind: ReminderKind) = flowOf(reminders)
        override suspend fun listOfKinds(kinds: Collection<ReminderKind>) = reminders.asSuccess()
        override suspend fun byId(id: Long) = reminders.first().asSuccess()
        override suspend fun create(reminder: Reminder) = reminder.copy(id = reminders.size + 1L).also { reminders.add(it) }.asSuccess()
        override suspend fun update(reminder: Reminder) = reminder.asSuccess()
        override suspend fun markFired(id: Long) = Unit.asSuccess()
        override suspend fun cancel(id: Long) = Unit.asSuccess()
        override suspend fun nextScheduled() = AppResult.Success(reminders.firstOrNull())
        override suspend fun rescheduleAll() = AppResult.Success(0)
    }

    private class FakeEveningReviewEngine : EveningReviewEngine {
        override suspend fun review(date: LocalDate): AppResult<EveningReviewReport> {
            val report = EveningReviewReport(
                date = date,
                completedToday = emptyList(),
                stillOpen = emptyList(),
                paymentsReceived = emptyList(),
                paymentsTotal = Money.zero(),
                expensesRecorded = 0,
                expensesTotal = Money.zero(),
                invoicesCreated = emptyList(),
                tomorrowsEvents = emptyList(),
                tomorrowsPriorities = emptyList(),
                calendarReadable = true,
            )
            return report.asSuccess()
        }
    }

    @Test
    fun `executeAutomation runs task creation action and logs execution run`() = runTest {
        val autoRepo = FakeAutomationRepo()
        val taskRepo = FakeTaskRepo()
        val reminderRepo = FakeReminderRepo()

        val automation = Automation(
            id = 100L,
            name = "Weekly Planning Rule",
            trigger = AutomationTriggerConfig.Manual,
            action = AutomationAction.CreateTask(title = "Weekly planning task"),
            isEnabled = true,
            createdAt = now,
            updatedAt = now,
        )
        autoRepo.automations.add(automation)

        val normalizer = com.rayluxeo.aida.ui.mira.FakeTextNormalizer()
        val commandUseCase = ExecuteCommandUseCase(
            normalizer = normalizer,
            interpreter = FakeCommandRouter(),
            validator = com.rayluxeo.aida.domain.command.DefaultCommandValidator(clock),
            dispatcher = com.rayluxeo.aida.domain.command.DefaultCommandDispatcher(
                work = FakeWorkCommandHandler(),
                knowledge = FakeKnowledgeCommandHandler(),
                business = FakeBusinessCommandHandler(),
                money = FakeMoneyCommandHandler(),
                phone = FakePhoneCommandHandler(),
                salah = FakeSalahCommandHandler(),
            ),
            conversation = FakeConversationRepo(),
            contextManager = com.rayluxeo.aida.ai.assistant.ConversationContextManager(),
            settings = FakeSettingsRepo(),
            clock = clock,
        )

        val briefUseCase = GetDailyBriefUseCase(
            briefs = FakeBriefRepository(),
        )

        val engine = AutomationEngine(
            automationRepository = autoRepo,
            automationValidator = AutomationValidator(),
            executeCommandUseCase = commandUseCase,
            taskRepository = taskRepo,
            reminderRepository = reminderRepo,
            getDailyBriefUseCase = briefUseCase,
            eveningReviewEngine = FakeEveningReviewEngine(),
            deviceGateway = FakeAutomationDeviceGateway(),
            activityLog = FakeActivityLogRepo(),
            clock = clock,
        )

        val runResult = engine.executeAutomation(automation)
        assertThat(runResult).isInstanceOf(AppResult.Success::class.java)

        val run = (runResult as AppResult.Success).value
        assertThat(run.outcome).isEqualTo(AutomationOutcome.SUCCESS)
        assertThat(taskRepo.tasks).hasSize(1)
        assertThat(taskRepo.tasks.first().title).isEqualTo("Weekly planning task")
    }
}

private class FakeAutomationDeviceGateway : com.rayluxeo.aida.domain.automation.AutomationDeviceGateway {
    override suspend fun notify(title: String, body: String?): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun openLink(url: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun performGlobalAction(action: com.rayluxeo.aida.domain.model.GlobalDeviceAction): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun launchApp(packageName: String): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeBriefRepository : BriefRepository {
    override suspend fun todayBrief(): AppResult<DailyBrief> =
        DailyBrief(
            date = LocalDate.now(),
            dayPart = DayPart.MORNING,
            openTaskCount = 0,
            dueTodayCount = 0,
            overdueCount = 0,
            completedTodayCount = 0,
            nextReminderTitle = null,
            nextReminderAt = null,
        ).asSuccess()
}

private class FakeSettingsRepo : SettingsRepository {
    override val settings: Flow<MiraSettings> = flowOf(MiraSettings())
    override suspend fun current() = MiraSettings()
    override suspend fun setLanguageMode(mode: com.rayluxeo.aida.core.common.LanguageMode) {}
    override suspend fun setActivationSound(enabled: Boolean) {}
    override suspend fun setHapticFeedback(enabled: Boolean) {}
    override suspend fun setOnboardingComplete(complete: Boolean) {}
}

private class FakeCommandRouter : CommandInterpreter {
    override suspend fun route(request: CommandRequest) =
        RoutedCommand(EngineResult.NotUnderstood, EngineId.LOCAL)
}

private class FakeConversationRepo : ConversationRepository {
    override fun observeRecent(limit: Int) = flowOf(emptyList<ConversationTurn>())
    override suspend fun append(turn: ConversationTurn) = turn.asSuccess()
    override suspend fun clear() = Unit.asSuccess()
}

private class FakeActivityLogRepo : com.rayluxeo.aida.domain.repository.ActivityLogRepository {
    override fun observeRecent(limit: Int) = flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeByCategory(category: com.rayluxeo.aida.domain.model.ActivityCategory, limit: Int) = flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeForSubject(subject: com.rayluxeo.aida.domain.model.ActivitySubject) = flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun between(from: Instant, to: Instant) = AppResult.Success(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun record(entry: com.rayluxeo.aida.domain.model.ActivityEntry) = AppResult.Success(entry)
}

private class FakeWorkCommandHandler : com.rayluxeo.aida.domain.command.handler.WorkCommandHandler {
    override suspend fun createTask(command: com.rayluxeo.aida.domain.command.AppCommand.CreateTask) = AppResult.Success(MiraResponse.TaskCreated(command.title, command.dueAt))
    override suspend fun updateTask(command: com.rayluxeo.aida.domain.command.AppCommand.UpdateTask) = error("unused")
    override suspend fun completeTask(command: com.rayluxeo.aida.domain.command.AppCommand.CompleteTask) = error("unused")
    override suspend fun deleteTask(command: com.rayluxeo.aida.domain.command.AppCommand.DeleteTask) = error("unused")
    override suspend fun searchTasks(command: com.rayluxeo.aida.domain.command.AppCommand.SearchTasks) = error("unused")
    override suspend fun createReminder(command: com.rayluxeo.aida.domain.command.AppCommand.CreateReminder) = error("unused")
    override suspend fun queryReminders(command: com.rayluxeo.aida.domain.command.AppCommand.QueryReminders) = error("unused")
    override suspend fun snoozeReminder(command: com.rayluxeo.aida.domain.command.AppCommand.SnoozeReminder) = error("unused")
    override suspend fun cancelReminder(command: com.rayluxeo.aida.domain.command.AppCommand.CancelReminder) = error("unused")
    override suspend fun createPlaceReminder(command: com.rayluxeo.aida.domain.command.AppCommand.CreatePlaceReminder) = error("unused")
    override suspend fun createAlarm(command: com.rayluxeo.aida.domain.command.AppCommand.CreateAlarm) = error("unused")
    override suspend fun createTimer(command: com.rayluxeo.aida.domain.command.AppCommand.CreateTimer) = error("unused")
    override suspend fun dailyBrief() = error("unused")
}

private class FakeKnowledgeCommandHandler : com.rayluxeo.aida.domain.command.handler.KnowledgeCommandHandler {
    override suspend fun createNote(command: com.rayluxeo.aida.domain.command.AppCommand.CreateNote) = error("unused")
    override suspend fun searchNotes(command: com.rayluxeo.aida.domain.command.AppCommand.SearchNotes) = error("unused")
    override suspend fun storeMemory(command: com.rayluxeo.aida.domain.command.AppCommand.StoreMemory) = error("unused")
    override suspend fun forgetMemory(command: com.rayluxeo.aida.domain.command.AppCommand.ForgetMemory) = error("unused")
    override suspend fun forgetFact(command: com.rayluxeo.aida.domain.command.AppCommand.ForgetFact) = error("unused")
    override suspend fun searchMemory(command: com.rayluxeo.aida.domain.command.AppCommand.SearchMemory) = error("unused")
    override suspend fun universalSearch(command: com.rayluxeo.aida.domain.command.AppCommand.UniversalSearch) = error("unused")
    override suspend fun newConversation(command: com.rayluxeo.aida.domain.command.AppCommand.NewConversation) = error("unused")
    override suspend fun deleteConversation(command: com.rayluxeo.aida.domain.command.AppCommand.DeleteConversation) = error("unused")
    override suspend fun deleteAllConversations(command: com.rayluxeo.aida.domain.command.AppCommand.DeleteAllConversations) = error("unused")
    override suspend fun searchConversations(command: com.rayluxeo.aida.domain.command.AppCommand.SearchConversations) = error("unused")
}

private class FakeBusinessCommandHandler : com.rayluxeo.aida.domain.command.handler.BusinessCommandHandler {
    override suspend fun createClient(command: com.rayluxeo.aida.domain.command.AppCommand.CreateClient) = error("unused")
    override suspend fun searchClients(command: com.rayluxeo.aida.domain.command.AppCommand.SearchClients) = error("unused")
    override suspend fun createInvoiceDraft(command: com.rayluxeo.aida.domain.command.AppCommand.CreateInvoiceDraft) = error("unused")
    override suspend fun createQuoteDraft(command: com.rayluxeo.aida.domain.command.AppCommand.CreateQuoteDraft) = error("unused")
    override suspend fun createRecurringInvoice(command: com.rayluxeo.aida.domain.command.AppCommand.CreateRecurringInvoice) = error("unused")
    override suspend fun queryInvoice(command: com.rayluxeo.aida.domain.command.AppCommand.QueryInvoice) = error("unused")
    override suspend fun queryClientStatement(command: com.rayluxeo.aida.domain.command.AppCommand.QueryClientStatement) = error("unused")
    override suspend fun deliverInvoice(command: com.rayluxeo.aida.domain.command.AppCommand.DeliverInvoice) = error("unused")
}

private class FakeMoneyCommandHandler : com.rayluxeo.aida.domain.command.handler.MoneyCommandHandler {
    override suspend fun recordPayment(command: com.rayluxeo.aida.domain.command.AppCommand.RecordPayment) = error("unused")
    override suspend fun recordExpense(command: com.rayluxeo.aida.domain.command.AppCommand.RecordExpense) = error("unused")
    override suspend fun recordIncome(command: com.rayluxeo.aida.domain.command.AppCommand.RecordIncome) = error("unused")
    override suspend fun performCalculation(command: com.rayluxeo.aida.domain.command.AppCommand.PerformCalculation) = error("unused")
    override suspend fun queryExpenses(command: com.rayluxeo.aida.domain.command.AppCommand.QueryExpenses) = error("unused")
    override suspend fun queryIncome(command: com.rayluxeo.aida.domain.command.AppCommand.QueryIncome) = error("unused")
    override suspend fun querySubscription(command: com.rayluxeo.aida.domain.command.AppCommand.QuerySubscription) = error("unused")
}

private class FakePhoneCommandHandler : com.rayluxeo.aida.domain.command.handler.PhoneCommandHandler {
    override suspend fun createCalendarEvent(command: com.rayluxeo.aida.domain.command.AppCommand.CreateCalendarEvent) = error("unused")
    override suspend fun querySchedule(command: com.rayluxeo.aida.domain.command.AppCommand.QuerySchedule) = error("unused")
    override suspend fun findFreeSlots(command: com.rayluxeo.aida.domain.command.AppCommand.FindFreeSlots) = error("unused")
    override suspend fun updateCalendarEvent(command: com.rayluxeo.aida.domain.command.AppCommand.UpdateCalendarEvent) = error("unused")
    override suspend fun deleteCalendarEvent(command: com.rayluxeo.aida.domain.command.AppCommand.DeleteCalendarEvent) = error("unused")
    override suspend fun findContact(command: com.rayluxeo.aida.domain.command.AppCommand.FindContact) = error("unused")
    override suspend fun prepareCall(command: com.rayluxeo.aida.domain.command.AppCommand.PrepareCall) = error("unused")
    override suspend fun prepareMessage(command: com.rayluxeo.aida.domain.command.AppCommand.PrepareMessage) = error("unused")
}

private class FakeSalahCommandHandler : com.rayluxeo.aida.domain.command.handler.SalahCommandHandler {
    override suspend fun markPrayerPrayed(command: com.rayluxeo.aida.domain.command.AppCommand.MarkPrayerPrayed) = error("unused")
    override suspend fun queryNextPrayer() = error("unused")
    override suspend fun queryPrayerTimeRemaining(command: com.rayluxeo.aida.domain.command.AppCommand.QueryPrayerTimeRemaining) = error("unused")
    override suspend fun queryPrayerTime(command: com.rayluxeo.aida.domain.command.AppCommand.QueryPrayerTime) = error("unused")
    override suspend fun queryPrayerTracking(command: com.rayluxeo.aida.domain.command.AppCommand.QueryPrayerTracking) = error("unused")
    override suspend fun setTahajjudReminder() = error("unused")
}
