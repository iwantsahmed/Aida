package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.domain.model.NoteSort

/**
 * The notes list in Bengali. Bengali script throughout — a screen that mixes an English chip into
 * a Bengali list is harder to read than either language on its own.
 *
 * Nothing here counts anything, so no number crosses into this file. Where one ever does it goes
 * through `bengaliNumerals`, as the tasks wording does: Latin digits inside a Bengali sentence are
 * the one place a bilingual screen always slips.
 */
internal object BengaliNotesWording : NotesWording {

    override val screenTitle: String = "নোট"

    override val back: String = "ফিরে যান"

    override val newNote: String = "নতুন নোট"
    override val voiceNotes: String = "ভয়েস নোট"

    override val writeFirstNote: String = "একটি নোট লিখুন"

    override val searchPlaceholder: String = "নোট খুঁজুন"

    override val clearSearch: String = "খোঁজা মুছুন"

    override val dismiss: String = "বন্ধ করুন"

    override val done: String = "ঠিক আছে"

    override val undo: String = "ফিরিয়ে আনুন"

    override fun viewLabel(view: NotesView): String = when (view) {
        NotesView.ALL -> "সব"
        NotesView.PINNED -> "পিন করা"
        NotesView.INK -> "হাতের লেখা"
        NotesView.ARCHIVED -> "সংরক্ষণাগার"
        NotesView.BIN -> "ঝুড়ি"
    }

    override val sortTitle: String = "সাজানো"

    override fun sortLabel(sort: NoteSort): String = when (sort) {
        NoteSort.UPDATED -> "সম্পাদনার সময়"
        NoteSort.CREATED -> "তৈরির সময়"
        NoteSort.TITLE -> "শিরোনাম"
    }

    override fun sortDirection(sort: NoteSort, ascending: Boolean): String = when (sort) {
        NoteSort.UPDATED -> if (ascending) "পুরোনো আগে" else "সাম্প্রতিক আগে"
        NoteSort.CREATED -> if (ascending) "পুরোনো আগে" else "নতুন আগে"
        NoteSort.TITLE -> if (ascending) "অ থেকে হ" else "হ থেকে অ"
    }

    override val clearTags: String = "ট্যাগ মুছুন"

    override val pinnedSection: String = "পিন করা"

    override val otherNotesSection: String = "বাকি সব"

    override val untitled: String = "শিরোনামহীন"

    override fun updatedAt(moment: String): String = "$moment এ সম্পাদিত"

    override fun tagSummary(names: List<String>): String = names.joinToString(separator = ", ")

    override val actionsTitle: String = "নোট"

    override fun moreActionsFor(title: String): String = "$title — আরও"

    override val pin: String = "পিন করুন"

    override val unpin: String = "পিন সরান"

    override val archive: String = "সংরক্ষণাগারে রাখুন"

    override val unarchive: String = "তালিকায় ফেরান"

    override val delete: String = "মুছে ফেলুন"

    override val restore: String = "ফিরিয়ে আনুন"

    override val noteArchived: String = "নোট সংরক্ষণাগারে রাখা হয়েছে।"

    override val noteUnarchived: String = "নোট তালিকায় ফিরেছে।"

    override val noteDeleted: String = "নোট মুছে ফেলা হয়েছে।"

    override val noteRestored: String = "নোট ফিরে এসেছে।"

    override val undone: String = "ফিরিয়ে আনা হয়েছে।"

    override fun emptyMessage(view: NotesView): String = when (view) {
        NotesView.ALL -> "এখনো কোনো নোট নেই।"
        NotesView.PINNED -> "কিছু পিন করা নেই।"
        NotesView.INK -> "এখনো কোনো হাতের লেখা নেই।"
        NotesView.ARCHIVED -> "সংরক্ষণাগার খালি।"
        NotesView.BIN -> "ঝুড়ি খালি।"
    }

    override fun noMatches(query: String): String = "\"$query\" এর সাথে কিছু মেলেনি।"

    override val noTagMatches: String = "এই ট্যাগগুলোর নিচে কোনো নোট নেই।"
}
