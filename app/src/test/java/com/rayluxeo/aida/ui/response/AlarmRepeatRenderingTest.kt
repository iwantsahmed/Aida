package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Test

/**
 * An alarm that repeats and one that rings once are two different instructions, and Mira's reply
 * is the only place Abu finds out which one he gave. These cases pin the four shapes a repeat can
 * take, in both languages, against the same fixed clock the other renderer tests use.
 */
class AlarmRepeatRenderingTest {

    private val renderer = testRenderer()

    private val halfSix = testInstant(TEST_TOMORROW, 6, 30)

    private fun speak(response: MiraResponse, language: SpokenLanguage) =
        renderer.speak(response, language)

    private fun display(response: MiraResponse, language: SpokenLanguage) =
        renderer.display(response, language)

    private fun alarm(days: Set<DayOfWeek>, label: String = "Wake up") =
        MiraResponse.AlarmSet(label, halfSix, days)

    @Test
    fun `an alarm with no repeat names the single moment`() {
        val response = alarm(emptySet())

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm set — Wake up, tomorrow at 6:30 am.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm: Wake up\nWhen: tomorrow at 6:30 am")
    }

    @Test
    fun `an alarm with no repeat names the single moment in Bengali`() {
        val response = alarm(emptySet(), label = "ওষুধ")

        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("আগামীকাল সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি — ওষুধ।")
        assertThat(display(response, SpokenLanguage.BENGALI))
            .isEqualTo("অ্যালার্ম: ওষুধ\nসময়: আগামীকাল সকাল ৬টা ৩০ মিনিটে")
    }

    @Test
    fun `all seven days are one idea, not seven names`() {
        val response = alarm(DayOfWeek.entries.toSet())

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm set — Wake up, 6:30 am, every day.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm: Wake up\nWhen: 6:30 am\nRepeats: every day")
    }

    @Test
    fun `all seven days are one idea in Bengali too`() {
        val response = alarm(DayOfWeek.entries.toSet(), label = "ওষুধ")

        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("প্রতিদিন সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি — ওষুধ।")
        assertThat(display(response, SpokenLanguage.BENGALI))
            .isEqualTo("অ্যালার্ম: ওষুধ\nসময়: সকাল ৬টা ৩০ মিনিটে\nপুনরাবৃত্তি: প্রতিদিন")
    }

    @Test
    fun `Monday to Friday is said the way Abu asks for it`() {
        val response = alarm(WEEKDAY_SET)

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm set — Wake up, 6:30 am, every weekday.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm: Wake up\nWhen: 6:30 am\nRepeats: every weekday")
    }

    @Test
    fun `Monday to Friday is one idea in Bengali too`() {
        val response = alarm(WEEKDAY_SET, label = "ওষুধ")

        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("প্রতি কর্মদিবসে সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি — ওষুধ।")
        assertThat(display(response, SpokenLanguage.BENGALI))
            .isEqualTo("অ্যালার্ম: ওষুধ\nসময়: সকাল ৬টা ৩০ মিনিটে\nপুনরাবৃত্তি: প্রতি কর্মদিবসে")
    }

