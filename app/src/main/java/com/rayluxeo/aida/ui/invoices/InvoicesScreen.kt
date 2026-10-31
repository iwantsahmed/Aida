package com.rayluxeo.aida.ui.invoices

import com.rayluxeo.aida.core.design.component.MiraIcons

import com.rayluxeo.aida.core.common.SpokenLanguage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.rayluxeo.aida.core.design.ContinuousRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.ui.invoices.delivery.InvoiceDeliveryConfirmationDialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.ui.tasks.TaskDatePickerDialog
import android.graphics.Bitmap
import java.math.BigDecimal
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.RecurringInvoiceProfile
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.moneyFigure
import com.rayluxeo.aida.ui.common.LocalDateStyle
import com.rayluxeo.aida.ui.common.formatDate
import java.time.LocalDate

@Composable
fun InvoicesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val words = invoicesWording(state.language)

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.title,
                subtitle = words.subtitle,
                onBack = onBack,
                action = {
                    val isQuotes = state.selectedTab == InvoicesTab.QUOTES
                    MiraIconButton(
                        icon = MiraIcons.Add,
                        contentDescription = if (isQuotes) words.newQuoteAction else words.newInvoiceAction,
                        onClick = {
                            if (isQuotes) {
                                viewModel.openNewQuote()
                            } else {
                                viewModel.openNewInvoice()
                            }
                        },
                    )
                },
            )
        },
        horizontalPadding = 0.dp,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Notice
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = words.noticeText(notice),
                    tone = MiraNoticeTone.CONFIRM,
                    onDismiss = { viewModel.clearNotice() },
                    modifier = Modifier.padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.xs),
                )
            }

            // Tabs Segmented Control
            val tabLabels = listOf(
                words.invoicesTab(state.invoices.size),
                words.quotesTab(state.quotes.size),
                words.recurringTab(state.recurringProfiles.size),
            )
            MiraSegmentedControl(
                options = tabLabels,
                selectedIndex = state.selectedTab.ordinal,
                onSelect = { index -> viewModel.selectTab(InvoicesTab.entries[index]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.xs),
            )

            // Search Bar
            MiraSearchField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = words.searchPlaceholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.xs),
            )

            when (state.selectedTab) {
                InvoicesTab.INVOICES -> {
                    // Filters row
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MiraTheme.spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                        modifier = Modifier.padding(vertical = MiraTheme.spacing.xs),
                    ) {
                        items(InvoiceFilter.entries) { f ->
                            MiraChip(
                                text = words.filterLabel(f),
                                selected = state.filter == f,
                                onClick = { viewModel.setFilter(f) },
                            )
                        }
                    }

                    if (state.invoices.isEmpty()) {
                        MiraEmptyState(
                            icon = MiraIcons.ReceiptLong,
                            message = words.noInvoices,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(state.invoices, key = { it.id }) { invoice ->
                                InvoiceCard(
                                    invoice = invoice,
                                    client = state.clients[invoice.clientId],
                                    words = words,
                                    language = state.language,
                                    onClick = { viewModel.selectInvoice(invoice) },
                                    onPdf = { viewModel.previewPdf(invoice) },
                                    onDeliver = { viewModel.prepareInvoiceDelivery(invoice) },
                                    onPayment = { viewModel.openRecordPayment(invoice) },
                                )
                            }
                        }
                    }
                }

                InvoicesTab.QUOTES -> {
                    if (state.quotes.isEmpty()) {
                        MiraEmptyState(
                            icon = MiraIcons.Description,
                            message = words.noQuotes,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(state.quotes, key = { it.id }) { quote ->
                                QuoteCard(
                                    quote = quote,
                                    client = state.clients[quote.clientId],
                                    words = words,
                                    language = state.language,
                                    onConvert = { viewModel.convertQuoteToInvoice(quote) },
                                )
                            }
                        }
                    }
                }

                InvoicesTab.RECURRING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = words.automatedSchedules,
                            style = MaterialTheme.typography.labelMedium,
                            color = MiraPalette.Muted,
                        )
                        MiraButton(
                            text = words.sweepDueNow,
                            onClick = { viewModel.processRecurringNow() },
                        )
                    }

                    if (state.recurringProfiles.isEmpty()) {
                        MiraEmptyState(
                            icon = MiraIcons.Repeat,
                            message = words.noRecurring,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = MiraTheme.spacing.screenHorizontal, vertical = MiraTheme.spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(state.recurringProfiles, key = { it.id }) { profile ->
                                RecurringCard(
                                    profile = profile,
                                    client = state.clients[profile.clientId],
                                    words = words,
                                    language = state.language,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Invoice Detail Dialog
    state.selectedInvoice?.let { invoice ->
        InvoiceDetailDialog(
            invoice = invoice,
            client = state.clients[invoice.clientId],
            words = words,
            language = state.language,
            onDismiss = { viewModel.selectInvoice(null) },
            onPreviewPdf = {
                viewModel.selectInvoice(null)
                viewModel.previewPdf(invoice)
            },
            onDeliver = {
                viewModel.selectInvoice(null)
                viewModel.prepareInvoiceDelivery(invoice)
            },
            onRecordPayment = {
                viewModel.selectInvoice(null)
                viewModel.openRecordPayment(invoice)
            },
            onMarkSent = {
                viewModel.markInvoiceSent(invoice)
            },
            onDelete = {
                viewModel.deleteInvoice(invoice)
            },
        )
    }

    // Record Payment Dialog
    state.paymentInvoice?.let { invoice ->
        RecordPaymentDialog(
            invoice = invoice,
            client = state.clients[invoice.clientId],
            words = words,
            language = state.language,
            onDismiss = { viewModel.dismissRecordPayment() },
            onConfirm = { amountText, method, reference, note ->
                viewModel.recordInvoicePayment(invoice, amountText, method, reference, note)
            },
        )
    }

    // Invoice Delivery Confirmation Dialog — in whatever language Abu chose in Settings.
    val context = LocalContext.current
    state.deliveryDraft?.let { draft ->
        InvoiceDeliveryConfirmationDialog(
            draft = draft,
            language = state.language,
            onMethodSelect = { method -> viewModel.updateDeliveryMethod(method) },
            onConfirm = { viewModel.confirmAndDeliver(context) },
            onDismiss = { viewModel.cancelDelivery() },
        )
    }

    // PDF Preview Modal
    if (state.pdfPreviewBitmaps.isNotEmpty()) {
        PdfPreviewDialog(
            bitmaps = state.pdfPreviewBitmaps,
            words = words,
            onDismiss = { viewModel.clearPdfPreview() },
        )
    }

    if (state.showNewInvoice) {
        NewInvoiceSheet(
            clients = state.clients.values.sortedBy { it.name.lowercase() },
            today = state.today,
            words = words,
            language = state.language,
            onDismiss = { viewModel.dismissNewInvoice() },
            onCreate = { clientId, description, amount, dueOn ->
                viewModel.createInvoice(clientId, description, amount, dueOn)
            },
        )
    }

    if (state.showNewQuote) {
        NewQuoteSheet(
            clients = state.clients.values.sortedBy { it.name.lowercase() },
            today = state.today,
            words = words,
            language = state.language,
            onDismiss = { viewModel.dismissNewQuote() },
            onCreate = { clientId, description, amount, validUntil ->
                viewModel.createQuote(clientId, description, amount, validUntil)
            },
        )
    }

    if (state.isGeneratingPdf) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiraPalette.Obsidian.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            MiraLoadingIndicator()
        }
    }
}

/**
 * The manual path to a bill: pick a client already on file, say what it is for and how much, and
 * when it falls due. It raises a single-line draft through the same use case the voice command
 * uses, so there is no second copy of the invoice logic — Abu prices and adds to it afterwards. A
 * bill to a stranger is a mistake, so the client must be one he has entered; with none on file it
 * says so plainly and offers nothing to fill in.
 */
@Composable
private fun NewInvoiceSheet(
    clients: List<Client>,
    today: LocalDate,
    words: InvoicesWording,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onCreate: (clientId: Long, description: String, amount: String, dueOn: LocalDate?) -> Unit,
) {
    val spacing = MiraTheme.spacing
    var selectedClientId by remember(clients) { mutableStateOf(clients.firstOrNull()?.id) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueOn by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val effectiveDue = dueOn ?: today.plusDays(NEW_INVOICE_DEFAULT_TERM_DAYS)

    MiraBottomSheet(onDismissRequest = onDismiss, title = words.newInvoiceTitle) {
        if (clients.isEmpty()) {
            MiraEmptyState(
                icon = MiraIcons.Groups,
                message = words.formNoClients,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xl),
            )
        } else {
            FormSectionLabel(words.formClient)
            Spacer(Modifier.height(spacing.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(clients, key = { it.id }) { client ->
                    MiraChip(
                        text = client.name,
                        selected = client.id == selectedClientId,
                        onClick = { selectedClientId = client.id },
                    )
                }
            }

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.formAmount)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = amount,
                onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' } },
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.formDescription)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = words.formDescriptionHint,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))
            MiraListRow(
                title = words.formDueDate,
                trailingText = invoiceDate(effectiveDue, language),
                onClick = { showDatePicker = true },
                leading = {
                    Icon(
                        imageVector = MiraIcons.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(Modifier.height(spacing.xl))
            MiraButton(
                text = words.formCreate,
                onClick = { selectedClientId?.let { id -> onCreate(id, description, amount, dueOn) } },
                enabled = selectedClientId != null && amount.isNotBlank(),
                leadingIcon = MiraIcons.ReceiptLong,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.sm))
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = effectiveDue,
            setText = words.formSet,
            cancelText = words.formCancel,
            onPick = { picked ->
                dueOn = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** The quiet upper-cased label over each field in the new-invoice sheet — the section rhythm. */
@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A fortnight, the same starting term [CreateInvoiceDraftUseCase] uses, so the form and the voice
 *  path agree on what "no date given" means. */
private const val NEW_INVOICE_DEFAULT_TERM_DAYS = 14L

@Composable
private fun InvoiceCard(
    invoice: Invoice,
    client: Client?,
    words: InvoicesWording,
    language: SpokenLanguage,
    onClick: () -> Unit,
    onPdf: () -> Unit,
    onDeliver: () -> Unit,
    onPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dueStr = invoiceDate(invoice.dueOn, language)

    MiraCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = invoice.number.ifEmpty { words.draftPlaceholder },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusBadge(status = invoice.status, words = words)
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            Text(
                text = client?.name ?: words.unknownClient,
                style = MaterialTheme.typography.bodyMedium,
                color = MiraPalette.Muted,
            )

            Spacer(modifier = Modifier.height(MiraTheme.spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = words.total(money(invoice.total, language)),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (invoice.outstanding.amount > BigDecimal.ZERO) {
                        Text(
                            text = words.dueBy(money(invoice.outstanding, language), dueStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MiraPalette.Accent,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (invoice.outstanding.amount > BigDecimal.ZERO) {
                        MiraIconButton(
                            icon = MiraIcons.AccountBalanceWallet,
                            contentDescription = words.recordPaymentAction,
                            onClick = onPayment,
                        )
                    }
                    MiraIconButton(
                        icon = MiraIcons.PictureAsPdf,
                        contentDescription = words.previewPdfAction,
                        onClick = onPdf,
                    )
                    MiraIconButton(
                        icon = MiraIcons.Send,
                        contentDescription = words.deliverInvoiceAction,
                        onClick = onDeliver,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(
    quote: Quote,
    client: Client?,
    words: InvoicesWording,
    language: SpokenLanguage,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = quote.number,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                QuoteStatusBadge(status = quote.status, words = words)
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            Text(
                text = client?.name ?: words.unknownClient,
                style = MaterialTheme.typography.bodyMedium,
                color = MiraPalette.Muted,
            )

            Spacer(modifier = Modifier.height(MiraTheme.spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = money(quote.total, language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (!quote.isConverted && quote.status != QuoteStatus.DECLINED && quote.status != QuoteStatus.EXPIRED) {
                    MiraButton(
                        text = words.convertToInvoice,
                        onClick = onConvert,
                    )
                } else if (quote.isConverted) {
                    Text(
                        text = words.converted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MiraPalette.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringCard(
    profile: RecurringInvoiceProfile,
    client: Client?,
    words: InvoicesWording,
    language: SpokenLanguage,
    modifier: Modifier = Modifier,
) {
    MiraCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = words.recurringTemplate(profile.sourceInvoiceNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = words.cycleLabel(profile.cycle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MiraPalette.Accent,
                )
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            Text(
                text = words.recurringClient(client?.name ?: words.unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MiraPalette.Muted,
            )

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            val nextStr = invoiceDate(profile.nextRunDate, language)
            Text(
                text = words.recurringNextDraft(nextStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: InvoiceStatus, words: InvoicesWording) {
    val color = when (status) {
        InvoiceStatus.DRAFT -> MiraPalette.Muted
        InvoiceStatus.READY -> MiraPalette.Accent
        InvoiceStatus.SENT -> MaterialTheme.colorScheme.primary
        InvoiceStatus.PARTIALLY_PAID -> MiraPalette.Accent
        InvoiceStatus.PAID -> MiraPalette.Positive
        InvoiceStatus.OVERDUE -> MiraPalette.Danger
        InvoiceStatus.VOID -> MiraPalette.Muted
    }
    val text = words.invoiceStatus(status)

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), ContinuousRoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun QuoteStatusBadge(status: QuoteStatus, words: InvoicesWording) {
    val color = when (status) {
        QuoteStatus.DRAFT -> MiraPalette.Muted
        QuoteStatus.SENT -> MaterialTheme.colorScheme.primary
        QuoteStatus.ACCEPTED -> MiraPalette.Positive
        QuoteStatus.DECLINED -> MiraPalette.Danger
        QuoteStatus.EXPIRED -> MiraPalette.Muted
    }
    val text = words.quoteStatus(status)

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), ContinuousRoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InvoiceDetailDialog(
    invoice: Invoice,
    client: Client?,
    words: InvoicesWording,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onPreviewPdf: () -> Unit,
    onDeliver: () -> Unit,
    onRecordPayment: () -> Unit,
    onMarkSent: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MiraCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = invoice.number.ifEmpty { words.draftPlaceholder },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MiraIconButton(
                        icon = MiraIcons.Close,
                        contentDescription = words.close,
                        onClick = onDismiss,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                StatusBadge(status = invoice.status, words = words)
                Spacer(modifier = Modifier.height(16.dp))

                DetailRow(words.labelClient, client?.name ?: words.unknown)
                DetailRow(words.labelIssued, invoiceDate(invoice.issuedOn, language))
                DetailRow(words.labelDue, invoiceDate(invoice.dueOn, language))
                DetailRow(words.labelCurrency, invoice.currency.currencyCode)

                MiraDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = words.lineItems(invoice.lines.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                invoice.lines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${words.lineQuantity(line.quantity)} ${line.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = money(line.lineTotal, language),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                MiraDivider(modifier = Modifier.padding(vertical = 12.dp))

                DetailRow(words.labelSubtotal, money(invoice.subtotal, language))
                if (invoice.discount.amount > BigDecimal.ZERO) {
                    DetailRow(words.labelDiscount, "-${money(invoice.discount, language)}")
                }
                if (invoice.tax.amount > BigDecimal.ZERO) {
                    DetailRow(words.labelTax, money(invoice.tax, language))
                }
                DetailRow(words.labelTotal, money(invoice.total, language), bold = true)
                DetailRow(words.labelAmountPaid, money(invoice.amountPaid, language))
                DetailRow(words.labelBalanceDue, money(invoice.outstanding, language), accent = true)

                invoice.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    MiraDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = words.notesPaymentInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MiraPalette.Muted,
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiraButton(
                        text = words.previewPdf,
                        onClick = onPreviewPdf,
                        modifier = Modifier.weight(1f),
                    )
                    MiraButton(
                        text = words.deliver,
                        onClick = onDeliver,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (invoice.outstanding.amount > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MiraButton(
                        text = words.recordPaymentAction,
                        onClick = onRecordPayment,
                        leadingIcon = MiraIcons.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (invoice.status == InvoiceStatus.DRAFT || invoice.status == InvoiceStatus.READY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiraButton(
                            text = words.markSentAction,
                            onClick = onMarkSent,
                            modifier = Modifier.weight(1f),
                        )
                        MiraButton(
                            text = words.deleteInvoiceAction,
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A date on the invoice cards and the detail dialog, in the style Abu chose in Settings and the
 * language Mira is speaking — Bengali months and digits when he is in Bengali.
 */
@androidx.compose.runtime.Composable
private fun invoiceDate(date: LocalDate, language: SpokenLanguage): String =
    formatDate(date, LocalDateStyle.current, language)

/**
 * An amount as Abu reads it — the taka sign and grouped digits — rather than the raw `BDT 45000`
 * a `BigDecimal.toString()` prints. Shares `moneyFigure` with Mira's spoken figures so a total on
 * screen matches one she says aloud; in Bengali the digits are Bengali too, so a total never reads
 * in a second voice beside the Bengali label next to it.
 */
private fun money(amount: Money, language: SpokenLanguage): String {
    val symbol = if (amount.currency.currencyCode == "BDT") "৳" else "${amount.currency.currencyCode} "
    val figure = moneyFigure(amount)
    val localized = if (language == SpokenLanguage.BENGALI) bengaliNumerals(figure) else figure
    return "$symbol$localized"
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    bold: Boolean = false,
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) MiraPalette.Accent else MiraPalette.Muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (bold || accent) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (accent) MiraPalette.Accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PdfPreviewDialog(
    bitmaps: List<Bitmap>,
    words: InvoicesWording,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MiraCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = words.pdfPreviewTitle(bitmaps.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MiraIconButton(
                        icon = MiraIcons.Close,
                        contentDescription = words.closePreview,
                        onClick = onDismiss,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(bitmaps) { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = words.invoicePdfPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MiraPalette.Platinum, ContinuousRoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordPaymentDialog(
    invoice: Invoice,
    client: Client?,
    words: InvoicesWording,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onConfirm: (amountText: String, method: PaymentMethod, reference: String?, note: String?) -> Unit,
) {
    val spacing = MiraTheme.spacing
    var amountText by remember { mutableStateOf(invoice.outstanding.amount.toPlainString()) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.BANK_TRANSFER) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val methods = listOf(
        PaymentMethod.BANK_TRANSFER,
        PaymentMethod.MOBILE_WALLET,
        PaymentMethod.CASH,
        PaymentMethod.CARD,
        PaymentMethod.OTHER,
    )

    MiraBottomSheet(onDismissRequest = onDismiss, title = words.recordPaymentTitle) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = invoice.number,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = client?.name ?: words.unknownClient,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiraPalette.Muted,
                )
            }

            Spacer(Modifier.height(spacing.sm))
            Text(
                text = "${words.labelBalanceDue}: ${money(invoice.outstanding, language)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MiraPalette.Accent,
            )

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.paymentAmount)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                placeholder = invoice.outstanding.amount.toPlainString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.paymentMethod)
            Spacer(Modifier.height(spacing.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(methods) { method ->
                    MiraChip(
                        text = when (method) {
                            PaymentMethod.BANK_TRANSFER -> "Bank Transfer"
                            PaymentMethod.MOBILE_WALLET -> "bKash / Mobile"
                            PaymentMethod.CASH -> "Cash"
                            PaymentMethod.CARD -> "Card"
                            PaymentMethod.CHEQUE -> "Cheque"
                            PaymentMethod.OTHER -> "Other"
                        },
                        selected = method == selectedMethod,
                        onClick = { selectedMethod = method },
                    )
                }
            }

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.paymentReference)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = reference,
                onValueChange = { reference = it },
                placeholder = words.paymentReferenceHint,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.paymentNotes)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = words.paymentNotes,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.xl))
            MiraButton(
                text = words.recordPaymentConfirm,
                onClick = {
                    onConfirm(
                        amountText,
                        selectedMethod,
                        reference.ifBlank { null },
                        note.ifBlank { null },
                    )
                },
                enabled = amountText.isNotBlank(),
                leadingIcon = MiraIcons.AccountBalanceWallet,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.sm))
        }
    }
}

@Composable
private fun NewQuoteSheet(
    clients: List<Client>,
    today: LocalDate,
    words: InvoicesWording,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onCreate: (clientId: Long, description: String, amount: String, validUntil: LocalDate?) -> Unit,
) {
    val spacing = MiraTheme.spacing
    var selectedClientId by remember(clients) { mutableStateOf(clients.firstOrNull()?.id) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var validUntil by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val effectiveValidUntil = validUntil ?: today.plusDays(DEFAULT_QUOTE_VALID_DAYS)

    MiraBottomSheet(onDismissRequest = onDismiss, title = words.newQuoteTitle) {
        if (clients.isEmpty()) {
            MiraEmptyState(
                icon = MiraIcons.Groups,
                message = words.formNoClients,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xl),
            )
        } else {
            FormSectionLabel(words.formClient)
            Spacer(Modifier.height(spacing.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(clients, key = { it.id }) { client ->
                    MiraChip(
                        text = client.name,
                        selected = client.id == selectedClientId,
                        onClick = { selectedClientId = client.id },
                    )
                }
            }

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.formAmount)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = amount,
                onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' } },
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))
            FormSectionLabel(words.formDescription)
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = words.formDescriptionHint,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.lg))
            MiraListRow(
                title = words.formValidUntil,
                trailingText = invoiceDate(effectiveValidUntil, language),
                onClick = { showDatePicker = true },
                leading = {
                    Icon(
                        imageVector = MiraIcons.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(Modifier.height(spacing.xl))
            MiraButton(
                text = words.formCreateQuote,
                onClick = { selectedClientId?.let { id -> onCreate(id, description, amount, validUntil) } },
                enabled = selectedClientId != null && amount.isNotBlank(),
                leadingIcon = MiraIcons.Description,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.sm))
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = effectiveValidUntil,
            setText = words.formSet,
            cancelText = words.formCancel,
            onPick = { picked ->
                validUntil = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

private const val DEFAULT_QUOTE_VALID_DAYS = 30L
