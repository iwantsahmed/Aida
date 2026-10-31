package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The statement is the one place a client's whole standing is totalled, so the three decisions that
 * could quietly be wrong — which invoices count, how the money adds up, and what happens when the
 * currencies do not match — are each pinned here. Hand-written fakes, no Hilt (§7.4).
 */
class BuildClientStatementUseCaseTest {

    private val invoices = FakeStatementInvoices()
    private val useCase = BuildClientStatementUseCase(invoices)

    private val client = Client(
        id = 7L,
        name = "Ahmed Traders",
        createdAt = NOW,
        updatedAt = NOW,
    )

    @Test
    fun `a client with no issued invoices reports nothing, not a zero`() = runTest {
        invoices.rows += invoice(InvoiceStatus.DRAFT, total = "50000", paid = "0")

        val statement = (useCase(client) as AppResult.Success).value

        assertThat(statement.invoiceCount).isEqualTo(0)
        assertThat(statement.totalInvoiced).isNull()
        assertThat(statement.outstanding).isNull()
        assertThat(statement.currencyMixed).isFalse()
    }

    @Test
    fun `issued invoices are totalled and drafts and voids are left out`() = runTest {
        invoices.rows += invoice(InvoiceStatus.SENT, total = "50000", paid = "20000")
        invoices.rows += invoice(InvoiceStatus.PAID, total = "30000", paid = "30000")
        invoices.rows += invoice(InvoiceStatus.OVERDUE, total = "10000", paid = "0")
        // Neither of these counts toward what the client owes.
        invoices.rows += invoice(InvoiceStatus.DRAFT, total = "99999", paid = "0")
        invoices.rows += invoice(InvoiceStatus.VOID, total = "88888", paid = "0")

        val statement = (useCase(client) as AppResult.Success).value

        assertThat(statement.invoiceCount).isEqualTo(3)
        assertThat(statement.totalInvoiced).isEqualTo(Money.of("90000"))
        assertThat(statement.totalPaid).isEqualTo(Money.of("50000"))
        assertThat(statement.outstanding).isEqualTo(Money.of("40000"))
        assertThat(statement.currencyMixed).isFalse()
    }

    @Test
    fun `invoices in more than one currency are reported as mixed, never summed`() = runTest {
        invoices.rows += invoice(InvoiceStatus.SENT, total = "50000", paid = "0")
        invoices.rows += invoice(InvoiceStatus.SENT, total = "500", paid = "0", currency = USD)

        val statement = (useCase(client) as AppResult.Success).value

        assertThat(statement.currencyMixed).isTrue()
        assertThat(statement.invoiceCount).isEqualTo(2)
        assertThat(statement.totalInvoiced).isNull()
        assertThat(statement.outstanding).isNull()
    }

    private fun invoice(
        status: InvoiceStatus,
        total: String,
        paid: String,
        currency: Currency = Money.BDT,
    ): Invoice {
        val zero = Money.zero(currency)
        val totalMoney = Money.of(total, currency)
        return Invoice(
            number = "INV-TEST",
            clientId = client.id,
            status = status,
            issuedOn = LocalDate.of(2026, 10, 1),
            dueOn = LocalDate.of(2026, 10, 15),
            subtotal = totalMoney,
            discount = zero,
            tax = zero,
            total = totalMoney,
            amountPaid = Money.of(paid, currency),
            lines = emptyList(),
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    private class FakeStatementInvoices : InvoiceRepository {
        val rows = mutableListOf<Invoice>()
        override fun observeForClient(clientId: Long): Flow<List<Invoice>> =
            flowOf(rows.filter { it.clientId == clientId })

        override fun observeAll(): Flow<List<Invoice>> = error("unused")
        override fun observeUnpaid(): Flow<List<Invoice>> = error("unused")
        override fun observeOverdue(): Flow<List<Invoice>> = error("unused")
        override suspend fun byId(id: Long) = error("unused")
        override suspend fun byNumber(number: String) = error("unused")
        override suspend fun search(query: String?) = error("unused")
        override suspend fun create(invoice: Invoice) = error("unused")
        override suspend fun createWithNextNumber(invoice: Invoice) = error("unused")
        override suspend fun update(invoice: Invoice) = error("unused")
        override suspend fun setStatus(id: Long, status: InvoiceStatus) = error("unused")
        override suspend fun softDelete(id: Long) = error("unused")
        override suspend fun restore(id: Long) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-16T09:00:00Z")
        val USD: Currency = Currency.getInstance("USD")
    }
}
