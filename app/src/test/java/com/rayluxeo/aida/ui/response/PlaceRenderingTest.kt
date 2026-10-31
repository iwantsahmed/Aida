package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.PlaceTrigger
import org.junit.Test

/**
 * A place reminder that is saved but not armed looks exactly like one that is armed, and Abu has
 * no way to notice the difference until the moment it fails to reach him. These cases pin the two
 * apart in both languages, and pin the refusal Mira gives for a place she does not hold.
 */
class PlaceRenderingTest {

    private val renderer = testRenderer()

    private fun speak(response: MiraResponse, language: SpokenLanguage) =
        renderer.speak(response, language)

    private fun display(response: MiraResponse, language: SpokenLanguage) =
        renderer.display(response, language)

    private fun placeReminder(
        title: String = "Buy printer ink",
        place: String = "the office",
        trigger: PlaceTrigger = PlaceTrigger.ENTER,
        isWatching: Boolean = true,
    ) = MiraResponse.PlaceReminderCreated(title, place, trigger, isWatching)

    @Test
    fun `arriving somewhere is confirmed plainly`() {
        val response = placeReminder()

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("I'll remind you when you get to the office — Buy printer ink.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Place reminder: Buy printer ink\nWhen: you get to the office")
    }

    @Test
    fun `leaving somewhere is a different promise`() {
        val response = placeReminder(trigger = PlaceTrigger.EXIT)

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("I'll remind you when you leave the office — Buy printer ink.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Place reminder: Buy printer ink\nWhen: you leave the office")
    }

    @Test
    fun `arriving and leaving are confirmed plainly in Bengali`() {
        val arriving = placeReminder(title = "প্রিন্টারের কালি কেনা", place = "অফিস")
        val leaving = placeReminder(
            title = "প্রিন্টারের কালি কেনা",
            place = "অফিস",
            trigger = PlaceTrigger.EXIT,
        )

        assertThat(speak(arriving, SpokenLanguage.BENGALI))
            .isEqualTo("অফিস-এ পৌঁছালে মনে করিয়ে দেব — প্রিন্টারের কালি কেনা।")
        assertThat(display(arriving, SpokenLanguage.BENGALI))
            .isEqualTo("জায়গার রিমাইন্ডার: প্রিন্টারের কালি কেনা\nকখন: অফিস-এ পৌঁছালে")
        assertThat(speak(leaving, SpokenLanguage.BENGALI))
            .isEqualTo("অফিস থেকে বের হলে মনে করিয়ে দেব — প্রিন্টারের কালি কেনা।")
        assertThat(display(leaving, SpokenLanguage.BENGALI))
            .isEqualTo("জায়গার রিমাইন্ডার: প্রিন্টারের কালি কেনা\nকখন: অফিস থেকে বের হলে")
    }

