package com.rayluxeo.aida.domain.quran

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class QuranDomainTest {

    @Test
    fun `bundled pages contain authentic Arabic with diacritics, Bengali transliteration, and Bengali translation`() {
        for (pageNum in 1..10) {
            val page = QuranPageProvider.page(pageNum)
            assertThat(page.pageNumber).isEqualTo(pageNum)
            assertThat(page.ayahs).isNotEmpty()

            for (ayah in page.ayahs) {
                // Arabic characters in Unicode range \u0600..\u06FF
                val hasArabic = ayah.arabic.any { it in '\u0600'..'\u06FF' }
                assertThat(hasArabic).isTrue()

                // Arabic text must include harakat / diacritics (unless individual Muqatta'at letters)
                if (ayah.arabic.length > 3) {
                    val hasTashkeel = ayah.arabic.any { it in '\u064B'..'\u065F' || it == '\u0670' }
                    assertThat(hasTashkeel).isTrue()
                }

                // Bengali transliteration in Unicode range \u0980..\u09FF
                val hasBengaliTranslit = ayah.banglaTransliteration.any { it in '\u0980'..'\u09FF' }
                assertThat(hasBengaliTranslit).isTrue()

                // Bengali meaning in Unicode range \u0980..\u09FF
                val hasBengaliMeaning = ayah.banglaMeaning.any { it in '\u0980'..'\u09FF' }
                assertThat(hasBengaliMeaning).isTrue()
            }
        }
    }

    @Test
    fun `quran progress accurately tracks minimum 2 pages daily recitation target`() {
        val date = LocalDate.of(2026, 10, 29)
        val initial = QuranProgress(
            currentPage = 1,
            pagesReadToday = 0,
            dailyTarget = 2,
            lastReadDate = date,
        )

        assertThat(initial.dailyTarget).isEqualTo(2)
        assertThat(initial.pagesReadToday).isEqualTo(0)
        assertThat(initial.targetMet).isFalse()
        assertThat(initial.remainingPagesToday).isEqualTo(2)
        assertThat(initial.progressRatio).isEqualTo(0f)

        val onePageRead = initial.copy(pagesReadToday = 1)
        assertThat(onePageRead.targetMet).isFalse()
        assertThat(onePageRead.remainingPagesToday).isEqualTo(1)
        assertThat(onePageRead.progressRatio).isEqualTo(0.5f)

        val twoPagesRead = initial.copy(pagesReadToday = 2)
        assertThat(twoPagesRead.targetMet).isTrue()
        assertThat(twoPagesRead.remainingPagesToday).isEqualTo(0)
        assertThat(twoPagesRead.progressRatio).isEqualTo(1.0f)
    }

    @Test
    fun `page mapping gracefully supports full 604 Madani Mushaf page range with serial continuity`() {
        val page1 = QuranPageProvider.page(1)
        assertThat(page1.pageNumber).isEqualTo(1)
        assertThat(page1.surahNumber).isEqualTo(1)
        assertThat(page1.surahNameBangla).contains("আল-ফাতিহা")
        assertThat(page1.ayahs.first().numberInSurah).isEqualTo(1)

        val page2 = QuranPageProvider.page(2)
        assertThat(page2.pageNumber).isEqualTo(2)
        assertThat(page2.surahNumber).isEqualTo(2)
        assertThat(page2.surahNameBangla).contains("আল-বাক্বারাহ")
        assertThat(page2.ayahs.first().numberInSurah).isEqualTo(1)
        assertThat(page2.ayahs.last().numberInSurah).isEqualTo(5)

        val page3 = QuranPageProvider.page(3)
        assertThat(page3.pageNumber).isEqualTo(3)
        assertThat(page3.surahNumber).isEqualTo(2)
        assertThat(page3.ayahs.first().numberInSurah).isEqualTo(6)
        assertThat(page3.ayahs.last().numberInSurah).isEqualTo(16)

        val page4 = QuranPageProvider.page(4)
        assertThat(page4.pageNumber).isEqualTo(4)
        assertThat(page4.surahNumber).isEqualTo(2)
        assertThat(page4.ayahs.first().numberInSurah).isEqualTo(17)

        val page5 = QuranPageProvider.page(5)
        assertThat(page5.pageNumber).isEqualTo(5)
        assertThat(page5.surahNumber).isEqualTo(2)
        assertThat(page5.ayahs.first().numberInSurah).isEqualTo(25)

        val page604 = QuranPageProvider.page(604)
        assertThat(page604.pageNumber).isEqualTo(604)
        assertThat(page604.juzNumber).isEqualTo(30)
        // Page 604 contains An-Nas (Surah 114)
        assertThat(page604.ayahs.any { it.banglaMeaning.contains("মানবজাতির প্রতিপালকের") }).isTrue()
    }
}
