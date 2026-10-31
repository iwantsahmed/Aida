package com.rayluxeo.aida.domain.salah

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class WaqtHadithTest {

    @Test
    fun `all 5 obligatory prayers have authentic hadith in arabic and bangla`() {
        val date = LocalDate.of(2026, 10, 29)
        val prayers = listOf(
            Prayer.FAJR,
            Prayer.DHUHR,
            Prayer.ASR,
            Prayer.MAGHRIB,
            Prayer.ISHA,
        )

        for (prayer in prayers) {
            val hadith = WaqtHadithProvider.forPrayer(prayer, date)

            assertThat(hadith.prayer).isEqualTo(prayer)
            assertThat(hadith.arabic.trim()).isNotEmpty()
            assertThat(hadith.bangla.trim()).isNotEmpty()
            assertThat(hadith.reference.trim()).isNotEmpty()

            // Verify Arabic text contains Arabic script
            val hasArabic = hadith.arabic.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
            assertThat(hasArabic).isTrue()

            // Verify Bangla text contains Bengali script
            val hasBengali = hadith.bangla.any { it in '\u0980'..'\u09FF' }
            assertThat(hasBengali).isTrue()
        }
    }

    @Test
    fun `hadith rotates across different days of the year`() {
        val day1 = LocalDate.of(2026, 1, 1)
        val day2 = LocalDate.of(2026, 1, 2)

        val hadithDay1 = WaqtHadithProvider.forPrayer(Prayer.FAJR, day1)
        val hadithDay2 = WaqtHadithProvider.forPrayer(Prayer.FAJR, day2)

        assertThat(hadithDay1).isNotNull()
        assertThat(hadithDay2).isNotNull()
        // Day 1 and Day 2 should rotate to different Hadiths in the list
        assertThat(hadithDay1.arabic).isNotEqualTo(hadithDay2.arabic)
    }

    @Test
    fun `tahajjud voluntary waqt also returns valid hadith`() {
        val hadith = WaqtHadithProvider.forPrayer(Prayer.TAHAJJUD, LocalDate.now())
        assertThat(hadith.prayer).isEqualTo(Prayer.TAHAJJUD)
        assertThat(hadith.arabic).isNotEmpty()
        assertThat(hadith.bangla).isNotEmpty()
        assertThat(hadith.reference).isNotEmpty()
    }

    @Test
    fun `every waqt has a rich collection of at least 20 authentic hadiths`() {
        for (prayer in Prayer.entries) {
            val list = WaqtHadithProvider.allForPrayer(prayer)
            assertThat(list.size).isAtLeast(20)
            for (hadith in list) {
                assertThat(hadith.prayer).isEqualTo(prayer)
                assertThat(hadith.arabic).isNotEmpty()
                assertThat(hadith.bangla).isNotEmpty()
                assertThat(hadith.reference).isNotEmpty()
            }
        }
    }

    @Test
    fun `different waqts on the same day never share the same hadith`() {
        val date = LocalDate.of(2026, 10, 31)
        val hadithsOnDay = Prayer.entries.map { WaqtHadithProvider.forPrayer(it, date).arabic }
        // Each waqt on the same day must show a unique hadith
        val uniqueHadiths = hadithsOnDay.toSet()
        assertThat(uniqueHadiths.size).isEqualTo(Prayer.entries.size)
    }

    @Test
    fun `offset allows non-repeating cycle through all hadiths`() {
        val prayer = Prayer.DHUHR
        val total = WaqtHadithProvider.allForPrayer(prayer).size
        val date = LocalDate.of(2026, 10, 31)

        val cycled = (0 until total).map { offset ->
            WaqtHadithProvider.forPrayer(prayer, date, offset).arabic
        }.toSet()

        assertThat(cycled.size).isEqualTo(total)
    }
}
