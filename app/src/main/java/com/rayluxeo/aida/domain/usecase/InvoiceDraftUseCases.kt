package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resolves a spoken client reference — "Ahmed", "Ahmed Traders" — to exactly one client.
 *
 * Mira never picks between two people with similar names, because the wrong one on an invoice is
 * a bill sent to a stranger. Several matches are reported as [Resolution.Ambiguous] for the
 * caller to ask about, and none as [Resolution.None] rather than as a new client created on the
 * quiet: Abu asking to bill somebody he has never entered is a mistake, not an instruction.
 *
 * Only live, unarchived clients are searched. An archived client is one Abu has deliberately put
 * out of the way, and reaching him again is a decision made on the clients screen.
 */
class ResolveClientUseCase @Inject constructor(private val clients: ClientRepository) {

    sealed interface Resolution {
        data class Exact(val client: Client) : Resolution
        data class Ambiguous(val candidates: List<Client>) : Resolution
        data object None : Resolution
    }

    suspend operator fun invoke(target: ClientTarget): AppResult<Resolution> = when (target) {
        is ClientTarget.ById -> clients.byId(target.id).map { Resolution.Exact(it) }
        is ClientTarget.ByName -> clients.search(target.name).map { narrow(target.name, it) }
    }

    /**
     * A full-text search matches on company and notes too, so "Ahmed" can legitimately return
     * three people. One whose name is exactly what Abu said is taken as the one he meant; that is
     * the only case where a multi-match is resolved rather than asked about.
     */
    private fun narrow(spoken: String, matches: List<Client>): Resolution {
        if (matches.isEmpty()) return Resolution.None
        if (matches.size == 1) return Resolution.Exact(matches.first())
        val needle = spoken.trim().lowercase()
        val named = matches.filter { it.name.trim().lowercase() == needle }
        return if (named.size == 1) {
            Resolution.Exact(named.first())
        } else {
            Resolution.Ambiguous(matches.take(MAX_CANDIDATES))
        }
    }

    private companion object {
        /** More names than this read back as a list nobody can hold in their head. */
        const val MAX_CANDIDATES = 5
    }
}

/**
 * Turns "invoice Ahmed for fifty thousand taka" into a stored draft.
 *
 * Three things are settled here rather than at the dispatcher seam, because each is a decision
 * with a reason behind it:
 *
 * - **The draft always has one line.** An invoice with no lines is refused by
 *   [InvoiceRepository], and rightly — a bill with nothing on it charges for nothing. What Abu
 *   said in passing becomes that single line, at quantity one and no tax, and he prices the rest
 *   in the editor.
 * - **A draft raised without an amount is still a draft.** Its one line sits at zero until he
 *   fills it in. Refusing to create it would mean Mira could only take an invoice he had already
 *   worked out, which is the part he does not need her for.
 * - **The line carries no invented description.** If Abu did not say what the work was, the line
 *   says nothing; a stand-in like "services" would be one language's word printed on his bill and
 *   would look, to him, like a figure he had already entered.
 *
 * The totals are never written by hand — [BuildInvoiceTotalsUseCase] prices the document, and the
 * repository refuses anything whose stored totals disagree with its own lines.
 */
class CreateInvoiceDraftUseCase @Inject constructor(
    private val invoices: InvoiceRepository,
    private val totals: BuildInvoiceTotalsUseCase,
    private val activityLog: com.rayluxeo.aida.domain.repository.ActivityLogRepository,
    private val profile: UserProfileRepository,
    private val clock: MiraClock,
) {

    /**
     * [amount] is in whatever currency Abu named, and that wins over the client's usual billing
     * currency: he said it out loud about this bill. The client's currency is the next fallback,
     * and below that Abu's own default currency from Settings — only a phone with no profile at all
     * falls back to the taka.
     */
    suspend operator fun invoke(
        client: Client,
        amount: Money?,
        description: String?,
        dueOn: LocalDate? = null,
    ): AppResult<Invoice> {
        val preferredCurrency = profile.current().getOrNull()?.defaultCurrency ?: Money.BDT
        val currency = amount?.currency ?: client.billing?.currency ?: preferredCurrency
        val zero = Money.zero(currency)
        val issuedOn = clock.today()
        val now = clock.now()
        val draft = Invoice(
            number = "",
            clientId = client.id,
            status = InvoiceStatus.DRAFT,
            issuedOn = issuedOn,
            dueOn = dueOn ?: issuedOn.plusDays(DEFAULT_TERM_DAYS),
            subtotal = zero,
            discount = zero,
            tax = zero,
            total = zero,
            amountPaid = zero,
            lines = listOf(
                InvoiceLine(
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
            invoices.createWithNextNumber(priced).map { created ->
                activityLog.record(
                    com.rayluxeo.aida.domain.model.ActivityEntry(
                        category = com.rayluxeo.aida.domain.model.ActivityCategory.MONEY,
                        action = "invoice.created",
                        summary = "Draft invoice ${created.number} created for ${client.name}",
                        subject = com.rayluxeo.aida.domain.model.ActivitySubject("invoices", created.id),
                        occurredAt = now,
                    )
                )
                created
            }
        }
    }

    private companion object {
        /**
         * `dueOn` is not nullable, so a draft raised by voice has to carry some date. A fortnight
         * is a starting point Abu can move in the editor, not a term agreed with anyone.
         */
        const val DEFAULT_TERM_DAYS = 14L
    }
}
