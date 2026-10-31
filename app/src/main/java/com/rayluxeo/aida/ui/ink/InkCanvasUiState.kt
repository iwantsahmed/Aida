package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.domain.ink.InkTool

/**
 * Something the canvas has to tell Abu, as a value rather than as a sentence.
 *
 * The wording lives in [InkWording] and the reason lives here, so a test can assert what Mira
 * concluded without pinning the prose, and so every line the screen can show is one that
 * something actually produces.
 *
 * Note what is *not* here: nothing about recognition is a failure. A keyboard that read nothing
 * out of a scribble, and a keyboard that will not read handwriting at all, are both ordinary —
 * the page is the note, and text pulled out of it is a convenience that must never look like a
 * fault (AGENTS.md §12, and the reasoning in `domain/ink/InkPorts.kt`).
 */
enum class InkNotice {
    SAVED,
    PAGE_CLEARED,
    TEXT_KEPT,

    /**
     * Abu rubbed a stored page down to nothing and saved that.
     *
     * A page with no strokes is not handwriting, so storing one is removing it — which is what
     * `InkPageRepository` says in as many words and what its refusal used to be silently turned
     * into "Mira could not save this page. Try again", an instruction that could never work.
     */
    PAGE_REMOVED,

    /** Abu wrote, and the keyboard made nothing of it. Usually a scribble, never an error. */
    NOTHING_READABLE,

    /** The keyboard declined this session, or turned out not to offer handwriting at all. */
    HANDWRITING_REFUSED,

    /** The platform reported a failure it did not explain. */
    HANDWRITING_FAILED,

    COULD_NOT_LOAD,
    COULD_NOT_SAVE,

    /** The emptied page could not be taken off the note, so it is still on it. */
    COULD_NOT_REMOVE,
    COULD_NOT_KEEP_TEXT,

    /** The note this page belongs to is in the bin, where nothing may be written into it. */
    NOTE_IN_BIN,
    ;

    /**
     * Clearing the page is the only one of these Abu can take back from the notice itself.
     *
     * It is also the one that most needs it: clear is the most destructive control on the canvas
     * and the easiest to hit by accident, and an undo he has to go looking for is not a way out.
     */
    val isUndoable: Boolean get() = this == PAGE_CLEARED

    val tone: MiraNoticeTone
        get() = when (this) {
            SAVED, PAGE_CLEARED, TEXT_KEPT, PAGE_REMOVED -> MiraNoticeTone.CONFIRM
            NOTHING_READABLE, HANDWRITING_REFUSED -> MiraNoticeTone.INFO
            HANDWRITING_FAILED, COULD_NOT_LOAD, COULD_NOT_SAVE, COULD_NOT_REMOVE,
            COULD_NOT_KEEP_TEXT, NOTE_IN_BIN,
            -> MiraNoticeTone.DANGER
        }
}

/**
 * Everything the handwriting screen draws that is not the ink itself.
 *
 * The strokes are deliberately absent: they live in [InkCanvasEngine], which the view mutates and
 * paints directly, because a page of handwriting is tens of thousands of points and routing them
 * through a `StateFlow` and a recomposition per frame is how ink starts lagging behind the nib.
 * What is here is everything the *controls* need, which changes once per stroke at most.
 *
 * [recognised] is held rather than applied. Recognition can misread a word, and text put straight
 * into Abu's note would be Mira editing his writing on a guess — so he sees what was read and
 * decides.
 */
data class InkCanvasUiState(
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    /** True until the page that was asked for has been read back, or found not to exist. */
    val loading: Boolean = true,
    val noteId: Long? = null,
    /** Null for a page that has never been saved; set once it has, so a second save replaces it. */
    val pageId: Long? = null,
    val tool: InkTool = InkTool.PEN,
    val fingerDrawing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val strokeCount: Int = 0,
    /** Counts the mark being drawn right now, which [strokeCount] deliberately does not. */
    val hasMarks: Boolean = false,
    val hasUnsavedWork: Boolean = false,
    val saving: Boolean = false,
    /**
     * Whether this phone's keyboard offers stylus handwriting, asked of the platform at runtime.
     *
     * False hides the control outright rather than showing one that fails: an offer Mira cannot
     * keep is worse than no offer (AGENTS.md §12).
     */
    val handwritingAvailable: Boolean = false,
    /** True while the keyboard, not the canvas, is taking what the pen writes. */
    val handwritingMode: Boolean = false,
    val recognised: String? = null,
    val notice: InkNotice? = null,
    /** True while Abu is being asked whether to close on unsaved writing. */
    val confirmingLeave: Boolean = false,
) {

    /**
     * Whether there is anything worth writing to disk.
     *
     * An empty page that has never been saved is nothing, so the control is simply not offered —
     * not a fault, and not a button that does nothing. An empty page that HAS been saved is a
     * different thing entirely: Abu has rubbed out everything he wrote, and refusing to let him
     * store that would leave the page on disk saying the opposite of what is in front of him.
     * What storing it *means* in that case is [saveIsRemoval].
     */
    val canSave: Boolean
        get() = !loading && !saving && hasUnsavedWork && (strokeCount > 0 || pageId != null)

    /**
     * Whether pressing the control takes the page off the note rather than writing it.
     *
     * A page with no strokes is not handwriting and storage refuses to hold one, so the only way
     * to record that Abu rubbed a stored page clean is to remove it. The control says so, because
     * a button that reads "Save page" and deletes one is the same defect as a button that reads
     * "Undo" and takes back the wrong edit.
     */
    val saveIsRemoval: Boolean get() = pageId != null && strokeCount == 0

    /**
     * Whether the save control is on the bar at all.
     *
     * [canSave] has always said that an untouched page has nothing to write; this is that said
     * out loud. A champagne button shown dim for the whole time Abu is writing is the loudest
     * thing on a page that is meant to be nothing but ink, and it is also a control that does
     * nothing, which §12 rules out. So it is not drawn until there is something to write, and its
     * going is what says the page was stored.
     *
     * It stays, dimmed, while the write is in flight: a control that vanished the instant it was
     * pressed would leave Abu unsure whether the press landed at all.
     */
    val isSaveOffered: Boolean get() = canSave || saving

    val canClear: Boolean get() = strokeCount > 0

    /** An untouched page says so, rather than looking like a screen that failed to load. */
    val showEmptyHint: Boolean get() = !loading && !hasMarks
}
