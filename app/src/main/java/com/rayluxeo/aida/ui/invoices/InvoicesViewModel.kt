package com.rayluxeo.aida.ui.invoices

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.RecurringInvoiceProfile
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import com.rayluxeo.aida.domain.repository.RecurringInvoiceRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.usecase.ConvertQuoteToInvoiceUseCase
import com.rayluxeo.aida.domain.usecase.CreateQuoteDraftUseCase
import com.rayluxeo.aida.domain.usecase.CreateInvoiceDraftUseCase
import com.rayluxeo.aida.domain.usecase.ProcessRecurringInvoicesUseCase
import com.rayluxeo.aida.domain.usecase.RecordPaymentUseCase
import java.time.LocalDate
import com.rayluxeo.aida.ui.invoices.pdf.InvoicePdfGenerator
import com.rayluxeo.aida.ui.invoices.pdf.PdfPreviewRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import com.rayluxeo.aida.domain.model.ClientFilter
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.InvoiceDeliveryRecord
import com.rayluxeo.aida.domain.model.InvoiceDeliveryStatus
import com.rayluxeo.aida.domain.repository.InvoiceDeliveryRepository
import com.rayluxeo.aida.domain.usecase.PrepareInvoiceDeliveryUseCase
import com.rayluxeo.aida.domain.usecase.RecordInvoiceDeliveryUseCase
import com.rayluxeo.aida.ui.invoices.delivery.AndroidInvoiceShareLauncher
import com.rayluxeo.aida.ui.tasks.displayLanguage
import android.content.Context

enum class InvoicesTab { INVOICES, QUOTES, RECURRING }
enum class InvoiceFilter { ALL, UNPAID, PAID, DRAFT, OVERDUE }

/**
 * What the screen has to tell Abu after an action, rendered per language by [InvoicesWording].
 * Carrying the facts rather than a finished English sentence is what lets the same outcome speak
 * Bengali to a Bengali exchange (§1, §4.6) — the view model never builds human wording.
 *
 * The numbers Abu assigned (an invoice or quote number) ride along as data and are placed into the
 * line unchanged; the raw [detail] of a failure is a technical code, shown in parentheses rather
 * than as the whole message (§12).
 */
sealed interface InvoicesNotice {
    data class QuoteConverted(val quoteNumber: String, val invoiceNumber: String) : InvoicesNotice
    data class QuoteConversionFailed(val detail: String?) : InvoicesNotice
    data class ClientNotFound(val invoiceNumber: String) : InvoicesNotice
    data class PdfPreviewFailed(val detail: String?) : InvoicesNotice
    data class PdfGenerationFailed(val detail: String?) : InvoicesNotice
    data class DeliveryPdfFailed(val detail: String?) : InvoicesNotice
    data class InvoiceShared(val invoiceNumber: String, val method: InvoiceDeliveryMethod) : InvoicesNotice
    data class DeliveryFailed(val detail: String?) : InvoicesNotice
    data class RecurringGenerated(val count: Int) : InvoicesNotice
    data object NoRecurringDue : InvoicesNotice
    data class RecurringFailed(val detail: String?) : InvoicesNotice
    data class InvoiceCreated(val invoiceNumber: String) : InvoicesNotice
    data class InvoiceCreateFailed(val detail: String?) : InvoicesNotice
    data class PaymentRecorded(val invoiceNumber: String, val amount: String) : InvoicesNotice
    data class PaymentFailed(val detail: String?) : InvoicesNotice
    data class InvoiceSent(val invoiceNumber: String) : InvoicesNotice
    data class InvoiceDeleted(val invoiceNumber: String) : InvoicesNotice
    data class QuoteCreated(val quoteNumber: String) : InvoicesNotice
    data class QuoteCreateFailed(val detail: String?) : InvoicesNotice
}

data class InvoicesUiState(
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val selectedTab: InvoicesTab = InvoicesTab.INVOICES,
    val filter: InvoiceFilter = InvoiceFilter.ALL,
    val searchQuery: String = "",
    val invoices: List<Invoice> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val recurringProfiles: List<RecurringInvoiceProfile> = emptyList(),
    val clients: Map<Long, Client> = emptyMap(),
    val userProfile: UserProfile? = null,
    val selectedInvoice: Invoice? = null,
    val paymentInvoice: Invoice? = null,
    val deliveryDraft: InvoiceDeliveryDraft? = null,
    val deliveryHistory: List<InvoiceDeliveryRecord> = emptyList(),
    val pdfPreviewBitmaps: List<Bitmap> = emptyList(),
    val isGeneratingPdf: Boolean = false,
    val showNewInvoice: Boolean = false,
    val showNewQuote: Boolean = false,
    val today: LocalDate = LocalDate.EPOCH,
    val notice: InvoicesNotice? = null,
)

