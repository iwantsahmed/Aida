package com.rayluxeo.aida.data.scheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Whether the OS will currently let Mira name a minute, and a stream of the moments that answer
 * changes.
 *
 * The stream is the point of this class. From Android 13 the permission is denied by default and
 * revocable at any time, and when it is revoked the platform stops the app and **cancels every
 * future exact alarm Mira holds** without telling her. Nothing comes back when it is granted
 * again either. A one-off check at startup therefore cannot keep Abu's reminders armed; only
 * reacting to the broadcast can.
 *
 * The receiver is registered at runtime rather than in the manifest because
 * `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` is not delivered to manifest receivers,
 * and it is registered NOT_EXPORTED because the only sender that matters is the system.
 */
@Singleton
class ExactAlarmAccessImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExactAlarmAccess {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    /**
     * Emits only when the platform says the answer changed, never an opening value: a caller that
     * wants the state right now asks [canScheduleExact] for it, and an opening emission would make
     * every collector re-arm Abu's whole reminder list at process start for no reason.
     *
     * Deliberately not de-duplicated. Two consecutive grants can straddle a revoke this process
     * never saw, and re-arming twice costs nothing while dropping a re-arm loses a reminder.
     */
    override val changes: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(canScheduleExact())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose {
            // Unregistering twice throws, and a cancelled scope can race the process teardown
            // that already tore the receiver down. Losing the alarms over tidying up would be
            // a poor trade.
            try {
                context.unregisterReceiver(receiver)
            } catch (alreadyGone: IllegalArgumentException) {
                Unit
            }
        }
    }
}
