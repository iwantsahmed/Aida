package com.rayluxeo.aida.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The wake-word verdict, tested where it can be: the ordering of the four facts.
 *
 * Gathering those facts needs a real `PackageManager` and a real platform to reflect against, so
 * [HotwordCapabilityDetectorImpl]'s probes are verified on a device rather than here — what the
 * S24 Ultra actually answers is the point of them. What *can* be pinned down is that the answers
 * are combined in the right order, because getting that wrong tells Abu his phone has no keyphrase
 * hardware when the truth is that Android reserves it, and those two sentences have different
 * futures.
 */
class HotwordCapabilityDetectorTest {

    @Test
    fun `not holding the role outranks every other fact`() {
        val everyOtherCombination = listOf(true, false).flatMap { api ->
            listOf(true, false).flatMap { enrolled ->
                listOf(true, false).map { privileged ->
                    hotwordVerdictOf(
                        isAssistant = false,
                        detectorApiPresent = api,
                        keyphraseEnrollmentPresent = enrolled,
                        privilegedHotwordAccess = privileged,
                    )
                }
            }
        }

        assertThat(everyOtherCombination).hasSize(8)
        assertThat(everyOtherCombination.toSet())
            .containsExactly(HotwordVerdict.NOT_THE_ASSISTANT)
    }

    @Test
    fun `an absent detector api is the platform reserving it`() {
        val verdict = hotwordVerdictOf(
            isAssistant = true,
            detectorApiPresent = false,
            keyphraseEnrollmentPresent = true,
            privilegedHotwordAccess = true,
        )

        assertThat(verdict).isEqualTo(HotwordVerdict.SYSTEM_ONLY)
    }

    /** The permanent fact is reported ahead of the policy, even when both would refuse. */
    @Test
    fun `no keyphrase enrollment reads as no hardware rather than as policy`() {
        val verdict = hotwordVerdictOf(
            isAssistant = true,
            detectorApiPresent = true,
            keyphraseEnrollmentPresent = false,
            privilegedHotwordAccess = false,
        )

        assertThat(verdict).isEqualTo(HotwordVerdict.NO_HARDWARE)
    }

    /** The S24 Ultra's case: Bixby's enrolment exists, and Mira may not use it. */
    @Test
    fun `enrolled hardware Mira cannot drive is system only`() {
        val verdict = hotwordVerdictOf(
            isAssistant = true,
            detectorApiPresent = true,
            keyphraseEnrollmentPresent = true,
            privilegedHotwordAccess = false,
        )

        assertThat(verdict).isEqualTo(HotwordVerdict.SYSTEM_ONLY)
    }

    @Test
    fun `available only when every one of the four says yes`() {
        val verdict = hotwordVerdictOf(
            isAssistant = true,
            detectorApiPresent = true,
            keyphraseEnrollmentPresent = true,
            privilegedHotwordAccess = true,
        )

        assertThat(verdict).isEqualTo(HotwordVerdict.AVAILABLE)
    }

    @Test
    fun `the assist gesture is listed only while Mira holds the role`() {
        assertThat(hotwordAlternatives(isAssistant = true))
            .contains(AssistantEntryPoint.SYSTEM_ASSIST_GESTURE)
        assertThat(hotwordAlternatives(isAssistant = false))
            .doesNotContain(AssistantEntryPoint.SYSTEM_ASSIST_GESTURE)
    }

    @Test
    fun `the ways in that do not need the role are always offered`() {
        val withoutTheRole = hotwordAlternatives(isAssistant = false)

        assertThat(withoutTheRole).containsExactly(
            AssistantEntryPoint.VOICE_BUTTON,
            AssistantEntryPoint.QUICK_SETTINGS_TILE,
            AssistantEntryPoint.HOME_SCREEN_WIDGET,
            AssistantEntryPoint.APP_SHORTCUT,
            AssistantEntryPoint.SHARE_SHEET,
        ).inOrder()
    }

    @Test
    fun `alternatives are never empty, whatever the verdict`() {
        listOf(true, false).forEach { isAssistant ->
            assertThat(hotwordAlternatives(isAssistant)).isNotEmpty()
        }
    }
}
