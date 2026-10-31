package com.rayluxeo.aida.ui.memory

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemorySource
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the Memory screen says to Abu, in the language he chose. Same phrasebook shape as
 * [com.rayluxeo.aida.ui.salah.SalahSettingsWording]: one interface, one object per language, one
 * selector. Human wording lives here and never in the domain (§4.6).
 *
 * Anything that carries data Abu typed — a subject, the search text — is a function rather than a
 * format string, because Bengali counts in its own digits and a `%d` baked into a sentence would
 * leave a Latin numeral in a Bengali row. A fact's recall count therefore comes in through
 * [recallSummary] and is folded with [bengaliNumerals] on the Bengali side.
 */
internal interface MemoryWording {

    // Frame
    val title: String

    // Search and filters
    val searchPlaceholder: String
    val allCategories: String
    fun categoryName(category: MemoryCategory): String

    // The list
    val rememberFact: String
    val addFact: String
    val emptyNoMemories: String

    /** The empty state when a search matched nothing; the query is placed, not translated. */
    fun emptyNoMatch(query: String): String

    // A memory card's actions (spoken aloud by the screen reader, so they are translated too).
    val pinMemory: String
    val editMemory: String
    val forgetMemory: String

    /** The card's footer line: where the fact came from and how often Mira has leaned on it. */
    fun recallSummary(source: MemorySource, recallCount: Int): String

    // The add / edit sheet
    val newFactTitle: String
    val editFactTitle: String
    val subjectLabel: String
    val subjectPlaceholder: String
    val factLabel: String
    val factPlaceholder: String
    val categoryLabel: String
    val storeMemory: String
    val saveChanges: String

    // The one dismissible notice above the content.
    fun noticeText(notice: MemoryNotice): String
}

internal object EnglishMemoryWording : MemoryWording {

    override val title = "Aida Memory"

    override val searchPlaceholder = "Search subjects and facts…"
    override val allCategories = "All Categories"
    override fun categoryName(category: MemoryCategory) = when (category) {
        MemoryCategory.PERSONAL_PREFERENCE -> "Personal preference"
        MemoryCategory.BUSINESS_RULE -> "Business rule"
        MemoryCategory.CLIENT_PREFERENCE -> "Client preference"
        MemoryCategory.PRICING -> "Pricing"
        MemoryCategory.PERSON -> "Person"
        MemoryCategory.ROUTINE -> "Routine"
        MemoryCategory.PROJECT_FACT -> "Project fact"
        MemoryCategory.GENERAL_FACT -> "General fact"
    }

    override val rememberFact = "Remember Fact"
    override val addFact = "Add Fact"
    override val emptyNoMemories = "No memories stored yet. Tell Aida what to remember."
    override fun emptyNoMatch(query: String) = "No memories found matching '$query'"

    override val pinMemory = "Pin memory"
    override val editMemory = "Edit memory"
    override val forgetMemory = "Forget memory"

    override fun recallSummary(source: MemorySource, recallCount: Int) =
        "Source: ${sourceName(source)} · Recalled ${recallCount}x"

    override val newFactTitle = "Remember New Fact"
    override val editFactTitle = "Edit Memory Fact"
    override val subjectLabel = "Subject (What is this about?)"
    override val subjectPlaceholder = "e.g., Ahmed, Website Deposit, Rayluxeo"
    override val factLabel = "Fact Wording"
    override val factPlaceholder = "e.g., Ahmed prefers WhatsApp for communication"
    override val categoryLabel = "Memory Category"
    override val storeMemory = "Store Memory"
    override val saveChanges = "Save Changes"

    override fun noticeText(notice: MemoryNotice) = when (notice) {
        is MemoryNotice.Remembered -> "Remembered: '${notice.subject}'"
        is MemoryNotice.Updated -> "Updated: '${notice.subject}'"
        MemoryNotice.Forgotten -> "Fact forgotten"
        MemoryNotice.RememberFailed -> "Aida could not remember that. Try once more."
        MemoryNotice.UpdateFailed -> "That change could not be saved. Try once more."
        MemoryNotice.ForgetFailed -> "That fact could not be forgotten. Try once more."
    }

