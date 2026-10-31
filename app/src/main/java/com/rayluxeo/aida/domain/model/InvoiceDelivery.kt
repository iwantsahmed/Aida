package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * Method used to share or deliver an invoice.
 */
enum class InvoiceDeliveryMethod {
    EMAIL,
    WHATSAPP,
    SYSTEM_SHARE,
}

/**
 * Lifecycle status of an invoice delivery attempt.
 */
enum class InvoiceDeliveryStatus {
    PREPARED,
    SHARED,
    SENT,
    FAILED,
    CANCELLED,
}

/**
 * An immutable record of an invoice delivery or sharing action.
 */
data class InvoiceDeliveryRecord(
    val id: String,
    val invoiceId: Long,
    val invoiceNumber: String,
    val clientName: String,
    val recipient: String,
    val method: InvoiceDeliveryMethod,
    val status: InvoiceDeliveryStatus,
    val subject: String,
    val message: String,
    val attachmentPath: String?,
    val timestamp: Instant,
    val notes: String? = null,
)

/**
 * Prepared delivery draft ready for preview and user confirmation.
 */
data class InvoiceDeliveryDraft(
    val invoiceId: Long,
    val invoiceNumber: String,
    val clientName: String,
    val recipient: String,
    val method: InvoiceDeliveryMethod,
    val amountFormatted: String,
    val subject: String,
    val message: String,
    val attachmentPath: String?,
)
