package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.nlu.NormalizedText
import org.junit.Test

class InputNormalizerTest {

    private val normalizer = InputNormalizer(LanguageHintDetector(), BanglishNormalizer())

    private fun normalize(raw: String, mode: LanguageMode = LanguageMode.AUTO): NormalizedText =
        normalizer.normalize(raw, mode)

    private fun canonical(raw: String): String = normalize(raw).normalized

    // ----------------------------------------------------------------- the shape

    @Test
    fun `keeps the raw input alongside the canonical form`() {
        val raw = "Remind me tomorrow at 10 to call Ahmed."
        assertThat(normalize(raw).raw).isEqualTo(raw)
    }

    @Test
    fun `lowercases, drops punctuation and collapses whitespace`() {
        assertThat(canonical("Remind   me tomorrow  at 10 to call Ahmed."))
            .isEqualTo("remind me tomorrow at 10 to call ahmed")
    }

    @Test
    fun `converts Bengali digits and splits them from the suffix they are glued to`() {
        assertThat(canonical("১০টায়")).isEqualTo("10 at")
    }

    @Test
    fun `keeps arithmetic operators and folds Bengali digits around them`() {
        assertThat(canonical("১২ * ৪")).isEqualTo("12 * 4")
        assertThat(canonical("what is 12 * 4?")).isEqualTo("what is 12 * 4")
    }

    @Test
    fun `keeps a clock time written with a colon in one piece`() {
        assertThat(canonical("Remind me at 10:30")).isEqualTo("remind me at 10:30")
    }

    @Test
    fun `keeps a percent sign on the number it belongs to`() {
        // Without it the calculator reads "7.5 of 2400" and answers 18000 instead of 180.
        assertThat(canonical("12450 * 3 + 7.5% of 2400"))
            .isEqualTo("12450 * 3 + 7.5% of 2400")
        assertThat(canonical("15% VAT")).isEqualTo("15% vat")
    }

    @Test
    fun `treats a hyphen between words as a break and between numbers as arithmetic`() {
        assertThat(canonical("12 - 4")).isEqualTo("12 - 4")
        assertThat(canonical("follow-up with Ahmed")).isEqualTo("follow up with ahmed")
    }

    @Test
    fun `returns an empty canonical form for input with no words`() {
        assertThat(canonical("   ...   ")).isEmpty()
        assertThat(canonical("")).isEmpty()
    }

    // ------------------------------------------------------------- the four ways

    @Test
    fun `folds Banglish onto canonical tokens`() {
        assertThat(canonical("Kal shokal 10 tay Ahmed ke call korar reminder dao."))
            .isEqualTo("tomorrow morning 10 at ahmed to call do remind give")
    }

    @Test
    fun `folds Bengali script onto the same canonical tokens as Banglish`() {
        val bengali = canonical("আগামীকাল সকাল ১০টায় Ahmed কে call করার reminder দাও.")
        val banglish = canonical("Kal shokal 10 tay Ahmed ke call korar reminder dao.")
        assertThat(bengali).isEqualTo(banglish)
    }

    @Test
    fun `gives all four phrasings of one request the same vocabulary`() {
        val phrasings = listOf(
            "Remind me tomorrow at 10 to call Ahmed.",
            "আগামীকাল সকাল ১০টায় Ahmed কে call করার reminder দাও.",
            "Kal shokal 10 tay Ahmed ke call korar reminder dao.",
            "Tomorrow 10 tay Ahmed ke call korar reminder dio.",
        )
        for (phrasing in phrasings) {
            val words = canonical(phrasing).split(' ')
            assertThat(words).contains(CanonicalToken.REMIND)
            assertThat(words).contains(CanonicalToken.TOMORROW)
            assertThat(words).contains("ahmed")
            assertThat(words).contains("call")
            assertThat(words).contains("10")
        }
    }

    @Test
    fun `keeps words that are not vocabulary intact`() {
        val normalized = canonical("Kal Ahmed ke invoice pathate hobe")
        assertThat(normalized).contains("ahmed")
        assertThat(normalized).contains("invoice")
        assertThat(normalized).contains("pathate")
    }

