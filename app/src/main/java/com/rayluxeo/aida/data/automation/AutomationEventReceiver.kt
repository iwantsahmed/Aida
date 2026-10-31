package com.rayluxeo.aida.data.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.automation.AutomationScheduler
import com.rayluxeo.aida.domain.model.AutomationTrigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where a device event becomes an automation run.
 *
 * The charger going in or out is a system broadcast the OS delivers even while Mira is closed, so
 * this is the first thing that ever gives [AutomationScheduler] a live caller: it maps the action to
 * a trigger kind and lets the scheduler fan it out to the enabled automations that asked for it. Each
 * one still passes the engine's full gate — validate, rate-limit, log — so nothing a charger does can
 * silently send, delete or pay (that is the engine's and the validator's job, not this receiver's).
 *
 * Only broadcasts Android still lets an app declare in the manifest are handled here
 * (`ACTION_POWER_CONNECTED`/`DISCONNECTED`); the many events that can only be received while a
 * process is already running belong to a later increment with a foreground presence.
 */
@AndroidEntryPoint
class AutomationEventReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: AutomationScheduler

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() — the Hilt plugin injects that call into the bytecode (§7.9).
    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> AutomationTrigger.POWER_CONNECTED
            Intent.ACTION_POWER_DISCONNECTED -> AutomationTrigger.POWER_DISCONNECTED
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                scheduler.checkAndTrigger(kind)
            } finally {
                pending.finish()
            }
        }
    }
}
