package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.repository.PaymentRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Recording money that arrived.
 *
 * The three refusals worth knowing about, all of which happen before anything is written:
 *
 * - **A different currency from the invoice.** Mira holds no exchange rates, has no way to get
 *   one offline, and a rate invented at the moment of recording would be baked into Abu's books
 *   for good. Refusing is the smaller error.
 * - **More than is outstanding.** There is no credit note in this app, so an overpayment would
 *   have to be stored as an invoice that is PAID while quietly holding money it cannot return.
 *   The payment is refused and the outstanding figure reported instead.
 * - **A zero or negative amount.** A refund is a different record, not a payment with a minus.
 *
 * Where the payment does settle an invoice, the insert, the invoice's paid figure and its new
 * status move together in one transaction, so an invoice can never end up carrying a payment it
 * has not counted.
 */
class RecordPaymentUseCase @Inject constructor(
    private val payments: PaymentRepository,
    private val activityLog: com.rayluxeo.aida.domain.repository.ActivityLogRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        amount: Money,
        method: PaymentMethod = PaymentMethod.OTHER,
        invoiceId: Long? = null,
        clientId: Long? = null,
        receivedAt: Instant? = null,
        reference: String? = null,
        note: String? = null,
    ): AppResult<Payment> {
        if (amount.amount.signum() <= 0) {
            return failure(MiraErrorCode.VALIDATION, "A payment must be more than zero")
        }
        val now = clock.now()
        val result = payments.record(
            Payment(
                invoiceId = invoiceId,
                clientId = clientId,
                amount = amount,
                method = method,
                receivedAt = receivedAt ?: now,
                reference = reference?.trim()?.takeIf { it.isNotEmpty() },
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            )
        )
        if (result is AppResult.Success) {
            activityLog.record(
                com.rayluxeo.aida.domain.model.ActivityEntry(
                    category = com.rayluxeo.aida.domain.model.ActivityCategory.MONEY,
                    action = "payment.recorded",
                    summary = "Payment recorded: ${amount.amount} ${amount.currency.currencyCode}",
                    subject = com.rayluxeo.aida.domain.model.ActivitySubject("payments", result.value.id),
                    occurredAt = now,
                )
            )
        }
        return result
    }
}
