package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import javax.inject.Inject

/**
 * Converts an existing quote into an invoice draft, preserving line items, client, amounts, and notes.
 *
 * Once converted, the quote is marked as converted with the newly minted invoice ID,
 * and its status moves to ACCEPTED in the same atomic write.
 */
class ConvertQuoteToInvoiceUseCase @Inject constructor(
    private val quotes: QuoteRepository,
    private val invoices: InvoiceRepository,
    private val totals: BuildInvoiceTotalsUseCase,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(quoteId: Long): AppResult<Invoice> =
        quotes.byId(quoteId).flatMap { quote -> invoke(quote) }

    suspend operator fun invoke(quote: Quote): AppResult<Invoice> {
        if (quote.isConverted) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Quote ${quote.number} has already been converted into invoice ${quote.convertedInvoiceId}",
            )
        }

        val today = clock.today()
        val now = clock.now()
        val invoiceLines = quote.lines.map { ql ->
            InvoiceLine(
                id = 0L,
                description = ql.description,
                quantity = ql.quantity,
                unitPrice = ql.unitPrice,
                taxRatePercent = ql.taxRatePercent,
                lineTotal = ql.lineTotal,
                position = ql.position,
            )
        }

        val draft = Invoice(
            number = "",
            clientId = quote.clientId,
            projectId = quote.projectId,
            status = InvoiceStatus.DRAFT,
            issuedOn = today,
            dueOn = today.plusDays(DEFAULT_DUE_DAYS),
            subtotal = quote.subtotal,
            discount = quote.discount,
            tax = quote.tax,
            total = quote.total,
            amountPaid = Money.zero(quote.currency),
            notes = quote.notes,
            lines = invoiceLines,
            createdAt = now,
            updatedAt = now,
        )

        return totals(draft).flatMap { priced ->
            invoices.createWithNextNumber(priced).flatMap { created ->
                quotes.markConverted(quote.id, created.id).flatMap {
                    AppResult.Success(created)
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_DUE_DAYS = 14L
    }
}
