package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * The conversation-history sheet's own words, in both languages.
 *
 * It is a sheet the Mira screen reaches but whose copy was written in English only — the one
 * bilingual gap on an otherwise careful screen. Kept here, beside the sheet, rather than folded
 * into `MiraWording`, because none of it is spoken: it is chrome for a list, not Mira's voice.
 */
internal interface ConversationHistoryWording {
    val title: String
    val searchPlaceholder: String
    val newConversation: String
    val noMatches: String
    val noConversations: String
    val deleteAll: String
    val delete: String
    /** Content description for a row that opens a past thread to read. */
    val openConversation: String
    /** Content description for the back control inside a thread. */
    val back: String
    /** Shown when a thread holds no messages — a binned one reads as empty. */
    val emptyThread: String
    fun searchResults(count: Int): String
    fun threads(count: Int): String
    fun untitledConversation(id: Long): String
    fun messageCount(count: Int): String
    fun threadReference(id: Long): String
}

internal fun conversationHistoryWording(language: SpokenLanguage): ConversationHistoryWording =
    when (language) {
        SpokenLanguage.ENGLISH -> EnglishConversationHistoryWording
        SpokenLanguage.BENGALI -> BengaliConversationHistoryWording
    }

internal object EnglishConversationHistoryWording : ConversationHistoryWording {
    override val title = "Conversation history"
    override val searchPlaceholder = "Search history…"
    override val newConversation = "New"
    override val noMatches = "No matches found."
    override val noConversations = "No saved conversations."
    override val deleteAll = "Delete all conversations"
    override val delete = "Delete"
    override val openConversation = "Open conversation"
    override val back = "Back to history"
    override val emptyThread = "This conversation is empty."
    override fun searchResults(count: Int) = "Search results ($count)"
    override fun threads(count: Int) = "Threads ($count)"
    override fun untitledConversation(id: Long) = "Conversation #$id"
    override fun messageCount(count: Int) = if (count == 1) "1 message" else "$count messages"
    override fun threadReference(id: Long) = "Thread #$id"
}

internal object BengaliConversationHistoryWording : ConversationHistoryWording {
    override val title = "কথোপকথনের ইতিহাস"
    override val searchPlaceholder = "ইতিহাসে খুঁজুন…"
    override val newConversation = "নতুন"
    override val noMatches = "কিছু মেলেনি।"
    override val noConversations = "সংরক্ষিত কোনো কথোপকথন নেই।"
    override val deleteAll = "সব কথোপকথন মুছুন"
    override val delete = "মুছুন"
    override val openConversation = "কথোপকথন খুলুন"
    override val back = "ইতিহাসে ফিরে যান"
    override val emptyThread = "এই কথোপকথন খালি।"
    override fun searchResults(count: Int) = "অনুসন্ধানের ফলাফল (${bn(count)})"
    override fun threads(count: Int) = "থ্রেড (${bn(count)})"
    override fun untitledConversation(id: Long) = "কথোপকথন #${bn(id)}"
    override fun messageCount(count: Int) = "${bn(count)} টি বার্তা"
    override fun threadReference(id: Long) = "থ্রেড #${bn(id)}"
    private fun bn(value: Number) = bengaliNumerals(value.toString())
}