    @Test
    fun `a reminder nothing is watching says so instead of promising`() {
        val response = placeReminder(isWatching = false)

        assertThat(speak(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "Saved — Buy printer ink. I'm not watching the office yet, " +
                "so it won't reach you on its own."
        )
        assertThat(display(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "Place reminder: Buy printer ink\n" +
                "When: you get to the office\n" +
                "Not watching yet — Android has not let Aida watch that place, so nothing will " +
                "reach you. The Reminders tab says why."
        )
    }

    @Test
    fun `a reminder nothing is watching says so in Bengali too`() {
        val response = placeReminder(
            title = "প্রিন্টারের কালি কেনা",
            place = "অফিস",
            isWatching = false,
        )

        assertThat(speak(response, SpokenLanguage.BENGALI)).isEqualTo(
            "রেখে দিয়েছি — প্রিন্টারের কালি কেনা। তবে অফিস-এর উপর এখনো নজর রাখতে পারছি না, " +
                "তাই এটি নিজে থেকে আসবে না।"
        )
        assertThat(display(response, SpokenLanguage.BENGALI)).isEqualTo(
            "জায়গার রিমাইন্ডার: প্রিন্টারের কালি কেনা\n" +
                "কখন: অফিস-এ পৌঁছালে\n" +
                "এখনো নজর রাখা যাচ্ছে না — অ্যান্ড্রয়েড এই জায়গাটি খেয়াল রাখতে দেয়নি, " +
                "তাই এটি নিজে থেকে আসবে না। রিমাইন্ডার ট্যাবে কারণ লেখা আছে।"
        )
    }

    /** The whole point of [MiraResponse.PlaceReminderCreated.isWatching] reaching the words. */
    @Test
    fun `a saved reminder never sounds armed when it is not`() {
        SpokenLanguage.entries.forEach { language ->
            val armed = speak(placeReminder(isWatching = true), language)
            val saved = speak(placeReminder(isWatching = false), language)

            assertThat(saved).isNotEqualTo(armed)
            assertThat(display(placeReminder(isWatching = false), language))
                .isNotEqualTo(display(placeReminder(isWatching = true), language))
        }
        assertThat(speak(placeReminder(isWatching = false), SpokenLanguage.ENGLISH))
            .doesNotContain("I'll remind you")
        assertThat(speak(placeReminder(isWatching = false), SpokenLanguage.BENGALI))
            .doesNotContain("মনে করিয়ে দেব")
    }

    @Test
    fun `a place Mira does not hold is answered with the ones she does`() {
        val response = MiraResponse.PlaceUnknown("the warehouse", listOf("Home", "The office"))

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("I don't have a place called the warehouse. I know Home and The office.")
        assertThat(display(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "I don't have a place called \"the warehouse\".\n" +
                "Places I know:\n" +
                "• Home\n" +
                "• The office"
        )
    }

    @Test
    fun `a place Mira does not hold is answered with the ones she does in Bengali`() {
        val response = MiraResponse.PlaceUnknown("গুদাম", listOf("বাসা", "অফিস"))

        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("গুদাম নামে কোনো জায়গা নেই। আমি চিনি বাসা ও অফিস।")
        assertThat(display(response, SpokenLanguage.BENGALI)).isEqualTo(
            "\"গুদাম\" নামে কোনো জায়গা আমার কাছে নেই।\n" +
                "যেগুলো চিনি:\n" +
                "• বাসা\n" +
                "• অফিস"
        )
    }

    /** Speech counts the rest; the transcript names every one of them. */
    @Test
    fun `more places than a sentence will carry are counted aloud and listed in full`() {
        val known = listOf("Home", "The office", "The bank", "The clinic")
        val response = MiraResponse.PlaceUnknown("the warehouse", known)

        assertThat(speak(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "I don't have a place called the warehouse. I know Home, The office and 2 more."
        )
        known.forEach { place ->
            assertThat(display(response, SpokenLanguage.ENGLISH)).contains("• $place")
        }
    }

    @Test
    fun `more places than a sentence will carry are counted aloud in Bengali`() {
        val known = listOf("বাসা", "অফিস", "ব্যাংক", "ক্লিনিক")
        val response = MiraResponse.PlaceUnknown("গুদাম", known)
        val spoken = speak(response, SpokenLanguage.BENGALI)

        assertThat(spoken).isEqualTo("গুদাম নামে কোনো জায়গা নেই। আমি চিনি বাসা, অফিস ও আরও ২টি।")
        assertThat(spoken.none { it in '0'..'9' }).isTrue()
        known.forEach { place ->
            assertThat(display(response, SpokenLanguage.BENGALI)).contains("• $place")
        }
    }

    @Test
    fun `with no places saved Mira says where to save one`() {
        val response = MiraResponse.PlaceUnknown("the warehouse", emptyList())

        assertThat(speak(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "I don't have a place called the warehouse. Add one in Settings and I'll use it."
        )
        assertThat(display(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "I don't have a place called \"the warehouse\".\n" +
                "No places saved yet — add one in Settings."
        )
    }

    @Test
    fun `with no places saved Mira says where to save one in Bengali`() {
        val response = MiraResponse.PlaceUnknown("গুদাম", emptyList())

        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("গুদাম নামে কোনো জায়গা নেই। সেটিংসে একটি যোগ করলে কাজে লাগাতে পারব।")
        assertThat(display(response, SpokenLanguage.BENGALI)).isEqualTo(
            "\"গুদাম\" নামে কোনো জায়গা আমার কাছে নেই।\n" +
                "এখনো কোনো জায়গা রাখা হয়নি — সেটিংস থেকে যোগ করতে পারেন।"
        )
    }

    /**
     * Mira is asking for a fact she genuinely does not hold. That is not a fault, and the reply
     * must not read as one — it names what Abu said and stays on one line, in either language.
     */
    @Test
    fun `an unknown place does not read as an error`() {
        val withPlaces = MiraResponse.PlaceUnknown("the warehouse", listOf("Home"))
        val withNone = MiraResponse.PlaceUnknown("the warehouse", emptyList())

        SpokenLanguage.entries.forEach { language ->
            listOf(withPlaces, withNone).forEach { response ->
                val spoken = speak(response, language)

                assertThat(spoken).doesNotContain("!")
                assertThat(spoken).doesNotContain("\n")
                assertThat(spoken).contains("the warehouse")
            }
        }
    }
}
