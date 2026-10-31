package com.rayluxeo.aida.domain.command.handler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientBilling
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.usecase.BuildInvoiceTotalsUseCase
import com.rayluxeo.aida.domain.usecase.CreateInvoiceDraftUseCase
import com.rayluxeo.aida.domain.usecase.CreateRecurringInvoiceProfileUseCase
import com.rayluxeo.aida.domain.usecase.ResolveClientUseCase
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The business seam of the dispatcher, driven directly.
 *
 * No Hilt (its local-test transform does not run under AGP 9): the handler is assembled here out
 * of hand-written repositories, a clock that never moves, and the **real**
 * [BuildInvoiceTotalsUseCase] — pricing a bill is exactly the part that must not be faked.
 *
 * The fake invoice repository enforces the same three refusals the real one does: no lines, mixed
 * currencies, and stored totals that do not add up to the stored lines. A draft that reached disk
 * without being priced would therefore fail here rather than in Abu's hands.
 */
internal class FakeTestActivityLog : com.rayluxeo.aida.domain.repository.ActivityLogRepository {
    override fun observeRecent(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeByCategory(category: com.rayluxeo.aida.domain.model.ActivityCategory, limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeForSubject(subject: com.rayluxeo.aida.domain.model.ActivitySubject) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun between(from: java.time.Instant, to: java.time.Instant) = AppResult.Success(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun record(entry: com.rayluxeo.aida.domain.model.ActivityEntry) = AppResult.Success(entry)
}

class BusinessCommandHandlerTest {

    private val clock = FixedMiraClock()
    private val clients = FakeBusinessClients()
    private val invoices = FakeBusinessInvoices()
    private val recurring = FakeBusinessRecurringInvoices()
    private val userProfiles = FakeUserProfileRepository()
    private val contextManager = com.rayluxeo.aida.domain.context.ConversationContextManager()
    private val handler = DefaultBusinessCommandHandler(
        clients = clients,
        resolveClient = ResolveClientUseCase(clients),
        createDraft = CreateInvoiceDraftUseCase(invoices, BuildInvoiceTotalsUseCase(), FakeTestActivityLog(), userProfiles, clock),
        createQuote = com.rayluxeo.aida.domain.usecase.CreateQuoteDraftUseCase(
            StubQuoteRepository(),
            com.rayluxeo.aida.domain.usecase.BuildQuoteTotalsUseCase(),
            FakeTestActivityLog(),
            userProfiles,
            clock,
        ),
        buildStatement = com.rayluxeo.aida.domain.usecase.BuildClientStatementUseCase(invoices),
        createRecurring = CreateRecurringInvoiceProfileUseCase(invoices, recurring, clock),
        invoiceRepository = invoices,
        prepareDelivery = com.rayluxeo.aida.domain.usecase.PrepareInvoiceDeliveryUseCase(com.rayluxeo.aida.ui.invoices.delivery.InvoiceDeliveryMessages()),
        userProfiles = userProfiles,
        contextManager = contextManager,
        clock = clock,
    )

    private val usd: Currency = Currency.getInstance("USD")

    // ------------------------------------------------------------------ clients

    @Test
    fun `a client is stored with everything Abu gave and answered by name`() = runTest {
        val response = succeeds(
            handler.createClient(
                AppCommand.CreateClient(
                    name = "Ahmed",
                    companyName = "Ahmed Traders",
                    phone = "01711223344",
                    email = "ahmed@example.com",
                ),
            ),
        )

        assertThat(response).isEqualTo(MiraResponse.ClientCreated("Ahmed"))
        val stored = clients.rows.single()
        assertThat(stored.companyName).isEqualTo("Ahmed Traders")
        assertThat(stored.phone).isEqualTo("01711223344")
        assertThat(stored.email).isEqualTo("ahmed@example.com")
        assertThat(stored.createdAt).isEqualTo(FixedMiraClock.DEFAULT_NOW)
    }

    @Test
    fun `a client named in Bengali keeps his name exactly as written`() = runTest {
        succeeds(handler.createClient(AppCommand.CreateClient(name = "আহমেদ ট্রেডার্স")))

        assertThat(clients.rows.single().name).isEqualTo("আহমেদ ট্রেডার্স")
    }

    @Test
    fun `a storage failure creating a client is returned as a value`() = runTest {
        clients.failWith = MiraErrorCode.STORAGE

        val result = handler.createClient(AppCommand.CreateClient(name = "Ahmed"))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `a client search says how many it found and reads back only the first three`() = runTest {
        repeat(5) { index -> clients.seed(client(name = "Ahmed $index")) }

        val response = succeedsWith<MiraResponse.ClientsFound>(
            handler.searchClients(AppCommand.SearchClients("Ahmed")),
        )

        assertThat(response.count).isEqualTo(5)
        assertThat(response.names).containsExactly("Ahmed 0", "Ahmed 1", "Ahmed 2").inOrder()
    }

    @Test
    fun `finding no client is an empty answer rather than a failure`() = runTest {
        val response = succeeds(handler.searchClients(AppCommand.SearchClients("Ahmed")))

        assertThat(response).isEqualTo(MiraResponse.ClientsFound(0, emptyList()))
    }

    // ------------------------------------------------------------------ invoice drafts

    @Test
    fun `a draft is raised against the one matching client, priced from what Abu said`() = runTest {
        clients.seed(client(name = "Ahmed"))

        val response = succeedsWith<MiraResponse.InvoiceDraftCreated>(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(
                    client = ClientTarget.ByName("Ahmed"),
                    amount = Money.of("50000"),
                    description = "logo design",
                ),
            ),
        )

        assertThat(response.number).isEqualTo("INV-2026-001")
        assertThat(response.clientName).isEqualTo("Ahmed")
        assertThat(response.total).isEqualTo(Money.of("50000"))

        val stored = invoices.rows.single()
        assertThat(stored.status).isEqualTo(InvoiceStatus.DRAFT)
        assertThat(stored.clientId).isEqualTo(clients.rows.single().id)
        assertThat(stored.subtotal).isEqualTo(Money.of("50000"))
        assertThat(stored.tax).isEqualTo(Money.zero())
        assertThat(stored.total).isEqualTo(Money.of("50000"))
        assertThat(stored.amountPaid).isEqualTo(Money.zero())
    }

    @Test
    fun `the amount becomes one line, priced by the totals use case rather than by hand`() = runTest {
        clients.seed(client(name = "Ahmed"))

        succeeds(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), "logo design"),
            ),
        )

        val line = invoices.rows.single().lines.single()
        assertThat(line.description).isEqualTo("logo design")
        assertThat(line.quantity.compareTo(BigDecimal.ONE)).isEqualTo(0)
        assertThat(line.unitPrice).isEqualTo(Money.of("50000"))
        assertThat(line.lineTotal).isEqualTo(Money.of("50000"))
        assertThat(line.taxRatePercent.signum()).isEqualTo(0)
        assertThat(line.position).isEqualTo(0)
    }

