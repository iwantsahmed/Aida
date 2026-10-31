package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import org.junit.Test

class LanguageHintDetectorTest {

    private val detector = LanguageHintDetector()

    private fun detect(raw: String, mode: LanguageMode = LanguageMode.AUTO): LanguageHint =
        detector.detect(raw, mode)

    // -------------------------------------------------------------------- script

    @Test
    fun `detects Bengali script`() {
        assertThat(detect("আগামীকাল সকাল দশটায়").script).isEqualTo(TextScript.BENGALI)
    }

    @Test
    fun `detects Latin script`() {
        assertThat(detect("Remind me tomorrow at 10").script).isEqualTo(TextScript.LATIN)
    }

    @Test
    fun `detects mixed script`() {
        assertThat(detect("আগামীকাল Ahmed কে call করার reminder দাও").script)
            .isEqualTo(TextScript.MIXED)
    }

    @Test
    fun `treats digits alone as Latin script`() {
        assertThat(detect("১২ * ৪").script).isEqualTo(TextScript.LATIN)
    }

    @Test
    fun `reports the script regardless of the language setting`() {
        assertThat(detect("আগামীকাল সকাল", LanguageMode.ENGLISH).script)
            .isEqualTo(TextScript.BENGALI)
        assertThat(detect("Remind me tomorrow", LanguageMode.BANGLA).script)
            .isEqualTo(TextScript.LATIN)
    }

    // ------------------------------------------------------------------ language

    @Test
    fun `resolves English for a plain English sentence under AUTO`() {
        assertThat(detect("Remind me tomorrow at 10 to call Ahmed.").language)
            .isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `resolves Bengali for Bengali script under AUTO`() {
        assertThat(detect("আগামীকাল সকাল ১০টায় reminder দাও").language)
            .isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `resolves Bengali for a Latin sentence that is Bengali in substance`() {
        assertThat(detect("Tomorrow 10 tay Ahmed ke call korar reminder dio.").language)
            .isEqualTo(SpokenLanguage.BENGALI)
        assertThat(detect("Kal shokal 10 tay Ahmed ke call korar reminder dao.").language)
            .isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `does not call a sentence Bengali on a single coincidental word`() {
        assertThat(detect("Book a table").language).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `does not read English borrowings as Bengali`() {
        // "note", "task" and "find" fold to canonical tokens, but they are ordinary English.
        assertThat(detect("find the note for that task").language).isEqualTo(SpokenLanguage.ENGLISH)
        assertThat(detect("50k").language).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `reads a lone Bengali word as Bengali`() {
        assertThat(detect("kal").language).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(detect("আজ").language).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(detect("today").language).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `honours the BANGLA setting over what the text looks like`() {
        assertThat(detect("Remind me tomorrow at 10", LanguageMode.BANGLA).language)
            .isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `honours the ENGLISH setting over what the text looks like`() {
        assertThat(detect("আগামীকাল সকাল ১০টায় reminder দাও", LanguageMode.ENGLISH).language)
            .isEqualTo(SpokenLanguage.ENGLISH)
    }

    // ---------------------------------------------------------------- confidence

    @Test
    fun `is certain about a language Abu pinned himself`() {
        assertThat(detect("whatever", LanguageMode.BANGLA).confidence).isEqualTo(1f)
        assertThat(detect("whatever", LanguageMode.ENGLISH).confidence).isEqualTo(1f)
    }

    @Test
    fun `is unsure about an utterance carrying no language at all`() {
        val digits = detect("১২ * ৪").confidence
        assertThat(digits).isLessThan(detect("what is 12 * 4").confidence)
        assertThat(digits).isAtMost(0.3f)
    }

    @Test
    fun `is less sure of one word than of a whole sentence`() {
        assertThat(detect("kal").confidence)
            .isLessThan(detect("kal shokal 10 tay dao").confidence)
        assertThat(detect("ok").confidence)
            .isLessThan(detect("Remind me tomorrow at 10 to call Ahmed.").confidence)
    }

    @Test
    fun `is less sure of a sentence that only half argues for Bengali`() {
        val halfBanglish = detect("Tomorrow 10 tay Ahmed ke call korar reminder dio.")
        val allBanglish = detect("Kal shokal 10 tay Ahmed ke call korar reminder dao.")
        assertThat(halfBanglish.language).isEqualTo(allBanglish.language)
        assertThat(halfBanglish.confidence).isLessThan(allBanglish.confidence)
    }

    @Test
    fun `never reports a confidence outside its range`() {
        val utterances = listOf(
            "",
            "   ...   ",
            "৳",
            "১২ * ৪",
            "ok",
            "kal",
            "Book a table",
            "আগামীকাল সকাল দশটায়",
            "Kal shokal 10 tay Ahmed ke call korar reminder dao.",
        )
        for (raw in utterances) {
            for (mode in LanguageMode.entries) {
                val confidence = detect(raw, mode).confidence
                assertThat(confidence).isAtLeast(0f)
                assertThat(confidence).isAtMost(1f)
            }
        }
    }
}
