package com.rayluxeo.aida.ui.scanner

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.scanner.ScanType
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the Scanner screen says to Abu, in the language he chose. Same phrasebook shape as its
 * sibling [FilesWording]: one interface, one object per language, one selector. Human wording lives
 * here and never in the view model or the domain (§4.6) — the view model asks for the line that
 * matches Abu's setting and never types a sentence in one language.
 *
 * The scan-type and expense-category chips used to print their raw enum constants ("BUSINESS CARD",
 * "PROFESSIONAL_FEES"), which read as a different voice inside a Bengali list and as shouting inside
 * an English one. They are worded here now, per language. Numbers in a Bengali line go through
 * [bengaliNumerals]; the names Abu scanned (a merchant, a note title) are his own data and pass
 * through untranslated.
 */
internal interface ScannerWording {

    // Frame.
    val title: String

    /** What TalkBack announces for the back arrow. It is never drawn, only spoken. */
    val back: String

    // Scan-type selector.
    val scanTypeSection: String
    fun scanTypeLabel(type: ScanType): String

    // Capture actions.
    val takePhoto: String
    val importImage: String

    // Image preview card.
    val imagePreview: String

    /** What TalkBack announces for the captured image. */
    val scannedImageDescription: String
    val cleanImage: String

    // OCR progress and the extracted-information heading.
    val processingOcr: String
    val extractedInformation: String

    // Receipt / invoice form.
    val merchantLabel: String
    val amountLabel: String
    val expenseCategorySection: String
    fun expenseCategoryLabel(category: ExpenseCategory): String
    val saveExpense: String

    // Business-card form.
    val cardNameLabel: String
    val cardCompanyLabel: String
    val cardPhoneLabel: String
    val cardEmailLabel: String
    val cardWebsiteLabel: String
    val cardAddressLabel: String
    val createClient: String
    val saveNote: String

    // Document / screenshot form.
    val documentTitleLabel: String
    val extractedTextLabel: String
    val saveDocument: String
    val saveAsNote: String
    val askMiraAboutDocument: String

    /** The words handed to Mira when Abu asks her to summarise a scanned document. */
    fun askMiraDocumentPrompt(extractedText: String): String

    // Handwriting form.
    val handwritingLowConfidence: String
    val noteTitleLabel: String
    val recognizedTextLabel: String
    val askMira: String

    /** The words handed to Mira when Abu asks her about a handwritten note. */
    fun askMiraHandwritingPrompt(extractedText: String): String

    // --- What Mira says back, and the names she gives records she creates --------------------

    /** The title the document form falls back to before Abu renames it. */
    val defaultDocumentTitle: String

    /** The title a handwritten note is seeded with. */
    val defaultHandwrittenTitle: String

    /** The title "Save as note" falls back to when the field is blank. */
    val defaultSavedNoteTitle: String

    /** The merchant used when the receipt names none. */
    val defaultMerchant: String

    /** The file name a saved receipt scan is filed under, with the merchant carried through. */
    fun receiptFileName(merchant: String): String

    /** The file name a saved business-card scan is filed under; the timestamp is an identifier. */
    fun businessCardFileName(timestamp: Long): String

    fun ocrFailed(detail: String?): String
    val captureReceiptFirst: String
    val enterValidAmount: String
    fun expenseSaved(amount: String, merchant: String): String
    fun failedToSaveExpense(detail: String?): String
    fun clientCreated(name: String): String
    fun failedToCreateClient(detail: String?): String
    fun noteSaved(title: String): String
    fun failedToSaveNote(detail: String?): String
    val captureDocumentFirst: String
    fun documentSaved(title: String): String
    fun failedToSaveDocument(detail: String?): String
}

internal fun scannerWording(language: SpokenLanguage): ScannerWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliScannerWording
    SpokenLanguage.ENGLISH -> EnglishScannerWording
}

