package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.brief.EveningReviewEngine
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.domain.usecase.GetDailyBriefUseCase
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationEngine @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val automationValidator: AutomationValidator,
    private val executeCommandUseCase: ExecuteCommandUseCase,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val getDailyBriefUseCase: GetDailyBriefUseCase,
    private val eveningReviewEngine: EveningReviewEngine,
    private val deviceGateway: AutomationDeviceGateway,
    private val activityLog: com.rayluxeo.aida.domain.repository.ActivityLogRepository,
    private val clock: MiraClock,
) {
    private var executionDepth = 0
    private val runTimestamps = ConcurrentHashMap<Long, MutableList<Long>>()

    suspend fun triggerAutomation(automationId: Long): AppResult<AutomationRun> {
        val automation = (automationRepository.byId(automationId) as? AppResult.Success)?.value
            ?: return failure(MiraErrorCode.NOT_FOUND, "Automation $automationId not found")

        return executeAutomation(automation)
    }

    suspend fun executeAutomation(automation: Automation): AppResult<AutomationRun> {
        if (!automation.isEnabled && automation.trigger !is com.rayluxeo.aida.domain.model.AutomationTriggerConfig.Manual) {
            return failure(MiraErrorCode.VALIDATION, "Automation '${automation.name}' is disabled")
        }

        if (executionDepth >= MAX_CALL_DEPTH) {
            return failure(MiraErrorCode.VALIDATION, "Execution depth limit reached to prevent infinite automation loops")
        }

        val nowEpoch = clock.now().toEpochMilli()
        val timestamps = runTimestamps.getOrPut(automation.id) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { nowEpoch - it > WINDOW_MS }
            if (timestamps.size >= MAX_RUNS_PER_WINDOW) {
                return failure(MiraErrorCode.VALIDATION, "Rate limit reached for automation '${automation.name}' to prevent loops")
            }
            timestamps.add(nowEpoch)
        }

        val validation = automationValidator.validate(automation)
        if (validation is AppResult.Failure) {
            return failure(validation.error.code, validation.error.detail)
        }

        val runResult = automationRepository.startRun(automation.id)
        val run = (runResult as? AppResult.Success)?.value
            ?: return failure(MiraErrorCode.STORAGE, "Failed to start automation run")

        executionDepth++
        return try {
            val outcome = runAction(automation.action)
            executionDepth--
            val detail = if (outcome is AppResult.Success) "Action executed successfully: ${automation.action.javaClass.simpleName}" else "Action execution failed"
            val outcomeStatus = if (outcome is AppResult.Success) AutomationOutcome.SUCCESS else AutomationOutcome.FAILED
            val finishedRun = automationRepository.finishRun(run.id, outcomeStatus, detail)

            activityLog.record(
                com.rayluxeo.aida.domain.model.ActivityEntry(
                    category = com.rayluxeo.aida.domain.model.ActivityCategory.AUTOMATION,
                    action = "automation.executed",
                    summary = "Automation '${automation.name}' executed with status ${outcomeStatus.name}",
                    subject = com.rayluxeo.aida.domain.model.ActivitySubject("automations", automation.id),
                    occurredAt = clock.now(),
                )
            )
            finishedRun
        } catch (e: Exception) {
            executionDepth--
            automationRepository.finishRun(run.id, AutomationOutcome.FAILED, "Error: ${e.message}")
        }
    }

    private suspend fun runAction(action: AutomationAction): AppResult<Unit> = when (action) {
        is AutomationAction.Notify -> deviceGateway.notify(action.title, action.body)

        is AutomationAction.OpenLink -> deviceGateway.openLink(action.url)

        is AutomationAction.PerformGlobalAction -> deviceGateway.performGlobalAction(action.action)

        is AutomationAction.LaunchApp -> deviceGateway.launchApp(action.packageName)

        is AutomationAction.RunUtterance -> {
            val outcome = executeCommandUseCase(action.utterance, InputSource.TEXT)
            if (outcome.response !is MiraResponse.Problem) Unit.asSuccess() else failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Utterance execution failed")
        }

        is AutomationAction.CreateTask -> {
            val now = clock.now()
            val task = Task(
                title = action.title,
                priority = action.priority,
                createdAt = now,
                updatedAt = now,
            )
            taskRepository.create(task).map { Unit }
        }

        is AutomationAction.CreateReminder -> {
            val now = clock.now()
            val triggerAt = now.plusSeconds((action.offsetMinutes * 60).toLong())
            val reminder = Reminder(
                title = action.title,
                triggerAt = triggerAt,
                createdAt = now,
            )
            reminderRepository.create(reminder).map { Unit }
        }

        is AutomationAction.PrepareFollowUp -> {
            val now = clock.now()
            val task = Task(
                title = "Follow-up prepared: ${action.targetName}",
                notes = action.notes,
                createdAt = now,
                updatedAt = now,
            )
            taskRepository.create(task).map { Unit }
        }

        is AutomationAction.PrepareInvoiceReminder -> {
            val now = clock.now()
            val task = Task(
                title = "Invoice Reminder Prepared: #${action.invoiceNumber}",
                notes = "Review and confirm invoice #${action.invoiceNumber} before sending.",
                createdAt = now,
                updatedAt = now,
            )
            taskRepository.create(task).map { Unit }
        }

        is AutomationAction.ChangeTaskPriority -> {
            when (val taskRes = taskRepository.byId(action.taskId)) {
                is AppResult.Success -> {
                    val updated = taskRes.value.copy(priority = action.priority)
                    taskRepository.update(updated).map { Unit }
                }
                is AppResult.Failure -> taskRes.map { Unit }
            }
        }

        AutomationAction.GenerateDailyBrief -> {
            getDailyBriefUseCase().map { Unit }
        }

        AutomationAction.GenerateEveningReview -> {
            eveningReviewEngine.review(clock.today()).map { Unit }
        }
    }

    private inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

    companion object {
        private const val MAX_CALL_DEPTH = 3
        private const val WINDOW_MS = 60_000L
        private const val MAX_RUNS_PER_WINDOW = 3
    }
}
