package com.rayluxeo.aida.ai.assistant

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConversationContextManagerTest {

    private val contextManager = ConversationContextManager()
    private val now = Instant.ofEpochMilli(1700000000000L)

    @Test
    fun `contextManager updates last client and invoice and resolves pronouns`() {
        val client = Client(
            id = 10L,
            name = "Ahmed",
            createdAt = now,
            updatedAt = now,
        )

        val invoice = Invoice(
            id = 100L,
            number = "INV-001",
            clientId = 10L,
            status = InvoiceStatus.SENT,
            issuedOn = LocalDate.now(),
            dueOn = LocalDate.now().plusDays(14),
            subtotal = Money.of("45000"),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.of("45000"),
            amountPaid = Money.zero(),
            lines = emptyList(),
            createdAt = now,
            updatedAt = now,
        )

        contextManager.updateClient(client)
        contextManager.updateInvoice(invoice)

        val resolved = contextManager.resolveContextualReference("Send it to him")
        assertThat(resolved).isEqualTo("Send invoice INV-001 to Ahmed")
    }

    @Test
    fun `contextManager resolves Bengali pronouns`() {
        val client = Client(id = 10L, name = "Ahmed", createdAt = now, updatedAt = now)
        val invoice = Invoice(
            id = 100L,
            number = "INV-001",
            clientId = 10L,
            status = InvoiceStatus.SENT,
            issuedOn = LocalDate.now(),
            dueOn = LocalDate.now().plusDays(14),
            subtotal = Money.of("45000"),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.of("45000"),
            amountPaid = Money.zero(),
            lines = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        contextManager.updateClient(client)
        contextManager.updateInvoice(invoice)

        val resolved = contextManager.resolveContextualReference("তাকে পাঠিয়ে দাও")
        assertThat(resolved).isEqualTo("Send invoice INV-001 to Ahmed")
    }

    @Test
    fun `contextManager refuses to guess when candidate invoices are ambiguous`() {
        val client = Client(id = 10L, name = "Ahmed", createdAt = now, updatedAt = now)
        val inv1 = Invoice(
            id = 101L,
            number = "INV-001",
            clientId = 10L,
            status = InvoiceStatus.SENT,
            issuedOn = LocalDate.now(),
            dueOn = LocalDate.now().plusDays(14),
            subtotal = Money.of("20000"),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.of("20000"),
            amountPaid = Money.zero(),
            lines = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        val inv2 = inv1.copy(id = 102L, number = "INV-002")

        contextManager.updateClient(client)
        contextManager.setCandidateInvoices(listOf(inv1, inv2))

        val resolved = contextManager.resolveContextualReference("Send it to him")
        // Did not guess! Asks concise question
        assertThat(resolved).isEqualTo("Which invoice for Ahmed")

        // Now user says "first one"
        val resolvedOrdinal = contextManager.resolveContextualReference("first one")
        assertThat(resolvedOrdinal).isEqualTo("Send invoice INV-001 to Ahmed")
    }

    @Test
    fun `contextManager clears state on clear`() {
        contextManager.updateSearchQuery("Ahmed invoice")
        contextManager.clear()

        assertThat(contextManager.currentState().lastSearchQuery).isNull()
    }
}
