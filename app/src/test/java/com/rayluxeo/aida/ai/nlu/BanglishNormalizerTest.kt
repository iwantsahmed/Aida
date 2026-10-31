package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BanglishNormalizerTest {

    private val banglish = BanglishNormalizer()

    private fun fold(vararg tokens: String): List<String> = banglish.foldTokens(tokens.toList())

    // ------------------------------------------------------------------ families

    @Test
    fun `folds every listed spelling of morning onto one token`() {
        assertThat(fold("shokal", "sokal", "skal", "shokale", "সকাল"))
            .containsExactly(
                CanonicalToken.MORNING,
                CanonicalToken.MORNING,
                CanonicalToken.MORNING,
                CanonicalToken.MORNING,
                CanonicalToken.MORNING,
            )
    }

    @Test
    fun `folds unlisted spellings by consonant skeleton`() {
        // None of these are in the table; they share a skeleton with a spelling that is.
        assertThat(fold("shokaal")).containsExactly(CanonicalToken.MORNING)
        assertThat(fold("duppur")).containsExactly(CanonicalToken.NOON)
        assertThat(fold("bikkel")).containsExactly(CanonicalToken.AFTERNOON)
    }

    @Test
    fun `folds the day part families`() {
        assertThat(fold("dupur", "dupure", "দুপুরে")).containsExactly(
            CanonicalToken.NOON, CanonicalToken.NOON, CanonicalToken.NOON,
        )
        assertThat(fold("shondha", "sondha", "sondhya", "সন্ধ্যায়")).containsExactly(
            CanonicalToken.EVENING, CanonicalToken.EVENING, CanonicalToken.EVENING, CanonicalToken.EVENING,
        )
        assertThat(fold("raat", "rat", "রাতে")).containsExactly(
            CanonicalToken.NIGHT, CanonicalToken.NIGHT, CanonicalToken.NIGHT,
        )
        assertThat(fold("bikal", "bikel", "বিকেল")).containsExactly(
            CanonicalToken.AFTERNOON, CanonicalToken.AFTERNOON, CanonicalToken.AFTERNOON,
        )
    }

    @Test
    fun `folds the relative day families`() {
        assertThat(fold("kal", "kaal", "kalke", "agamikal", "agamikaal", "আগামীকাল")).containsExactly(
            CanonicalToken.TOMORROW, CanonicalToken.TOMORROW, CanonicalToken.TOMORROW,
            CanonicalToken.TOMORROW, CanonicalToken.TOMORROW, CanonicalToken.TOMORROW,
        )
        assertThat(fold("aaj", "aj", "ajke", "আজ")).containsExactly(
            CanonicalToken.TODAY, CanonicalToken.TODAY, CanonicalToken.TODAY, CanonicalToken.TODAY,
        )
        assertThat(fold("porshu", "পরশু")).containsExactly(
            CanonicalToken.DAY_AFTER, CanonicalToken.DAY_AFTER,
        )
    }

    @Test
    fun `folds the verb and marker families`() {
        assertThat(fold("korar", "kora", "korte", "করার")).containsExactly(
            CanonicalToken.DO, CanonicalToken.DO, CanonicalToken.DO, CanonicalToken.DO,
        )
        assertThat(fold("dao", "dio", "den", "deben", "দাও")).containsExactly(
            CanonicalToken.GIVE, CanonicalToken.GIVE, CanonicalToken.GIVE,
            CanonicalToken.GIVE, CanonicalToken.GIVE,
        )
        assertThat(fold("ke", "কে")).containsExactly(CanonicalToken.TO, CanonicalToken.TO)
        assertThat(fold("ghonta", "ghanta", "ঘন্টা")).containsExactly(
            CanonicalToken.HOUR, CanonicalToken.HOUR, CanonicalToken.HOUR,
        )
        assertThat(fold("minit", "মিনিট")).containsExactly(CanonicalToken.MINUTE, CanonicalToken.MINUTE)
    }

    @Test
    fun `folds the command word families`() {
        assertThat(fold("reminder", "রিমাইন্ডার")).containsExactly(
            CanonicalToken.REMIND, CanonicalToken.REMIND,
        )
        assertThat(fold("kaj", "কাজ")).containsExactly(CanonicalToken.TASK, CanonicalToken.TASK)
        assertThat(fold("khojo", "khuje", "খুঁজে", "find")).containsExactly(
            CanonicalToken.SEARCH, CanonicalToken.SEARCH, CanonicalToken.SEARCH, CanonicalToken.SEARCH,
        )
        assertThat(fold("hisab", "hishab", "হিসাব")).containsExactly(
            CanonicalToken.CALCULATE, CanonicalToken.CALCULATE, CanonicalToken.CALCULATE,
        )
        assertThat(fold("sesh", "শেষ")).containsExactly(CanonicalToken.DONE, CanonicalToken.DONE)
        assertThat(fold("banao", "add", "তৈরি")).containsExactly(
            CanonicalToken.CREATE, CanonicalToken.CREATE, CanonicalToken.CREATE,
        )
        assertThat(fold("bodlao", "change", "বদলাও")).containsExactly(
            CanonicalToken.UPDATE, CanonicalToken.UPDATE, CanonicalToken.UPDATE,
        )
    }

    @Test
    fun `folds what a request is about`() {
        assertThat(fold("নোট", "notes")).containsExactly(CanonicalToken.NOTE, CanonicalToken.NOTE)
        assertThat(fold("ক্লায়েন্ট", "customer")).containsExactly(
            CanonicalToken.CLIENT, CanonicalToken.CLIENT,
        )
        assertThat(fold("ইনভয়েস", "চালান")).containsExactly(
            CanonicalToken.INVOICE, CanonicalToken.INVOICE,
        )
        assertThat(fold("khoroch", "খরচ", "spent")).containsExactly(
            CanonicalToken.EXPENSE, CanonicalToken.EXPENSE, CanonicalToken.EXPENSE,
        )
        assertThat(fold("ay", "আয়", "earned")).containsExactly(
            CanonicalToken.INCOME, CanonicalToken.INCOME, CanonicalToken.INCOME,
        )
        assertThat(fold("পেমেন্ট", "paid")).containsExactly(
            CanonicalToken.PAYMENT, CanonicalToken.PAYMENT,
        )
        assertThat(fold("কল", "মেসেজ", "মিটিং")).containsExactly(
            CanonicalToken.CALL, CanonicalToken.MESSAGE, CanonicalToken.MEETING,
        )
    }

    @Test
    fun `folds the money multipliers`() {
        assertThat(fold("hajar", "hazar", "হাজার")).containsExactly(
            CanonicalToken.THOUSAND, CanonicalToken.THOUSAND, CanonicalToken.THOUSAND,
        )
        assertThat(fold("lakh", "lac", "লাখ", "লক্ষ")).containsExactly(
            CanonicalToken.LAKH, CanonicalToken.LAKH, CanonicalToken.LAKH, CanonicalToken.LAKH,
        )
        assertThat(fold("koti", "crore", "কোটি")).containsExactly(
            CanonicalToken.CRORE, CanonicalToken.CRORE, CanonicalToken.CRORE,
        )
        assertThat(fold("taka", "tk", "৳", "টাকা", "bdt")).containsExactly(
            CanonicalToken.TAKA, CanonicalToken.TAKA, CanonicalToken.TAKA,
            CanonicalToken.TAKA, CanonicalToken.TAKA,
        )
        assertThat(fold("adha", "আধা", "dedh", "দেড়", "arai", "আড়াই")).containsExactly(
            CanonicalToken.HALF, CanonicalToken.HALF,
            CanonicalToken.ONE_AND_HALF, CanonicalToken.ONE_AND_HALF,
            CanonicalToken.TWO_AND_HALF, CanonicalToken.TWO_AND_HALF,
        )
    }

    @Test
    fun `folds the larger spans of time`() {
        assertThat(fold("agami", "আগামী", "samner")).containsExactly(
            CanonicalToken.NEXT, CanonicalToken.NEXT, CanonicalToken.NEXT,
        )
        assertThat(fold("ei", "এই")).containsExactly(CanonicalToken.THIS, CanonicalToken.THIS)
        assertThat(fold("soptah", "সপ্তাহ")).containsExactly(CanonicalToken.WEEK, CanonicalToken.WEEK)
        assertThat(fold("mash", "mase", "মাসের")).containsExactly(
            CanonicalToken.MONTH, CanonicalToken.MONTH, CanonicalToken.MONTH,
        )
        assertThat(fold("tarik", "tarikh", "তারিখ")).containsExactly(
            CanonicalToken.DATE, CanonicalToken.DATE, CanonicalToken.DATE,
        )
        assertThat(fold("shukrobar", "শুক্রবার", "jumabar")).containsExactly(
            CanonicalToken.FRIDAY, CanonicalToken.FRIDAY, CanonicalToken.FRIDAY,
        )
    }

    // ------------------------------------------------------------------- phrases

    @Test
    fun `folds multi word phrases before single words`() {
        assertThat(fold("mone", "koriye")).containsExactly(CanonicalToken.REMIND)
        assertThat(fold("মনে", "করিয়ে")).containsExactly(CanonicalToken.REMIND)
        assertThat(fold("hoye", "geche")).containsExactly(CanonicalToken.DONE)
        assertThat(fold("হয়ে", "গেছে")).containsExactly(CanonicalToken.DONE)
        assertThat(fold("mone", "rakho")).containsExactly(CanonicalToken.REMEMBER)
        assertThat(fold("মনে", "রাখো")).containsExactly(CanonicalToken.REMEMBER)
    }

    @Test
    fun `reads tomorrow written as two words`() {
        assertThat(fold("agami", "kal")).containsExactly(CanonicalToken.TOMORROW)
        assertThat(fold("আগামী", "কাল")).containsExactly(CanonicalToken.TOMORROW)
        // Still two words when the second one is not a day.
        assertThat(fold("agami", "soptah"))
            .containsExactly(CanonicalToken.NEXT, CanonicalToken.WEEK).inOrder()
    }

    @Test
    fun `reads tonight written as two words`() {
        assertThat(fold("aj", "rate")).containsExactly(CanonicalToken.TONIGHT)
        assertThat(fold("আজ", "রাতে")).containsExactly(CanonicalToken.TONIGHT)
        assertThat(fold("kal", "rate"))
            .containsExactly(CanonicalToken.TOMORROW, CanonicalToken.NIGHT).inOrder()
    }

    // --------------------------------------------------------- forms needing context

    @Test
    fun `reads the hour marker only after a number`() {
        assertThat(fold("10", "ta")).containsExactly("10", CanonicalToken.AT).inOrder()
        assertThat(fold(CanonicalToken.TASK, "ta")).containsExactly(CanonicalToken.TASK, "ta").inOrder()
    }

    @Test
    fun `reads the unambiguous hour marker anywhere`() {
        assertThat(fold("10", "tay")).containsExactly("10", CanonicalToken.AT).inOrder()
        assertThat(fold("10", "টায়")).containsExactly("10", CanonicalToken.AT).inOrder()
    }

    @Test
    fun `reads the thousand marker only after a number`() {
        assertThat(fold("50", "k")).containsExactly("50", CanonicalToken.THOUSAND).inOrder()
        assertThat(fold("ahmed", "k")).containsExactly("ahmed", "k").inOrder()
    }

    @Test
    fun `reads a clock time and a decimal as numbers`() {
        assertThat(fold("10:30", "ta")).containsExactly("10:30", CanonicalToken.AT).inOrder()
        assertThat(fold("1.5", "k")).containsExactly("1.5", CanonicalToken.THOUSAND).inOrder()
    }

    @Test
    fun `reads der as one and a half only in front of a quantity`() {
        assertThat(fold("der", "lakh"))
            .containsExactly(CanonicalToken.ONE_AND_HALF, CanonicalToken.LAKH).inOrder()
        assertThat(fold("der", "ghonta"))
            .containsExactly(CanonicalToken.ONE_AND_HALF, CanonicalToken.HOUR).inOrder()
        // The possessive plural ending, which Abu types as its own word.
        assertThat(fold("chele", "der", "jonno"))
            .containsExactly("chele", "der", "jonno").inOrder()
    }

    // -------------------------------------------------------------------- safety

    @Test
    fun `leaves protected tokens out of the fuzzy fold`() {
        assertThat(fold("ahmed")).containsExactly("ahmed")
        assertThat(banglish.foldTokens(listOf("sakil"), protectedTokens = setOf("sakil")))
            .containsExactly("sakil")
    }

    @Test
    fun `never folds a canonical token onto something else`() {
        // "tomorrow" and "taimar" share the consonant frame "tmr"; so do "month" and "minit".
        assertThat(fold("tomorrow", "month", "minute", "morning", "timer", "note", "call"))
            .containsExactly(
                CanonicalToken.TOMORROW,
                CanonicalToken.MONTH,
                CanonicalToken.MINUTE,
                CanonicalToken.MORNING,
                CanonicalToken.TIMER,
                CanonicalToken.NOTE,
                CanonicalToken.CALL,
            ).inOrder()
    }

    @Test
    fun `leaves shielded English words alone`() {
        // Each of these shares a consonant frame with a Bengali word in the table.
        assertThat(fold("send", "nothing", "measure", "skill", "giant", "spot", "press"))
            .containsExactly("send", "nothing", "measure", "skill", "giant", "spot", "press")
            .inOrder()
    }

    @Test
    fun `leaves words that are not vocabulary alone`() {
        assertThat(fold("ahmed", "invoice", "meeting", "draft"))
            .containsExactly("ahmed", CanonicalToken.INVOICE, CanonicalToken.MEETING, "draft")
            .inOrder()
        assertThat(fold("blorf", "asdkjh")).containsExactly("blorf", "asdkjh").inOrder()
    }

    // -------------------------------------------------------------- the small API

    @Test
    fun `folds a single token without a sentence around it`() {
        assertThat(banglish.fold("shokal")).isEqualTo(CanonicalToken.MORNING)
        assertThat(banglish.fold("SHOKAL")).isEqualTo(CanonicalToken.MORNING)
        assertThat(banglish.fold("ahmed")).isEqualTo("ahmed")
    }

    @Test
    fun `leaves context bound forms alone when there is no context`() {
        assertThat(banglish.fold("ta")).isEqualTo("ta")
        assertThat(banglish.fold("k")).isEqualTo("k")
        assertThat(banglish.fold("der")).isEqualTo("der")
    }

    @Test
    fun `folds a whole stretch of text`() {
        assertThat(banglish.foldAll("kal shokal 10 tay dao"))
            .isEqualTo("tomorrow morning 10 at give")
        assertThat(banglish.foldAll("Kal Shokal")).isEqualTo("tomorrow morning")
        assertThat(banglish.foldAll("")).isEmpty()
    }

    @Test
    fun `reduces spellings of one word to the same skeleton`() {
        assertThat(banglish.skeleton("shokal")).isEqualTo(banglish.skeleton("sokal"))
        assertThat(banglish.skeleton("skal")).isEqualTo(banglish.skeleton("shokal"))
        assertThat(banglish.skeleton("raat")).isEqualTo(banglish.skeleton("rat"))
        assertThat(banglish.skeleton("sondhya")).isEqualTo(banglish.skeleton("shondha"))
    }

    @Test
    fun `gives Bengali script an empty skeleton because it needs no fuzzy match`() {
        assertThat(banglish.skeleton("আগামীকাল")).isEmpty()
    }
}