/** The Scanner screen in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishScannerWording : ScannerWording {

    override val title: String = "Scanner"
    override val back: String = "Back"

    override val scanTypeSection: String = "Scan type"
    override fun scanTypeLabel(type: ScanType): String = when (type) {
        ScanType.RECEIPT -> "Receipt"
        ScanType.INVOICE -> "Invoice"
        ScanType.BUSINESS_CARD -> "Business card"
        ScanType.DOCUMENT -> "Document"
        ScanType.HANDWRITING -> "Handwriting"
        ScanType.SCREENSHOT -> "Screenshot"
    }

    override val takePhoto: String = "Take photo"
    override val importImage: String = "Import image"

    override val imagePreview: String = "Image preview"
    override val scannedImageDescription: String = "Scanned image"
    override val cleanImage: String = "Clean image (high contrast)"

    override val processingOcr: String = "Recognising text…"
    override val extractedInformation: String = "Extracted information"

    override val merchantLabel: String = "Merchant / vendor"
    override val amountLabel: String = "Amount (BDT / ৳)"
    override val expenseCategorySection: String = "Expense category"
    override fun expenseCategoryLabel(category: ExpenseCategory): String = when (category) {
        ExpenseCategory.SOFTWARE -> "Software"
        ExpenseCategory.HARDWARE -> "Hardware"
        ExpenseCategory.TRAVEL -> "Travel"
        ExpenseCategory.FOOD -> "Food"
        ExpenseCategory.RENT -> "Rent"
        ExpenseCategory.UTILITIES -> "Utilities"
        ExpenseCategory.MARKETING -> "Marketing"
        ExpenseCategory.PROFESSIONAL_FEES -> "Professional fees"
        ExpenseCategory.TAX -> "Tax"
        ExpenseCategory.OTHER -> "Other"
    }
    override val saveExpense: String = "Save expense"

    override val cardNameLabel: String = "Name"
    override val cardCompanyLabel: String = "Company"
    override val cardPhoneLabel: String = "Phone"
    override val cardEmailLabel: String = "Email"
    override val cardWebsiteLabel: String = "Website"
    override val cardAddressLabel: String = "Address"
    override val createClient: String = "Create client"
    override val saveNote: String = "Save note"

    override val documentTitleLabel: String = "Document title"
    override val extractedTextLabel: String = "Extracted text"
    override val saveDocument: String = "Save document"
    override val saveAsNote: String = "Save as note"
    override val askMiraAboutDocument: String = "Ask Aida about this document"
    override fun askMiraDocumentPrompt(extractedText: String): String =
        "Summarise this document:\n\n$extractedText"

    override val handwritingLowConfidence: String =
        "Handwriting recognition has low confidence. The original image is kept."
    override val noteTitleLabel: String = "Note title"
    override val recognizedTextLabel: String = "Recognised text"
    override val askMira: String = "Ask Aida"
    override fun askMiraHandwritingPrompt(extractedText: String): String =
        "Here is handwritten text extracted from my note:\n\n$extractedText"

    override val defaultDocumentTitle: String = "Scanned document"
    override val defaultHandwrittenTitle: String = "Handwritten note"
    override val defaultSavedNoteTitle: String = "Scanned note"
    override val defaultMerchant: String = "Receipt expense"

    override fun receiptFileName(merchant: String): String = "Receipt — $merchant"
    override fun businessCardFileName(timestamp: Long): String = "Business card $timestamp"

    override fun ocrFailed(detail: String?): String =
        "Text could not be read: ${detail ?: "nothing was recognised"}"
    override val captureReceiptFirst: String = "Capture or import a receipt image first."
    override val enterValidAmount: String = "Enter a valid expense amount."
    override fun expenseSaved(amount: String, merchant: String): String =
        "Expense saved: BDT $amount at $merchant"
    override fun failedToSaveExpense(detail: String?): String =
        "The expense could not be saved: ${detail ?: "try once more"}"
    override fun clientCreated(name: String): String = "Client \"$name\" created."
    override fun failedToCreateClient(detail: String?): String =
        "The client could not be created: ${detail ?: "try once more"}"
    override fun noteSaved(title: String): String = "Note \"$title\" saved."
    override fun failedToSaveNote(detail: String?): String =
        "The note could not be saved: ${detail ?: "try once more"}"
    override val captureDocumentFirst: String = "Capture or import a document first."
    override fun documentSaved(title: String): String = "Document \"$title\" saved."
    override fun failedToSaveDocument(detail: String?): String =
        "The document could not be saved: ${detail ?: "try once more"}"
}

/**
 * The Scanner screen in Bengali. Bengali script throughout — a screen that drops an English chip
 * into a Bengali list is harder to read than either language on its own.
 *
 * Numbers go through [bengaliNumerals], as the Files and tasks phrasebooks do: Latin digits inside a
 * Bengali sentence are the one place a bilingual screen always slips. The amount Abu saved and the
 * names he scanned are his own data and pass through as typed; a record's timestamp identifier stays
 * in Latin, because it is a handle and not a number to read.
 */
internal object BengaliScannerWording : ScannerWording {

    override val title: String = "স্ক্যানার"
    override val back: String = "ফিরে যান"

    override val scanTypeSection: String = "স্ক্যানের ধরন"
    override fun scanTypeLabel(type: ScanType): String = when (type) {
        ScanType.RECEIPT -> "রসিদ"
        ScanType.INVOICE -> "চালান"
        ScanType.BUSINESS_CARD -> "ভিজিটিং কার্ড"
        ScanType.DOCUMENT -> "নথি"
        ScanType.HANDWRITING -> "হাতের লেখা"
        ScanType.SCREENSHOT -> "স্ক্রিনশট"
    }

    override val takePhoto: String = "ছবি তুলুন"
    override val importImage: String = "ছবি আনুন"

    override val imagePreview: String = "ছবির পূর্বরূপ"
    override val scannedImageDescription: String = "স্ক্যান করা ছবি"
    override val cleanImage: String = "পরিষ্কার ছবি (উঁচু কনট্রাস্ট)"

    override val processingOcr: String = "লেখা শনাক্ত করা হচ্ছে…"
    override val extractedInformation: String = "উদ্ধার করা তথ্য"

