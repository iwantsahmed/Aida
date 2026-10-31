package com.rayluxeo.aida.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rayluxeo.aida.domain.usecase.FireRecordTriggeredAutomationsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * The daily check that fires record-driven automations, built on the same reasoning as
 * [DerivedReminderWorker]: it has to happen roughly daily and survive a reboot, and WorkManager's
 * on-disk queue gives the second for free, so there is nothing for `BootReceiver` to replay. The
 * exact alarms time-of-day automations use are the opposite case and keep their own re-arm.
 *
 * [Result.failure] is never returned, for the same reason the sweep never returns it: a read that
 * failed this morning will succeed tomorrow and the facts are still in the records, so a retry costs
 * a second read while a failure would cost Abu the feature silently. Running it twice is harmless —
 * the fire ledger suppresses every occurrence that already fired.
 */
@HiltWorker
class RecordTriggerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val fireRecordTriggeredAutomations: FireRecordTriggeredAutomationsUseCase,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        fireRecordTriggeredAutomations()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unexpected: Exception) {
        // The use case returns its failures as values, so reaching here means a read threw where it
        // was not expected to. A bad morning, never a dead feature.
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "com.rayluxeo.aida.work.RECORD_TRIGGERS"
    }
}
