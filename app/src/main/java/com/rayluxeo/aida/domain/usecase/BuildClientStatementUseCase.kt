package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientStatement
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Totals a client's issued invoices into a [ClientStatement] — billed, paid, outstanding.
 *
 * Two decisions live here rather than at the call site:
 *
 * - **Only issued invoices count.** A DRAFT or a READY invoice has not been put to the client and a
 *   VOID one has been withdrawn, so none of the three is a sum Abu would read back as "what they
 *   owe". Including a draft would quietly bill the client for a figure Abu was still working out.
 * - **Money is never added across currencies.** If a client has issued invoices in more than one
 *   currency the statement says so and leaves the totals empty, the same refusal
 *   [BuildInvoiceTotalsUseCase] and the reports make — a wrong total on a statement is worse than no
 *   total, because it reads as fact.
 */
class BuildClientStatementUseCase @Inject constructor(
    private val invoices: InvoiceRepository,
) {

    suspend operator fun invoke(client: Client): AppResult<ClientStatement> {
        val issued = invoices.observeForClient(client.id).firstOrNull().orEmpty()
            .filter { it.status in ISSUED_STATUSES }

        if (issued.isEmpty()) {
            return AppResult.Success(
                ClientStatement(
                    clientName = client.name,
                    invoiceCount = 0,
                    totalInvoiced = null,
                    totalPaid = null,
                    outstanding = null,
                    currencyMixed = false,
                ),
            )
        }

        val currencies = issued.map { it.currency }.toSet()
        if (currencies.size > 1) {
            return AppResult.Success(
                ClientStatement(
                    clientName = client.name,
                    invoiceCount = issued.size,
                    totalInvoiced = null,
                    totalPaid = null,
                    outstanding = null,
                    currencyMixed = true,
                ),
            )
        }

        val zero = Money.zero(currencies.first())
        return AppResult.Success(
            ClientStatement(
                clientName = client.name,
                invoiceCount = issued.size,
                totalInvoiced = issued.fold(zero) { running, invoice -> running + invoice.total },
                totalPaid = issued.fold(zero) { running, invoice -> running + invoice.amountPaid },
                outstanding = issued.fold(zero) { running, invoice -> running + invoice.outstanding },
                currencyMixed = false,
            ),
        )
    }

    private companion object {
        /** Invoices that represent a real obligation — put to the client, not withdrawn. */
        val ISSUED_STATUSES = setOf(
            InvoiceStatus.SENT,
            InvoiceStatus.PARTIALLY_PAID,
            InvoiceStatus.PAID,
            InvoiceStatus.OVERDUE,
        )
    }
}
