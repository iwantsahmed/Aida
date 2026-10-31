package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.ink.InkTool

/**
 * Every word the handwriting canvas says, in the language Abu chose.
 *
 * Same shape as the phrasebooks under `ui/settings` and `ui/tasks`: one interface, one object per
 * language, one selector, so either language can be reworded without reading the other. It exists
 * for a canvas with almost no text on it because the little there is carries all of the meaning —
 * a bare glyph with no name is unreadable to TalkBack, and a line explaining why nothing was
 * saved is the moment Abu most needs his own language.
 */
internal interface InkWording {

    /** The page itself, named for TalkBack — a view that draws has nothing else to say. */
    val canvasDescription: String

    /** The quiet line on an untouched page, so an empty canvas reads as ready, not broken. */
    val emptyHint: String

    /** The name of the screen, so the bar at the top says where Abu is. */
    val title: String

    val close: String
    val save: String

    /** What the same control is called once the page is empty and saving it means removing it. */
    val removePage: String
    val saving: String

    /** Named per tool rather than as two fields: a third tool is then a compile error, not a gap. */
    fun toolName(tool: InkTool): String

    val undo: String
    val redo: String
    val clear: String

    /** Says what the control will do next, which is what a toggle's name has to say. */
    fun fingerDrawing(enabled: Boolean): String

    /**
     * Whether it is on right now, which the name deliberately does not say.
     *
     * TalkBack reads a name and a state separately, and a bare tint is a state only the eye can
     * read. Without this the toggle is announced identically in both of its positions.
     */
    fun fingerDrawingState(enabled: Boolean): String

    val handwriting: String
    val handwritingStop: String
    val handwritingHint: String

    val recognisedTitle: String
    val keepText: String
    val discardText: String

    val leaveTitle: String
    val leaveMessage: String
    val leaveConfirm: String
    val leaveCancel: String

    val dismiss: String

    /** Something that happened, as a sentence. Never a code and never an exception. */
    fun notice(notice: InkNotice): String

    /**
     * What the notice's own action is called, or null when it has none.
     *
     * Exactly the notices that [InkNotice.isUndoable] marks get a word here; anything else would
     * be a button on a line that has nothing to undo.
     */
    fun noticeAction(notice: InkNotice): String?
}

internal object EnglishInkWording : InkWording {

    override val canvasDescription = "Handwriting page"
    override val emptyHint = "Write with the S Pen. Turn it over to rub something out."

    override val title = "Handwriting"

    override val close = "Close"
    override val save = "Save page"
    override val removePage = "Remove page"
    override val saving = "Saving"

    override fun toolName(tool: InkTool) = when (tool) {
        InkTool.PEN -> "Pen"
        InkTool.ERASER -> "Eraser"
    }

    override val undo = "Undo"
    override val redo = "Redo"
    override val clear = "Clear the page"

    override fun fingerDrawing(enabled: Boolean) =
        if (enabled) "Stop drawing with a finger" else "Draw with a finger"

    override fun fingerDrawingState(enabled: Boolean) = if (enabled) "On" else "Off"

    override val handwriting = "Read my handwriting"
    override val handwritingStop = "Go back to drawing"
    override val handwritingHint =
        "Write with the pen and this phone's keyboard will read it. Nothing leaves the phone."

    override val recognisedTitle = "Aida read this"
    override val keepText = "Add to the note"
    override val discardText = "Leave it out"

    override val leaveTitle = "Leave this page?"
    override val leaveMessage = "What you have written has not been saved, and closing loses it."
    override val leaveConfirm = "Leave it"
    override val leaveCancel = "Keep writing"

    override val dismiss = "Dismiss"

    override fun notice(notice: InkNotice) = when (notice) {
        InkNotice.SAVED -> "Page saved."
        InkNotice.PAGE_CLEARED -> "Page cleared."
        InkNotice.TEXT_KEPT -> "Added to the note."
        InkNotice.PAGE_REMOVED ->
            "There was nothing left on the page, so Aida took it off the note."
        InkNotice.NOTHING_READABLE ->
            "There was nothing readable there. Your writing is exactly as you left it."
        InkNotice.HANDWRITING_REFUSED ->
            "This phone's keyboard would not read handwriting just now. You can still write and " +
                "save the page as it is."
        InkNotice.HANDWRITING_FAILED ->
            "Reading your handwriting did not work this time. Your writing is untouched."
        InkNotice.COULD_NOT_LOAD ->
            "Aida could not open this page of handwriting. Nothing on it has been changed."
        InkNotice.COULD_NOT_SAVE ->
            "Aida could not save this page. Your writing is still here — try saving it again."
        InkNotice.COULD_NOT_REMOVE ->
            "Aida could not take this page off the note. It is still there — try again."
        InkNotice.COULD_NOT_KEEP_TEXT ->
            "Aida could not add that text to the note. The note is as it was."
        InkNotice.NOTE_IN_BIN ->
            "This note is in the bin, so Aida left it alone. Restore it and try again."
    }

