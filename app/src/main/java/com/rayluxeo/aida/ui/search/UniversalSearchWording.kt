package com.rayluxeo.aida.ui.search

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.moneyFigure

/**
 * The nine kinds of record Universal Search can turn up, in the order the screen lists them.
 *
 * Mira has no single "search result" type — each kind is its own list on [com.rayluxeo.aida.domain
 * .search.UniversalSearchResults] — so this enum is only what the wording needs: one stable key per
 * section, so a heading cannot be named in English on one row and Bengali on the next.
 */
internal enum class SearchResultKind {
    TASKS,
    NOTES,
    CLIENTS,
    PROJECTS,
    INVOICES,
    EXPENSES,
    MEMORIES,
    FILES,
    CONVERSATIONS,
}

/**
 * Every word Universal Search puts in front of Abu, in one language: the screen's own chrome, the
 * section headings over each kind of hit, the two empty states, and the handful of labels a result
 * card falls back to when the record it stands for carries no title or detail of its own.
 *
 * English and Bengali are separate implementations, as the other screen phrasebooks are, so either
 * can be reworded without reading the other. The figures on a money result are the one thing that
 * must not differ between them — a total Abu reads in Bengali is the same total in English — so an
 * amount goes through the shared [moneyFigure], and only its numerals and the word for the currency
 * change around it. Anything counted takes the number rather than a pre-formatted string, so a count
 * in a Bengali sentence is written with Bengali digits.
 */
internal interface UniversalSearchWording {

    val screenTitle: String

    val searchPlaceholder: String

    /** Shown under the spinner while every store is being searched at once. */
    val searching: String

    /** The resting state, before anything has been typed. */
    val emptyPrompt: String

    /** The bare noun for a kind, used in its section heading. */
    fun kindLabel(kind: SearchResultKind): String

    /** A section heading: the kind, then how many of it matched. */
    fun sectionHeader(kind: SearchResultKind, count: Int): String

    /** The running total over the heading, said once above the list. */
    fun resultsFound(count: Int): String

    /** The no-results state, naming back the query that found nothing. */
    fun noMatches(query: String): String

    // --- What a result card falls back to when its record names nothing ------------------------

    val taskFallback: String

    val clientFallback: String

    val projectFallback: String

    val fileFallback: String

    /** A note with no title of its own is named by its number instead. */
    fun noteTitleFallback(id: Long): String

    // --- Results that are worded rather than shown verbatim ------------------------------------

    fun invoiceTitle(number: String): String

    fun invoiceSubtitle(total: Money): String

    fun expenseSubtitle(amount: Money, category: ExpenseCategory): String

    fun expenseCategoryLabel(category: ExpenseCategory): String

    /** Who said a matched conversation turn. Shown, never tapped — there is no per-turn screen. */
    fun conversationSpeaker(role: TurnRole): String
}

/** Universal Search in English. Plain nouns, nothing counted without saying what of. */
internal object EnglishUniversalSearchWording : UniversalSearchWording {

    override val screenTitle: String = "Universal Search"

    override val searchPlaceholder: String = "Search tasks, notes, clients, expenses…"

    override val searching: String = "Searching everything…"

    override val emptyPrompt: String =
        "Type a query above to search across tasks, notes, clients, expenses, memories, and files."

    override fun kindLabel(kind: SearchResultKind): String = when (kind) {
        SearchResultKind.TASKS -> "Tasks"
        SearchResultKind.NOTES -> "Notes"
        SearchResultKind.CLIENTS -> "Clients"
        SearchResultKind.PROJECTS -> "Projects"
        SearchResultKind.INVOICES -> "Invoices"
        SearchResultKind.EXPENSES -> "Expenses"
        SearchResultKind.MEMORIES -> "Memories"
        SearchResultKind.FILES -> "Files & Scans"
        SearchResultKind.CONVERSATIONS -> "Conversations"
    }

    override fun sectionHeader(kind: SearchResultKind, count: Int): String =
        "${kindLabel(kind)} ($count)"

    override fun resultsFound(count: Int): String =
        if (count == 1) "1 result found" else "$count results found"

    override fun noMatches(query: String): String =
        "No matching records found for \"$query\"."

