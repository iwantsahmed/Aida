package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.Quote
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import javax.inject.Inject

/**
 * Prices an invoice: every line total, then the subtotal, tax and total the header will store.
 *
 * This exists because the totals are **stored**, not recomputed on read — a sent invoice has to
 * keep saying what it said when it was sent — and something has to keep the stored figures and
 * the lines in step. That is real logic with a decision in it, which is why it is a use case and
 * not a line in a repository.
 *
 * The arithmetic is entirely [BigDecimal] and whole minor units. No value is a `Double` at any
 * point, not even briefly.
 *
 * **Where rounding happens, and why there.** A money value is rounded exactly once, HALF_UP, at
 * the moment it becomes an amount in the currency's own scale:
 *
 * - A line total is rounded when quantity times unit price becomes money. It has to be: the line
 *   total is a figure printed on the bill, and if the printed lines were unrounded products
 *   summed and rounded only at the end, the lines Abu shows a client would not add up to the
 *   total he is asking them to pay.
 * - A line's tax is taken from that **already rounded** line total, so the tax on a line always
 *   corresponds to the amount the same line shows, rather than to a fraction of a paisa the
 *   invoice never mentions.
 * - The document discount is applied after tax. It belongs to the document rather than to any
 *   one line, so pushing it into the lines would mean inventing an apportionment and restating
 *   line tax figures that were already agreed.
 *
 * Nothing else rounds, so the sum of the stored line totals is exactly the stored subtotal.
 */
class BuildInvoiceTotalsUseCase @Inject constructor() {

    operator fun invoke(invoice: Invoice): AppResult<Invoice> {
        val currency = invoice.currency
        val inputs = invoice.lines.map {
            MoneyDocumentPricing.LineInput(it.quantity, it.unitPrice, it.taxRatePercent)
        }
        return MoneyDocumentPricing.priceLines(currency, inputs).flatMap { priced ->
            MoneyDocumentPricing.documentTotals(currency, priced, invoice.discount).map { totals ->
                invoice.copy(
                    lines = invoice.lines.mapIndexed { index, line ->
                        line.copy(lineTotal = priced[index].net, position = index)
                    },
                    subtotal = totals.subtotal,
                    tax = totals.tax,
                    total = totals.total,
                )
            }
        }
    }
}

/**
 * The same pricing, for a quote.
 *
 * A quote and an invoice are priced identically on purpose: the number Abu offers has to be the
 * number he later bills, to the paisa, or the conversion from one to the other is an argument
 * waiting to happen. The rules are documented once, on [BuildInvoiceTotalsUseCase].
 */
class BuildQuoteTotalsUseCase @Inject constructor() {

    operator fun invoke(quote: Quote): AppResult<Quote> {
        val currency = quote.currency
        val inputs = quote.lines.map {
            MoneyDocumentPricing.LineInput(it.quantity, it.unitPrice, it.taxRatePercent)
        }
        return MoneyDocumentPricing.priceLines(currency, inputs).flatMap { priced ->
            MoneyDocumentPricing.documentTotals(currency, priced, quote.discount).map { totals ->
                quote.copy(
                    lines = quote.lines.mapIndexed { index, line ->
                        line.copy(lineTotal = priced[index].net, position = index)
                    },
                    subtotal = totals.subtotal,
                    tax = totals.tax,
                    total = totals.total,
                )
            }
        }
    }
}

/**
 * The arithmetic both use cases share, kept in one place so an invoice and the quote it came
 * from can never be priced by two slightly different rules.
 */
private object MoneyDocumentPricing {

    /** A line as it is handed in: what is being charged for, before it knows its own total. */
    class LineInput(
        val quantity: BigDecimal,
        val unitPrice: Money,
        val taxRatePercent: BigDecimal,
    )

    /** A priced line: the amount it shows, and the tax that amount attracts. */
    class PricedLine(val net: Money, val tax: Money)

    class Totals(val subtotal: Money, val tax: Money, val total: Money)

    fun priceLines(currency: Currency, lines: List<LineInput>): AppResult<List<PricedLine>> {
        val priced = ArrayList<PricedLine>(lines.size)
        lines.forEachIndexed { index, line ->
            if (line.unitPrice.currency != currency) {
                return failure(
                    MiraErrorCode.VALIDATION,
                    "Line $index is priced in ${line.unitPrice.currency.currencyCode}, " +
                        "not the document's ${currency.currencyCode}",
                )
            }
            if (!line.unitPrice.isStorable()) {
                return failure(
                    MiraErrorCode.VALIDATION,
                    "Line $index unit price has more decimals than ${currency.currencyCode} holds",
                )
            }
            if (line.quantity.signum() < 0) {
                return failure(MiraErrorCode.VALIDATION, "Line $index has a negative quantity")
            }
            if (line.quantity.stripTrailingZeros().scale() > MoneyAmount.QUANTITY_SCALE) {
                return failure(
                    MiraErrorCode.VALIDATION,
                    "Line $index quantity is finer than ${MoneyAmount.QUANTITY_SCALE} decimal places",
                )
            }
            if (line.taxRatePercent.signum() < 0) {
                return failure(MiraErrorCode.VALIDATION, "Line $index has a negative tax rate")
            }
            if (line.taxRatePercent.stripTrailingZeros().scale() > MoneyAmount.PERCENT_SCALE) {
                return failure(
                    MiraErrorCode.VALIDATION,
                    "Line $index tax rate is finer than ${MoneyAmount.PERCENT_SCALE} decimal places",
                )
            }
            val net = line.unitPrice * line.quantity
            priced += PricedLine(
                net = net,
                tax = net * line.taxRatePercent.movePointLeft(MoneyAmount.PERCENT_SCALE),
            )
        }
        return AppResult.Success(priced)
    }

    fun documentTotals(
        currency: Currency,
        lines: List<PricedLine>,
        discount: Money,
    ): AppResult<Totals> {
        if (discount.currency != currency) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Discount is in ${discount.currency.currencyCode}, not the document's ${currency.currencyCode}",
            )
        }
        if (!discount.isStorable()) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Discount has more decimals than ${currency.currencyCode} holds",
            )
        }
        if (discount.amount.signum() < 0) {
            return failure(MiraErrorCode.VALIDATION, "Discount is negative")
        }
        val zero = Money.zero(currency)
        val subtotal = lines.fold(zero) { running, line -> running + line.net }
        val tax = lines.fold(zero) { running, line -> running + line.tax }
        val total = subtotal - discount + tax
        if (total.amount.signum() < 0) {
            return failure(
                MiraErrorCode.VALIDATION,
                "A discount of ${discount.amount} is more than the document is worth",
            )
        }
        return AppResult.Success(Totals(subtotal = subtotal, tax = tax, total = total))
    }

    /**
     * Whether this amount survives the trip to minor units untouched. An amount carrying more
     * decimals than its currency has is a programming error, and is refused rather than rounded
     * away where nobody would see it happen.
     */
    private fun Money.isStorable(): Boolean =
        amount.setScale(currency.defaultFractionDigits.coerceAtLeast(0), RoundingMode.HALF_UP)
            .compareTo(amount) == 0
}
