package com.rayluxeo.aida.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.brief.AttentionRules
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.reminder.DerivedReminder
import com.rayluxeo.aida.domain.reminder.DerivedReminderRules
import com.rayluxeo.aida.domain.reminder.DerivedReminderTarget
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MeetingRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The daily sweep: the reminders Mira raises from Abu's own records, which he never typed.
 *
 * An invoice coming due and one that has gone late, a project deadline arriving, a subscription
 * about to charge, a client who has gone quiet. It reads, it asks [DerivedReminderRules] what any
 * of it means, and it writes through [CreateReminderUseCase] — every decision is in the rules,
 * where it can be tested without Android, and nothing is decided here.
 *
 * **Why WorkManager and not an alarm.** Nothing about this sweep turns on a particular minute: it
 * has to happen roughly daily and it has to survive a reboot, and WorkManager gives the second one
 * for free — its queue is on disk and the OS restarts it, so there is nothing for `BootReceiver`
 * to replay. The exact alarms this sweep *creates* are the opposite case. AlarmManager forgets
 * every one of them across a restart, which is exactly why `BootReceiver` still exists and still
 * re-arms the reminder rows.
 *
 * **Running it twice is harmless.** Every reminder it raises is suppressed on the next pass by the
 * rules' occurrence anchor, so a retry after a storage failure re-raises nothing that landed.
 */
@HiltWorker
class DerivedReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val invoices: InvoiceRepository,
    private val projects: ProjectRepository,
    private val subscriptions: SubscriptionRepository,
    private val clients: ClientRepository,
    private val meetings: MeetingRepository,
    private val reminders: ReminderRepository,
    private val createReminder: CreateReminderUseCase,
    private val clock: MiraClock,
) : CoroutineWorker(context, parameters) {

    /**
     * [Result.failure] is never returned, even for a write that did not land.
     *
     * It is the one outcome that gives up for good, and nothing here is permanently wrong: a
     * database that could not be opened this morning will open tomorrow, and the facts are still
     * in the records waiting to be raised. A retry costs a second read; a failure costs Abu the
     * feature silently and for ever.
     */
    override suspend fun doWork(): Result = try {
        sweep()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        // The repositories return their failures as values, so reaching here means a read threw
        // where it was not expected to. It is still only a bad morning, never a dead feature.
        Result.retry()
    }

    private suspend fun sweep(): Result {
        val now = clock.now()
        val today = clock.today()
        val zone = clock.zone()

        val renewals = when (
            val result = subscriptions.dueOnOrBefore(today.plusDays(AttentionRules.RENEWAL_HORIZON_DAYS))
        ) {
            is AppResult.Failure -> return Result.retry()
            is AppResult.Success -> result.value
        }
        // Every state, not just the scheduled ones: a reminder that has already fired, and one Abu
        // cancelled, are both proof that Mira has said this once and must not say it again.
        val existing = when (val result = reminders.listOfKinds(DerivedReminderRules.KINDS)) {
            is AppResult.Failure -> return Result.retry()
            is AppResult.Success -> result.value
        }
        val contact = contactFrom(meetingHistory(today, zone), now)

        val decisions = DerivedReminderRules.decide(
            now = now,
            zone = zone,
            unpaidInvoices = invoices.observeUnpaid().first(),
            openProjects = projects.observe(ProjectFilter.OPEN).first(),
            renewals = renewals,
            clients = clients.observe(ClientFilter.ACTIVE).first(),
            lastContact = contact.lastContact,
            meetingBooked = contact.booked,
            existing = existing,
        )

        return if (raise(decisions)) Result.retry() else Result.success()
    }

    /**
     * Writes each decision, and answers whether storage let Mira down on the way.
     *
     * Every one is attempted before anything is reported: an invoice reminder the database refused
     * must not cost Abu the four that would have been written after it. A refusal that is Mira's
     * own fault — a kind missing the link it needs — is a defect in the rules rather than
     * something a retry can mend, so it does not ask for one.
     */
    private suspend fun raise(decisions: List<DerivedReminder>): Boolean {
        var storageFailed = false
        for (decision in decisions) {
            val written = createReminder(
                title = decision.subject,
                triggerAt = decision.triggerAt,
                kind = decision.kind,
                origin = RecordOrigin.DERIVED,
                linkedInvoiceId = decision.linkIdFor(DerivedReminderTarget.INVOICE),
                linkedClientId = decision.clientId,
                linkedProjectId = decision.linkIdFor(DerivedReminderTarget.PROJECT),
                linkedSubscriptionId = decision.linkIdFor(DerivedReminderTarget.SUBSCRIPTION),
            )
            if (written is AppResult.Failure && written.error.code == MiraErrorCode.STORAGE) {
                storageFailed = true
            }
        }
        return storageFailed
    }

    /** The id of the record this decision is about, for the one link that identifies it. */
    private fun DerivedReminder.linkIdFor(wanted: DerivedReminderTarget): Long? =
        targetId.takeIf { target == wanted }

    /**
     * The whole meeting history plus the near diary, in one range query — the brief reads it the
     * same way and for the same reason: a query per client would cost one for every client Abu has
     * ever had, and the meetings table is small enough to read whole.
     */
    private suspend fun meetingHistory(today: LocalDate, zone: ZoneId): List<Meeting> {
        val until = today
            .plusDays(AttentionRules.UPCOMING_CONTACT_DAYS + 1)
            .atStartOfDay(zone)
            .toInstant()
        return meetings.observeBetween(Instant.EPOCH, until).first()
    }

    private data class Contact(val lastContact: Map<Long, Instant>, val booked: Set<Long>)

    private fun contactFrom(history: List<Meeting>, now: Instant): Contact {
        val lastContact = mutableMapOf<Long, Instant>()
        val booked = mutableSetOf<Long>()
        for (meeting in history) {
            val clientId = meeting.clientId ?: continue
            if (meeting.startsAt.isAfter(now)) {
                booked += clientId
            } else {
                val previous = lastContact[clientId]
                if (previous == null || meeting.startsAt.isAfter(previous)) {
                    lastContact[clientId] = meeting.startsAt
                }
            }
        }
        return Contact(lastContact, booked)
    }

    companion object {
        /**
         * The name the sweep is enqueued under. It is on the worker rather than on the scheduler
         * because it identifies this class's work: renaming one without the other would leave the
         * old periodic request running beside the new one for ever.
         */
        const val UNIQUE_WORK_NAME: String = "com.rayluxeo.aida.work.DERIVED_REMINDERS"
    }
}
