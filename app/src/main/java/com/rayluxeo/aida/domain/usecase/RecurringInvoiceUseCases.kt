package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.RecurringInvoiceProfile
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.RecurringInvoiceRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Creates and stores a recurring schedule pointing at an existing invoice.
 *
 * If no source invoice number is specified, it attaches to the most recent invoice issued.
 */
class CreateRecurringInvoiceProfileUseCase @Inject constructor(
    private val invoices: InvoiceRepository,
    private val recurringRepo: RecurringInvoiceRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        sourceInvoiceNumber: String? = null,
        cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
        customDays: Int? = null,
    ): AppResult<RecurringInvoiceProfile> {
        val invoice: Invoice = if (sourceInvoiceNumber != null) {
            when (val found = invoices.byNumber(sourceInvoiceNumber)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> found.value
            }
        } else {
            // Find most recent invoice
            when (val search = invoices.search(null)) {
                is AppResult.Failure -> return search
                is AppResult.Success -> {
                    search.value.maxByOrNull { it.issuedOn.toEpochDay() }
                        ?: return failure(MiraErrorCode.NOT_FOUND, "No existing invoice found to base recurring schedule on")
                }
            }
        }

        val today = clock.today()
        val nextRun = advance(today, cycle, customDays)
        val profile = RecurringInvoiceProfile(
            id = UUID.randomUUID().toString(),
            sourceInvoiceId = invoice.id,
            sourceInvoiceNumber = invoice.number,
            clientId = invoice.clientId,
            cycle = cycle,
            customDays = customDays,
            nextRunDate = nextRun,
            active = true,
        )

        return recurringRepo.save(profile)
    }

    private fun advance(base: LocalDate, cycle: SubscriptionCycle, customDays: Int?): LocalDate = when (cycle) {
        SubscriptionCycle.WEEKLY -> base.plusWeeks(1)
        SubscriptionCycle.MONTHLY -> base.plusMonths(1)
        SubscriptionCycle.QUARTERLY -> base.plusMonths(3)
        SubscriptionCycle.YEARLY -> base.plusYears(1)
        SubscriptionCycle.CUSTOM_DAYS -> base.plusDays((customDays ?: 30).toLong())
    }
}

/**
 * Sweeps all recurring invoice schedules, creating new draft invoices for any schedule due.
 *
 * Rule: MIRA NEVER AUTOMATICALLY SENDS RECURRING INVOICES.
 * She generates a fresh DRAFT and raises an on-device reminder for Abu to inspect.
 */
class ProcessRecurringInvoicesUseCase @Inject constructor(
    private val recurringRepo: RecurringInvoiceRepository,
    private val invoices: InvoiceRepository,
    private val reminders: ReminderRepository,
    private val totals: BuildInvoiceTotalsUseCase,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(): AppResult<List<Invoice>> {
        val profilesResult = recurringRepo.getAll()
        val profiles = when (profilesResult) {
            is AppResult.Failure -> return profilesResult
            is AppResult.Success -> profilesResult.value
        }

        val today = clock.today()
        val now = clock.now()
        val createdInvoices = mutableListOf<Invoice>()

        for (profile in profiles) {
            if (!profile.active || profile.nextRunDate.isAfter(today)) continue

            // Load source invoice template
            val sourceInvoice = when (val res = invoices.byId(profile.sourceInvoiceId)) {
                is AppResult.Success -> res.value
                is AppResult.Failure -> {
                    // Try by number fallback
                    when (val numRes = invoices.byNumber(profile.sourceInvoiceNumber)) {
                        is AppResult.Success -> numRes.value
                        is AppResult.Failure -> null
                    }
                }
            }

            if (sourceInvoice == null) {
                // The template invoice is gone; leave the profile untouched and try again next sweep.
                continue
            }

            val newLines = sourceInvoice.lines.map { line ->
                InvoiceLine(
                    id = 0L,
                    description = line.description,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    taxRatePercent = line.taxRatePercent,
                    lineTotal = line.lineTotal,
                    position = line.position,
                )
            }

            val draft = Invoice(
                number = "",
                clientId = sourceInvoice.clientId,
                projectId = sourceInvoice.projectId,
                status = InvoiceStatus.DRAFT,
                issuedOn = today,
                dueOn = today.plusDays(14),
                subtotal = sourceInvoice.subtotal,
                discount = sourceInvoice.discount,
                tax = sourceInvoice.tax,
                total = sourceInvoice.total,
                amountPaid = Money.zero(sourceInvoice.currency),
                notes = sourceInvoice.notes,
                lines = newLines,
                createdAt = now,
                updatedAt = now,
            )

            val created = totals(draft).flatMap { priced ->
                invoices.createWithNextNumber(priced)
            }

            when (created) {
                is AppResult.Success -> {
                    val invoice = created.value
                    createdInvoices += invoice

                    // Notify Abu with an on-device reminder
                    val reminder = Reminder(
                        title = "Draft invoice ${invoice.number} created from recurring schedule. Review before sending.",
                        triggerAt = now,
                        kind = ReminderKind.INVOICE,
                        linkedInvoiceId = invoice.id,
                        linkedClientId = invoice.clientId,
                        createdAt = now,
                    )
                    reminders.create(reminder)

                    // Advance from the profile's own scheduled date, not from `today` — advancing from
                    // the run date would drift the billing anchor later every time the daily sweep is
                    // deferred. Then persist the advance IMMEDIATELY, coupled to this invoice: a draft is
                    // already committed, so if the date advance is not saved, tomorrow's sweep would see
                    // the profile still due and regenerate a duplicate draft. On a save failure the use
                    // case fails so the worker retries, and the date-gate makes the retry safe — every
                    // profile already advanced is skipped.
                    val nextDate = advance(profile.nextRunDate, profile.cycle, profile.customDays)
                    when (val saved = recurringRepo.save(profile.copy(nextRunDate = nextDate))) {
                        is AppResult.Failure -> return AppResult.Failure(saved.error)
                        is AppResult.Success -> Unit
                    }
                }
                // A draft that could not be created is left for the next sweep; nothing to persist.
                is AppResult.Failure -> Unit
            }
        }

        return AppResult.Success(createdInvoices)
    }

    private fun advance(base: LocalDate, cycle: SubscriptionCycle, customDays: Int?): LocalDate = when (cycle) {
        SubscriptionCycle.WEEKLY -> base.plusWeeks(1)
        SubscriptionCycle.MONTHLY -> base.plusMonths(1)
        SubscriptionCycle.QUARTERLY -> base.plusMonths(3)
        SubscriptionCycle.YEARLY -> base.plusYears(1)
        SubscriptionCycle.CUSTOM_DAYS -> base.plusDays((customDays ?: 30).toLong())
    }
}
