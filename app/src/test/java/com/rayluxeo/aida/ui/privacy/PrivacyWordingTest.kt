package com.rayluxeo.aida.ui.privacy

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The dashboard's phrasebook is the one place the screen and the view model get their words, so a
 * missed translation here is not a red build — it is an English label sitting in a Bengali screen,
 * or an English notice raised after a Bengali toggle, which nobody notices until Abu does.
 *
 * Every assertion below is about a gap a human eye skips: a member added to the interface and
 * forgotten in one language, or a line pasted across from English and never translated.
 */
class PrivacyWordingTest {

    private val english = privacyWording(SpokenLanguage.ENGLISH)
    private val bengali = privacyWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishPrivacyWording)
        assertThat(bengali).isSameInstanceAs(BengaliPrivacyWording)
    }

    @Test
    fun `every string is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            words.everyString().forEach { line ->
                assertThat(line).isNotEmpty()
            }
        }
    }

    @Test
    fun `no string was left in English on the Bengali side`() {
        english.everyString().zip(bengali.everyString()).forEach { (en, bn) ->
            assertThat(bn).isNotEqualTo(en)
        }
    }

    /** Every member the dashboard puts in front of Abu, in a fixed order the two sides share. */
    private fun PrivacyWording.everyString(): List<String> = listOf(
        dashboardTitle,
        offlineArchitectureTitle,
        offlineArchitectureBody,
        cloudDisabledChip,
        onDeviceNluChip,
        cloudAiTitle,
        geminiEngineTitle,
        geminiActiveSubtitle,
        geminiOffSubtitle,
        appLockTitle,
        appLockRowTitle,
        appLockOnSubtitle,
        appLockOffSubtitle,
        destructiveActionsTitle,
        eraseAllButton,
        eraseDialogTitle,
        eraseDialogMessage,
        eraseDialogConfirm,
        eraseDialogCancel,
        geminiDisabledNotice,
        geminiKeyRequiredNotice,
        appLockOnNotice,
        appLockOffNotice,
        dataErasedNotice,
        eraseFailedNotice,
    )
}
