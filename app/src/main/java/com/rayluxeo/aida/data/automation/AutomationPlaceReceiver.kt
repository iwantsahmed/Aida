package com.rayluxeo.aida.data.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.automation.AutomationEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Abu arriving at a place a rule watches. It runs that one automation through the engine, exactly
 * as a time or power trigger would — the same validate, rate-limit, log and effect.
 *
 * It mirrors [com.rayluxeo.aida.data.scheduler.PlaceReceiver], with one deliberate difference: a
 * place automation is recurring, so the watch is **not** released here — it stays armed and fires
 * again on the next arrival. The engine's own rate limiter absorbs GPS jitter that re-crosses the
 * boundary within a minute.
 */
@AndroidEntryPoint
class AutomationPlaceReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: AutomationEngine

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() — the Hilt plugin injects that call into the bytecode (§7.9).
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ARRIVED) return
        // Absent rather than false: a missing extra means this did not come from a proximity alert.
        if (!intent.hasExtra(LocationManager.KEY_PROXIMITY_ENTERING)) return
        // MVP fires on the ENTER edge only; the EXIT edge is dropped without waking anything.
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return

        val automationId = intent.getLongExtra(EXTRA_AUTOMATION_ID, 0L)
        if (automationId <= 0L) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                engine.triggerAutomation(automationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ARRIVED = "com.rayluxeo.aida.action.AUTOMATION_PLACE_ARRIVED"
        const val EXTRA_AUTOMATION_ID = "com.rayluxeo.aida.extra.AUTOMATION_PLACE_AUTOMATION_ID"
    }
}