    @Test
    fun `keeps a name out of the vocabulary even when it looks like a Bengali word`() {
        // "sakil" would otherwise reach the fuzzy fold; capitalised mid-sentence, it is a name.
        assertThat(canonical("Kal Sakil ke call koro")).contains("sakil")
    }

    // ------------------------------------------------------------- Bengali money

    @Test
    fun `normalizes money written in Bengali script`() {
        assertThat(canonical("৫০০ টাকা")).isEqualTo("500 taka")
        assertThat(canonical("৫০ হাজার")).isEqualTo("50 thousand")
        assertThat(canonical("দেড় লাখ")).isEqualTo("one_and_half lakh")
        assertThat(canonical("আড়াই লাখ")).isEqualTo("two_and_half lakh")
        assertThat(canonical("১ লাখ ৫০ হাজার")).isEqualTo("1 lakh 50 thousand")
    }

    @Test
    fun `leaves a spelled out Bengali numeral for the number parser to read`() {
        // The multiplier is canonical; the numeral itself is still Bengali, because
        // transliterating it here would be guesswork and the number parser reads both scripts.
        assertThat(canonical("পঞ্চাশ হাজার")).isEqualTo("পঞ্চাশ thousand")
        assertThat(canonical("এক লাখ")).isEqualTo("এক lakh")
        assertThat(canonical("এক কোটি")).isEqualTo("এক crore")
        assertThat(canonical("দুই লাখ পঁচিশ হাজার")).isEqualTo("দুই lakh পঁচিশ thousand")
    }

    @Test
    fun `normalizes money written in Banglish and English`() {
        assertThat(canonical("500")).isEqualTo("500")
        assertThat(canonical("50k")).isEqualTo("50 thousand")
        assertThat(canonical("50 K")).isEqualTo("50 thousand")
        assertThat(canonical("50 thousand")).isEqualTo("50 thousand")
        assertThat(canonical("1 lakh")).isEqualTo("1 lakh")
        assertThat(canonical("1 lac")).isEqualTo("1 lakh")
        assertThat(canonical("1.5 lakh")).isEqualTo("1.5 lakh")
        assertThat(canonical("2.25 lakh")).isEqualTo("2.25 lakh")
        assertThat(canonical("1 crore")).isEqualTo("1 crore")
        assertThat(canonical("1 million")).isEqualTo("1 million")
        assertThat(canonical("der lakh taka")).isEqualTo("one_and_half lakh taka")
    }

    @Test
    fun `folds every way of naming the currency`() {
        assertThat(canonical("৳500")).isEqualTo("taka 500")
        assertThat(canonical("BDT 500")).isEqualTo("taka 500")
        assertThat(canonical("500 taka")).isEqualTo("500 taka")
        assertThat(canonical("500 tk")).isEqualTo("500 taka")
        assertThat(canonical("500 টাকা")).isEqualTo("500 taka")
    }

    // ------------------------------------------------------------- Bengali dates

    @Test
    fun `normalizes dates written in Bengali script`() {
        assertThat(canonical("আজ")).isEqualTo("today")
        assertThat(canonical("কাল")).isEqualTo("tomorrow")
        assertThat(canonical("আগামীকাল")).isEqualTo("tomorrow")
        assertThat(canonical("পরশু")).isEqualTo("day_after")
        assertThat(canonical("আজ রাতে")).isEqualTo("tonight")
        assertThat(canonical("কাল সকালে")).isEqualTo("tomorrow morning")
        assertThat(canonical("কাল দুপুরে")).isEqualTo("tomorrow noon")
        assertThat(canonical("কাল বিকেলে")).isEqualTo("tomorrow afternoon")
        assertThat(canonical("কাল রাতে")).isEqualTo("tomorrow night")
    }

    @Test
    fun `normalizes the larger spans written in Bengali script`() {
        assertThat(canonical("আগামী শুক্রবার")).isEqualTo("next friday")
        assertThat(canonical("এই শুক্রবার")).isEqualTo("this friday")
        assertThat(canonical("আগামী সপ্তাহ")).isEqualTo("next week")
        assertThat(canonical("আগামী মাস")).isEqualTo("next month")
        assertThat(canonical("মাসের ৫ তারিখ")).isEqualTo("month 5 date")
    }

