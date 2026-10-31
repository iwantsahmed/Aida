package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.InvoiceDeliveryRecord
import com.rayluxeo.aida.domain.model.InvoiceDeliveryStatus
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.InvoiceDeliveryRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Prepares the recipient, amount and attachment for invoice delivery, and assembles the draft.
 *
 * The subject and body are worded by [InvoiceMessageComposer] — a client-facing message is human
 * copy, and §4.6 keeps all such wording out of `domain`, so this use case holds only the port and
 * the data work (resolving the recipient, formatting the amount). The UI path passes Abu's
 * [language]; the command handler, which is language-agnostic by design, lets it default.
 */
class PrepareInvoiceDeliveryUseCase @Inject constructor(
    private val composer: InvoiceMessageComposer,
) {

    operator fun invoke(
        invoice: Invoice,
        client: Client,
        method: InvoiceDeliveryMethod,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
        profile: UserProfile? = null,
        attachmentFile: File? = null,
    ): InvoiceDeliveryDraft {
        val recipient = when (method) {
            InvoiceDeliveryMethod.EMAIL -> client.email ?: client.phone.orEmpty()
            InvoiceDeliveryMethod.WHATSAPP -> client.phone ?: client.email.orEmpty()
            InvoiceDeliveryMethod.SYSTEM_SHARE -> client.email ?: client.phone.orEmpty()
        }
        val amountStr = "${invoice.currency.currencyCode} ${invoice.total.amount}"
        val text = composer.compose(language, method, invoice, client, profile)

        return InvoiceDeliveryDraft(
            invoiceId = invoice.id,
            invoiceNumber = invoice.number,
            clientName = client.name,
            recipient = recipient,
            method = method,
            amountFormatted = amountStr,
            subject = text.subject,
            message = text.message,
            attachmentPath = attachmentFile?.absolutePath,
        )
    }
}

/**
 * Records an invoice delivery event, updates the invoice status to SENT if confirmed,
 * and logs an immutable audit entry in the activity log.
 */
class RecordInvoiceDeliveryUseCase @Inject constructor(
    private val deliveryRepository: InvoiceDeliveryRepository,
    private val invoiceRepository: InvoiceRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        draft: InvoiceDeliveryDraft,
        status: InvoiceDeliveryStatus,
        notes: String? = null,
    ): AppResult<InvoiceDeliveryRecord> {
        val now = clock.now()
        val record = InvoiceDeliveryRecord(
            id = UUID.randomUUID().toString(),
            invoiceId = draft.invoiceId,
            invoiceNumber = draft.invoiceNumber,
            clientName = draft.clientName,
            recipient = draft.recipient,
            method = draft.method,
            status = status,
            subject = draft.subject,
            message = draft.message,
            attachmentPath = draft.attachmentPath,
            timestamp = now,
            notes = notes,
        )

        // 1. Record in Delivery Repository
        val saveResult = deliveryRepository.record(record)
        if (saveResult is AppResult.Failure) {
            return saveResult
        }

        // 2. If delivery confirmed/shared and invoice was in DRAFT or READY, update status to SENT
        if (status == InvoiceDeliveryStatus.SENT || status == InvoiceDeliveryStatus.SHARED) {
            val currentInvoice = invoiceRepository.byId(draft.invoiceId).getOrNull()
            if (currentInvoice != null && (currentInvoice.status == InvoiceStatus.DRAFT || currentInvoice.status == InvoiceStatus.READY)) {
                invoiceRepository.setStatus(draft.invoiceId, InvoiceStatus.SENT)
            }
        }

        // 3. Log to ActivityLog
        val actionVerb = when (status) {
            InvoiceDeliveryStatus.PREPARED -> "invoice.delivery.prepared"
            InvoiceDeliveryStatus.SHARED -> "invoice.delivery.shared"
            InvoiceDeliveryStatus.SENT -> "invoice.delivery.sent"
            InvoiceDeliveryStatus.FAILED -> "invoice.delivery.failed"
            InvoiceDeliveryStatus.CANCELLED -> "invoice.delivery.cancelled"
        }

        val summary = "Invoice ${draft.invoiceNumber} ${status.name.lowercase()} via ${draft.method.name.lowercase()} to ${draft.clientName}"

        activityLogRepository.record(
            ActivityEntry(
                category = ActivityCategory.MONEY,
                action = actionVerb,
                summary = summary,
                subject = ActivitySubject(table = "invoices", id = draft.invoiceId),
                occurredAt = now,
            ),
        )

        return AppResult.Success(record)
    }
}