    override val merchantLabel: String = "বিক্রেতা"
    override val amountLabel: String = "পরিমাণ (৳)"
    override val expenseCategorySection: String = "খরচের ধরন"
    override fun expenseCategoryLabel(category: ExpenseCategory): String = when (category) {
        ExpenseCategory.SOFTWARE -> "সফটওয়্যার"
        ExpenseCategory.HARDWARE -> "হার্ডওয়্যার"
        ExpenseCategory.TRAVEL -> "ভ্রমণ"
        ExpenseCategory.FOOD -> "খাবার"
        ExpenseCategory.RENT -> "ভাড়া"
        ExpenseCategory.UTILITIES -> "ইউটিলিটি"
        ExpenseCategory.MARKETING -> "মার্কেটিং"
        ExpenseCategory.PROFESSIONAL_FEES -> "পেশাদার ফি"
        ExpenseCategory.TAX -> "ট্যাক্স"
        ExpenseCategory.OTHER -> "অন্যান্য"
    }
    override val saveExpense: String = "খরচ সংরক্ষণ করুন"

    override val cardNameLabel: String = "নাম"
    override val cardCompanyLabel: String = "প্রতিষ্ঠান"
    override val cardPhoneLabel: String = "ফোন"
    override val cardEmailLabel: String = "ইমেইল"
    override val cardWebsiteLabel: String = "ওয়েবসাইট"
    override val cardAddressLabel: String = "ঠিকানা"
    override val createClient: String = "ক্লায়েন্ট তৈরি করুন"
    override val saveNote: String = "নোট সংরক্ষণ করুন"

    override val documentTitleLabel: String = "নথির শিরোনাম"
    override val extractedTextLabel: String = "উদ্ধার করা লেখা"
    override val saveDocument: String = "নথি সংরক্ষণ করুন"
    override val saveAsNote: String = "নোট হিসেবে সংরক্ষণ করুন"
    override val askMiraAboutDocument: String = "এই নথি নিয়ে আইদাকে জিজ্ঞাসা করুন"
    override fun askMiraDocumentPrompt(extractedText: String): String =
        "এই নথিটি সংক্ষেপে বলুন:\n\n$extractedText"

    override val handwritingLowConfidence: String =
        "হাতের লেখা শনাক্তকরণে আস্থা কম। মূল ছবিটি রেখে দেওয়া হয়েছে।"
    override val noteTitleLabel: String = "নোটের শিরোনাম"
    override val recognizedTextLabel: String = "শনাক্ত করা লেখা"
    override val askMira: String = "আইদাকে জিজ্ঞাসা করুন"
    override fun askMiraHandwritingPrompt(extractedText: String): String =
        "আমার নোট থেকে উদ্ধার করা হাতে-লেখা লেখা এখানে:\n\n$extractedText"

    override val defaultDocumentTitle: String = "স্ক্যান করা নথি"
    override val defaultHandwrittenTitle: String = "হাতে-লেখা নোট"
    override val defaultSavedNoteTitle: String = "স্ক্যান করা নোট"
    override val defaultMerchant: String = "রসিদের খরচ"

    override fun receiptFileName(merchant: String): String = "রসিদ — $merchant"
    override fun businessCardFileName(timestamp: Long): String = "ভিজিটিং কার্ড $timestamp"

    override fun ocrFailed(detail: String?): String =
        "লেখা পড়া গেল না: ${detail ?: "কিছুই শনাক্ত হয়নি"}"
    override val captureReceiptFirst: String = "আগে একটি রসিদের ছবি তুলুন বা আনুন।"
    override val enterValidAmount: String = "খরচের একটি সঠিক পরিমাণ লিখুন।"
    override fun expenseSaved(amount: String, merchant: String): String =
        "খরচ সংরক্ষিত: ৳${bengaliNumerals(amount)}, $merchant"
    override fun failedToSaveExpense(detail: String?): String =
        "খরচটি সংরক্ষণ করা গেল না: ${detail ?: "আরেকবার চেষ্টা করুন"}"
    override fun clientCreated(name: String): String = "\"$name\" ক্লায়েন্ট তৈরি হয়েছে।"
    override fun failedToCreateClient(detail: String?): String =
        "ক্লায়েন্টটি তৈরি করা গেল না: ${detail ?: "আরেকবার চেষ্টা করুন"}"
    override fun noteSaved(title: String): String = "\"$title\" নোট সংরক্ষিত হয়েছে।"
    override fun failedToSaveNote(detail: String?): String =
        "নোটটি সংরক্ষণ করা গেল না: ${detail ?: "আরেকবার চেষ্টা করুন"}"
    override val captureDocumentFirst: String = "আগে একটি নথি তুলুন বা আনুন।"
    override fun documentSaved(title: String): String = "\"$title\" নথি সংরক্ষিত হয়েছে।"
    override fun failedToSaveDocument(detail: String?): String =
        "নথিটি সংরক্ষণ করা গেল না: ${detail ?: "আরেকবার চেষ্টা করুন"}"
}
