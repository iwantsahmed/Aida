package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.dao.InvoiceWithItems
import com.rayluxeo.aida.data.db.dao.QuoteWithItems
import com.rayluxeo.aida.data.db.entity.InvoiceEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.data.db.entity.QuoteEntity
import com.rayluxeo.aida.data.db.entity.QuoteItemEntity
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteLine
import java.time.Instant
import java.time.LocalDate

/**
 * The boundary where a stored invoice or quote becomes a domain document.
 *
 * Three representations change here and nowhere else:
 *
 * - Money is a whole number of minor units on disk and a [com.rayluxeo.aida.domain.model.Money]
 *   in the domain. The document's own `currencyCode` is what tells a bare `Long` which currency
 *   it is counting, so it is read once per document and applied to every amount on it, lines
 *   included. Nothing here is ever a `Double`.
 * - A date with no time of day is an epoch day, so "due on the 3rd" cannot drift when Abu's
 *   phone changes timezone.
 * - An instant is epoch millis, keeping the indexed time columns primitive.
 *
 * Lines arrive from Room in join order and leave here in position order.
 */
internal fun InvoiceWithItems.toDomain(): Invoice {
    val code = invoice.currencyCode
    return Invoice(
        id = invoice.id,
        uuid = invoice.uuid,
        number = invoice.number,
        clientId = invoice.clientId,
        projectId = invoice.projectId,
        status = invoice.status,
        issuedOn = LocalDate.ofEpochDay(invoice.issuedOnEpochDay),
        dueOn = LocalDate.ofEpochDay(invoice.dueOnEpochDay),
        subtotal = MoneyAmount.fromMinorUnits(invoice.subtotalMinor, code),
        discount = MoneyAmount.fromMinorUnits(invoice.discountMinor, code),
        tax = MoneyAmount.fromMinorUnits(invoice.taxMinor, code),
        total = MoneyAmount.fromMinorUnits(invoice.totalMinor, code),
        amountPaid = MoneyAmount.fromMinorUnits(invoice.amountPaidMinor, code),
        notes = invoice.notes,
        lines = items.sortedBy { it.position }.map { it.toDomain(code) },
        deletedAt = invoice.deletedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(invoice.createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(invoice.updatedAtEpochMillis),
    )
}

internal fun InvoiceItemEntity.toDomain(currencyCode: String): InvoiceLine = InvoiceLine(
    id = id,
    description = description,
    quantity = MoneyAmount.fromStoredQuantity(quantityMilli),
    unitPrice = MoneyAmount.fromMinorUnits(unitPriceMinor, currencyCode),
    taxRatePercent = MoneyAmount.fromBasisPoints(taxRateBasisPoints),
    lineTotal = MoneyAmount.fromMinorUnits(lineTotalMinor, currencyCode),
    position = position,
)

internal fun Invoice.toEntity(): InvoiceEntity = InvoiceEntity(
    id = id,
    uuid = uuid,
    number = number,
    clientId = clientId,
    projectId = projectId,
    status = status,
    issuedOnEpochDay = issuedOn.toEpochDay(),
    dueOnEpochDay = dueOn.toEpochDay(),
    currencyCode = currency.currencyCode,
    subtotalMinor = MoneyAmount.toMinorUnits(subtotal),
    discountMinor = MoneyAmount.toMinorUnits(discount),
    taxMinor = MoneyAmount.toMinorUnits(tax),
    totalMinor = MoneyAmount.toMinorUnits(total),
    amountPaidMinor = MoneyAmount.toMinorUnits(amountPaid),
    notes = notes,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/** [invoiceId] is supplied by the DAO transaction, because a line cannot know it before the insert. */
internal fun InvoiceLine.toEntity(invoiceId: Long): InvoiceItemEntity = InvoiceItemEntity(
    id = id,
    invoiceId = invoiceId,
    description = description,
    quantityMilli = MoneyAmount.toStoredQuantity(quantity),
    unitPriceMinor = MoneyAmount.toMinorUnits(unitPrice),
    taxRateBasisPoints = MoneyAmount.toBasisPoints(taxRatePercent),
    lineTotalMinor = MoneyAmount.toMinorUnits(lineTotal),
    position = position,
)

internal fun QuoteWithItems.toDomain(): Quote {
    val code = quote.currencyCode
    return Quote(
        id = quote.id,
        uuid = quote.uuid,
        number = quote.number,
        clientId = quote.clientId,
        projectId = quote.projectId,
        status = quote.status,
        issuedOn = LocalDate.ofEpochDay(quote.issuedOnEpochDay),
        validUntil = quote.validUntilEpochDay?.let(LocalDate::ofEpochDay),
        subtotal = MoneyAmount.fromMinorUnits(quote.subtotalMinor, code),
        discount = MoneyAmount.fromMinorUnits(quote.discountMinor, code),
        tax = MoneyAmount.fromMinorUnits(quote.taxMinor, code),
        total = MoneyAmount.fromMinorUnits(quote.totalMinor, code),
        convertedInvoiceId = quote.convertedInvoiceId,
        notes = quote.notes,
        lines = items.sortedBy { it.position }.map { it.toDomain(code) },
        deletedAt = quote.deletedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(quote.createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(quote.updatedAtEpochMillis),
    )
}

internal fun QuoteItemEntity.toDomain(currencyCode: String): QuoteLine = QuoteLine(
    id = id,
    description = description,
    quantity = MoneyAmount.fromStoredQuantity(quantityMilli),
    unitPrice = MoneyAmount.fromMinorUnits(unitPriceMinor, currencyCode),
    taxRatePercent = MoneyAmount.fromBasisPoints(taxRateBasisPoints),
    lineTotal = MoneyAmount.fromMinorUnits(lineTotalMinor, currencyCode),
    position = position,
)

internal fun Quote.toEntity(): QuoteEntity = QuoteEntity(
    id = id,
    uuid = uuid,
    number = number,
    clientId = clientId,
    projectId = projectId,
    status = status,
    issuedOnEpochDay = issuedOn.toEpochDay(),
    validUntilEpochDay = validUntil?.toEpochDay(),
    currencyCode = currency.currencyCode,
    subtotalMinor = MoneyAmount.toMinorUnits(subtotal),
    discountMinor = MoneyAmount.toMinorUnits(discount),
    taxMinor = MoneyAmount.toMinorUnits(tax),
    totalMinor = MoneyAmount.toMinorUnits(total),
    convertedInvoiceId = convertedInvoiceId,
    notes = notes,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun QuoteLine.toEntity(quoteId: Long): QuoteItemEntity = QuoteItemEntity(
    id = id,
    quoteId = quoteId,
    description = description,
    quantityMilli = MoneyAmount.toStoredQuantity(quantity),
    unitPriceMinor = MoneyAmount.toMinorUnits(unitPrice),
    taxRateBasisPoints = MoneyAmount.toBasisPoints(taxRatePercent),
    lineTotalMinor = MoneyAmount.toMinorUnits(lineTotal),
    position = position,
)

/**
 * The one invariant the stored totals exist to carry: every figure on the header is exactly the
 * figure its lines produce.
 *
 * This is integer arithmetic on the values about to be written, not a second implementation of
 * the pricing rules — it cannot round, so it cannot disagree with the use case that did. A
 * document that fails it is refused rather than stored, because a total that quietly disagrees
 * with its own lines is the one defect nobody notices until Abu is reading it aloud to a client.
 */
internal fun documentTotalsAgree(
    lineTotalsMinor: List<Long>,
    subtotalMinor: Long,
    discountMinor: Long,
    taxMinor: Long,
    totalMinor: Long,
): Boolean {
    val summed = lineTotalsMinor.fold(0L) { running, line -> running + line }
    return summed == subtotalMinor && totalMinor == subtotalMinor - discountMinor + taxMinor
}
