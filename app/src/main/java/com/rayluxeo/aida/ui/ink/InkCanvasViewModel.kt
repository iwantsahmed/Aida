package com.rayluxeo.aida.ui.ink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.ink.HandwritingResult
import com.rayluxeo.aida.domain.ink.HandwritingUnavailable
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkTool
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.usecase.CreateNoteUseCase
import com.rayluxeo.aida.domain.usecase.ExtractNoteTextUseCase
import com.rayluxeo.aida.domain.usecase.DeleteNoteUseCase
import com.rayluxeo.aida.domain.usecase.UpdateNoteUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The handwriting page: what is on it, and everything Abu can do to it that is not drawing.
 *
 * The strokes themselves are [engine]'s, not this class's, and that split is the whole design.
 * A `MotionEvent` has to reach the glass in the frame it arrived in, so the canvas view mutates
 * the engine directly rather than sending points through a `StateFlow`; what comes back up here
 * is only what the *controls* need — whether undo is possible, how much is on the page, whether
 * any of it is still unsaved — which changes once per stroke at most. Holding the engine above
 * the view is also what keeps a rotation, which destroys the view, from taking the page with it.
 *
 * **Recognition never gates saving.** A page of Abu's handwriting is the note; text the keyboard
 * reads out of it is a convenience he may keep or leave. Nothing on the save path consults it.
 *
 * The ink writes go straight to their repository rather than through a use case, which is a
 * deviation from AGENTS.md §4 and is flagged as one: `InkPageRepository.save` is already the
 * atomic unit the lead wrote for this — row, strokes and exported image together or not at all —
 * and there is no rule left for a use case to hold. If an ink write ever grows one (a note that
 * has to be created, a page that has to be counted), it belongs in `domain/usecase` and this
 * class should call that instead. Everything that touches the NOTE goes through a use case.
 */
