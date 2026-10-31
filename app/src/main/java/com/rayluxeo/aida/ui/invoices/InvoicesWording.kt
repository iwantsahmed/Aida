package com.rayluxeo.aida.ui.invoices

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.math.BigDecimal

/**
 * Every word the Invoices & Quotes screen says to Abu, in the language he chose. Same phrasebook
 * shape as [com.rayluxeo.aida.ui.salah.SalahSettingsWording]: one interface, one object per
 * language, one selector. Human wording lives here and never in the domain (§4.6).
 *
 * The screen used to be English-only on a built, reachable surface — a defect under §1 and §12, for
 * a user who may be speaking Bengali the whole time. What a client reads is deliberately not here:
 * the generated PDF and the email/WhatsApp subject and body are client-facing business output, built
 * in the delivery use case and left as they are. This phrasebook is only the chrome and the notices
 * Mira shows Abu.
 *
 * Latin numerals in a Bengali line read as a different voice, so every counted or quantity string
 * goes through [bengaliNumerals]. An invoice or quote number is a code Abu assigned, not a word, so
 * it is placed unchanged in both languages.
 */
internal interface InvoicesWording {

    // Frame.
    val title: String
    val subtitle: String
    val searchPlaceholder: String
    fun invoicesTab(count: Int): String
    fun quotesTab(count: Int): String
    fun recurringTab(count: Int): String

    // Filters.
    fun filterLabel(filter: InvoiceFilter): String

    // Empty states.
    val noInvoices: String
    val noQuotes: String
    val noRecurring: String

    // Recurring tab.
    val automatedSchedules: String
    val sweepDueNow: String
    fun recurringTemplate(number: String): String
    fun recurringClient(name: String): String
    fun recurringNextDraft(dateText: String): String
    fun cycleLabel(cycle: SubscriptionCycle): String

    // Cards.
    val draftPlaceholder: String
    val unknownClient: String
    val unknown: String
    fun total(amountText: String): String
    fun dueBy(amountText: String, dateText: String): String
    val previewPdfAction: String
    val deliverInvoiceAction: String
    fun invoiceStatus(status: InvoiceStatus): String
    fun quoteStatus(status: QuoteStatus): String
    val convertToInvoice: String
    val converted: String

    // Detail dialog.
    val close: String
    val labelClient: String
    val labelIssued: String
    val labelDue: String
    val labelCurrency: String
    fun lineItems(count: Int): String
    fun lineQuantity(quantity: BigDecimal): String
    val labelSubtotal: String
    val labelDiscount: String
    val labelTax: String
    val labelTotal: String
    val labelAmountPaid: String
    val labelBalanceDue: String
    val notesPaymentInfo: String
    val previewPdf: String
    val deliver: String
    val recordPaymentAction: String
    val markPaidAction: String
    val markSentAction: String
    val deleteInvoiceAction: String

    // PDF preview dialog.
    fun pdfPreviewTitle(pageCount: Int): String
    val closePreview: String
    val invoicePdfPage: String

    // New invoice form.
    val newInvoiceTitle: String
    val newInvoiceAction: String
    val formClient: String
    val formSelectClient: String
    val formNoClients: String
    val formDescription: String
    val formDescriptionHint: String
    val formAmount: String
    val formDueDate: String
    val formCreate: String
    val formSet: String
    val formCancel: String

    // Quotes & Payment forms.
    val newQuoteTitle: String
    val newQuoteAction: String
    val formValidUntil: String
    val formCreateQuote: String
    val recordPaymentTitle: String
    val paymentAmount: String
    val paymentMethod: String
    val paymentReference: String
    val paymentReferenceHint: String
    val paymentNotes: String
    val recordPaymentConfirm: String

    // Notices.
    fun noticeText(notice: InvoicesNotice): String
}

internal fun invoicesWording(language: SpokenLanguage): InvoicesWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishInvoicesWording
    SpokenLanguage.BENGALI -> BengaliInvoicesWording
}

