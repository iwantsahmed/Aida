package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.domain.ink.InkTool

/** What the canvas should do with a gesture. */
internal sealed interface InkInputVerdict {

    /** Draw or erase with this implement, whatever the on-screen toggle happens to say. */
    data class Mark(val tool: InkTool) : InkInputVerdict

    /** Not Abu writing: a resting palm, a stray finger, a mouse. Nothing is recorded. */
    data object Ignore : InkInputVerdict
}

/**
 * Who is allowed to write, and with what.
 *
 * A pure function with every input passed in, because these three rules are the ones a handwriting
 * canvas is judged on and none of them can be exercised on a device inside a local test:
 *
 * 1. **The hardware outranks the toggle.** Reversing the S Pen, or holding its button, arrives as
 *    `TOOL_TYPE_ERASER`. Abu has already said what he wants with his hand; making him find a
 *    control on screen to confirm it would be Mira ignoring the more direct instruction.
 * 2. **A finger is not a pen.** On a phone sold with a stylus the finger is far more often the
 *    hand holding the phone than the hand writing with it, so [fingerDrawingEnabled] defaults off
 *    and the toggle exists for the times Abu has put the pen down.
 * 3. **Nothing but the pen counts while the pen is down.** Palm rejection, at its simplest and
 *    most reliable: the heel of a hand lands after the nib does, and while a stylus is on the
 *    glass no other contact can leave a mark — even with finger drawing switched on.
 */
internal object InkInputPolicy {

    fun verdict(
        toolType: InkToolType,
        selectedTool: InkTool,
        fingerDrawingEnabled: Boolean,
        stylusDown: Boolean,
    ): InkInputVerdict = when (toolType) {
        // The pen reversed. Erasing is what Abu asked for with his hand, so the toggle is not
        // consulted and is not changed either — putting the pen back the right way round resumes
        // whatever he had selected.
        InkToolType.ERASER -> InkInputVerdict.Mark(InkTool.ERASER)

        InkToolType.STYLUS -> InkInputVerdict.Mark(selectedTool)

        // A mouse or a digitiser that will not say what it is gets a finger's rules rather than a
        // pen's: the conservative reading, and the one that keeps the toggle meaning something.
        InkToolType.FINGER, InkToolType.OTHER ->
            if (fingerDrawingEnabled && !stylusDown) {
                InkInputVerdict.Mark(selectedTool)
            } else {
                InkInputVerdict.Ignore
            }
    }
}
