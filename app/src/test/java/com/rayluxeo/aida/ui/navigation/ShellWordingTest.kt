package com.rayluxeo.aida.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The shell's phrasebook is the one place the hub, the tab strip and a planned area get their
 * words, so a missed translation here is not a red build — it is an English label sitting in a
 * Bengali list, which nobody notices until Abu does.
 *
 * Every assertion below is about a gap a human eye skips: an area added to the enum and forgotten
 * in one language, or copy pasted across from English and never translated.
 */
class ShellWordingTest {

    private val english = shellWording(SpokenLanguage.ENGLISH)
    private val bengali = shellWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishShellWording)
        assertThat(bengali).isSameInstanceAs(BengaliShellWording)
    }

    @Test
    fun `every area is named and described in both languages`() {
        MiraArea.entries.forEach { area ->
            listOf(english, bengali).forEach { words ->
                assertThat(words.areaTitle(area)).isNotEmpty()
                assertThat(words.areaSummary(area)).isNotEmpty()
            }
        }
    }

    @Test
    fun `no area's copy was left in English on the Bengali side`() {
        MiraArea.entries.forEach { area ->
            assertThat(bengali.areaTitle(area)).isNotEqualTo(english.areaTitle(area))
            assertThat(bengali.areaSummary(area)).isNotEqualTo(english.areaSummary(area))
        }
    }

    @Test
    fun `every tab is labelled in both languages, and differently`() {
        MiraTab.entries.forEach { tab ->
            assertThat(english.tabLabel(tab)).isNotEmpty()
            assertThat(bengali.tabLabel(tab)).isNotEmpty()
            assertThat(bengali.tabLabel(tab)).isNotEqualTo(english.tabLabel(tab))
        }
    }

    @Test
    fun `every planned area carries the capabilities its screen renders`() {
        MiraArea.entries.filterNot { it.isBuilt }.forEach { area ->
            listOf(english, bengali).forEach { words ->
                val capabilities = words.areaCapabilities(area)
                assertThat(capabilities).isNotEmpty()
                assertThat(capabilities.filter { it.isBlank() }).isEmpty()
            }
        }
    }

    @Test
    fun `both languages promise an unbuilt area exactly the same number of things`() {
        MiraArea.entries.filterNot { it.isBuilt }.forEach { area ->
            assertThat(bengali.areaCapabilities(area))
                .hasSize(english.areaCapabilities(area).size)
        }
    }

    /**
     * A built area's capabilities are copy about a feature that has shipped: its own screen is the
     * answer now, and the list would only rot. Flipping `isBuilt` and clearing the list are one
     * change, and this is what says so.
     */
    @Test
    fun `a built area has no capability list left behind`() {
        MiraArea.entries.filter { it.isBuilt }.forEach { area ->
            assertThat(english.areaCapabilities(area)).isEmpty()
            assertThat(bengali.areaCapabilities(area)).isEmpty()
        }
    }

    @Test
    fun `every fixed label is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.back).isNotEmpty()
            assertThat(words.notBuiltYet).isNotEmpty()
            assertThat(words.whatItWillDo).isNotEmpty()
            assertThat(words.askMira).isNotEmpty()
            assertThat(words.askMiraHint).isNotEmpty()
            with(words.appLock) {
                assertThat(title).isNotEmpty()
                assertThat(subtitle).isNotEmpty()
                assertThat(unlock).isNotEmpty()
                assertThat(promptTitle).isNotEmpty()
                assertThat(promptSubtitle).isNotEmpty()
                assertThat(lockedOut).isNotEmpty()
                assertThat(cannotVerify).isNotEmpty()
                assertThat(notEnrolled).isNotEmpty()
                assertThat(enroll).isNotEmpty()
            }
        }
    }

    /**
     * Nothing the shell says gives a date or promises a release. AGENTS.md §12.1 allows the
     * planned-area screen to exist only while it stays a statement of scope, and §12 binds both
     * languages — a Bengali "শীঘ্রই" is exactly the promise the English side is forbidden.
     *
     * Both lists are checked against both languages on purpose: the English words run against the
     * Bengali copy too, which is the same assertion as "this line was never translated".
     */
    @Test
    fun `nothing the shell says promises a date, in either language`() {
        val promises = listOf(
            "soon",
            "coming",
            "next version",
            "next release",
            "beta",
            "শীঘ্রই",
            "অচিরেই",
            "আসছে",
            "পরের সংস্করণ",
            "বিটা",
        )

        listOf(english, bengali).forEach { words ->
            words.everythingAboutWhatIsMissing().forEach { line ->
                promises.forEach { promise ->
                    assertThat(line.lowercase()).doesNotContain(promise)
                }
            }
        }
    }

    /** Every line the planned-area screen uses to talk about something that does not exist yet. */
    private fun ShellWording.everythingAboutWhatIsMissing(): List<String> = buildList {
        add(notBuiltYet)
        add(askMiraHint)
        MiraArea.entries.forEach { addAll(areaCapabilities(it)) }
    }
}
