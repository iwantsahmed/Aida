package com.rayluxeo.aida.assistant.share

import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the share sheet puts in front of Abu, in one language.
 *
 * It exists for the same reason `ui/tasks/TasksWording` does: the sheet's own labels sit beside
 * a reply that came back from `ResponseRenderer` already in Bengali, and a label typed as an
 * English literal at the call site would leave "Ask" sitting over "নোট লিখে রেখেছি".
 *
 * The three request builders are the part that matters most. What a suggestion puts in the field
 * is what travels the pipeline and what the transcript records as Abu's own words, so it has to
 * be a sentence he would have written — and one the on-device engine genuinely parses. The
 * leading word of each is the trigger the local lexicon scores; moving it off the front of the
 * sentence loses the edge bonus and can cost the classification.
 */
internal interface ShareWording {

    val title: String

    val sharedHeading: String

    val askHeading: String

    val requestPlaceholder: String

    /** The primary control while there is still something to ask. */
    val ask: String

    /** The primary control once Mira has answered and nothing is pending. */
    val done: String

    val close: String

    val cancel: String

    val confirm: String

    val working: String

    /** Opens the app itself, which is the one thing still worth offering over a share Mira refuses. */
    val openMira: String

    // ---------------------------------------------------------------- what arrived

    val textKind: String

    val linkKind: String

    val imageKind: String

    val fileKind: String

    /** Said of a share of several items, where the names are listed above it. */
    fun itemCount(count: Int): String

    /** How many characters of prose arrived, so a long share does not have to be scrolled to be judged. */
    fun textLength(characters: Int): String

    // ---------------------------------------------------------------- what she can and cannot do

    /**
     * The honest line under a payload Mira can only partly handle. There is deliberately none for
     * plain text: she handles that whole, and inventing a caveat for it would be noise.
     */
    val linkCaveat: String

    val imageCaveat: String

    val fileCaveat: String

    fun unsupported(reason: UnsupportedShare, declaredType: String?): String

    // ---------------------------------------------------------------- the suggestions

    val noteSuggestion: String

    val taskSuggestion: String

    val rememberSuggestion: String

    fun noteRequest(seed: String): String

    fun taskRequest(seed: String): String

    fun rememberRequest(seed: String): String
}

internal fun shareWording(language: SpokenLanguage): ShareWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliShareWording
    SpokenLanguage.ENGLISH -> EnglishShareWording
}

/** The share sheet in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishShareWording : ShareWording {

    override val title: String = "Ask Aida"

    override val sharedHeading: String = "Shared"

    override val askHeading: String = "Ask"

    override val requestPlaceholder: String = "What should Aida do with this?"

    override val ask: String = "Ask"

    override val done: String = "Done"

    override val close: String = "Close"

    override val cancel: String = "Cancel"

    override val confirm: String = "Yes"

    override val working: String = "Working"

    override val openMira: String = "Open Aida"

    override val textKind: String = "Text"

    override val linkKind: String = "Link"

    override val imageKind: String = "Image"

    override val fileKind: String = "File"

    override fun itemCount(count: Int): String = if (count == 1) "1 item" else "$count items"

    override fun textLength(characters: Int): String =
        if (characters == 1) "1 character" else "$characters characters"

    override val linkCaveat: String =
        "Aida keeps the words of a link. She does not open the page, and the address itself is " +
            "not stored yet."

    override val imageCaveat: String =
        "Aida cannot look at a picture yet. She can write down a line about this one."

    override val fileCaveat: String =
        "Aida cannot read a file yet. She can write down a line about this one."

    override fun unsupported(reason: UnsupportedShare, declaredType: String?): String = when (reason) {
        UnsupportedShare.NOTHING_SHARED -> "Nothing came through with that share."
        UnsupportedShare.EMPTY_TEXT -> "What was shared was empty."
        UnsupportedShare.UNREADABLE_ITEM -> "Android did not let Aida read what was shared."
        UnsupportedShare.UNSUPPORTED_TYPE -> declaredType
            ?.let { "Aida has nothing she can do with $it yet." }
            ?: "Aida has nothing she can do with this kind of file yet."
    }

    override val noteSuggestion: String = "Note"

    override val taskSuggestion: String = "Task"

    override val rememberSuggestion: String = "Remember"

    override fun noteRequest(seed: String): String = "Note: $seed"

    override fun taskRequest(seed: String): String = "Task: $seed"

    override fun rememberRequest(seed: String): String = "Remember: $seed"
}

/** The share sheet in Bengali. */
internal object BengaliShareWording : ShareWording {

    override val title: String = "আইদাকে বলুন"

    override val sharedHeading: String = "যা পাঠানো হয়েছে"

    override val askHeading: String = "অনুরোধ"

    override val requestPlaceholder: String = "এটা নিয়ে আইদা কী করবে?"

    override val ask: String = "বলুন"

    override val done: String = "হয়েছে"

    override val close: String = "বন্ধ"

    override val cancel: String = "বাতিল"

    override val confirm: String = "হ্যাঁ"

    override val working: String = "করছি"

    override val openMira: String = "আইদা খুলুন"

    override val textKind: String = "লেখা"

    override val linkKind: String = "লিংক"

    override val imageKind: String = "ছবি"

    override val fileKind: String = "ফাইল"

    override fun itemCount(count: Int): String = "${bengaliNumerals(count.toString())} টি"

    override fun textLength(characters: Int): String = "${bengaliNumerals(characters.toString())} অক্ষর"

    override val linkCaveat: String =
        "লিংকের কথাগুলো আইদা লিখে রাখতে পারে। পাতাটা সে খোলে না, আর ঠিকানাটা এখনও রাখা যায় না।"

    override val imageCaveat: String =
        "ছবি আইদা এখনও দেখতে পারে না। এটা নিয়ে এক লাইন লিখে রাখতে পারে।"

    override val fileCaveat: String =
        "ফাইলের ভেতরটা আইদা এখনও পড়তে পারে না। এটা নিয়ে এক লাইন লিখে রাখতে পারে।"

    override fun unsupported(reason: UnsupportedShare, declaredType: String?): String = when (reason) {
        UnsupportedShare.NOTHING_SHARED -> "শেয়ারের সাথে কিছুই আসেনি।"
        UnsupportedShare.EMPTY_TEXT -> "যা পাঠানো হয়েছে তা খালি।"
        UnsupportedShare.UNREADABLE_ITEM -> "যা পাঠানো হয়েছে অ্যান্ড্রয়েড আইদাকে তা পড়তে দেয়নি।"
        UnsupportedShare.UNSUPPORTED_TYPE -> declaredType
            ?.let { "$it নিয়ে আইদার এখনও কিছু করার নেই।" }
            ?: "এই ধরনের ফাইল নিয়ে আইদার এখনও কিছু করার নেই।"
    }

    override val noteSuggestion: String = "নোট"

    override val taskSuggestion: String = "কাজ"

    override val rememberSuggestion: String = "মনে রাখো"

    override fun noteRequest(seed: String): String = "নোট করো: $seed"

    override fun taskRequest(seed: String): String = "কাজ: $seed"

    override fun rememberRequest(seed: String): String = "মনে রাখো: $seed"
}
