package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.context.ConversationContextManager
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import com.rayluxeo.aida.domain.usecase.BuildClientStatementUseCase
import com.rayluxeo.aida.domain.usecase.CreateInvoiceDraftUseCase
import com.rayluxeo.aida.domain.usecase.CreateQuoteDraftUseCase
import com.rayluxeo.aida.domain.usecase.CreateRecurringInvoiceProfileUseCase
import com.rayluxeo.aida.domain.usecase.PrepareInvoiceDeliveryUseCase
import com.rayluxeo.aida.domain.usecase.ResolveClientUseCase
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Clients, and the invoices Abu raises against them.
 *
 * The one rule that shapes this whole file: Mira never guesses which client was meant. A name
 * that matches several people comes back as [MiraResponse.AmbiguousClient] for Abu to settle, and
 * a name that matches nobody is refused — speaking an invoice into existence for a client who
 * does not exist would quietly create the client too, and he would find out at the point he sent
 * a bill to a name he had never entered.
 */
class DefaultBusinessCommandHandler @Inject constructor(
    private val clients: ClientRepository,
    private val resolveClient: ResolveClientUseCase,
    private val createDraft: CreateInvoiceDraftUseCase,
    private val createQuote: CreateQuoteDraftUseCase,
    private val buildStatement: BuildClientStatementUseCase,
    private val createRecurring: CreateRecurringInvoiceProfileUseCase,
    private val invoiceRepository: InvoiceRepository,
    private val prepareDelivery: PrepareInvoiceDeliveryUseCase,
    private val userProfiles: UserProfileRepository,
    private val contextManager: ConversationContextManager,
    private val clock: MiraClock,
) : BusinessCommandHandler {

    override suspend fun createClient(command: AppCommand.CreateClient): AppResult<MiraResponse> {
        val now = clock.now()
        val client = Client(
            name = command.name,
            companyName = command.companyName,
            email = command.email,
            phone = command.phone,
            createdAt = now,
            updatedAt = now,
        )
        return clients.create(client).map {
            contextManager.updateClient(it)
            MiraResponse.ClientCreated(it.name)
        }
    }

    override suspend fun searchClients(command: AppCommand.SearchClients): AppResult<MiraResponse> =
        clients.search(command.query).map { found ->
            if (found.size == 1) contextManager.updateClient(found.first())
            MiraResponse.ClientsFound(
                count = found.size,
                names = found.take(SPOKEN_RESULTS).map { it.name },
            )
        }

    override suspend fun createInvoiceDraft(
        command: AppCommand.CreateInvoiceDraft,
    ): AppResult<MiraResponse> = resolveClient(command.client).flatMap { resolution ->
        when (resolution) {
            is ResolveClientUseCase.Resolution.Exact ->
                createDraft(resolution.client, command.amount, command.description, command.dueOn).map { invoice ->
                    contextManager.updateClient(resolution.client)
                    contextManager.updateInvoice(invoice)
                    MiraResponse.InvoiceDraftCreated(
                        number = invoice.number,
                        clientName = resolution.client.name,
                        total = invoice.total,
                    )
                }

            is ResolveClientUseCase.Resolution.Ambiguous -> {
                contextManager.setCandidateClients(resolution.candidates)
                AppResult.Success(
                    MiraResponse.AmbiguousClient(
                        query = command.client.spoken(),
                        candidates = resolution.candidates.map { it.name },
                    )
                )
            }

            // NOT_FOUND, not VALIDATION, for the same reason queryInvoice and deliverInvoice use it
            // on this identical "no such client" case: the request was understood, the client simply
            // is not on file. VALIDATION here rendered as the generic "…name and a time", which is
            // what made the documented empty-state invoice phrase look broken on a fresh install.
            ResolveClientUseCase.Resolution.None -> failure(
                MiraErrorCode.NOT_FOUND,
                "No client matches \"${command.client.spoken()}\"",
            )
        }
    }

    override suspend fun createQuoteDraft(
        command: AppCommand.CreateQuoteDraft,
    ): AppResult<MiraResponse> = resolveClient(command.client).flatMap { resolution ->
        when (resolution) {
            is ResolveClientUseCase.Resolution.Exact ->
                createQuote(resolution.client, command.amount, command.description, command.validUntil).map { quote ->
                    contextManager.updateClient(resolution.client)
                    MiraResponse.QuoteDraftCreated(
                        number = quote.number,
                        clientName = resolution.client.name,
                        total = quote.total,
                    )
                }

            is ResolveClientUseCase.Resolution.Ambiguous -> {
                contextManager.setCandidateClients(resolution.candidates)
                AppResult.Success(
                    MiraResponse.AmbiguousClient(
                        query = command.client.spoken(),
                        candidates = resolution.candidates.map { it.name },
                    )
                )
            }

            // NOT_FOUND, not VALIDATION — the request was understood, the client is simply not on
            // file, exactly as createInvoiceDraft treats it. Quoting a stranger into existence is a
            // mistake, not an instruction.
            ResolveClientUseCase.Resolution.None -> failure(
                MiraErrorCode.NOT_FOUND,
                "No client matches \"${command.client.spoken()}\"",
            )
        }
    }

    override suspend fun queryClientStatement(
        command: AppCommand.QueryClientStatement,
    ): AppResult<MiraResponse> = resolveClient(command.client).flatMap { resolution ->
        when (resolution) {
            is ResolveClientUseCase.Resolution.Exact -> {
                contextManager.updateClient(resolution.client)
                buildStatement(resolution.client).map { MiraResponse.ClientStatementReport(it) }
            }

            is ResolveClientUseCase.Resolution.Ambiguous -> {
                contextManager.setCandidateClients(resolution.candidates)
                AppResult.Success(
                    MiraResponse.AmbiguousClient(
                        query = command.client.spoken(),
                        candidates = resolution.candidates.map { it.name },
                    )
                )
            }

            ResolveClientUseCase.Resolution.None -> failure(
                MiraErrorCode.NOT_FOUND,
                "No client matches \"${command.client.spoken()}\"",
            )
        }
    }

    override suspend fun createRecurringInvoice(
        command: AppCommand.CreateRecurringInvoice,
    ): AppResult<MiraResponse> = createRecurring(
        sourceInvoiceNumber = command.sourceInvoiceNumber,
        cycle = command.cycle,
    ).map { profile ->
        MiraResponse.RecurringInvoiceScheduled(
            invoiceNumber = profile.sourceInvoiceNumber,
            cycle = profile.cycle,
        )
    }

    override suspend fun queryInvoice(command: AppCommand.QueryInvoice): AppResult<MiraResponse> {
        val clientResult = when (val target = command.client) {
            null -> null
            else -> resolveClient(target)
        }

        if (clientResult != null) {
            return clientResult.flatMap { resolution ->
                when (resolution) {
                    is ResolveClientUseCase.Resolution.Exact -> {
                        val client = resolution.client
                        contextManager.updateClient(client)

                        val clientInvoices = invoiceRepository.observeForClient(client.id)
                            .firstOrNull()
                            .orEmpty()
                            .filter { it.outstanding.amount > BigDecimal.ZERO && it.status != InvoiceStatus.VOID }

                        when {
                            clientInvoices.isEmpty() -> {
                                contextManager.clearCandidateInvoices()
                                val zero = Money.zero(Currency.getInstance("BDT"))
                                AppResult.Success(
                                    MiraResponse.InvoiceOutstandingReport(
                                        clientName = client.name,
                                        number = null,
                                        outstanding = zero,
                                        total = zero,
                                    )
                                )
                            }
                            clientInvoices.size == 1 -> {
                                val invoice = clientInvoices.first()
                                contextManager.updateInvoice(invoice)
                                contextManager.clearCandidateInvoices()
                                AppResult.Success(
                                    MiraResponse.InvoiceOutstandingReport(
                                        clientName = client.name,
                                        number = invoice.number,
                                        outstanding = invoice.outstanding,
                                        total = invoice.total,
                                    )
                                )
                            }
                            else -> {
                                contextManager.setCandidateInvoices(clientInvoices)
                                AppResult.Success(
                                    MiraResponse.AmbiguousInvoice(
                                        clientName = client.name,
                                        candidateNumbers = clientInvoices.map { it.number },
                                    )
                                )
                            }
                        }
                    }
                    is ResolveClientUseCase.Resolution.Ambiguous -> {
                        contextManager.setCandidateClients(resolution.candidates)
                        AppResult.Success(
                            MiraResponse.AmbiguousClient(
                                query = command.client?.spoken().orEmpty(),
                                candidates = resolution.candidates.map { it.name },
                            )
                        )
                    }
                    ResolveClientUseCase.Resolution.None -> failure(
                        MiraErrorCode.NOT_FOUND,
                        "No client matches \"${command.client?.spoken().orEmpty()}\"",
                    )
                }
            }
        }

        val invoiceNumber = command.invoiceNumber
        if (!invoiceNumber.isNullOrBlank()) {
            return invoiceRepository.byNumber(invoiceNumber).flatMap { invoice ->
                val client = clients.byId(invoice.clientId).getOrNull()
                if (client != null) contextManager.updateClient(client)
                contextManager.updateInvoice(invoice)
                AppResult.Success(
                    MiraResponse.InvoiceOutstandingReport(
                        clientName = client?.name ?: "Client",
                        number = invoice.number,
                        outstanding = invoice.outstanding,
                        total = invoice.total,
                    )
                )
            }
        }

        return failure(MiraErrorCode.VALIDATION, "Client or invoice number required")
    }

    override suspend fun deliverInvoice(command: AppCommand.DeliverInvoice): AppResult<MiraResponse> {
        val state = contextManager.currentState()

        // If candidates are ambiguous and none chosen, do NOT guess!
        if (command.invoiceNumber.isNullOrBlank() && state.candidateInvoices.size > 1) {
            return AppResult.Success(
                MiraResponse.AmbiguousInvoice(
                    clientName = state.lastClient?.name,
                    candidateNumbers = state.candidateInvoices.map { it.number },
                )
            )
        }

        val invoice: Invoice = when {
            !command.invoiceNumber.isNullOrBlank() -> {
                when (val invRes = invoiceRepository.byNumber(command.invoiceNumber)) {
                    is AppResult.Success -> invRes.value
                    is AppResult.Failure -> return invRes
                }
            }
            state.lastInvoice != null -> state.lastInvoice!!
            state.candidateInvoices.size == 1 -> state.candidateInvoices.first()
            else -> return failure(MiraErrorCode.NOT_FOUND, "No invoice to deliver")
        }

        val client: Client = when {
            command.client != null -> {
                when (val cRes = resolveClient(command.client)) {
                    is AppResult.Success -> when (val res = cRes.value) {
                        is ResolveClientUseCase.Resolution.Exact -> res.client
                        is ResolveClientUseCase.Resolution.Ambiguous -> return AppResult.Success(
                            MiraResponse.AmbiguousClient(
                                query = command.client.spoken(),
                                candidates = res.candidates.map { it.name },
                            )
                        )
                        ResolveClientUseCase.Resolution.None -> return failure(
                            MiraErrorCode.NOT_FOUND,
                            "No client matches \"${command.client.spoken()}\"",
                        )
                    }
                    is AppResult.Failure -> return cRes
                }
            }
            state.lastClient != null && state.lastClient.id == invoice.clientId -> state.lastClient!!
            else -> {
                when (val cRes = clients.byId(invoice.clientId)) {
                    is AppResult.Success -> cRes.value
                    is AppResult.Failure -> return cRes
                }
            }
        }

        contextManager.updateClient(client)
        contextManager.updateInvoice(invoice)
        contextManager.clearCandidateInvoices()

        val profile = userProfiles.observe().firstOrNull()
        val draft = prepareDelivery(
            invoice = invoice,
            client = client,
            method = command.method,
            profile = profile,
            attachmentFile = null,
        )

        return AppResult.Success(MiraResponse.InvoiceDeliveryPrepared(draft))
    }

    /**
     * What Abu called the client, for a reply that has to quote him back. An id has no spoken
     * form; the branch is here because the `when` must be total, not because a reference by id
     * can be ambiguous — [ClientRepository.byId] answers with one client or with NOT_FOUND.
     */
    private fun ClientTarget.spoken(): String = when (this) {
        is ClientTarget.ByName -> name
        is ClientTarget.ById -> id.toString()
    }

    private companion object {
        /** Three names is what Mira can read out without Abu losing track of the first. */
        const val SPOKEN_RESULTS = 3
    }
}
