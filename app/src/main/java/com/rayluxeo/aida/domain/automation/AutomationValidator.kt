package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationValidator @Inject constructor() {

    fun validate(automation: Automation): AppResult<Automation> {
        val name = automation.name.trim()
        if (name.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Automation name cannot be blank")
        }

        val triggerResult = validateTrigger(automation.trigger)
        if (triggerResult is AppResult.Failure) return triggerResult

        val actionResult = validateAction(automation.action)
        if (actionResult is AppResult.Failure) return actionResult

        return automation.copy(name = name).asSuccess()
    }

    private fun validateTrigger(config: AutomationTriggerConfig): AppResult<Unit> = when (config) {
        is AutomationTriggerConfig.Interval -> {
            if (config.everyMinutes <= 0) {
                failure(MiraErrorCode.VALIDATION, "Interval must be greater than zero minutes")
            } else {
                Unit.asSuccess()
            }
        }
        is AutomationTriggerConfig.Place -> {
            if (config.label.isBlank()) {
                failure(MiraErrorCode.VALIDATION, "Place label is required")
            } else if (config.latitude !in -90.0..90.0 || config.longitude !in -180.0..180.0) {
                failure(MiraErrorCode.VALIDATION, "Invalid place coordinates")
            } else {
                Unit.asSuccess()
            }
        }
        else -> Unit.asSuccess()
    }

    private fun validateAction(action: AutomationAction): AppResult<Unit> = when (action) {
        is AutomationAction.RunUtterance -> {
            val lower = action.utterance.lowercase(Locale.ROOT)
            if (FORBIDDEN_AUTOMATION_VERBS.any { lower.contains(it) }) {
                failure(
                    MiraErrorCode.VALIDATION,
                    "Automations are not permitted to silently send communications, delete records, or record payments.",
                )
            } else {
                Unit.asSuccess()
            }
        }
        is AutomationAction.Notify -> {
            if (action.title.isBlank()) {
                failure(MiraErrorCode.VALIDATION, "Notification title is required")
            } else {
                Unit.asSuccess()
            }
        }
        is AutomationAction.OpenLink -> {
            val url = action.url.trim()
            if (url.isBlank()) {
                failure(MiraErrorCode.VALIDATION, "A link is required")
            } else if (!url.contains("://")) {
                // A bare "example.com" has no scheme for the system to resolve; require one so the
                // action fails here, where Abu can see why, rather than silently at fire time.
                failure(MiraErrorCode.VALIDATION, "A link needs a scheme, like https://")
            } else {
                Unit.asSuccess()
            }
        }
        is AutomationAction.CreateTask -> {
            if (action.title.isBlank()) {
                failure(MiraErrorCode.VALIDATION, "Task title is required")
            } else {
                Unit.asSuccess()
            }
        }
        is AutomationAction.CreateReminder -> {
            if (action.title.isBlank()) {
                failure(MiraErrorCode.VALIDATION, "Reminder title is required")
            } else {
                Unit.asSuccess()
            }
        }
        // A launch with no package is a rule that can never fire; fail here where Abu can see why,
        // not silently at fire time. A global action carries no field to validate, and a
        // notification trigger's filters are both optional, so both fall through to success below.
        is AutomationAction.LaunchApp -> {
            if (action.packageName.isBlank()) {
                failure(MiraErrorCode.VALIDATION, "An app is required")
            } else {
                Unit.asSuccess()
            }
        }
        else -> Unit.asSuccess()
    }

    companion object {
        private val FORBIDDEN_AUTOMATION_VERBS = listOf(
            "send invoice",
            "send document",
            "send message",
            "send email",
            "delete task",
            "delete note",
            "delete client",
            "delete project",
            "delete invoice",
            "record payment",
            "record_payment",
            "pay invoice",
        )
    }
}