@HiltViewModel
class InvoicesViewModel @Inject constructor(
    private val invoiceRepo: InvoiceRepository,
    private val quoteRepo: QuoteRepository,
    private val clientRepo: ClientRepository,
    private val recurringRepo: RecurringInvoiceRepository,
    private val userProfileRepo: UserProfileRepository,
    private val settingsRepo: SettingsRepository,
    private val deliveryRepo: InvoiceDeliveryRepository,
    private val convertQuote: ConvertQuoteToInvoiceUseCase,
    private val processRecurring: ProcessRecurringInvoicesUseCase,
    private val recordPayment: RecordPaymentUseCase,
    private val prepareDelivery: PrepareInvoiceDeliveryUseCase,
    private val recordDelivery: RecordInvoiceDeliveryUseCase,
    private val shareLauncher: AndroidInvoiceShareLauncher,
    private val pdfGenerator: InvoicePdfGenerator,
    private val pdfRenderer: PdfPreviewRenderer,
    private val createDraft: CreateInvoiceDraftUseCase,
    private val createQuoteDraft: CreateQuoteDraftUseCase,
    private val clock: MiraClock,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(InvoicesTab.INVOICES)
    private val filter = MutableStateFlow(InvoiceFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)
    private val paymentInvoice = MutableStateFlow<Invoice?>(null)
    private val deliveryDraft = MutableStateFlow<InvoiceDeliveryDraft?>(null)
    private val pdfPreviewBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    private val isGeneratingPdf = MutableStateFlow(false)
    private val showNewInvoice = MutableStateFlow(false)
    private val showNewQuote = MutableStateFlow(false)
    private val notice = MutableStateFlow<InvoicesNotice?>(null)

    val uiState: StateFlow<InvoicesUiState> = combine(
        selectedTab,
        filter,
        searchQuery,
        invoiceRepo.observeAll(),
        quoteRepo.observeAll(),
        recurringRepo.observeAll(),
        clientRepo.observe(ClientFilter.ALL),
        userProfileRepo.observe(),
        selectedInvoice,
        deliveryDraft,
        deliveryRepo.observeAll(),
        pdfPreviewBitmaps,
        isGeneratingPdf,
        notice,
        settingsRepo.settings,
        showNewInvoice,
        paymentInvoice,
        showNewQuote,
    ) { args: Array<Any?> ->
        val tab = args[0] as InvoicesTab
        val f = args[1] as InvoiceFilter
        val q = args[2] as String
        @Suppress("UNCHECKED_CAST")
        val invs = args[3] as List<Invoice>
        @Suppress("UNCHECKED_CAST")
        val qus = args[4] as List<Quote>
        @Suppress("UNCHECKED_CAST")
        val recs = args[5] as List<RecurringInvoiceProfile>
        @Suppress("UNCHECKED_CAST")
        val cls = (args[6] as List<Client>).associateBy { it.id }
        val prof = args[7] as? UserProfile
        val selInv = args[8] as? Invoice
        val draft = args[9] as? InvoiceDeliveryDraft
        @Suppress("UNCHECKED_CAST")
        val deliveries = args[10] as List<InvoiceDeliveryRecord>
        @Suppress("UNCHECKED_CAST")
        val pdfBmps = args[11] as List<Bitmap>
        val genPdf = args[12] as Boolean
        val not = args[13] as? InvoicesNotice
        val language = (args[14] as MiraSettings).languageMode.displayLanguage()
        val newInvoiceOpen = args[15] as Boolean
        val payInv = args[16] as? Invoice
        val newQuoteOpen = args[17] as Boolean

        val filteredInvoices = invs.filter { inv ->
            val matchesSearch = q.isBlank() || inv.number.contains(q, ignoreCase = true) ||
                cls[inv.clientId]?.name?.contains(q, ignoreCase = true) == true
            val matchesFilter = when (f) {
                InvoiceFilter.ALL -> true
                InvoiceFilter.UNPAID -> inv.outstanding.amount > BigDecimal.ZERO && inv.status != InvoiceStatus.VOID
                InvoiceFilter.PAID -> inv.status == InvoiceStatus.PAID
                InvoiceFilter.DRAFT -> inv.status == InvoiceStatus.DRAFT
                InvoiceFilter.OVERDUE -> inv.status == InvoiceStatus.OVERDUE
            }
            matchesSearch && matchesFilter
        }

        val filteredQuotes = qus.filter { quo ->
            q.isBlank() || quo.number.contains(q, ignoreCase = true) ||
                cls[quo.clientId]?.name?.contains(q, ignoreCase = true) == true
        }

        InvoicesUiState(
            language = language,
            selectedTab = tab,
            filter = f,
            searchQuery = q,
            invoices = filteredInvoices,
            quotes = filteredQuotes,
            recurringProfiles = recs,
            clients = cls,
            userProfile = prof,
            selectedInvoice = selInv,
            paymentInvoice = payInv,
            deliveryDraft = draft,
            deliveryHistory = deliveries,
            pdfPreviewBitmaps = pdfBmps,
            isGeneratingPdf = genPdf,
            showNewInvoice = newInvoiceOpen,
            showNewQuote = newQuoteOpen,
            today = clock.today(),
            notice = not,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvoicesUiState(),
    )

    fun selectTab(tab: InvoicesTab) {
        selectedTab.value = tab
    }

    fun setFilter(newFilter: InvoiceFilter) {
        filter.value = newFilter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectInvoice(invoice: Invoice?) {
        selectedInvoice.value = invoice
    }

    fun clearPdfPreview() {
        pdfPreviewBitmaps.value = emptyList()
    }

    fun clearNotice() {
        notice.value = null
    }

    fun openNewInvoice() {
        showNewInvoice.value = true
    }

    fun dismissNewInvoice() {
        showNewInvoice.value = false
    }

    /**
     * Raises a one-line draft from the manual form — the typed path to what the voice command does,
     * so Abu can bill without speaking. The client must be one he has already entered (an invoice
     * to a stranger is a mistake, not a convenience), and the amount is parsed as [BigDecimal] in
     * the client's own currency, never a Double (§8). Pricing, numbering and the activity log all
     * stay with [CreateInvoiceDraftUseCase], the single owner of that logic — this adds no second
     * copy of it.
     */
    fun createInvoice(clientId: Long, description: String, amountText: String, dueOn: LocalDate?) {
        viewModelScope.launch {
            val client = uiState.value.clients[clientId] ?: run {
                notice.value = InvoicesNotice.InvoiceCreateFailed(null)
                return@launch
            }
            val currency = client.billing?.currency
                ?: uiState.value.userProfile?.defaultCurrency
                ?: Money.BDT
            val parsed = amountText.trim().toBigDecimalOrNull()
            if (parsed == null || parsed <= BigDecimal.ZERO) {
                notice.value = InvoicesNotice.InvoiceCreateFailed(null)
                return@launch
            }
            val amount = try {
                Money.of(parsed.toPlainString(), currency)
            } catch (invalid: NumberFormatException) {
                notice.value = InvoicesNotice.InvoiceCreateFailed(null)
                return@launch
            }
            when (val res = createDraft(client, amount, description.trim().ifBlank { null }, dueOn)) {
                is AppResult.Success -> {
                    showNewInvoice.value = false
                    selectedTab.value = InvoicesTab.INVOICES
                    notice.value = InvoicesNotice.InvoiceCreated(res.value.number)
                }
                is AppResult.Failure ->
                    notice.value = InvoicesNotice.InvoiceCreateFailed(res.error.detail ?: res.error.code.name)
            }
        }
    }

    fun convertQuoteToInvoice(quote: Quote) {
        viewModelScope.launch {
            when (val res = convertQuote(quote)) {
                is AppResult.Success -> {
                    notice.value = InvoicesNotice.QuoteConverted(
                        quoteNumber = quote.number,
                        invoiceNumber = res.value.number,
                    )
                    selectedTab.value = InvoicesTab.INVOICES
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.QuoteConversionFailed(res.error.detail ?: res.error.code.name)
                }
            }
        }
    }

    fun openRecordPayment(invoice: Invoice) {
        paymentInvoice.value = invoice
    }

    fun dismissRecordPayment() {
        paymentInvoice.value = null
    }

    fun recordInvoicePayment(
        invoice: Invoice,
        amountText: String,
        method: PaymentMethod = PaymentMethod.BANK_TRANSFER,
        reference: String? = null,
        note: String? = null,
    ) {
        viewModelScope.launch {
            val parsed = amountText.trim().toBigDecimalOrNull()
            if (parsed == null || parsed <= BigDecimal.ZERO) {
                notice.value = InvoicesNotice.PaymentFailed(null)
                return@launch
            }
            val money = try {
                Money.of(parsed.toPlainString(), invoice.total.currency)
            } catch (e: Exception) {
                notice.value = InvoicesNotice.PaymentFailed(e.message)
                return@launch
            }
            when (val res = recordPayment(
                amount = money,
                method = method,
                invoiceId = invoice.id,
                clientId = invoice.clientId,
                reference = reference,
                note = note,
            )) {
                is AppResult.Success -> {
                    paymentInvoice.value = null
                    notice.value = InvoicesNotice.PaymentRecorded(
                        invoiceNumber = invoice.number,
                        amount = "${money.currency.currencyCode} ${money.amount.toPlainString()}",
                    )
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.PaymentFailed(res.error.detail ?: res.error.code.name)
                }
            }
        }
    }

    fun markInvoicePaid(invoice: Invoice, method: PaymentMethod = PaymentMethod.BANK_TRANSFER) {
        if (invoice.outstanding.amount <= BigDecimal.ZERO) return
        recordInvoicePayment(
            invoice = invoice,
            amountText = invoice.outstanding.amount.toPlainString(),
            method = method,
            reference = "Full payment",
            note = null,
        )
    }

    fun markInvoiceSent(invoice: Invoice) {
        viewModelScope.launch {
            when (val res = invoiceRepo.setStatus(invoice.id, InvoiceStatus.SENT)) {
                is AppResult.Success -> {
                    if (selectedInvoice.value?.id == invoice.id) {
                        selectedInvoice.value = res.value
                    }
                    notice.value = InvoicesNotice.InvoiceSent(invoice.number)
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.InvoiceCreateFailed(res.error.detail ?: res.error.code.name)
                }
            }
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            when (val res = invoiceRepo.softDelete(invoice.id)) {
                is AppResult.Success -> {
                    if (selectedInvoice.value?.id == invoice.id) {
                        selectedInvoice.value = null
                    }
                    notice.value = InvoicesNotice.InvoiceDeleted(invoice.number)
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.InvoiceCreateFailed(res.error.detail ?: res.error.code.name)
                }
            }
        }
    }

    fun openNewQuote() {
        showNewQuote.value = true
    }

    fun dismissNewQuote() {
        showNewQuote.value = false
    }

    fun createQuote(clientId: Long, description: String, amountText: String, validUntil: LocalDate?) {
        viewModelScope.launch {
            val client = uiState.value.clients[clientId] ?: run {
                notice.value = InvoicesNotice.QuoteCreateFailed(null)
                return@launch
            }
            val currency = client.billing?.currency
                ?: uiState.value.userProfile?.defaultCurrency
                ?: Money.BDT
            val parsed = amountText.trim().toBigDecimalOrNull()
            val amount = if (parsed != null && parsed > BigDecimal.ZERO) {
                try {
                    Money.of(parsed.toPlainString(), currency)
                } catch (e: Exception) {
                    null
                }
            } else null

            when (val res = createQuoteDraft(client, amount, description.trim().ifBlank { null }, validUntil)) {
                is AppResult.Success -> {
                    showNewQuote.value = false
                    selectedTab.value = InvoicesTab.QUOTES
                    notice.value = InvoicesNotice.QuoteCreated(res.value.number)
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.QuoteCreateFailed(res.error.detail ?: res.error.code.name)
                }
            }
        }
    }

    fun previewPdf(invoice: Invoice) {
        viewModelScope.launch {
            isGeneratingPdf.value = true
            val client = clientRepo.byId(invoice.clientId).getOrNull()
            if (client == null) {
                notice.value = InvoicesNotice.ClientNotFound(invoice.number)
                isGeneratingPdf.value = false
                return@launch
            }
            val profile = userProfileRepo.current().getOrNull()
            when (val genResult = pdfGenerator.generate(invoice, client, profile, uiState.value.language)) {
                is AppResult.Success -> {
                    val file = genResult.value
                    when (val renderResult = pdfRenderer.renderPages(file)) {
                        is AppResult.Success -> {
                            pdfPreviewBitmaps.value = renderResult.value
                        }
                        is AppResult.Failure -> {
                            notice.value = InvoicesNotice.PdfPreviewFailed(
                                renderResult.error.detail ?: renderResult.error.code.name,
                            )
                        }
                    }
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.PdfGenerationFailed(
                        genResult.error.detail ?: genResult.error.code.name,
                    )
                }
            }
            isGeneratingPdf.value = false
        }
    }

    fun prepareInvoiceDelivery(
        invoice: Invoice,
        preferredMethod: InvoiceDeliveryMethod = InvoiceDeliveryMethod.EMAIL,
    ) {
        viewModelScope.launch {
            isGeneratingPdf.value = true
            val client = clientRepo.byId(invoice.clientId).getOrNull()
            if (client == null) {
                notice.value = InvoicesNotice.ClientNotFound(invoice.number)
                isGeneratingPdf.value = false
                return@launch
            }
            val profile = userProfileRepo.current().getOrNull()
            val pdfFile = when (val genResult = pdfGenerator.generate(invoice, client, profile, uiState.value.language)) {
                is AppResult.Success -> genResult.value
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.DeliveryPdfFailed(
                        genResult.error.detail ?: genResult.error.code.name,
                    )
                    isGeneratingPdf.value = false
                    return@launch
                }
            }

            val draft = prepareDelivery(
                invoice = invoice,
                client = client,
                method = preferredMethod,
                language = uiState.value.language,
                profile = profile,
                attachmentFile = pdfFile,
            )

            // Record PREPARED status
            recordDelivery(draft, InvoiceDeliveryStatus.PREPARED)

            deliveryDraft.value = draft
            isGeneratingPdf.value = false
        }
    }

    fun updateDeliveryMethod(newMethod: InvoiceDeliveryMethod) {
        val currentDraft = deliveryDraft.value ?: return
        viewModelScope.launch {
            val invoice = invoiceRepo.byId(currentDraft.invoiceId).getOrNull() ?: return@launch
            val client = clientRepo.byId(invoice.clientId).getOrNull() ?: return@launch
            val profile = userProfileRepo.current().getOrNull()
            val attachmentFile = currentDraft.attachmentPath?.let { File(it) }

            deliveryDraft.value = prepareDelivery(
                invoice = invoice,
                client = client,
                method = newMethod,
                language = uiState.value.language,
                profile = profile,
                attachmentFile = attachmentFile,
            )
        }
    }

    fun cancelDelivery() {
        val draft = deliveryDraft.value
        if (draft != null) {
            viewModelScope.launch {
                recordDelivery(draft, InvoiceDeliveryStatus.CANCELLED)
            }
        }
        deliveryDraft.value = null
    }

    fun confirmAndDeliver(context: Context) {
        val draft = deliveryDraft.value ?: return
        viewModelScope.launch {
            // Explicit user confirmation provided. The chooser runs in Abu's language.
            val language = settingsRepo.current().languageMode.displayLanguage()
            when (val launchResult = shareLauncher.launchDelivery(context, draft, language)) {
                is AppResult.Success -> {
                    // Record SHARED and transition invoice status to SENT if appropriate
                    recordDelivery(draft, InvoiceDeliveryStatus.SHARED)
                    recordDelivery(draft, InvoiceDeliveryStatus.SENT)
                    notice.value = InvoicesNotice.InvoiceShared(draft.invoiceNumber, draft.method)
                }
                is AppResult.Failure -> {
                    recordDelivery(draft, InvoiceDeliveryStatus.FAILED, notes = launchResult.error.detail)
                    notice.value = InvoicesNotice.DeliveryFailed(
                        launchResult.error.detail ?: launchResult.error.code.name,
                    )
                }
            }
            deliveryDraft.value = null
        }
    }

    fun processRecurringNow() {
        viewModelScope.launch {
            when (val res = processRecurring()) {
                is AppResult.Success -> {
                    val count = res.value.size
                    notice.value = if (count > 0) {
                        InvoicesNotice.RecurringGenerated(count)
                    } else {
                        InvoicesNotice.NoRecurringDue
                    }
                }
                is AppResult.Failure -> {
                    notice.value = InvoicesNotice.RecurringFailed(res.error.detail ?: res.error.code.name)
                }
            }
        }
    }
}
