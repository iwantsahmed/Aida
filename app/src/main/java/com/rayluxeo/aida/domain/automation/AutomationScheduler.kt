package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.repository.AutomationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationScheduler @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val automationEngine: AutomationEngine,
) {
    suspend fun checkAndTrigger(triggerKind: AutomationTrigger): List<AppResult<AutomationRun>> {
        val enabledAutomations = runCatching { automationRepository.observeEnabled().first() }.getOrDefault(emptyList())
        val matching = enabledAutomations.filter { it.trigger.kind == triggerKind }

        val results = mutableListOf<AppResult<AutomationRun>>()
        for (automation in matching) {
            val result = automationEngine.executeAutomation(automation)
            results.add(result)
        }
        return results
    }

    suspend fun triggerAppStartAutomations(): List<AppResult<AutomationRun>> =
        checkAndTrigger(AutomationTrigger.APP_START)

    /**
     * Fires the notification-posted rules that match this notification. Unlike [checkAndTrigger], the
     * config is consulted, not just the kind: a rule filtered to one app or a title phrase runs only
     * when both filters match ([AutomationTriggerConfig.NotificationPosted.matches]). Called from the
     * opt-in notification-listener service; the title is used for the match and nothing else.
     */
    suspend fun checkAndTriggerNotification(
        fromPackage: String,
        title: String?,
    ): List<AppResult<AutomationRun>> {
        val enabled = runCatching { automationRepository.observeEnabled().first() }.getOrDefault(emptyList())
        val matching = enabled.filter { automation ->
            val config = automation.trigger
            config is AutomationTriggerConfig.NotificationPosted && config.matches(fromPackage, title)
        }
        return matching.map { automationEngine.executeAutomation(it) }
    }
}
