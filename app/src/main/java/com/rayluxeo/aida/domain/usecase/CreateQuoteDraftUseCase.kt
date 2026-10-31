package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteLine
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Turns "quote Ahmed for fifty thousand taka" into a stored draft quote.
 *
 * The counterpart to [CreateInvoiceDraftUseCase], and deliberately the same in every decision that
 * file documents: one line carrying whatever Abu said, priced at quantity one and no tax; a quote
 * raised without an amount is still a quote, its one line at zero until he fills it; and no invented
 * description, because a stand-in would look like a figure he had already entered. The pricing goes
 * through [BuildQuoteTotalsUseCase], which prices a quote to the paisa the same way the invoice it may
 * become will be priced — so the conversion is never an argument about a rounded total.
 *
 * It always lands in [QuoteStatus.DRAFT]: a quote is the offer, and offering it to a client is a
 * decision Abu makes on the quotes screen, not one spoken into being.
 */
class CreateQuoteDraftUseCase @Inject constructor(
    private val quotes: QuoteRepository,
    private val totals: BuildQuoteTotalsUseCase,
    private val activityLog: ActivityLogRepository,
    private val profile: UserProfileRepository,
    private val clock: MiraClock,
) {

    /**
     * [amount] is in whatever currency Abu named, and that wins over the client's usual billing
     * currency: he said it out loud about this quote. The client's currency is the next fallback, and
     * below that his own default currency from Settings; only a phone with no profile falls back to
     * the taka.
     */
    suspend operator fun invoke(
        client: Client,
        amount: Money?,
        description: String?,
        validUntil: LocalDate? = null,
    ): AppResult<Quote> {
        val preferredCurrency = profile.current().getOrNull()?.defaultCurrency ?: Money.BDT
        val currency = amount?.currency ?: client.billing?.currency ?: preferredCurrency
        val zero = Money.zero(currency)
        val issuedOn = clock.today()
        val now = clock.now()
        val draft = Quote(
            number = "",
            clientId = client.id,
            status = QuoteStatus.DRAFT,
            issuedOn = issuedOn,
            validUntil = validUntil ?: issuedOn.plusDays(DEFAULT_VALID_DAYS),
            subtotal = zero,
            discount = zero,
            tax = zero,
            total = zero,
            lines = listOf(
                QuoteLine(
                    description = description?.trim().orEmpty(),
                    quantity = BigDecimal.ONE,
                    unitPrice = amount ?: zero,
                    taxRatePercent = BigDecimal.ZERO,
                    lineTotal = zero,
                    position = 0,
                ),
            ),
            createdAt = now,
            updatedAt = now,
        )
        return totals(draft).flatMap { priced ->
            quotes.createWithNextNumber(priced).map { created ->
                activityLog.record(
                    ActivityEntry(
                        category = ActivityCategory.MONEY,
                        action = "quote.created",
                        summary = "Draft quote ${created.number} created for ${client.name}",
                        subject = ActivitySubject("quotes", created.id),
                        occurredAt = now,
                    ),
                )
                created
            }
        }
    }

    private companion object {
        /**
         * `validUntil` can be null on the model, but a quote with no expiry invites a client to hold
         * Abu to a stale price. A month is a starting point he can move in the editor, not a term
         * agreed with anyone — the same spirit as the invoice draft's fortnight term.
         */
        const val DEFAULT_VALID_DAYS = 30L
    }
}
