package com.rayluxeo.aida.ui.settings

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import java.time.Instant
import org.junit.Test

/**
 * The Places phrasebook, held to the two rules it exists to keep.
 *
 * Abu reads one of these languages, so a line that was only ever written in the other is not a
 * cosmetic gap — it is Mira telling him something about his own phone in a language he did not
 * choose. And a refusal is only worth showing if it either explains itself or offers the thing
 * that answers it, which is why the button and the remedy behind it are checked together.
 */
class PlacesWordingTest {

    @Test
    fun `every refusal has a sentence in both languages`() {
        SpokenLanguage.entries.forEach { language ->
            val words = placesWording(language)
            PlaceWatchRefusal.entries.forEach { refusal ->
                assertThat(words.refusal(refusal)).isNotEmpty()
            }
        }
    }

    @Test
    fun `every problem has a sentence in both languages`() {
        SpokenLanguage.entries.forEach { language ->
            val words = placesWording(language)
            PlacesProblem.entries.forEach { problem ->
                assertThat(words.problem(problem)).isNotEmpty()
            }
        }
    }

    @Test
    fun `a refusal is offered a button exactly when it has a remedy`() {
        SpokenLanguage.entries.forEach { language ->
            val words = placesWording(language)
            PlaceWatchRefusal.entries.forEach { refusal ->
                val label = words.refusalAction(refusal)
                if (refusal.remedy() == null) {
                    assertThat(label).isNull()
                } else {
                    assertThat(label).isNotEmpty()
                }
            }
        }
    }

    @Test
    fun `the two refusals nothing can answer offer no way out`() {
        assertThat(PlaceWatchRefusal.NO_PROVIDER.remedy()).isNull()
        assertThat(PlaceWatchRefusal.LIMIT_REACHED.remedy()).isNull()
    }

    @Test
    fun `a fix that did not arrive asks for a retry rather than for a settings screen`() {
        // Saying "this phone has no location provider" when the truth is that Abu was indoors
        // would send him looking for a setting that does not exist.
        assertThat(PlaceWatchRefusal.NO_FIX_YET.remedy()).isEqualTo(PlaceRefusalRemedy.RETRY)
        assertThat(PlaceWatchRefusal.NO_PROVIDER.remedy())
            .isNotEqualTo(PlaceWatchRefusal.NO_FIX_YET.remedy())
    }

    @Test
    fun `the two languages never share a line`() {
        // Bengali that was never written would pass every other test in this file by quietly
        // falling back to the English sentence.
        PlaceWatchRefusal.entries.forEach { refusal ->
            assertThat(BengaliPlacesWording.refusal(refusal))
                .isNotEqualTo(EnglishPlacesWording.refusal(refusal))
        }
        PlacesProblem.entries.forEach { problem ->
            assertThat(BengaliPlacesWording.problem(problem))
                .isNotEqualTo(EnglishPlacesWording.problem(problem))
        }
    }

    @Test
    fun `a row reads back its radius and its coordinate to four places`() {
        val detail = EnglishPlacesWording.placeDetail(office)

        // Four decimals is about eleven metres: enough to tell one street from the next, and not
        // so much that it reads as a precision the phone never had.
        assertThat(detail).isEqualTo("150 m · 23.7808, 90.4074")
    }

    @Test
    fun `a Bengali row carries no Latin digits`() {
        val detail = BengaliPlacesWording.placeDetail(office)

        assertThat(detail.none { it in '0'..'9' }).isTrue()
        assertThat(detail).contains("মি")
    }

    private val office = SavedPlace(
        id = 1L,
        label = "Office",
        labelKey = "office",
        latitude = 23.780753,
        longitude = 90.407448,
        radiusMeters = 150f,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