    /** Built out of week order on purpose: what Mira says must not follow how the set was made. */
    @Test
    fun `any other set is named in week order`() {
        val response = alarm(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm set — Wake up, 6:30 am, Monday, Wednesday and Saturday.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm: Wake up\nWhen: 6:30 am\nRepeats: Monday, Wednesday and Saturday")
    }

    @Test
    fun `any other set is named in week order in Bengali`() {
        val response = alarm(
            setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            label = "ওষুধ",
        )

        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("সোমবার, বুধবার ও শনিবার সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি — ওষুধ।")
        assertThat(display(response, SpokenLanguage.BENGALI)).isEqualTo(
            "অ্যালার্ম: ওষুধ\nসময়: সকাল ৬টা ৩০ মিনিটে\nপুনরাবৃত্তি: সোমবার, বুধবার ও শনিবার"
        )
    }

    /** One day over the working week is not the working week, and must not be read as it. */
    @Test
    fun `Monday to Saturday is listed rather than called the weekdays`() {
        val response = alarm(WEEKDAY_SET + DayOfWeek.SATURDAY)

        assertThat(speak(response, SpokenLanguage.ENGLISH)).isEqualTo(
            "Alarm set — Wake up, 6:30 am, " +
                "Monday, Tuesday, Wednesday, Thursday, Friday and Saturday."
        )
        assertThat(speak(response, SpokenLanguage.BENGALI)).doesNotContain("কর্মদিবস")
    }

    @Test
    fun `an unlabelled repeating alarm still reads its repeat back`() {
        val response = alarm(DayOfWeek.entries.toSet(), label = "  ")

        assertThat(speak(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm set for 6:30 am, every day.")
        assertThat(display(response, SpokenLanguage.ENGLISH))
            .isEqualTo("Alarm set\nWhen: 6:30 am\nRepeats: every day")
        assertThat(speak(response, SpokenLanguage.BENGALI))
            .isEqualTo("প্রতিদিন সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি।")
    }

    /**
     * "Wake me up at seven" names no subject the way a reminder always does, so a blank label now
     * reaches the renderer in earnest. The one thing it must never produce is a heading with
     * nothing after it — "Alarm: " is worse than no heading, because it reads as a lost title.
     */
    @Test
    fun `an alarm Abu never named never leaves a heading dangling`() {
        listOf("", "   ", "\n").forEach { blank ->
            DAY_SHAPES.forEach { days ->
                SpokenLanguage.entries.forEach { language ->
                    val spoken = speak(alarm(days, blank), language)
                    val shown = display(alarm(days, blank), language)

                    assertThat(spoken).isNotEmpty()
                    assertThat(spoken).isEqualTo(spoken.trim())
                    shown.split("\n").forEach { line ->
                        assertThat(line).isNotEmpty()
                        assertThat(line).isEqualTo(line.trimEnd())
                    }
                }
            }
        }

        assertThat(display(alarm(emptySet(), "  "), SpokenLanguage.ENGLISH))
            .startsWith("Alarm set\n")
        assertThat(display(alarm(emptySet(), "  "), SpokenLanguage.BENGALI))
            .startsWith("অ্যালার্ম দিয়েছি\n")
    }

    @Test
    fun `a Bengali repeat carries no Latin digits`() {
        DAY_SHAPES.forEach { days ->
            val spoken = speak(alarm(days, label = "ওষুধ"), SpokenLanguage.BENGALI)

            assertThat(spoken.none { it in '0'..'9' }).isTrue()
        }
    }

    /**
     * The decision is shared so that the Alarms screen's own row cannot drift from Mira's reply.
     * This stands in for that second caller: different words, same four shapes, same week order.
     */
    @Test
    fun `a caller with its own words gets the same four shapes`() {
        assertThat(repeatPhrase(emptySet(), ChipWording)).isNull()
        assertThat(repeatPhrase(DayOfWeek.entries.toSet(), ChipWording)).isEqualTo("Every day")
        assertThat(repeatPhrase(WEEKDAY_SET, ChipWording)).isEqualTo("Weekdays")
        assertThat(
            repeatPhrase(
                setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                ChipWording,
            )
        ).isEqualTo("Mon · Wed · Sat")
    }

    /** A row has no room for "Monday", and the shared decision does not ask it to find any. */
    private object ChipWording : RepeatWording {
        override val everyDay: String = "Every day"
        override val everyWeekday: String = "Weekdays"
        override fun day(day: DayOfWeek): String =
            day.getDisplayName(TextStyle.SHORT, Locale.US)
        override fun joinDays(names: List<String>): String = names.joinToString(" · ")
    }

    private companion object {
        val DAY_SHAPES: List<Set<DayOfWeek>> = listOf(
            emptySet(),
            DayOfWeek.entries.toSet(),
            WEEKDAY_SET,
            setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
    }
}