    override fun noticeAction(notice: InkNotice) =
        if (notice.isUndoable) "Undo" else null
}

internal object BengaliInkWording : InkWording {

    override val canvasDescription = "হাতে লেখার পাতা"
    override val emptyHint = "এস পেন দিয়ে লিখুন। মুছতে চাইলে কলমটি উল্টে নিন।"

    override val title = "হাতের লেখা"

    override val close = "বন্ধ করুন"
    override val save = "পাতা সেভ করুন"
    override val removePage = "পাতাটি সরান"
    override val saving = "সেভ হচ্ছে"

    override fun toolName(tool: InkTool) = when (tool) {
        InkTool.PEN -> "কলম"
        InkTool.ERASER -> "রাবার"
    }

    override val undo = "ফিরিয়ে নিন"
    override val redo = "আবার করুন"
    override val clear = "পুরো পাতা মুছুন"

    override fun fingerDrawing(enabled: Boolean) =
        if (enabled) "আঙুল দিয়ে লেখা বন্ধ করুন" else "আঙুল দিয়ে লিখুন"

    override fun fingerDrawingState(enabled: Boolean) = if (enabled) "চালু" else "বন্ধ"

    override val handwriting = "আমার হাতের লেখা পড়ুন"
    override val handwritingStop = "আবার আঁকায় ফিরুন"
    override val handwritingHint =
        "কলম দিয়ে লিখুন, এই ফোনের কিবোর্ড সেটি পড়ে নেবে। কিছুই ফোনের বাইরে যায় না।"

    override val recognisedTitle = "আইদা যা পড়ল"
    override val keepText = "নোটে যোগ করুন"
    override val discardText = "থাক, লাগবে না"

    override val leaveTitle = "এই পাতা ছেড়ে যাবেন?"
    override val leaveMessage = "যা লিখেছেন তা সেভ করা হয়নি, বন্ধ করলে সেটি হারিয়ে যাবে।"
    override val leaveConfirm = "ছেড়ে দিন"
    override val leaveCancel = "লেখা চালিয়ে যান"

    override val dismiss = "বন্ধ করুন"

    override fun notice(notice: InkNotice) = when (notice) {
        InkNotice.SAVED -> "পাতা সেভ হয়েছে।"
        InkNotice.PAGE_CLEARED -> "পাতা মুছে ফেলা হয়েছে।"
        InkNotice.TEXT_KEPT -> "নোটে যোগ করা হয়েছে।"
        InkNotice.PAGE_REMOVED ->
            "পাতায় আর কিছু ছিল না, তাই আইদা সেটি নোট থেকে সরিয়ে দিয়েছে।"
        InkNotice.NOTHING_READABLE ->
            "পড়ার মতো কিছু পাওয়া যায়নি। আপনার লেখা যেমন ছিল তেমনই আছে।"
        InkNotice.HANDWRITING_REFUSED ->
            "এই ফোনের কিবোর্ড এখন হাতের লেখা পড়তে চাইল না। আপনি তবুও লিখে পাতাটি সেভ করতে পারবেন।"
        InkNotice.HANDWRITING_FAILED ->
            "এবার হাতের লেখা পড়া গেল না। আপনার লেখায় কিছু বদলায়নি।"
        InkNotice.COULD_NOT_LOAD ->
            "আইদা হাতে লেখা এই পাতাটি খুলতে পারেনি। এর কিছুই বদলানো হয়নি।"
        InkNotice.COULD_NOT_SAVE ->
            "আইদা পাতাটি সেভ করতে পারেনি। আপনার লেখা এখানেই আছে — আবার সেভ করে দেখুন।"
        InkNotice.COULD_NOT_REMOVE ->
            "আইদা পাতাটি নোট থেকে সরাতে পারেনি। এটি এখনও আছে — আবার চেষ্টা করুন।"
        InkNotice.COULD_NOT_KEEP_TEXT ->
            "আইদা লেখাটি নোটে যোগ করতে পারেনি। নোট আগের মতোই আছে।"
        InkNotice.NOTE_IN_BIN ->
            "এই নোটটি বিনে আছে, তাই আইদা এতে হাত দেয়নি। ফিরিয়ে এনে আবার চেষ্টা করুন।"
    }

    override fun noticeAction(notice: InkNotice) =
        if (notice.isUndoable) "ফিরিয়ে নিন" else null
}

internal fun inkWording(language: SpokenLanguage): InkWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliInkWording
    SpokenLanguage.ENGLISH -> EnglishInkWording
}