    @Test
    fun `the draft is dated from the injected clock and due a fortnight later`() = runTest {
        clients.seed(client(name = "Ahmed"))

        succeeds(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
            ),
        )

        val stored = invoices.rows.single()
        assertThat(stored.issuedOn).isEqualTo(LocalDate.of(2026, 3, 10))
        assertThat(stored.dueOn).isEqualTo(LocalDate.of(2026, 3, 24))
    }

    @Test
    fun `a draft raised without a figure still lands, with its line at zero`() = runTest {
        clients.seed(client(name = "Ahmed"))

        val response = succeedsWith<MiraResponse.InvoiceDraftCreated>(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), amount = null, description = null),
            ),
        )

        assertThat(response.total).isEqualTo(Money.zero())
        val stored = invoices.rows.single()
        assertThat(stored.status).isEqualTo(InvoiceStatus.DRAFT)
        assertThat(stored.lines.single().unitPrice).isEqualTo(Money.zero())
    }

    @Test
    fun `a line Abu did not describe is left blank rather than given an invented description`() = runTest {
        clients.seed(client(name = "Ahmed"))

        succeeds(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
            ),
        )

        assertThat(invoices.rows.single().lines.single().description).isEmpty()
    }

    @Test
    fun `the currency is the one Abu named, even when the client usually bills in another`() = runTest {
        clients.seed(client(name = "Ahmed", billing = ClientBilling(currency = usd)))

        succeeds(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
            ),
        )

        assertThat(invoices.rows.single().currency).isEqualTo(Money.BDT)
    }

    @Test
    fun `with no figure at all the draft takes the client's own billing currency`() = runTest {
        clients.seed(client(name = "Ahmed", billing = ClientBilling(currency = usd)))

        succeeds(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), amount = null, description = null),
            ),
        )

        val stored = invoices.rows.single()
        assertThat(stored.currency).isEqualTo(usd)
        assertThat(stored.total).isEqualTo(Money.zero(usd))
    }

    @Test
    fun `a client referred to by id is invoiced without searching by name`() = runTest {
        clients.seed(client(name = "Ahmed"))
        val id = clients.rows.single().id

        val response = succeedsWith<MiraResponse.InvoiceDraftCreated>(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ById(id), Money.of("50000"), null),
            ),
        )

        assertThat(response.clientName).isEqualTo("Ahmed")
        assertThat(clients.searches).isEmpty()
    }

    @Test
    fun `an id that matches nobody is not found`() = runTest {
        val result = handler.createInvoiceDraft(
            AppCommand.CreateInvoiceDraft(ClientTarget.ById(77L), Money.of("50000"), null),
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(invoices.rows).isEmpty()
    }

    // ------------------------------------------------------------------ when Mira cannot be sure

    @Test
    fun `two people matching the name are asked about rather than guessed between`() = runTest {
        clients.seed(client(name = "Ahmed Khan"))
        clients.seed(client(name = "Ahmed Rahman"))

        val response = succeedsWith<MiraResponse.AmbiguousClient>(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
            ),
        )

        assertThat(response.query).isEqualTo("Ahmed")
        assertThat(response.candidates).containsExactly("Ahmed Khan", "Ahmed Rahman")
        assertThat(invoices.rows).isEmpty()
    }

    @Test
    fun `a long list of near matches is cut to what can be read aloud`() = runTest {
        repeat(6) { index -> clients.seed(client(name = "Ahmed $index")) }

        val response = succeedsWith<MiraResponse.AmbiguousClient>(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
            ),
        )

        assertThat(response.candidates).hasSize(5)
    }

    @Test
    fun `an exact name among several matches is taken as the one he meant`() = runTest {
        clients.seed(client(name = "Ahmed"))
        clients.seed(client(name = "Ahmed Traders"))

        val response = succeedsWith<MiraResponse.InvoiceDraftCreated>(
            handler.createInvoiceDraft(
                AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
            ),
        )

        assertThat(response.clientName).isEqualTo("Ahmed")
        assertThat(invoices.rows).hasSize(1)
    }

    @Test
    fun `a name matching nobody is refused rather than quietly creating the client`() = runTest {
        clients.seed(client(name = "Rahim"))

        val result = handler.createInvoiceDraft(
            AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(clients.rows).hasSize(1)
        assertThat(invoices.rows).isEmpty()
    }

    @Test
    fun `an archived client is out of the way here too`() = runTest {
        clients.seed(client(name = "Ahmed", archived = true))

        val result = handler.createInvoiceDraft(
            AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a storage failure raising the draft is returned as a value`() = runTest {
        clients.seed(client(name = "Ahmed"))
        invoices.failWith = MiraErrorCode.STORAGE

        val result = handler.createInvoiceDraft(
            AppCommand.CreateInvoiceDraft(ClientTarget.ByName("Ahmed"), Money.of("50000"), null),
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------------------ query & deliver invoice

    @Test
    fun `query invoice for client returns outstanding report and updates context`() = runTest {
        val client = client(name = "Ahmed")
        clients.seed(client)
        val seededClient = clients.rows.single()

        val invoice = Invoice(
            id = 101L,
            uuid = "inv-uuid",
            number = "INV-2026-001",
            clientId = seededClient.id,
            status = InvoiceStatus.SENT,
            issuedOn = LocalDate.of(2026, 10, 1),
            dueOn = LocalDate.of(2026, 10, 15),
            subtotal = Money.of("45000", Currency.getInstance("BDT")),
            discount = Money.zero(Currency.getInstance("BDT")),
            tax = Money.zero(Currency.getInstance("BDT")),
            total = Money.of("45000", Currency.getInstance("BDT")),
            amountPaid = Money.zero(Currency.getInstance("BDT")),
            createdAt = FixedMiraClock.DEFAULT_NOW,
            updatedAt = FixedMiraClock.DEFAULT_NOW,
        )
        invoices.rows += invoice

        val response = succeedsWith<MiraResponse.InvoiceOutstandingReport>(
            handler.queryInvoice(AppCommand.QueryInvoice(client = ClientTarget.ByName("Ahmed"), invoiceNumber = null)),
        )

        assertThat(response.clientName).isEqualTo("Ahmed")
        assertThat(response.number).isEqualTo("INV-2026-001")
        assertThat(response.outstanding).isEqualTo(Money.of("45000", Currency.getInstance("BDT")))
        assertThat(contextManager.currentState().lastClient?.name).isEqualTo("Ahmed")
        assertThat(contextManager.currentState().lastInvoice?.number).isEqualTo("INV-2026-001")
    }

    @Test
    fun `deliver invoice uses context when no invoice specified`() = runTest {
        val client = client(name = "Ahmed")
        clients.seed(client)
        val seededClient = clients.rows.single()

        val invoice = Invoice(
            id = 101L,
            uuid = "inv-uuid",
            number = "INV-2026-001",
            clientId = seededClient.id,
            status = InvoiceStatus.SENT,
            issuedOn = LocalDate.of(2026, 10, 1),
            dueOn = LocalDate.of(2026, 10, 15),
            subtotal = Money.of("45000", Currency.getInstance("BDT")),
            discount = Money.zero(Currency.getInstance("BDT")),
            tax = Money.zero(Currency.getInstance("BDT")),
            total = Money.of("45000", Currency.getInstance("BDT")),
            amountPaid = Money.zero(Currency.getInstance("BDT")),
            createdAt = FixedMiraClock.DEFAULT_NOW,
            updatedAt = FixedMiraClock.DEFAULT_NOW,
        )
        invoices.rows += invoice

        contextManager.updateClient(seededClient)
        contextManager.updateInvoice(invoice)

        val response = succeedsWith<MiraResponse.InvoiceDeliveryPrepared>(
            handler.deliverInvoice(AppCommand.DeliverInvoice(client = null, invoiceNumber = null)),
        )

        assertThat(response.draft.invoiceNumber).isEqualTo("INV-2026-001")
    }

    @Test
    fun `deliver invoice refuses to guess when candidates are ambiguous`() = runTest {
        val client = client(name = "Ahmed")
        clients.seed(client)
        val seededClient = clients.rows.single()

        val inv1 = Invoice(
            id = 101L,
            uuid = "inv-1",
            number = "INV-001",
            clientId = seededClient.id,
            status = InvoiceStatus.SENT,
            issuedOn = LocalDate.of(2026, 10, 1),
            dueOn = LocalDate.of(2026, 10, 15),
            subtotal = Money.of("10000", Currency.getInstance("BDT")),
            discount = Money.zero(Currency.getInstance("BDT")),
            tax = Money.zero(Currency.getInstance("BDT")),
            total = Money.of("10000", Currency.getInstance("BDT")),
            amountPaid = Money.zero(Currency.getInstance("BDT")),
            createdAt = FixedMiraClock.DEFAULT_NOW,
            updatedAt = FixedMiraClock.DEFAULT_NOW,
        )
        val inv2 = inv1.copy(id = 102L, uuid = "inv-2", number = "INV-002")
        invoices.rows.addAll(listOf(inv1, inv2))

        contextManager.updateClient(seededClient)
        contextManager.setCandidateInvoices(listOf(inv1, inv2))

        val response = succeedsWith<MiraResponse.AmbiguousInvoice>(
            handler.deliverInvoice(AppCommand.DeliverInvoice(client = null, invoiceNumber = null)),
        )

        assertThat(response.candidateNumbers).containsExactly("INV-001", "INV-002")
    }

    // ------------------------------------------------------------------ helpers

    private fun succeeds(result: AppResult<MiraResponse>): MiraResponse {
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private inline fun <reified T : MiraResponse> succeedsWith(result: AppResult<MiraResponse>): T {
        val response = succeeds(result)
        assertThat(response).isInstanceOf(T::class.java)
        return response as T
    }

    private fun client(
        name: String,
        billing: ClientBilling? = null,
        archived: Boolean = false,
    ) = Client(
        name = name,
        billing = billing,
        isArchived = archived,
        createdAt = FixedMiraClock.DEFAULT_NOW,
        updatedAt = FixedMiraClock.DEFAULT_NOW,
    )
}

/**
 * Clients, in memory. [searches] records the name lookups so a test can show that a reference by
 * id never went near the search index.
 */
private class FakeBusinessClients : ClientRepository {

    val rows = mutableListOf<Client>()
    val searches = mutableListOf<String?>()
    private var nextId = 1L

    var failWith: MiraErrorCode? = null

    fun seed(client: Client) {
        rows += client.copy(id = nextId++, uuid = UUID.randomUUID().toString())
    }

    override suspend fun byId(id: Long): AppResult<Client> =
        rows.firstOrNull { it.id == id && it.deletedAt == null }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No client with id $id")

    /** Matches name, company and notes, and honours [filter] exactly as the real search does. */
    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> {
        searches += query
        val needle = query?.trim()?.lowercase()
        return AppResult.Success(
            rows.filter { it.deletedAt == null }
                .filter { candidate ->
                    when (filter) {
                        ClientFilter.ALL -> true
                        ClientFilter.ACTIVE -> !candidate.isArchived
                        ClientFilter.ARCHIVED -> candidate.isArchived
                    }
                }
                .filter { candidate ->
                    needle.isNullOrEmpty() ||
                        candidate.name.lowercase().contains(needle) ||
                        candidate.companyName?.lowercase()?.contains(needle) == true ||
                        candidate.notes?.lowercase()?.contains(needle) == true
                },
        )
    }

    override suspend fun create(client: Client): AppResult<Client> {
        failWith?.let { return failure(it, "fake client failure") }
        val saved = client.copy(id = nextId++, uuid = UUID.randomUUID().toString())
        rows += saved
        return AppResult.Success(saved)
    }

    override fun observe(filter: ClientFilter): Flow<List<Client>> = unusedByBusinessCommands("observe")
    override suspend fun update(client: Client): AppResult<Client> = unusedByBusinessCommands("update")
    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = unusedByBusinessCommands("setArchived")
    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedByBusinessCommands("softDelete")
    override suspend fun restore(id: Long): AppResult<Client> = unusedByBusinessCommands("restore")
    override suspend fun purge(id: Long): AppResult<Unit> = unusedByBusinessCommands("purge")
}

/**
 * Invoices, in memory, refusing what the real repository refuses.
 *
 * The three checks are the point of this fake: a document with no lines, a document mixing
 * currencies, and a document whose stored totals do not add up to its own lines are all rejected
 * here, so a draft assembled without being priced cannot pass this test file.
 */
private class FakeBusinessInvoices : InvoiceRepository {

    val rows = mutableListOf<Invoice>()
    private var nextId = 1L

    var failWith: MiraErrorCode? = null

    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> {
        failWith?.let { return failure(it, "fake invoice failure") }
        if (invoice.lines.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "An invoice with no lines has nothing to charge for")
        }
        val currency = invoice.currency
        val amounts = invoice.lines.flatMap { listOf(it.unitPrice, it.lineTotal) } +
            listOf(invoice.subtotal, invoice.discount, invoice.tax, invoice.amountPaid)
        if (amounts.any { it.currency != currency }) {
            return failure(MiraErrorCode.VALIDATION, "Invoice mixes currencies")
        }
        val lineSum = invoice.lines.fold(Money.zero(currency)) { running, line -> running + line.lineTotal }
        val expected = invoice.subtotal - invoice.discount + invoice.tax
        if (lineSum.amount.compareTo(invoice.subtotal.amount) != 0 ||
            expected.amount.compareTo(invoice.total.amount) != 0
        ) {
            return failure(MiraErrorCode.VALIDATION, "Stored totals do not add up to the stored lines")
        }
        val saved = invoice.copy(
            id = nextId,
            uuid = UUID.randomUUID().toString(),
            number = "INV-${invoice.issuedOn.year}-${nextId.toString().padStart(3, '0')}",
        )
        nextId++
        rows += saved
        return AppResult.Success(saved)
    }

    override fun observeAll(): Flow<List<Invoice>> = unusedByBusinessCommands("observeAll")
    override fun observeForClient(clientId: Long): Flow<List<Invoice>> =
        flowOf(rows.filter { it.clientId == clientId && it.deletedAt == null })
    override fun observeUnpaid(): Flow<List<Invoice>> = unusedByBusinessCommands("observeUnpaid")
    override fun observeOverdue(): Flow<List<Invoice>> = unusedByBusinessCommands("observeOverdue")
    override suspend fun byId(id: Long): AppResult<Invoice> =
        rows.firstOrNull { it.id == id && it.deletedAt == null }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "Invoice with id $id not found")
    override suspend fun byNumber(number: String): AppResult<Invoice> =
        rows.firstOrNull { it.number.equals(number, ignoreCase = true) && it.deletedAt == null }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "Invoice $number not found")
    override suspend fun search(query: String?): AppResult<List<Invoice>> = unusedByBusinessCommands("search")
    override suspend fun create(invoice: Invoice): AppResult<Invoice> = unusedByBusinessCommands("create")
    override suspend fun update(invoice: Invoice): AppResult<Invoice> = unusedByBusinessCommands("update")
    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = unusedByBusinessCommands("setStatus")
    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedByBusinessCommands("softDelete")
    override suspend fun restore(id: Long): AppResult<Invoice> = unusedByBusinessCommands("restore")
    override suspend fun delete(id: Long): AppResult<Unit> = unusedByBusinessCommands("delete")
}

private class FakeUserProfileRepository : com.rayluxeo.aida.domain.repository.UserProfileRepository {
    private var profile: com.rayluxeo.aida.domain.model.UserProfile? = null
    override fun observe(): Flow<com.rayluxeo.aida.domain.model.UserProfile?> = flowOf(profile)
    override suspend fun current(): AppResult<com.rayluxeo.aida.domain.model.UserProfile?> = AppResult.Success(profile)
    override suspend fun save(profile: com.rayluxeo.aida.domain.model.UserProfile): AppResult<com.rayluxeo.aida.domain.model.UserProfile> {
        this.profile = profile
        return AppResult.Success(profile)
    }
}

/**
 * A member no business command can reach. It fails loudly rather than answering something
 * plausible, so a handler that started calling it would be caught here instead of in the app.
 */
private fun unusedByBusinessCommands(member: String): Nothing =
    error("$member is outside what the business commands touch")

private class FakeBusinessRecurringInvoices : com.rayluxeo.aida.domain.repository.RecurringInvoiceRepository {
    private val rows = mutableListOf<com.rayluxeo.aida.domain.model.RecurringInvoiceProfile>()
    override fun observeAll(): Flow<List<com.rayluxeo.aida.domain.model.RecurringInvoiceProfile>> = flowOf(rows.toList())
    override suspend fun getAll(): AppResult<List<com.rayluxeo.aida.domain.model.RecurringInvoiceProfile>> = AppResult.Success(rows.toList())
    override suspend fun save(profile: com.rayluxeo.aida.domain.model.RecurringInvoiceProfile): AppResult<com.rayluxeo.aida.domain.model.RecurringInvoiceProfile> {
        rows.removeAll { it.id == profile.id }
        rows += profile
        return AppResult.Success(profile)
    }
    override suspend fun saveAll(profiles: List<com.rayluxeo.aida.domain.model.RecurringInvoiceProfile>): AppResult<Unit> {
        rows.clear()
        rows.addAll(profiles)
        return AppResult.Success(Unit)
    }
    override suspend fun delete(id: String): AppResult<Unit> {
        rows.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }
}

/**
 * These tests exercise the invoice, client and delivery paths; the quote-draft dependency is present
 * only so the handler can be constructed, and none of its methods is reached. Quote creation has its
 * own coverage in `CreateQuoteDraftUseCaseTest`.
 */
private class StubQuoteRepository : com.rayluxeo.aida.domain.repository.QuoteRepository {
    override fun observeAll(): Flow<List<com.rayluxeo.aida.domain.model.Quote>> = flowOf(emptyList())
    override fun observeForClient(clientId: Long): Flow<List<com.rayluxeo.aida.domain.model.Quote>> = flowOf(emptyList())
    override fun observeOpen(): Flow<List<com.rayluxeo.aida.domain.model.Quote>> = flowOf(emptyList())
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun byNumber(number: String) = error("unused")
    override suspend fun search(query: String?) = error("unused")
    override suspend fun create(quote: com.rayluxeo.aida.domain.model.Quote) = error("unused")
    override suspend fun createWithNextNumber(quote: com.rayluxeo.aida.domain.model.Quote) = error("unused")
    override suspend fun update(quote: com.rayluxeo.aida.domain.model.Quote) = error("unused")
    override suspend fun setStatus(id: Long, status: com.rayluxeo.aida.domain.model.QuoteStatus) = error("unused")
    override suspend fun markConverted(id: Long, invoiceId: Long) = error("unused")
    override suspend fun softDelete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
}
