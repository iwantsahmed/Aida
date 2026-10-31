package com.rayluxeo.aida.ui.clients

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the Clients screen puts in front of Abu, in one language: the title, the search
 * field, the list's empty state, the detail sheet, the add sheet, the delete dialog and the undo
 * notice.
 *
 * One phrasebook with one voice, so the word the sheet uses for "Delete" and the word the dialog
 * uses for it cannot drift apart. Anything that carries data Abu typed — a client's name, a count
 * of projects — is a function rather than a format string, because Bengali writes its own digits
 * and its own punctuation and a `%d` baked into a sentence would leave Latin numerals in a Bengali
 * row.
 */
internal interface ClientsWording {

    val title: String

    val searchPlaceholder: String

    val addClient: String

    /** The list's empty state: a normal state, not a fault, and a reason to add the first row. */
    val noClients: String

    // --- The detail sheet ----------------------------------------------------------------------

    /** The company line, worded whole so the colon is a Bengali decision, not the composable's. */
    fun companyDetail(company: String): String

    fun emailDetail(email: String): String

    fun phoneDetail(phone: String): String

    /** The two summary chips. Bengali counts in its own digits, so the number comes in here. */
    fun projectsCount(count: Int): String

    fun invoicesCount(count: Int): String

    /** The sheet's button, distinct from the dialog's bare [delete] confirm. */
    val deleteClientAction: String

    // --- The delete dialog ---------------------------------------------------------------------

    val deleteTitle: String

    /** The name is data Abu typed, so it is placed rather than translated. */
    fun deleteMessage(name: String): String

    val delete: String

    val cancel: String

    // --- The undo notice -----------------------------------------------------------------------

    val undo: String

    fun clientAdded(name: String): String

    val clientMovedToBin: String

    // --- The add sheet -------------------------------------------------------------------------

    val newClient: String

    val nameLabel: String

    val namePlaceholder: String

    val companyNameLabel: String

    val emailLabel: String

    val phoneLabel: String

    val saveClient: String
}

internal fun clientsWording(language: SpokenLanguage): ClientsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliClientsWording
    SpokenLanguage.ENGLISH -> EnglishClientsWording
}

/** The Clients screen in English: plain, short, never chatty. */
internal object EnglishClientsWording : ClientsWording {

    override val title: String = "Clients"

    override val searchPlaceholder: String = "Search clients"

    override val addClient: String = "Add Client"

    override val noClients: String =
        "No clients yet. Add one to keep their projects and invoices together."

    override fun companyDetail(company: String): String = "Company: $company"

    override fun emailDetail(email: String): String = "Email: $email"

    override fun phoneDetail(phone: String): String = "Phone: $phone"

    override fun projectsCount(count: Int): String = "$count Projects"

    override fun invoicesCount(count: Int): String = "$count Invoices"

    override val deleteClientAction: String = "Delete Client"

    override val deleteTitle: String = "Delete this client?"

    override fun deleteMessage(name: String): String =
        "\"$name\" moves to the bin. You can undo straight after."

    override val delete: String = "Delete"

    override val cancel: String = "Cancel"

    override val undo: String = "Undo"

    override fun clientAdded(name: String): String = "Client '$name' added"

    override val clientMovedToBin: String = "Client moved to bin"

    override val newClient: String = "New Client"

    override val nameLabel: String = "Client Name"

    override val namePlaceholder: String = "e.g., Ahmed Traders"

    override val companyNameLabel: String = "Company Name (Optional)"

    override val emailLabel: String = "Email (Optional)"

    override val phoneLabel: String = "Phone (Optional)"

    override val saveClient: String = "Save Client"
}

/**
 * The Clients screen in Bengali.
 *
 * Every count goes through [bengaliNumerals], so a chip reads "৩ প্রকল্প" rather than "3 প্রকল্প"
 * in the middle of a Bengali sheet. "ইনভয়েস" is written in Bengali script rather than translated,
 * because that is the word Abu already uses for it.
 */
internal object BengaliClientsWording : ClientsWording {

    override val title: String = "ক্লায়েন্ট"

    override val searchPlaceholder: String = "ক্লায়েন্ট খুঁজুন"

    override val addClient: String = "ক্লায়েন্ট যোগ করুন"

    override val noClients: String =
        "এখনও কোনো ক্লায়েন্ট নেই। প্রকল্প আর ইনভয়েস একসঙ্গে রাখতে একজনকে যোগ করুন।"

    override fun companyDetail(company: String): String = "কোম্পানি: $company"

    override fun emailDetail(email: String): String = "ইমেইল: $email"

    override fun phoneDetail(phone: String): String = "ফোন: $phone"

    override fun projectsCount(count: Int): String = "${bengaliNumerals(count.toString())} প্রকল্প"

    override fun invoicesCount(count: Int): String = "${bengaliNumerals(count.toString())} ইনভয়েস"

    override val deleteClientAction: String = "ক্লায়েন্ট মুছুন"

    override val deleteTitle: String = "এই ক্লায়েন্ট মুছবেন?"

    override fun deleteMessage(name: String): String =
        "\"$name\" বিনে চলে যাবে। ঠিক পরেই আপনি ফিরিয়ে আনতে পারবেন।"

    override val delete: String = "মুছুন"

    override val cancel: String = "বাতিল"

    override val undo: String = "ফিরিয়ে আনুন"

    override fun clientAdded(name: String): String = "ক্লায়েন্ট '$name' যোগ হয়েছে"

    override val clientMovedToBin: String = "ক্লায়েন্ট বিনে সরানো হয়েছে"

    override val newClient: String = "নতুন ক্লায়েন্ট"

    override val nameLabel: String = "ক্লায়েন্টের নাম"

    override val namePlaceholder: String = "যেমন, আহমেদ ট্রেডার্স"

    override val companyNameLabel: String = "কোম্পানির নাম (ঐচ্ছিক)"

    override val emailLabel: String = "ইমেইল (ঐচ্ছিক)"

    override val phoneLabel: String = "ফোন (ঐচ্ছিক)"

    override val saveClient: String = "ক্লায়েন্ট সংরক্ষণ করুন"
}