internal object EnglishInvoicesWording : InvoicesWording {

    override val title = "Invoices & Quotes"
    override val subtitle = "Rayluxeo billing, estimates and recurring schedules"
    override val searchPlaceholder = "Search by number or client…"
    override fun invoicesTab(count: Int) = "Invoices ($count)"
    override fun quotesTab(count: Int) = "Quotes ($count)"
    override fun recurringTab(count: Int) = "Recurring ($count)"

    override fun filterLabel(filter: InvoiceFilter) = when (filter) {
        InvoiceFilter.ALL -> "All"
        InvoiceFilter.UNPAID -> "Unpaid"
        InvoiceFilter.PAID -> "Paid"
        InvoiceFilter.DRAFT -> "Draft"
        InvoiceFilter.OVERDUE -> "Overdue"
    }

    override val noInvoices =
        "No invoices yet. Tap + to raise one, or — once the client is on file — tell Aida " +
            "\"Ahmed er jonno website development 45 hazar takar invoice banao\"."
    override val noQuotes = "No price estimates created yet."
    override val noRecurring =
        "No recurring schedules. Say \"Create the same invoice monthly\" to schedule automated " +
            "draft generations."

    override val automatedSchedules = "Automated draft schedules"
    override val sweepDueNow = "Sweep due now"
    override fun recurringTemplate(number: String) = "Template: $number"
    override fun recurringClient(name: String) = "Client: $name"
    override fun recurringNextDraft(dateText: String) = "Next draft generation: $dateText"
    override fun cycleLabel(cycle: SubscriptionCycle) = when (cycle) {
        SubscriptionCycle.WEEKLY -> "Weekly"
        SubscriptionCycle.MONTHLY -> "Monthly"
        SubscriptionCycle.QUARTERLY -> "Quarterly"
        SubscriptionCycle.YEARLY -> "Yearly"
        SubscriptionCycle.CUSTOM_DAYS -> "Custom"
    }

    override val draftPlaceholder = "DRAFT"
    override val unknownClient = "Unknown client"
    override val unknown = "Unknown"
    override fun total(amountText: String) = "Total: $amountText"
    override fun dueBy(amountText: String, dateText: String) = "Due: $amountText (by $dateText)"
    override val previewPdfAction = "Preview PDF"
    override val deliverInvoiceAction = "Deliver invoice"
    override fun invoiceStatus(status: InvoiceStatus) = when (status) {
        InvoiceStatus.DRAFT -> "DRAFT"
        InvoiceStatus.READY -> "READY"
        InvoiceStatus.SENT -> "SENT"
        InvoiceStatus.PARTIALLY_PAID -> "PARTIAL"
        InvoiceStatus.PAID -> "PAID"
        InvoiceStatus.OVERDUE -> "OVERDUE"
        InvoiceStatus.VOID -> "CANCELLED"
    }
    override fun quoteStatus(status: QuoteStatus) = when (status) {
        QuoteStatus.DRAFT -> "DRAFT"
        QuoteStatus.SENT -> "SENT"
        QuoteStatus.ACCEPTED -> "ACCEPTED"
        QuoteStatus.DECLINED -> "DECLINED"
        QuoteStatus.EXPIRED -> "EXPIRED"
    }
    override val convertToInvoice = "Convert to invoice"
    override val converted = "Converted"

    override val close = "Close"
    override val labelClient = "Client"
    override val labelIssued = "Issued"
    override val labelDue = "Due"
    override val labelCurrency = "Currency"
    override fun lineItems(count: Int) = "Line items ($count)"
    override fun lineQuantity(quantity: BigDecimal) = "${quantity.toPlainString()}x"
    override val labelSubtotal = "Subtotal"
    override val labelDiscount = "Discount"
    override val labelTax = "Tax"
    override val labelTotal = "Total"
    override val labelAmountPaid = "Amount paid"
    override val labelBalanceDue = "Balance due"
    override val notesPaymentInfo = "Notes / payment info:"
    override val previewPdf = "Preview PDF"
    override val deliver = "Deliver"
    override val recordPaymentAction = "Record payment"
    override val markPaidAction = "Mark as paid"
    override val markSentAction = "Mark as sent"
    override val deleteInvoiceAction = "Delete invoice"

