package com.rayluxeo.aida.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.usecase.ProcessRecurringInvoicesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * The daily sweep that turns a due recurring-invoice profile into a fresh DRAFT, built on the same
 * reasoning as [RecordTriggerWorker] and [DerivedReminderWorker]: it has to happen roughly daily and
 * survive a reboot, and WorkManager's on-disk queue gives the second for free, so there is nothing
 * for `BootReceiver` to replay.
 *
 * Until now [ProcessRecurringInvoicesUseCase] had a single caller — the "run now" button on the
 * Recurring tab — so a monthly invoice only appeared when Abu happened to open that screen and tap it.
 * This is what makes "recurring" actually recurring.
 *
 * It never auto-sends: the use case clones the source invoice to a DRAFT and raises a reminder, so Abu
 * still checks and delivers it himself (the same restraint [CreateInvoiceDraftUseCase] keeps).
 *
 * [Result.retry] rather than [Result.failure] on an unexpected throw, for the same reason the other
 * sweeps use it: a read that failed this morning will succeed tomorrow and the profile is still on
 * file. Running it twice is harmless — the use case only acts on a profile whose next run has actually
 * fallen due, and advances its date once it has.
 */
@HiltWorker
class RecurringInvoiceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val processRecurringInvoices: ProcessRecurringInvoicesUseCase,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        when (processRecurringInvoices()) {
            // A value-level failure means a write (a profile's date advance) did not land, so a draft
            // may have been created without its schedule moving on. Retry: the date-gate skips every
            // profile already advanced, so the retry only finishes the ones that did not.
            is AppResult.Failure -> Result.retry()
            is AppResult.Success -> Result.success()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unexpected: Exception) {
        // A read threw where it was not expected to. A bad morning, never a dead feature.
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "com.rayluxeo.aida.work.RECURRING_INVOICES"
    }
}
