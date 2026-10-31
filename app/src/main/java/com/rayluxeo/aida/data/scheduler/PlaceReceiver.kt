package com.rayluxeo.aida.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Abu arriving somewhere.
 *
 * It posts nothing itself. A place is a different *trigger*, not a different reminder, so the
 * arrival is handed to [ReminderReceiver] and fires down exactly the path a named minute fires
 * down: the same notification, the same actions, the same transcript entry. Building a second
 * notification here would be how the two quietly drift apart.
 *
 * The platform raises both edges of a boundary and Mira asked about one of them, so a crossing
 * that is not the one Abu meant is dropped without waking anything else.
 */
@AndroidEntryPoint
class PlaceReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var places: PlaceReminderScheduler

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ARRIVED) return
        // Absent rather than false: a missing extra means this did not come from the platform's
        // proximity alert at all, and reading it as "leaving" would fire an exit reminder.
        if (!intent.hasExtra(LocationManager.KEY_PROXIMITY_ENTERING)) return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        if (entering != intent.getBooleanExtra(EXTRA_ON_ENTER, true)) return

        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, 0L)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        if (placeId <= 0L || reminderId <= 0L) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                // A place reminder is asked for once, so the registration is released before
                // anything else — otherwise a second crossing on the way back out fires it again.
                places.stopWatching(placeId)

                val reminder = reminders.byId(reminderId).getOrNull()
                // A reminder Mira cannot read still fires: the database being unavailable is not a
                // reason to break the promise. One she can read and which is no longer scheduled
                // does not, because Abu has already dealt with it.
                if (reminder != null && reminder.state != ReminderState.SCHEDULED) return@launch
                appContext.sendBroadcast(
                    Intent(appContext, ReminderReceiver::class.java).apply {
                        action = ReminderReceiver.ACTION_FIRE
                        putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                        reminder?.title?.let { putExtra(ReminderReceiver.EXTRA_TITLE, it) }
                    },
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ARRIVED = "com.rayluxeo.aida.action.PLACE_ARRIVED"
        const val EXTRA_PLACE_ID = "com.rayluxeo.aida.extra.PLACE_ID"
        const val EXTRA_REMINDER_ID = "com.rayluxeo.aida.extra.PLACE_REMINDER_ID"

        /** True when Abu asked to be told on arriving, false when he asked on leaving. */
        const val EXTRA_ON_ENTER = "com.rayluxeo.aida.extra.PLACE_ON_ENTER"
    }
}