    override val taskFallback: String = "Task"

    override val clientFallback: String = "Client"

    override val projectFallback: String = "Project"

    override val fileFallback: String = "Scanned file"

    override fun noteTitleFallback(id: Long): String = "Note #$id"

    override fun invoiceTitle(number: String): String = "Invoice #$number"

    override fun invoiceSubtitle(total: Money): String = "Total: ${money(total)}"

    override fun expenseSubtitle(amount: Money, category: ExpenseCategory): String =
        "Amount: ${money(amount)} · Category: ${expenseCategoryLabel(category)}"

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

    override fun conversationSpeaker(role: TurnRole): String = when (role) {
        TurnRole.ABU -> "Abu"
        TurnRole.MIRA -> "Aida"
    }

    private fun money(value: Money): String =
        "${value.currency.currencyCode} ${moneyFigure(value)}"
}

/**
 * Universal Search in Bengali. Bengali script throughout, so a Bengali list is not an English
 * heading over Bengali rows. Every number runs through [bengaliNumerals]; a foreign currency keeps
 * its code and an invoice number is left exactly as Abu wrote it, the way Mira never translates a
 * name she was given.
 */
internal object BengaliUniversalSearchWording : UniversalSearchWording {

    override val screenTitle: String = "সর্বজনীন অনুসন্ধান"

    override val searchPlaceholder: String = "কাজ, নোট, ক্লায়েন্ট, খরচ খুঁজুন…"

    override val searching: String = "সবকিছুতে খুঁজছি…"

    override val emptyPrompt: String =
        "ওপরে কিছু লিখে কাজ, নোট, ক্লায়েন্ট, খরচ, স্মৃতি আর ফাইলে খুঁজুন।"

    override fun kindLabel(kind: SearchResultKind): String = when (kind) {
        SearchResultKind.TASKS -> "কাজ"
        SearchResultKind.NOTES -> "নোট"
        SearchResultKind.CLIENTS -> "ক্লায়েন্ট"
        SearchResultKind.PROJECTS -> "প্রকল্প"
        SearchResultKind.INVOICES -> "ইনভয়েস"
        SearchResultKind.EXPENSES -> "খরচ"
        SearchResultKind.MEMORIES -> "স্মৃতি"
        SearchResultKind.FILES -> "ফাইল ও স্ক্যান"
        SearchResultKind.CONVERSATIONS -> "কথোপকথন"
    }

    override fun sectionHeader(kind: SearchResultKind, count: Int): String =
        "${kindLabel(kind)} (${digits(count)})"

    override fun resultsFound(count: Int): String = "${digits(count)}টি ফলাফল পাওয়া গেছে"

    override fun noMatches(query: String): String = "\"$query\" মেলে এমন কিছু পাওয়া যায়নি।"

    override val taskFallback: String = "কাজ"

    override val clientFallback: String = "ক্লায়েন্ট"

    override val projectFallback: String = "প্রকল্প"

    override val fileFallback: String = "স্ক্যান করা ফাইল"

    override fun noteTitleFallback(id: Long): String = "নোট #${digits(id)}"

    override fun invoiceTitle(number: String): String = "ইনভয়েস #$number"

    override fun invoiceSubtitle(total: Money): String = "মোট: ${money(total)}"

    override fun expenseSubtitle(amount: Money, category: ExpenseCategory): String =
        "পরিমাণ: ${money(amount)} · ধরন: ${expenseCategoryLabel(category)}"

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

    override fun conversationSpeaker(role: TurnRole): String = when (role) {
        TurnRole.ABU -> "আবু"
        TurnRole.MIRA -> "আইদা"
    }

    private fun money(value: Money): String =
        "${bengaliNumerals(moneyFigure(value))} ${currencyWord(value)}"

    private fun currencyWord(value: Money): String =
        if (value.currency == Money.BDT) "টাকা" else value.currency.currencyCode

    private fun digits(value: Int): String = bengaliNumerals(value.toString())

    private fun digits(value: Long): String = bengaliNumerals(value.toString())
}

internal fun universalSearchWording(language: SpokenLanguage): UniversalSearchWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliUniversalSearchWording
    SpokenLanguage.ENGLISH -> EnglishUniversalSearchWording
}
