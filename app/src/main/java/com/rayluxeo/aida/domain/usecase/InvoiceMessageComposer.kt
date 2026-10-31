package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.UserProfile

/** The subject line and body of the message that carries an invoice to a client. */
data class InvoiceMessageText(val subject: String, val message: String)

/**
 * Words the email / WhatsApp message that carries an invoice to a client, in Abu's language.
 *
 * A port, declared here so [PrepareInvoiceDeliveryUseCase] can assemble an already-worded draft —
 * but the copy itself is text a client reads, so its implementation lives in the UI layer and the
 * wording never enters `domain` (§4.6), the same discipline the NLU and date ports follow. The
 * command handler, which cannot know the language, leaves [PrepareInvoiceDeliveryUseCase] to default
 * it; the UI delivery path passes Abu's real language.
 */
interface InvoiceMessageComposer {
    fun compose(
        language: SpokenLanguage,
        method: InvoiceDeliveryMethod,
        invoice: Invoice,
        client: Client,
        profile: UserProfile?,
    ): InvoiceMessageText
}