    override fun pdfPreviewTitle(pageCount: Int) =
        if (pageCount == 1) "PDF preview (1 page)" else "PDF preview ($pageCount pages)"
    override val closePreview = "Close preview"
    override val invoicePdfPage = "Invoice PDF page"

    override val newInvoiceTitle = "New invoice"
    override val newInvoiceAction = "New invoice"
    override val formClient = "Client"
    override val formSelectClient = "Choose who it is for"
    override val formNoClients = "Add a client first, then you can raise an invoice for them."
    override val formDescription = "Description"
    override val formDescriptionHint = "What is it for?"
    override val formAmount = "Amount"
    override val formDueDate = "Due date"
    override val formCreate = "Create draft"
    override val formSet = "Set"
    override val formCancel = "Cancel"

    override val newQuoteTitle = "New quote"
    override val newQuoteAction = "New quote"
    override val formValidUntil = "Valid until"
    override val formCreateQuote = "Create quote"
    override val recordPaymentTitle = "Record payment"
    override val paymentAmount = "Amount"
    override val paymentMethod = "Payment method"
    override val paymentReference = "Reference (optional)"
    override val paymentReferenceHint = "e.g. TrxID, cheque #, bank ref"
    override val paymentNotes = "Notes (optional)"
    override val recordPaymentConfirm = "Confirm payment"

    override fun noticeText(notice: InvoicesNotice) = when (notice) {
        is InvoicesNotice.QuoteConverted ->
            "Quote ${notice.quoteNumber} converted into Invoice ${notice.invoiceNumber}."
        is InvoicesNotice.QuoteConversionFailed ->
            withDetail("The quote could not be converted.", notice.detail)
        is InvoicesNotice.InvoiceCreated ->
            "Draft invoice ${notice.invoiceNumber} created."
        is InvoicesNotice.InvoiceCreateFailed ->
            withDetail("The invoice could not be created — check the amount and the client.", notice.detail)
        is InvoicesNotice.ClientNotFound ->
            "Client details not found for invoice ${notice.invoiceNumber}."
        is InvoicesNotice.PdfPreviewFailed ->
            withDetail("The PDF preview could not be rendered.", notice.detail)
        is InvoicesNotice.PdfGenerationFailed ->
            withDetail("The PDF could not be generated.", notice.detail)
        is InvoicesNotice.DeliveryPdfFailed ->
            withDetail("The invoice PDF could not be generated.", notice.detail)
        is InvoicesNotice.InvoiceShared ->
            "Invoice ${notice.invoiceNumber} shared via ${methodWord(notice.method)}."
        is InvoicesNotice.DeliveryFailed ->
            withDetail("The invoice could not be shared.", notice.detail)
        is InvoicesNotice.RecurringGenerated ->
            if (notice.count == 1) "1 recurring invoice draft generated."
            else "${notice.count} recurring invoice drafts generated."
        InvoicesNotice.NoRecurringDue -> "No recurring invoices are due today."
        is InvoicesNotice.RecurringFailed ->
            withDetail("The recurring invoices could not be processed.", notice.detail)
        is InvoicesNotice.PaymentRecorded ->
            "Payment of ${notice.amount} recorded for invoice ${notice.invoiceNumber}."
        is InvoicesNotice.PaymentFailed ->
            withDetail("Could not record payment.", notice.detail)
        is InvoicesNotice.InvoiceSent ->
            "Invoice ${notice.invoiceNumber} marked as sent."
        is InvoicesNotice.InvoiceDeleted ->
            "Invoice ${notice.invoiceNumber} deleted."
        is InvoicesNotice.QuoteCreated ->
            "Quote ${notice.quoteNumber} created."
        is InvoicesNotice.QuoteCreateFailed ->
            withDetail("Could not create quote.", notice.detail)
    }