    @Test
    fun `normalizes dates written in Banglish`() {
        assertThat(canonical("aj")).isEqualTo("today")
        assertThat(canonical("ajke")).isEqualTo("today")
        assertThat(canonical("kal")).isEqualTo("tomorrow")
        assertThat(canonical("kalke")).isEqualTo("tomorrow")
        assertThat(canonical("agami kal")).isEqualTo("tomorrow")
        assertThat(canonical("porshu")).isEqualTo("day_after")
        assertThat(canonical("kal shokal")).isEqualTo("tomorrow morning")
        assertThat(canonical("kal sokal")).isEqualTo("tomorrow morning")
        assertThat(canonical("kal dupur")).isEqualTo("tomorrow noon")
        assertThat(canonical("kal bikal")).isEqualTo("tomorrow afternoon")
        assertThat(canonical("kal raat")).isEqualTo("tomorrow night")
        assertThat(canonical("kal rat")).isEqualTo("tomorrow night")
        assertThat(canonical("agami shukrobar")).isEqualTo("next friday")
        assertThat(canonical("agami soptah")).isEqualTo("next week")
        assertThat(canonical("agami mase")).isEqualTo("next month")
        assertThat(canonical("5 tarikh")).isEqualTo("5 date")
    }

    // ------------------------------------------------------------ what Mira does

    @Test
    fun `normalizes what a request is about`() {
        assertThat(canonical("5000 taka khoroch")).isEqualTo("5000 taka expense")
        assertThat(canonical("ay 50 hajar")).isEqualTo("income 50 thousand")
        assertThat(canonical("mone rakho Ahmed er number"))
            .isEqualTo("remember ahmed er number")
        assertThat(canonical("dupur 2 tay meeting")).isEqualTo("noon 2 at meeting")
        assertThat(canonical("ajke raat 9 tay alarm")).isEqualTo("tonight 9 at alarm")
    }

    @Test
    fun `leaves an English sentence that shares a Bengali skeleton alone`() {
        assertThat(canonical("nothing to send")).isEqualTo("nothing to send")
        assertThat(canonical("I have a meeting next month"))
            .isEqualTo("i have a meeting next month")
    }

    // -------------------------------------------------- what the detector decided

    @Test
    fun `carries the language and the script the detector decided`() {
        assertThat(normalize("Remind me tomorrow at 10 to call Ahmed.").language)
            .isEqualTo(SpokenLanguage.ENGLISH)
        assertThat(normalize("আগামীকাল সকাল ১০টায় reminder দাও").language)
            .isEqualTo(SpokenLanguage.BENGALI)
        assertThat(normalize("Tomorrow 10 tay Ahmed ke call korar reminder dio.").language)
            .isEqualTo(SpokenLanguage.BENGALI)
        assertThat(normalize("আগামীকাল সকাল দশটায়").script).isEqualTo(TextScript.BENGALI)
        assertThat(normalize("Remind me tomorrow at 10").script).isEqualTo(TextScript.LATIN)
        assertThat(normalize("আগামীকাল Ahmed কে call করার reminder দাও").script)
            .isEqualTo(TextScript.MIXED)
    }

    @Test
    fun `honours the language Abu pinned`() {
        assertThat(normalize("Remind me tomorrow at 10", LanguageMode.BANGLA).language)
            .isEqualTo(SpokenLanguage.BENGALI)
        assertThat(normalize("আগামীকাল সকাল ১০টায় reminder দাও", LanguageMode.ENGLISH).language)
            .isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `normalizes the same way whatever language is pinned`() {
        val raw = "Kal shokal 10 tay Ahmed ke call korar reminder dao."
        val auto = normalize(raw, LanguageMode.AUTO).normalized
        assertThat(normalize(raw, LanguageMode.BANGLA).normalized).isEqualTo(auto)
        assertThat(normalize(raw, LanguageMode.ENGLISH).normalized).isEqualTo(auto)
    }
}