@HiltViewModel
class InkCanvasViewModel @Inject constructor(
    private val inkPages: InkPageRepository,
    private val notes: NoteRepository,
    private val createNote: CreateNoteUseCase,
    private val extractNoteText: ExtractNoteTextUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val updateNote: UpdateNoteUseCase,
    settings: SettingsRepository,
) : ViewModel() {

    internal val engine = InkCanvasEngine()

    private val _state = MutableStateFlow(InkCanvasUiState())
    val state: StateFlow<InkCanvasUiState> = _state.asStateFlow()

    /** The page is read once. A recomposition asking again must not reopen it over new writing. */
    private var opened = false

    /**
     * The engine revision the notice on screen was raised at.
     *
     * Only a notice that offers an undo needs this, and it needs it badly: "Page cleared. Undo"
     * stays on screen while Abu starts writing again, and its action is the canvas's plain undo —
     * so an offer that outlived its own edit took back his new stroke instead of restoring the
     * page it named. Comparing revisions retires the offer the moment anything else changes.
     */
    private var noticeRevision: Int? = null

    init {
        engine.onHistoryChanged = ::publishPageState
        // Observed rather than read once: the language toggle is several screens away, and a
        // canvas that went on naming its controls in English after Abu chose বাংলা is the defect
        // this wording exists to fix.
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                _state.update { it.copy(language = preferences.languageMode.displayLanguage()) }
            }
        }
    }

    /**
     * Opens a note's page, or a blank one when [pageId] is null.
     *
     * [noteId] is null when Quick Ink was started from a shortcut or the widget: Abu asked for a
     * blank page and there is no note behind it yet. The row is minted on the first save rather
     * than here, so opening the canvas and walking away leaves nothing in his list.
     *
     * A page whose stroke file has been reclaimed comes back as a refusal, and Abu is told so
     * rather than shown an empty canvas — a blank page looks exactly like one he never wrote on,
     * and he would write over the fact that something was lost.
     */
    fun openPage(noteId: Long?, pageId: Long?) {
        if (opened) return
        opened = true
        _state.update { it.copy(noteId = noteId, pageId = pageId) }
        if (pageId == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            when (val loaded = inkPages.load(pageId)) {
                is AppResult.Failure ->
                    _state.update { it.copy(loading = false, notice = InkNotice.COULD_NOT_LOAD) }

                is AppResult.Success -> {
                    engine.restore(loaded.value)
                    _state.update { it.copy(loading = false) }
                }
            }
        }
    }

    fun selectTool(tool: InkTool) {
        engine.selectedTool = tool
        _state.update { it.copy(tool = tool) }
    }

    fun setFingerDrawing(enabled: Boolean) {
        engine.fingerDrawingEnabled = enabled
        _state.update { it.copy(fingerDrawing = enabled) }
    }

    fun undo() = engine.undo()

    fun redo() = engine.redo()

    fun clear() {
        if (!_state.value.canClear) return
        engine.clear()
        _state.update { it.copy(notice = InkNotice.PAGE_CLEARED) }
        noticeRevision = engine.revisionMark()
    }

    /**
     * Records the page as it stands: written to disk, or taken off the note when nothing is left.
     *
     * **An emptied page is removed, not stored.** Storage refuses a drawing with no strokes — a
     * page with nothing on it is not handwriting — and says in as many words that erasing one
     * down to nothing is a delete the caller has to ask for. Turning that refusal into "try
     * saving it again" left Abu pressing a control that could never succeed while the page on
     * disk went on saying the opposite of what was in front of him.
     *
     * The drawing and its revision are taken together before the write starts, so a word written
     * while it is in flight still counts as unsaved afterwards.
     */
    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val drawing = engine.drawing
        val mark = engine.revisionMark()
        val pageId = current.pageId
        _state.update { it.copy(saving = true, notice = null) }
        viewModelScope.launch {
            val noteId = current.noteId ?: when (val minted = mintNote()) {
                null -> return@launch
                else -> minted
            }
            if (drawing.isEmpty && pageId != null) {
                remove(pageId, mark)
            } else {
                store(noteId, drawing, pageId, mark, mintedHere = current.noteId == null)
            }
        }
    }

    /**
     * Makes the note a page started from a shortcut will hang on.
     *
     * Blank on purpose: Abu asked for a page, not a form. The note is the handwriting until he
     * gives it words, which is why [CreateNoteUseCase] has to be told a page is coming — an empty
     * row would otherwise be refused, and rightly so.
     *
     * @return the new id, or null when the row could not be written, having already said so.
     */
    private suspend fun mintNote(): Long? = when (val made = createNote(willCarryInk = true)) {
        is AppResult.Failure -> {
            _state.update { it.copy(saving = false, notice = InkNotice.COULD_NOT_SAVE) }
            null
        }
        is AppResult.Success -> {
            _state.update { it.copy(noteId = made.value.id) }
            made.value.id
        }
    }

    private suspend fun store(
        noteId: Long,
        drawing: InkDrawing,
        pageId: Long?,
        mark: Int,
        mintedHere: Boolean = false,
    ) {
        when (val saved = inkPages.save(noteId = noteId, drawing = drawing, pageId = pageId)) {
            is AppResult.Failure -> {
                // A note minted a moment ago purely to hang this page on is taken back out when
                // the page will not save. Leaving it behind would put a blank, untitled row in
                // Abu's list that he never asked for and cannot tell from writing that was lost.
                //
                // It goes to the bin rather than vanishing, because notes only ever soft delete —
                // that is what makes an accidental delete undoable, and carving a hard delete
                // into the port for this one case would weaken the guarantee everywhere else. A
                // blank row in the bin is recoverable clutter; a blank row in his notes is not.
                if (mintedHere) {
                    deleteNote(noteId)
                    _state.update { it.copy(noteId = null) }
                }
                _state.update { it.copy(saving = false, notice = InkNotice.COULD_NOT_SAVE) }
            }

            is AppResult.Success -> {
                engine.markSaved(mark)
                _state.update {
                    it.copy(saving = false, pageId = saved.value.id, notice = InkNotice.SAVED)
                }
            }
        }
    }

    private suspend fun remove(pageId: Long, mark: Int) {
        when (inkPages.delete(pageId)) {
            is AppResult.Failure ->
                _state.update { it.copy(saving = false, notice = InkNotice.COULD_NOT_REMOVE) }

            is AppResult.Success -> {
                engine.markSaved(mark)
                // The id goes with the page: what is on the canvas now has never been stored, so
                // a stroke written next is a new page rather than a replacement for a dead row.
                _state.update {
                    it.copy(saving = false, pageId = null, notice = InkNotice.PAGE_REMOVED)
                }
            }
        }
    }

    /**
     * What the platform said about stylus handwriting, which only the screen can ask.
     *
     * False hides the control outright. An offer that fails every time is worse than no offer,
     * and on a phone whose keyboard cannot do this it would fail every time.
     */
    fun setHandwritingAvailable(available: Boolean) {
        _state.update {
            it.copy(
                handwritingAvailable = available,
                handwritingMode = it.handwritingMode && available,
            )
        }
    }

    fun startHandwriting() {
        if (!_state.value.handwritingAvailable) return
        _state.update { it.copy(handwritingMode = true, notice = null, recognised = null) }
    }

    fun stopHandwriting() {
        _state.update { it.copy(handwritingMode = false) }
    }

    /**
     * What the keyboard made of Abu's writing.
     *
     * Nothing readable is not a failure and is not worded as one — a scribble, a diagram or a
     * Bengali hand the recogniser does not know are all ordinary, and the page is untouched
     * either way.
     */
    fun onHandwriting(result: HandwritingResult) {
        when (result) {
            is HandwritingResult.Recognised -> _state.update {
                it.copy(handwritingMode = false, recognised = result.text)
            }

            HandwritingResult.NoTextRecognised -> _state.update {
                it.copy(handwritingMode = false, notice = InkNotice.NOTHING_READABLE)
            }

            is HandwritingResult.Unavailable -> _state.update {
                it.copy(
                    handwritingMode = false,
                    // A keyboard with no recogniser at all is permanent, so the control stops
                    // being offered rather than failing again the next time it is pressed.
                    handwritingAvailable = it.handwritingAvailable &&
                        result.reason != HandwritingUnavailable.NO_RECOGNISER,
                    notice = when (result.reason) {
                        HandwritingUnavailable.FAILED -> InkNotice.HANDWRITING_FAILED
                        HandwritingUnavailable.NO_RECOGNISER,
                        HandwritingUnavailable.REFUSED,
                        -> InkNotice.HANDWRITING_REFUSED
                    },
                )
            }
        }
    }

    /**
     * Puts what the keyboard read at the end of the note's own text.
     *
     * Appended rather than substituted, and only when Abu says so: recognition guesses, and a
     * guess that quietly replaced what he had already written would be Mira editing his note.
     * A blank line between the two, because what he typed and what he wrote by hand are two
     * things and reading them as one paragraph is not what he wrote.
     *
     * It goes through [UpdateNoteUseCase] rather than to `NoteRepository.update`, so the note
     * rules — the trimming every verb shares, and the rule that a note has to be *something* —
     * hold for a canvas exactly as they do for the editor.
     *
     * All of that is [ExtractNoteTextUseCase]'s, not this screen's. The canvas used to carry its
     * own copy of the appending rule — where the blank line goes, what happens to a note that is
     * already empty — and a second copy of a rule is the drift AGENTS.md §7.12 exists to forbid.
     */
    fun keepRecognisedText() {
        val text = _state.value.recognised?.trim().orEmpty()
        val noteId = _state.value.noteId
        if (text.isEmpty() || noteId == null) return
        viewModelScope.launch {
            val kept = extractNoteText(noteId, HandwritingResult.Recognised(text))
            _state.update {
                when (kept) {
                    is AppResult.Success -> it.copy(recognised = null, notice = InkNotice.TEXT_KEPT)
                    // VALIDATION is the use case's word for a note in the bin. Text filed onto one
                    // is text no list but the bin would ever show him again, so it is worth its own
                    // line rather than the generic refusal.
                    is AppResult.Failure -> when (kept.error.code) {
                        MiraErrorCode.VALIDATION -> it.copy(notice = InkNotice.NOTE_IN_BIN)
                        else -> it.copy(notice = InkNotice.COULD_NOT_KEEP_TEXT)
                    }
                }
            }
        }
    }

    fun discardRecognisedText() {
        _state.update { it.copy(recognised = null) }
    }

    /**
     * Whether the screen may close now.
     *
     * @return true when there is nothing to lose; false when Abu is being asked first, which is
     *   the only case where closing is held back.
     */
    fun requestClose(): Boolean {
        if (!_state.value.hasUnsavedWork) return true
        _state.update { it.copy(confirmingLeave = true) }
        return false
    }

    fun cancelLeave() {
        _state.update { it.copy(confirmingLeave = false) }
    }

    fun confirmLeave() {
        _state.update { it.copy(confirmingLeave = false) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
        noticeRevision = null
    }

    override fun onCleared() {
        engine.onHistoryChanged = null
        super.onCleared()
    }

    private fun publishPageState() {
        // An offer to undo belongs to one edit. Anything else reaching the page — a new stroke,
        // an eraser sweep, the undo itself — means the offer no longer names what it would take
        // back, so it goes rather than quietly pointing at whatever happened last. The nib
        // touching down counts, not only the finished mark: the offer must not still be there to
        // press while Abu is halfway through the word that would replace it.
        val stale = _state.value.notice?.isUndoable == true &&
            (noticeRevision != engine.revisionMark() || engine.liveStroke != null)
        _state.update {
            it.copy(
                canUndo = engine.canUndo,
                canRedo = engine.canRedo,
                strokeCount = engine.strokeCount,
                hasMarks = engine.hasMarks,
                hasUnsavedWork = engine.hasUnsavedWork,
                notice = if (stale) null else it.notice,
            )
        }
        if (stale) noticeRevision = null
    }
}