    private fun methodWord(method: InvoiceDeliveryMethod) = when (method) {
        InvoiceDeliveryMethod.EMAIL -> "email"
        InvoiceDeliveryMethod.WHATSAPP -> "WhatsApp"
        InvoiceDeliveryMethod.SYSTEM_SHARE -> "share"
    }

    private fun withDetail(base: String, detail: String?) =
        if (detail.isNullOrBlank()) base else "$base ($detail)"
}

internal object BengaliInvoicesWording : InvoicesWording {

    override val title = "ইনভয়েস ও কোট"
    override val subtitle = "Rayluxeo-র বিলিং, দরপত্র আর নিয়মিত সূচি"
    override val searchPlaceholder = "নম্বর বা ক্লায়েন্ট দিয়ে খুঁজুন…"
    override fun invoicesTab(count: Int) = "ইনভয়েস (${bengaliNumerals(count.toString())})"
    override fun quotesTab(count: Int) = "কোট (${bengaliNumerals(count.toString())})"
    override fun recurringTab(count: Int) = "নিয়মিত (${bengaliNumerals(count.toString())})"

    override fun filterLabel(filter: InvoiceFilter) = when (filter) {
        InvoiceFilter.ALL -> "সব"
        InvoiceFilter.UNPAID -> "বকেয়া"
        InvoiceFilter.PAID -> "পরিশোধিত"
        InvoiceFilter.DRAFT -> "খসড়া"
        InvoiceFilter.OVERDUE -> "সময় পেরোনো"
    }

    override val noInvoices =
        "এখনো কোনো ইনভয়েস নেই। + চাপুন, অথবা ক্লায়েন্ট যোগ করা থাকলে আইদাকে বলুন " +
            "\"Ahmed er jonno website development 45 hazar takar invoice banao\"।"
    override val noQuotes = "এখনো কোনো দরপত্র তৈরি হয়নি।"
    override val noRecurring =
        "কোনো নিয়মিত সূচি নেই। \"প্রতি মাসে একই ইনভয়েস বানাও\" বললে আইদা খসড়া তৈরির সূচি করে রাখবে।"

    override val automatedSchedules = "স্বয়ংক্রিয় খসড়ার সূচি"
    override val sweepDueNow = "এখন চালান"
    override fun recurringTemplate(number: String) = "টেমপ্লেট: $number"
    override fun recurringClient(name: String) = "ক্লায়েন্ট: $name"
    override fun recurringNextDraft(dateText: String) = "পরের খসড়া: $dateText"
    override fun cycleLabel(cycle: SubscriptionCycle) = when (cycle) {
        SubscriptionCycle.WEEKLY -> "সাপ্তাহিক"
        SubscriptionCycle.MONTHLY -> "মাসিক"
        SubscriptionCycle.QUARTERLY -> "ত্রৈমাসিক"
        SubscriptionCycle.YEARLY -> "বার্ষিক"
        SubscriptionCycle.CUSTOM_DAYS -> "নিজস্ব"
    }

