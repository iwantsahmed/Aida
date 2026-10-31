package com.rayluxeo.aida.data.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler
import com.rayluxeo.aida.domain.automation.AutomationEngine
import com.rayluxeo.aida.domain.repository.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where a time-based automation's alarm lands. It runs the one automation the alarm named, then
 * re-arms the next occurrence through [AutomationAlarmScheduler.sync] — which, for a recurring rule,
 * schedules tomorrow (or the next interval), and for a one-shot date leaves nothing behind.
 *
 * The re-arm sits in the `finally` for the same reason the reminder receiver's does: a recurring rule
 * must come back even if this run threw, or it would fire exactly once and then stop for good.
 */
@AndroidEntryPoint
class AutomationAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: AutomationEngine

    @Inject lateinit var scheduler: AutomationAlarmScheduler

    @Inject lateinit var repository: AutomationRepository

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() — the Hilt plugin injects that call into the bytecode (§7.9).
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val automationId = intent.getLongExtra(EXTRA_AUTOMATION_ID, 0L)
        if (automationId <= 0L) return

        val pending = goAsync()
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:automation")?.apply {
            acquire(30_000L)
        }
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                val automation = (repository.byId(automationId) as? AppResult.Success)?.value
                try {
                    if (automation != null) engine.executeAutomation(automation)
                } finally {
                    // Re-arm from the automation's current state, so a rule edited or disabled since it
                    // was armed arms its new time, or stops. A row that could not be read arms nothing,
                    // which is the safe failure: a silent rule beats a wrong one.
                    if (automation != null) scheduler.sync(automation)
                }
            } finally {
                try {
                    wakeLock?.let { if (it.isHeld) it.release() }
                } catch (_: Exception) {}
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rayluxeo.aida.action.AUTOMATION_ALARM_FIRE"
        const val EXTRA_AUTOMATION_ID = "com.rayluxeo.aida.extra.AUTOMATION_ID"
    }
}
