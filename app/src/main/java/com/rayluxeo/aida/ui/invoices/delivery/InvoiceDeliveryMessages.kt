package com.rayluxeo.aida.ui.invoices.delivery

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.usecase.InvoiceMessageComposer
import com.rayluxeo.aida.domain.usecase.InvoiceMessageText
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The UI-layer implementation of [InvoiceMessageComposer]: the words of an invoice-delivery email or
 * WhatsApp message, in Abu's language.
 *
 * It lives here, not in the use case, because it is human copy a client will read, and §4.6 keeps
 * all such wording out of `domain`. The amount and due date are formatted here too, with Bengali
 * numerals when Mira is speaking Bangla.
 */
class InvoiceDeliveryMessages @Inject constructor() : InvoiceMessageComposer {

    override fun compose(
        language: SpokenLanguage,
        method: InvoiceDeliveryMethod,
        invoice: Invoice,
        client: Client,
        profile: UserProfile?,
    ): InvoiceMessageText {
        val businessName = profile?.businessName ?: DEFAULT_BUSINESS
        val amount = money(invoice, language)
        val due = date(invoice, language)
        val notes = invoice.notes?.takeIf { it.isNotBlank() }
        val bengali = language == SpokenLanguage.BENGALI

        val subject = if (bengali) {
            "$businessName থেকে ইনভয়েস ${digits(invoice.number, true)}"
        } else {
            "Invoice ${invoice.number} from $businessName"
        }

        val body = when (method) {
            InvoiceDeliveryMethod.WHATSAPP ->
                if (bengali) whatsAppBn(client.name, invoice.number, amount, due, businessName, notes)
                else whatsAppEn(client.name, invoice.number, amount, due, businessName, notes)
            InvoiceDeliveryMethod.EMAIL, InvoiceDeliveryMethod.SYSTEM_SHARE ->
                if (bengali) emailBn(client.name, invoice.number, amount, due, businessName, notes)
                else emailEn(client.name, invoice.number, amount, due, businessName, notes)
        }
        return InvoiceMessageText(subject = subject, message = body)
    }

    private fun emailEn(
        clientName: String,
        number: String,
        amount: String,
        due: String,
        businessName: String,
        notes: String?,
    ): String = buildString {
        append("Dear $clientName,\n\n")
        append("Please find attached invoice $number for $amount from $businessName.\n\n")
        append("Summary:\n")
        append("• Invoice: $number\n")
        append("• Total due: $amount\n")
        append("• Due date: $due\n")
        append("• Payment: bank transfer or mobile wallet (bKash, Nagad)\n\n")
        if (notes != null) append("Notes:\n$notes\n\n")
        append("Thank you for your business.\n\n")
        append("Best regards,\n$businessName")
    }

    private fun emailBn(
        clientName: String,
        number: String,
        amount: String,
        due: String,
        businessName: String,
        notes: String?,
    ): String = buildString {
        append("প্রিয় $clientName,\n\n")
        append("$businessName থেকে ${digits(number, true)} নম্বর ইনভয়েসটি সংযুক্ত করা হলো — মোট $amount।\n\n")
        append("সারসংক্ষেপ:\n")
        append("• ইনভয়েস: ${digits(number, true)}\n")
        append("• বকেয়া: $amount\n")
        append("• শেষ তারিখ: $due\n")
        append("• পেমেন্ট: ব্যাংক ট্রান্সফার বা মোবাইল ওয়ালেট (বিকাশ, নগদ)\n\n")
        if (notes != null) append("নোট:\n$notes\n\n")
        append("আপনার সঙ্গে ব্যবসা করে আনন্দিত।\n\n")
        append("শুভেচ্ছান্তে,\n$businessName")
    }

    private fun whatsAppEn(
        clientName: String,
        number: String,
        amount: String,
        due: String,
        businessName: String,
        notes: String?,
    ): String = buildString {
        append("Hello $clientName,\n\n")
        append("Here is invoice *$number* from $businessName.\n\n")
        append("• Amount: *$amount*\n")
        append("• Due date: $due\n")
        if (notes != null) append("• Note: $notes\n")
        append("\nThe PDF copy is attached. Please let us know once payment has been sent.")
    }

    private fun whatsAppBn(
        clientName: String,
        number: String,
        amount: String,
        due: String,
        businessName: String,
        notes: String?,
    ): String = buildString {
        append("হ্যালো $clientName,\n\n")
        append("$businessName থেকে ইনভয়েস *${digits(number, true)}*।\n\n")
        append("• পরিমাণ: *$amount*\n")
        append("• শেষ তারিখ: $due\n")
        if (notes != null) append("• নোট: $notes\n")
        append("\nPDF কপি সংযুক্ত করা হলো। পেমেন্ট পাঠানো হলে জানাবেন।")
    }

    private fun money(invoice: Invoice, language: SpokenLanguage): String =
        "${invoice.currency.currencyCode} ${digits(AMOUNT_FORMAT.format(invoice.total.amount), language == SpokenLanguage.BENGALI)}"

    private fun date(invoice: Invoice, language: SpokenLanguage): String =
        digits(invoice.dueOn.format(DATE_FORMAT), language == SpokenLanguage.BENGALI)

    private fun digits(text: String, bengali: Boolean): String = if (bengali) bengaliNumerals(text) else text

    private companion object {
        const val DEFAULT_BUSINESS = "Rayluxeo Studio"
        val AMOUNT_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    }
}