    override val draftPlaceholder = "খসড়া"
    override val unknownClient = "অজানা ক্লায়েন্ট"
    override val unknown = "অজানা"
    override fun total(amountText: String) = "মোট: $amountText"
    override fun dueBy(amountText: String, dateText: String) = "বকেয়া: $amountText ($dateText-এর মধ্যে)"
    override val previewPdfAction = "PDF দেখুন"
    override val deliverInvoiceAction = "ইনভয়েস পাঠান"
    override fun invoiceStatus(status: InvoiceStatus) = when (status) {
        InvoiceStatus.DRAFT -> "খসড়া"
        InvoiceStatus.READY -> "প্রস্তুত"
        InvoiceStatus.SENT -> "পাঠানো"
        InvoiceStatus.PARTIALLY_PAID -> "আংশিক"
        InvoiceStatus.PAID -> "পরিশোধিত"
        InvoiceStatus.OVERDUE -> "সময় পেরোনো"
        InvoiceStatus.VOID -> "বাতিল"
    }
    override fun quoteStatus(status: QuoteStatus) = when (status) {
        QuoteStatus.DRAFT -> "খসড়া"
        QuoteStatus.SENT -> "পাঠানো"
        QuoteStatus.ACCEPTED -> "গৃহীত"
        QuoteStatus.DECLINED -> "প্রত্যাখ্যাত"
        QuoteStatus.EXPIRED -> "মেয়াদোত্তীর্ণ"
    }
    override val convertToInvoice = "ইনভয়েসে রূপান্তর"
    override val converted = "রূপান্তরিত"

    override val close = "বন্ধ করুন"
    override val labelClient = "ক্লায়েন্ট"
    override val labelIssued = "ইস্যুর তারিখ"
    override val labelDue = "শেষ তারিখ"
    override val labelCurrency = "মুদ্রা"
    override fun lineItems(count: Int) = "আইটেম (${bengaliNumerals(count.toString())})"
    override fun lineQuantity(quantity: BigDecimal) = "${bengaliNumerals(quantity.toPlainString())}x"
    override val labelSubtotal = "উপমোট"
    override val labelDiscount = "ছাড়"
    override val labelTax = "কর"
    override val labelTotal = "মোট"
    override val labelAmountPaid = "পরিশোধিত পরিমাণ"
    override val labelBalanceDue = "বকেয়া পরিমাণ"
    override val notesPaymentInfo = "নোট / পেমেন্ট তথ্য:"
    override val previewPdf = "PDF দেখুন"
    override val deliver = "পাঠান"
    override val recordPaymentAction = "পেমেন্ট রেকর্ড করুন"
    override val markPaidAction = "পরিশোধিত মার্ক করুন"
    override val markSentAction = "পাঠানো হয়েছে"
    override val deleteInvoiceAction = "ইনভয়েস মুছুন"

    override fun pdfPreviewTitle(pageCount: Int) =
        "PDF প্রিভিউ (${bengaliNumerals(pageCount.toString())} পৃষ্ঠা)"
    override val closePreview = "প্রিভিউ বন্ধ করুন"
    override val invoicePdfPage = "ইনভয়েস PDF পৃষ্ঠা"

    override val newInvoiceTitle = "নতুন ইনভয়েস"
    override val newInvoiceAction = "নতুন ইনভয়েস"
    override val formClient = "ক্লায়েন্ট"
    override val formSelectClient = "কার জন্য তা বেছে নিন"
    override val formNoClients = "আগে একজন ক্লায়েন্ট যোগ করুন, তারপর তার জন্য ইনভয়েস তৈরি করা যাবে।"
    override val formDescription = "বিবরণ"
    override val formDescriptionHint = "কিসের জন্য?"
    override val formAmount = "পরিমাণ"
    override val formDueDate = "শেষ তারিখ"
    override val formCreate = "খসড়া তৈরি করুন"
    override val formSet = "সেট করুন"
    override val formCancel = "বাতিল"

    override val newQuoteTitle = "নতুন কোট"
    override val newQuoteAction = "নতুন কোট তৈরি"
    override val formValidUntil = "মেয়াদ উত্তীর্ণের তারিখ"
    override val formCreateQuote = "কোট তৈরি করুন"
    override val recordPaymentTitle = "পেমেন্ট রেকর্ড করুন"
    override val paymentAmount = "পেমেন্টের পরিমাণ"
    override val paymentMethod = "পেমেন্ট পদ্ধতি"
    override val paymentReference = "রেফারেন্স (ঐচ্ছিক)"
    override val paymentReferenceHint = "যেমন: TrxID, চেক নম্বর, ব্যাংক রেফারেন্স"
    override val paymentNotes = "নোট (ঐচ্ছিক)"
    override val recordPaymentConfirm = "পেমেন্ট নিশ্চিত করুন"

