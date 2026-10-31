package com.rayluxeo.aida.ui.ink

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.domain.ink.InkTool
import org.junit.Test

/**
 * The canvas phrasebook, held to the rules it exists to keep.
 *
 * Abu reads one of these two languages. A line that was only ever written in the other is not a
 * cosmetic gap — it is Mira explaining his own phone to him in a language he did not choose. And
 * the canvas has so few words on it that every one of them carries weight, including the ones
 * only TalkBack ever reads out.
 */
class InkWordingTest {

    private val languages = SpokenLanguage.entries

    @Test
    fun `every notice has a sentence in both languages`() {
        languages.forEach { language ->
            val words = inkWording(language)
            InkNotice.entries.forEach { notice ->
                assertThat(words.notice(notice)).isNotEmpty()
            }
        }
    }

    @Test
    fun `a notice is offered an action exactly when it can be taken back`() {
        languages.forEach { language ->
            val words = inkWording(language)
            InkNotice.entries.forEach { notice ->
                val action = words.noticeAction(notice)
                if (notice.isUndoable) {
                    assertThat(action).isNotEmpty()
                } else {
                    assertThat(action).isNull()
                }
            }
        }
    }

    @Test
    fun `every control on the page has a name in both languages`() {
        languages.forEach { language ->
            val words = inkWording(language)
            assertThat(
                listOf(
                    words.canvasDescription,
                    words.emptyHint,
                    words.title,
                    words.close,
                    words.save,
                    words.removePage,
                    words.saving,
                    words.toolName(InkTool.PEN),
                    words.toolName(InkTool.ERASER),
                    words.undo,
                    words.redo,
                    words.clear,
                    words.fingerDrawing(enabled = true),
                    words.fingerDrawing(enabled = false),
                    words.fingerDrawingState(enabled = true),
                    words.fingerDrawingState(enabled = false),
                    words.handwriting,
                    words.handwritingStop,
                    words.handwritingHint,
                    words.recognisedTitle,
                    words.keepText,
                    words.discardText,
                    words.leaveTitle,
                    words.leaveMessage,
                    words.leaveConfirm,
                    words.leaveCancel,
                    words.dismiss,
                ),
            ).doesNotContain("")
        }
    }

    @Test
    fun `the finger toggle says what pressing it will do, not what is already true`() {
        languages.forEach { language ->
            val words = inkWording(language)
            assertThat(words.fingerDrawing(enabled = true))
                .isNotEqualTo(words.fingerDrawing(enabled = false))
        }
    }

    @Test
    fun `the two languages do not share a sentence`() {
        InkNotice.entries.forEach { notice ->
            assertThat(EnglishInkWording.notice(notice))
                .isNotEqualTo(BengaliInkWording.notice(notice))
        }
    }

    @Test
    fun `nothing readable is never worded or coloured as a failure`() {
        // A scribble, a diagram, or a hand the recogniser does not know are all ordinary. The
        // page is the note; text pulled out of it is a convenience, and a convenience that did
        // not work is not an error Abu has to answer.
        assertThat(InkNotice.NOTHING_READABLE.tone).isEqualTo(MiraNoticeTone.INFO)
        assertThat(InkNotice.HANDWRITING_REFUSED.tone).isEqualTo(MiraNoticeTone.INFO)
    }

    @Test
    fun `the two tools are named apart in both languages`() {
        languages.forEach { language ->
            val words = inkWording(language)
            assertThat(words.toolName(InkTool.PEN))
                .isNotEqualTo(words.toolName(InkTool.ERASER))
        }
    }

    @Test
    fun `the finger toggle's state reads apart from its name`() {
        languages.forEach { language ->
            val words = inkWording(language)
            // A name that says what pressing it will do plus a state that says where it is now:
            // a tint is a state only the eye can read.
            assertThat(words.fingerDrawingState(enabled = true))
                .isNotEqualTo(words.fingerDrawingState(enabled = false))
        }
    }

    @Test
    fun `removing a page is not worded as saving one`() {
        languages.forEach { language ->
            val words = inkWording(language)
            assertThat(words.removePage).isNotEqualTo(words.save)
        }
    }

    @Test
    fun `a refusal Mira owns is coloured as one`() {
        assertThat(InkNotice.COULD_NOT_SAVE.tone).isEqualTo(MiraNoticeTone.DANGER)
        assertThat(InkNotice.COULD_NOT_LOAD.tone).isEqualTo(MiraNoticeTone.DANGER)
        assertThat(InkNotice.COULD_NOT_KEEP_TEXT.tone).isEqualTo(MiraNoticeTone.DANGER)
        assertThat(InkNotice.COULD_NOT_REMOVE.tone).isEqualTo(MiraNoticeTone.DANGER)
    }

    @Test
    fun `taking an emptied page off the note is not worded as a failure`() {
        // Abu rubbed the page clean and asked Mira to record that. She did exactly what he asked,
        // so it is a confirmation — the old path turned storage's refusal into a red line telling
        // him to try again, which could never have worked.
        assertThat(InkNotice.PAGE_REMOVED.tone).isEqualTo(MiraNoticeTone.CONFIRM)
    }
}
