package com.rayluxeo.aida.ui.invoices.delivery

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * The delivery dialog's words, in both languages.
 *
 * The dialog is the confirmation gate before an invoice is sent or shared, reached from the Mira
 * screen in whatever language the exchange is in — so a Bengali conversation must not end at an
 * English dialog. Kept beside the dialog rather than in a spoken phrasebook: none of it is said
 * aloud, it is the labels on a form.
 */
internal interface InvoiceDeliveryWording {
    val deliverInvoice: String
    val confirmBeforeSending: String
    val cancelDelivery: String
    val deliveryMethod: String
    val email: String
    val whatsApp: String
    val share: String
    val client: String
    val recipient: String
    val recipientNotSpecified: String
    val invoiceNumber: String
    val amount: String
    val attachment: String
    val subject: String
    val messageBody: String
    val cancel: String
    val sendEmail: String
    val sendWhatsApp: String

    /** Titles for the Android chooser the launcher raises — the one piece of chrome the system draws. */
    val chooserEmailTitle: String
    val chooserShareTitle: String
}

internal fun invoiceDeliveryWording(language: SpokenLanguage): InvoiceDeliveryWording =
    when (language) {
        SpokenLanguage.ENGLISH -> EnglishInvoiceDeliveryWording
        SpokenLanguage.BENGALI -> BengaliInvoiceDeliveryWording
    }

internal object EnglishInvoiceDeliveryWording : InvoiceDeliveryWording {
    override val deliverInvoice = "Deliver invoice"
    override val confirmBeforeSending = "Confirm before sending or sharing"
    override val cancelDelivery = "Cancel delivery"
    override val deliveryMethod = "Delivery method"
    override val email = "Email"
    override val whatsApp = "WhatsApp"
    override val share = "Share"
    override val client = "Client"
    override val recipient = "Recipient"
    override val recipientNotSpecified = "Not specified (can enter in app)"
    override val invoiceNumber = "Invoice number"
    override val amount = "Amount"
    override val attachment = "Attachment"
    override val subject = "Subject"
    override val messageBody = "Message body"
    override val cancel = "Cancel"
    override val sendEmail = "Send email"
    override val sendWhatsApp = "Send WhatsApp"
    override val chooserEmailTitle = "Send invoice via email"
    override val chooserShareTitle = "Share invoice"
}

internal object BengaliInvoiceDeliveryWording : InvoiceDeliveryWording {
    override val deliverInvoice = "ইনভয়েস পাঠান"
    override val confirmBeforeSending = "পাঠানোর বা শেয়ারের আগে নিশ্চিত করুন"
    override val cancelDelivery = "পাঠানো বাতিল করুন"
    override val deliveryMethod = "পাঠানোর মাধ্যম"
    override val email = "ইমেইল"
    override val whatsApp = "হোয়াটসঅ্যাপ"
    override val share = "শেয়ার"
    override val client = "ক্লায়েন্ট"
    override val recipient = "প্রাপক"
    override val recipientNotSpecified = "নির্দিষ্ট নয় (অ্যাপে লেখা যাবে)"
    override val invoiceNumber = "ইনভয়েস নম্বর"
    override val amount = "পরিমাণ"
    override val attachment = "সংযুক্তি"
    override val subject = "বিষয়"
    override val messageBody = "বার্তা"
    override val cancel = "বাতিল"
    override val sendEmail = "ইমেইল পাঠান"
    override val sendWhatsApp = "হোয়াটসঅ্যাপে পাঠান"
    override val chooserEmailTitle = "ইমেইলে ইনভয়েস পাঠান"
    override val chooserShareTitle = "ইনভয়েস শেয়ার করুন"
}
