package com.rayluxeo.aida.ui.ink

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.ink.InkTool
import org.junit.Test

/**
 * The three rules a handwriting canvas is judged on, held to without a device.
 *
 * None of these can be exercised on a phone inside a local test — `MotionEvent` cannot be built,
 * and a resting palm cannot be faked — which is exactly why the decision was pulled out of the
 * view into a pure function.
 */
class InkInputPolicyTest {

    @Test
    fun `a reversed pen erases even while the pen is the chosen tool`() {
        val verdict = InkInputPolicy.verdict(
            toolType = InkToolType.ERASER,
            selectedTool = InkTool.PEN,
            fingerDrawingEnabled = false,
            stylusDown = false,
        )

        assertThat(verdict).isEqualTo(InkInputVerdict.Mark(InkTool.ERASER))
    }

    @Test
    fun `the pen draws with whichever tool is chosen`() {
        InkTool.entries.forEach { tool ->
            val verdict = InkInputPolicy.verdict(
                toolType = InkToolType.STYLUS,
                selectedTool = tool,
                fingerDrawingEnabled = false,
                stylusDown = false,
            )

            assertThat(verdict).isEqualTo(InkInputVerdict.Mark(tool))
        }
    }

    @Test
    fun `a finger leaves no mark while finger drawing is off`() {
        val verdict = InkInputPolicy.verdict(
            toolType = InkToolType.FINGER,
            selectedTool = InkTool.PEN,
            fingerDrawingEnabled = false,
            stylusDown = false,
        )

        assertThat(verdict).isEqualTo(InkInputVerdict.Ignore)
    }

    @Test
    fun `a finger draws once Abu has turned it on`() {
        val verdict = InkInputPolicy.verdict(
            toolType = InkToolType.FINGER,
            selectedTool = InkTool.PEN,
            fingerDrawingEnabled = true,
            stylusDown = false,
        )

        assertThat(verdict).isEqualTo(InkInputVerdict.Mark(InkTool.PEN))
    }

    @Test
    fun `nothing but the pen counts while the pen is down`() {
        val verdict = InkInputPolicy.verdict(
            toolType = InkToolType.FINGER,
            selectedTool = InkTool.PEN,
            // Even with finger drawing switched on: this is the resting hand, not the writing one.
            fingerDrawingEnabled = true,
            stylusDown = true,
        )

        assertThat(verdict).isEqualTo(InkInputVerdict.Ignore)
    }

    @Test
    fun `a tool the digitiser will not name is treated as a finger, not as a pen`() {
        val withFingerOff = InkInputPolicy.verdict(
            toolType = InkToolType.OTHER,
            selectedTool = InkTool.PEN,
            fingerDrawingEnabled = false,
            stylusDown = false,
        )
        val withFingerOn = InkInputPolicy.verdict(
            toolType = InkToolType.OTHER,
            selectedTool = InkTool.PEN,
            fingerDrawingEnabled = true,
            stylusDown = false,
        )

        assertThat(withFingerOff).isEqualTo(InkInputVerdict.Ignore)
        assertThat(withFingerOn).isEqualTo(InkInputVerdict.Mark(InkTool.PEN))
    }
}
