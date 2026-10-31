package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.BanglaNumberParser
import com.rayluxeo.aida.ai.nlu.CommandSlotParser
import com.rayluxeo.aida.ai.nlu.CurrencyExpressionParser
import com.rayluxeo.aida.ai.nlu.EnglishNumberParser
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.ai.nlu.MiraDateTimeParser
import com.rayluxeo.aida.ai.nlu.MiraIntent
import com.rayluxeo.aida.ai.nlu.TimeExpressionParser
import com.rayluxeo.aida.ai.nlu.TitleExtractor
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteLine
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.RecurringInvoiceProfile
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import com.rayluxeo.aida.domain.repository.RecurringInvoiceRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QuoteAndInvoiceSystemTest {

    private val clock = FixedMiraClock(FixedMiraClock.dhaka(2026, 9, 25, 10))
    private val invoiceTotals = BuildInvoiceTotalsUseCase()
    private val quoteTotals = BuildQuoteTotalsUseCase()

    private val fakeInvoices = FakeTestInvoices()
    private val fakeQuotes = FakeTestQuotes()
    private val fakeRecurringRepo = FakeTestRecurringRepo()
    private val fakeReminders = FakeTestReminders()

    // -------------------------------------------------------------------------
    // 1. Quote to Invoice Conversion
    // -------------------------------------------------------------------------

    @Test
    fun `converting a quote to an invoice creates a draft invoice and marks the quote accepted`() = runTest {
        val converter = ConvertQuoteToInvoiceUseCase(fakeQuotes, fakeInvoices, invoiceTotals, clock)

        val quote = Quote(
            id = 1L,
            number = "QUO-2026-001",
            clientId = 42L,
            status = QuoteStatus.SENT,
            issuedOn = LocalDate.of(2026, 9, 20),
            subtotal = Money.of("45000"),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.of("45000"),
            lines = listOf(
                QuoteLine(
                    id = 10L,
                    description = "Website development",
                    quantity = BigDecimal.ONE,
                    unitPrice = Money.of("45000"),
                    taxRatePercent = BigDecimal.ZERO,
                    lineTotal = Money.of("45000"),
                    position = 0,
                ),
            ),
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        fakeQuotes.add(quote)

        val result = converter(1L)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val invoice = (result as AppResult.Success).value

        assertThat(invoice.clientId).isEqualTo(42L)
        assertThat(invoice.status).isEqualTo(InvoiceStatus.DRAFT)
        assertThat(invoice.total).isEqualTo(Money.of("45000.00"))
        assertThat(invoice.lines).hasSize(1)
        assertThat(invoice.lines[0].description).isEqualTo("Website development")
        assertThat(invoice.lines[0].unitPrice).isEqualTo(Money.of("45000.00"))

        // Quote is now accepted and links to new invoice
        val updatedQuote = fakeQuotes.byId(1L).getOrNull()!!
        assertThat(updatedQuote.status).isEqualTo(QuoteStatus.ACCEPTED)
        assertThat(updatedQuote.isConverted).isTrue()
        assertThat(updatedQuote.convertedInvoiceId).isEqualTo(invoice.id)
    }

    @Test
    fun `cannot convert a quote that has already been converted`() = runTest {
        val converter = ConvertQuoteToInvoiceUseCase(fakeQuotes, fakeInvoices, invoiceTotals, clock)

        val quote = Quote(
            id = 2L,
            number = "QUO-2026-002",
            clientId = 42L,
            status = QuoteStatus.ACCEPTED,
            convertedInvoiceId = 99L,
            issuedOn = LocalDate.of(2026, 9, 20),
            subtotal = Money.of("10000"),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.of("10000"),
            lines = emptyList(),
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        fakeQuotes.add(quote)

        val result = converter(2L)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    // -------------------------------------------------------------------------
    // 2. Deterministic Totals & Precise Calculations
    // -------------------------------------------------------------------------

    @Test
    fun `quote totals calculate subtotal tax discount and total deterministically`() {
        val quote = Quote(
            number = "QUO-2026-003",
            clientId = 1L,
            issuedOn = clock.today(),
            subtotal = Money.zero(),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.zero(),
            lines = listOf(
                QuoteLine(
                    description = "Mobile App Design",
                    quantity = BigDecimal("2.5"),
                    unitPrice = Money.of("10000"),
                    taxRatePercent = BigDecimal("5.00"),
                    lineTotal = Money.zero(),
                    position = 0,
                ),
            ),
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )

        val priced = quoteTotals(quote).getOrNull()!!
        assertThat(priced.subtotal).isEqualTo(Money.of("25000.00"))
        assertThat(priced.tax).isEqualTo(Money.of("1250.00"))
        assertThat(priced.total).isEqualTo(Money.of("26250.00"))
    }

    // -------------------------------------------------------------------------
    // 3. Recurring Invoices
    // -------------------------------------------------------------------------

    @Test
    fun `recurring invoice processor generates a draft invoice and reminds Abu without auto-sending`() = runTest {
        val sourceInvoice = Invoice(
            id = 41L,
            number = "INV-2026-041",
            clientId = 7L,
            status = InvoiceStatus.PAID,
            issuedOn = LocalDate.of(2026, 8, 25),
            dueOn = LocalDate.of(2026, 9, 8),
            subtotal = Money.of("30000"),
            discount = Money.zero(),
            tax = Money.zero(),
            total = Money.of("30000"),
            amountPaid = Money.of("30000"),
            lines = listOf(
                InvoiceLine(
                    description = "Monthly Retainer",
                    quantity = BigDecimal.ONE,
                    unitPrice = Money.of("30000"),
                    taxRatePercent = BigDecimal.ZERO,
                    lineTotal = Money.of("30000"),
                    position = 0,
                ),
            ),
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        fakeInvoices.add(sourceInvoice)

        // Today is Sept 25, 2026
        val profile = RecurringInvoiceProfile(
            id = "rec-1",
            sourceInvoiceId = 41L,
            sourceInvoiceNumber = "INV-2026-041",
            clientId = 7L,
            cycle = SubscriptionCycle.MONTHLY,
            nextRunDate = LocalDate.of(2026, 9, 25), // Due today!
            active = true,
        )
        fakeRecurringRepo.save(profile)

        val processor = ProcessRecurringInvoicesUseCase(fakeRecurringRepo, fakeInvoices, fakeReminders, invoiceTotals, clock)
        val generated = processor().getOrNull()!!

        assertThat(generated).hasSize(1)
        val newInvoice = generated.first()

        // MUST be DRAFT, never auto-sent
        assertThat(newInvoice.status).isEqualTo(InvoiceStatus.DRAFT)
        assertThat(newInvoice.issuedOn).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(newInvoice.total).isEqualTo(Money.of("30000.00"))

        // Reminder created for Abu
        assertThat(fakeReminders.created).hasSize(1)
        val reminder = fakeReminders.created.first()
        assertThat(reminder.kind).isEqualTo(ReminderKind.INVOICE)
        assertThat(reminder.title).contains("INV-")

        // Schedule nextRunDate advanced by 1 month to Oct 25, 2026
        val updatedProfile = fakeRecurringRepo.getAll().getOrNull()!!.first()
        assertThat(updatedProfile.nextRunDate).isEqualTo(LocalDate.of(2026, 10, 25))
    }

    // -------------------------------------------------------------------------
    // 4. Voice Commands NLU Parsing
    // -------------------------------------------------------------------------

    private fun createSlotParser(): CommandSlotParser {
        val dateTimes = MiraDateTimeParser(clock)
        val times = TimeExpressionParser(clock)
        val currencies = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser())
        val titles = TitleExtractor()
        return CommandSlotParser(dateTimes, times, currencies, titles, clock, EnglishNumberParser())
    }

    @Test
    fun `parses Ahmed invoice command with 45 hazar taka and due date October 5`() {
        val slotParser = createSlotParser()

        val raw = "Ahmed এর জন্য website development 45 হাজার টাকার invoice বানাও, due October 5."
        val normalized = "ahmed er jonno website development 45 hazar takar invoice banao, due october 5"

        val scan = slotParser.scan(normalized)
        val request = CommandRequest(
            raw = raw,
            normalized = normalized,
            language = SpokenLanguage.BENGALI,
            script = TextScript.BENGALI,
            source = InputSource.VOICE,
        )

        val slotted = slotParser.fill(MiraIntent.CREATE_INVOICE_DRAFT, request, scan)
        assertThat(slotted).isNotNull()

        val command = slotted!!.command as AppCommand.CreateInvoiceDraft
        assertThat((command.client as ClientTarget.ByName).name).isEqualTo("Ahmed")
        assertThat(command.amount).isEqualTo(Money.of("45000"))
        assertThat(command.description).contains("website development")
        assertThat(command.dueOn).isEqualTo(LocalDate.of(2026, 10, 5))
    }

    @Test
    fun `parses Make invoice 41 fifty percent paid`() {
        val slotParser = createSlotParser()

        val raw = "Make invoice 41 fifty percent paid."
        val normalized = "make invoice 41 fifty percent paid"

        val scan = slotParser.scan(normalized)
        val request = CommandRequest(
            raw = raw,
            normalized = normalized,
            language = SpokenLanguage.ENGLISH,
            script = TextScript.LATIN,
            source = InputSource.VOICE,
        )

        val slotted = slotParser.fill(MiraIntent.RECORD_PAYMENT, request, scan)
        assertThat(slotted).isNotNull()

        val command = slotted!!.command as AppCommand.RecordPayment
        assertThat(command.invoiceNumber).isEqualTo("41")
        assertThat(command.percentage).isEqualTo(BigDecimal(50))
    }

    @Test
    fun `parses Create the same invoice monthly`() {
        val slotParser = createSlotParser()

        val raw = "Create the same invoice monthly."
        val normalized = "create the same invoice monthly"

        val scan = slotParser.scan(normalized)
        val request = CommandRequest(
            raw = raw,
            normalized = normalized,
            language = SpokenLanguage.ENGLISH,
            script = TextScript.LATIN,
            source = InputSource.VOICE,
        )

        val slotted = slotParser.fill(MiraIntent.CREATE_INVOICE_DRAFT, request, scan)
        assertThat(slotted).isNotNull()

        val command = slotted!!.command as AppCommand.CreateRecurringInvoice
        assertThat(command.cycle).isEqualTo(SubscriptionCycle.MONTHLY)
    }

    // -------------------------------------------------------------------------
    // Test Fakes
    // -------------------------------------------------------------------------

    private class FakeTestInvoices : InvoiceRepository {
        val store = mutableListOf<Invoice>()
        private var nextId = 100L

        fun add(invoice: Invoice) { store += invoice }

        override fun observeAll(): Flow<List<Invoice>> = MutableStateFlow(store)
        override fun observeForClient(clientId: Long): Flow<List<Invoice>> = MutableStateFlow(store.filter { it.clientId == clientId })
        override fun observeUnpaid(): Flow<List<Invoice>> = MutableStateFlow(store.filter { it.outstanding.amount > BigDecimal.ZERO })
        override fun observeOverdue(): Flow<List<Invoice>> = MutableStateFlow(emptyList())
        override suspend fun byId(id: Long): AppResult<Invoice> =
            store.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "Not found")
        override suspend fun byNumber(number: String): AppResult<Invoice> =
            store.firstOrNull { it.number == number || it.number.endsWith("-$number") }?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "Not found")
        override suspend fun search(query: String?): AppResult<List<Invoice>> = AppResult.Success(store)
        override suspend fun create(invoice: Invoice): AppResult<Invoice> {
            val assigned = invoice.copy(id = nextId++)
            store += assigned
            return AppResult.Success(assigned)
        }
        override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> {
            val assigned = invoice.copy(id = nextId++, number = "INV-2026-00${store.size + 1}")
            store += assigned
            return AppResult.Success(assigned)
        }
        override suspend fun update(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
        override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = byId(id)
        override suspend fun softDelete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(id: Long): AppResult<Invoice> = byId(id)
        override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeTestQuotes : QuoteRepository {
        val store = mutableListOf<Quote>()
        private var nextId = 100L

        fun add(quote: Quote) { store += quote }

        override fun observeAll(): Flow<List<Quote>> = MutableStateFlow(store)
        override fun observeForClient(clientId: Long): Flow<List<Quote>> = MutableStateFlow(store.filter { it.clientId == clientId })
        override fun observeOpen(): Flow<List<Quote>> = MutableStateFlow(store)
        override suspend fun byId(id: Long): AppResult<Quote> =
            store.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "Not found")
        override suspend fun byNumber(number: String): AppResult<Quote> =
            store.firstOrNull { it.number == number }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "Not found")
        override suspend fun search(query: String?): AppResult<List<Quote>> = AppResult.Success(store)
        override suspend fun create(quote: Quote): AppResult<Quote> = AppResult.Success(quote)
        override suspend fun createWithNextNumber(quote: Quote): AppResult<Quote> = AppResult.Success(quote)
        override suspend fun update(quote: Quote): AppResult<Quote> = AppResult.Success(quote)
        override suspend fun setStatus(id: Long, status: QuoteStatus): AppResult<Quote> = byId(id)
        override suspend fun markConverted(id: Long, invoiceId: Long): AppResult<Quote> {
            val existing = store.firstOrNull { it.id == id } ?: return failure(MiraErrorCode.NOT_FOUND, "Not found")
            val updated = existing.copy(status = QuoteStatus.ACCEPTED, convertedInvoiceId = invoiceId)
            store.removeIf { it.id == id }
            store.add(updated)
            return AppResult.Success(updated)
        }
        override suspend fun softDelete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(id: Long): AppResult<Quote> = byId(id)
        override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeTestRecurringRepo : RecurringInvoiceRepository {
        val store = mutableListOf<RecurringInvoiceProfile>()
        override fun observeAll(): Flow<List<RecurringInvoiceProfile>> = MutableStateFlow(store)
        override suspend fun getAll(): AppResult<List<RecurringInvoiceProfile>> = AppResult.Success(store.toList())
        override suspend fun save(profile: RecurringInvoiceProfile): AppResult<RecurringInvoiceProfile> {
            store.removeIf { it.id == profile.id }
            store.add(profile)
            return AppResult.Success(profile)
        }
        override suspend fun saveAll(profiles: List<RecurringInvoiceProfile>): AppResult<Unit> {
            store.clear()
            store.addAll(profiles)
            return AppResult.Success(Unit)
        }
        override suspend fun delete(id: String): AppResult<Unit> {
            store.removeIf { it.id == id }
            return AppResult.Success(Unit)
        }
    }

    private class FakeTestReminders : ReminderRepository {
        val created = mutableListOf<Reminder>()
        override fun observeUpcoming(): Flow<List<Reminder>> = MutableStateFlow(created)
        override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> = MutableStateFlow(created.filter { it.kind == kind })
        override suspend fun listOfKinds(kinds: Collection<ReminderKind>): AppResult<List<Reminder>> = AppResult.Success(created)
        override suspend fun byId(id: Long): AppResult<Reminder> = failure(MiraErrorCode.NOT_FOUND, "Not found")
        override suspend fun create(reminder: Reminder): AppResult<Reminder> {
            created += reminder
            return AppResult.Success(reminder)
        }
        override suspend fun update(reminder: Reminder): AppResult<Reminder> = AppResult.Success(reminder)
        override suspend fun markFired(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancel(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun nextScheduled(): AppResult<Reminder?> = AppResult.Success(null)
        override suspend fun rescheduleAll(): AppResult<Int> = AppResult.Success(0)
    }
}