    private fun sourceName(source: MemorySource) = when (source) {
        MemorySource.SPOKEN -> "Spoken"
        MemorySource.TYPED -> "Typed"
        MemorySource.INFERRED -> "Inferred"
        MemorySource.IMPORTED -> "Imported"
    }
}

internal object BengaliMemoryWording : MemoryWording {

    override val title = "আইদার স্মৃতি"

    override val searchPlaceholder = "বিষয় আর তথ্য খুঁজুন…"
    override val allCategories = "সব বিভাগ"
    override fun categoryName(category: MemoryCategory) = when (category) {
        MemoryCategory.PERSONAL_PREFERENCE -> "ব্যক্তিগত পছন্দ"
        MemoryCategory.BUSINESS_RULE -> "ব্যবসার নিয়ম"
        MemoryCategory.CLIENT_PREFERENCE -> "ক্লায়েন্টের পছন্দ"
        MemoryCategory.PRICING -> "মূল্য"
        MemoryCategory.PERSON -> "ব্যক্তি"
        MemoryCategory.ROUTINE -> "রুটিন"
        MemoryCategory.PROJECT_FACT -> "প্রকল্পের তথ্য"
        MemoryCategory.GENERAL_FACT -> "সাধারণ তথ্য"
    }

    override val rememberFact = "তথ্য মনে রাখুন"
    override val addFact = "তথ্য যোগ করুন"
    override val emptyNoMemories = "এখনও কিছু মনে রাখা হয়নি। আইদাকে বলুন কী মনে রাখতে হবে।"
    override fun emptyNoMatch(query: String) = "'$query' এর সাথে মেলে এমন কিছু পাওয়া যায়নি"

    override val pinMemory = "স্মৃতি পিন করুন"
    override val editMemory = "স্মৃতি সম্পাদনা করুন"
    override val forgetMemory = "স্মৃতি ভুলে যান"

    override fun recallSummary(source: MemorySource, recallCount: Int) =
        "উৎস: ${sourceName(source)} · মনে করা হয়েছে ${bengaliNumerals(recallCount.toString())} বার"

    override val newFactTitle = "নতুন তথ্য মনে রাখুন"
    override val editFactTitle = "স্মৃতির তথ্য সম্পাদনা করুন"
    override val subjectLabel = "বিষয় (এটি কী নিয়ে?)"
    override val subjectPlaceholder = "যেমন, আহমেদ, ওয়েবসাইট ডিপোজিট, রেলাক্সিও"
    override val factLabel = "তথ্যের বিবরণ"
    override val factPlaceholder = "যেমন, আহমেদ যোগাযোগের জন্য হোয়াটসঅ্যাপ পছন্দ করেন"
    override val categoryLabel = "স্মৃতির বিভাগ"
    override val storeMemory = "স্মৃতি সংরক্ষণ করুন"
    override val saveChanges = "পরিবর্তন সংরক্ষণ করুন"

    override fun noticeText(notice: MemoryNotice) = when (notice) {
        is MemoryNotice.Remembered -> "মনে রাখা হয়েছে: '${notice.subject}'"
        is MemoryNotice.Updated -> "হালনাগাদ হয়েছে: '${notice.subject}'"
        MemoryNotice.Forgotten -> "তথ্যটি ভুলে যাওয়া হয়েছে"
        MemoryNotice.RememberFailed -> "আইদা এটি মনে রাখতে পারেনি। আরেকবার চেষ্টা করুন।"
        MemoryNotice.UpdateFailed -> "পরিবর্তনটি সেভ করা গেল না। আরেকবার চেষ্টা করুন।"
        MemoryNotice.ForgetFailed -> "তথ্যটি ভোলা গেল না। আরেকবার চেষ্টা করুন।"
    }

    private fun sourceName(source: MemorySource) = when (source) {
        MemorySource.SPOKEN -> "মুখে বলা"
        MemorySource.TYPED -> "টাইপ করা"
        MemorySource.INFERRED -> "অনুমান করা"
        MemorySource.IMPORTED -> "ইমপোর্ট করা"
    }
}

internal fun memoryWording(language: SpokenLanguage): MemoryWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishMemoryWording
    SpokenLanguage.BENGALI -> BengaliMemoryWording
}