    override fun noticeText(notice: InvoicesNotice) = when (notice) {
        is InvoicesNotice.QuoteConverted ->
            "কোট ${notice.quoteNumber} ইনভয়েস ${notice.invoiceNumber}-এ রূপান্তরিত হয়েছে।"
        is InvoicesNotice.QuoteConversionFailed ->
            withDetail("কোটটি রূপান্তর করা গেল না।", notice.detail)
        is InvoicesNotice.InvoiceCreated ->
            "খসড়া ইনভয়েস ${notice.invoiceNumber} তৈরি হয়েছে।"
        is InvoicesNotice.InvoiceCreateFailed ->
            withDetail("ইনভয়েস তৈরি করা গেল না — পরিমাণ ও ক্লায়েন্ট যাচাই করুন।", notice.detail)
        is InvoicesNotice.ClientNotFound ->
            "ইনভয়েস ${notice.invoiceNumber}-এর ক্লায়েন্টের তথ্য পাওয়া যায়নি।"
        is InvoicesNotice.PdfPreviewFailed ->
            withDetail("PDF প্রিভিউ তৈরি করা গেল না।", notice.detail)
        is InvoicesNotice.PdfGenerationFailed ->
            withDetail("PDF তৈরি করা গেল না।", notice.detail)
        is InvoicesNotice.DeliveryPdfFailed ->
            withDetail("ইনভয়েসের PDF তৈরি করা গেল না।", notice.detail)
        is InvoicesNotice.InvoiceShared ->
            "ইনভয়েস ${notice.invoiceNumber} ${methodWord(notice.method)} দিয়ে পাঠানো হয়েছে।"
        is InvoicesNotice.DeliveryFailed ->
            withDetail("ইনভয়েসটি পাঠানো গেল না।", notice.detail)
        is InvoicesNotice.RecurringGenerated ->
            "${bengaliNumerals(notice.count.toString())}টি নিয়মিত ইনভয়েসের খসড়া তৈরি হয়েছে।"
        InvoicesNotice.NoRecurringDue -> "আজ কোনো নিয়মিত ইনভয়েস বকেয়া নেই।"
        is InvoicesNotice.RecurringFailed ->
            withDetail("নিয়মিত ইনভয়েস প্রক্রিয়া করা গেল না।", notice.detail)
        is InvoicesNotice.PaymentRecorded ->
            "ইনভয়েস ${notice.invoiceNumber}-এর জন্য ${notice.amount} পেমেন্ট রেকর্ড করা হয়েছে।"
        is InvoicesNotice.PaymentFailed ->
            withDetail("পেমেন্ট রেকর্ড করা গেল না।", notice.detail)
        is InvoicesNotice.InvoiceSent ->
            "ইনভয়েস ${notice.invoiceNumber} পাঠানো হয়েছে হিসেবে মার্ক করা হয়েছে।"
        is InvoicesNotice.InvoiceDeleted ->
            "ইনভয়েস ${notice.invoiceNumber} মুছে ফেলা হয়েছে।"
        is InvoicesNotice.QuoteCreated ->
            "কোট ${notice.quoteNumber} তৈরি হয়েছে।"
        is InvoicesNotice.QuoteCreateFailed ->
            withDetail("কোট তৈরি করা গেল না।", notice.detail)
    }

    private fun methodWord(method: InvoiceDeliveryMethod) = when (method) {
        InvoiceDeliveryMethod.EMAIL -> "ইমেইল"
        InvoiceDeliveryMethod.WHATSAPP -> "হোয়াটসঅ্যাপ"
        InvoiceDeliveryMethod.SYSTEM_SHARE -> "শেয়ার"
    }

    private fun withDetail(base: String, detail: String?) =
        if (detail.isNullOrBlank()) base else "$base ($detail)"
}
