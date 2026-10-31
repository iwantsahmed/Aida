package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.PlaceTrigger
import org.junit.Test

/**
 * The parser reads the text the normalizer hands over, so every sentence here is already
 * lowercase and folded — "Office e pouchhale" arrives spelled exactly as it is written below.
 *
 * The two grammars are asserted side by side on purpose. English names the place after the
 * marker and Bengali names it before, and a change that quietly serves only one of them is what
 * these tests exist to catch.
 */
class PlaceExpressionParserTest {

    private fun parse(text: String): ParsedPlace? = PlaceExpressionParser.parse(text)

    // ---------------------------------------------------------------- English: place follows

    @Test
    fun `reads the English arrival markers`() {
        for (marker in listOf(
            "when i get near",
            "when i get to",
            "when i reach",
            "when i arrive at",
            "when i arrive",
            "when i am near",
            "when im near",
            "when i m near",
        )) {
            val parsed = requireNotNull(parse("remind me to buy ink $marker the office")) { marker }
            assertThat(parsed.label).isEqualTo("the office")
            assertThat(parsed.trigger).isEqualTo(PlaceTrigger.ENTER)
            assertThat(parsed.remainder).isEqualTo("remind me to buy ink")
        }
    }

    @Test
    fun `reads the English departure markers`() {
        for (marker in listOf("when i leave", "when i get away from")) {
            val parsed = requireNotNull(parse("remind me to lock the door $marker the office")) { marker }
            assertThat(parsed.label).isEqualTo("the office")
            assertThat(parsed.trigger).isEqualTo(PlaceTrigger.EXIT)
            assertThat(parsed.remainder).isEqualTo("remind me to lock the door")
        }
    }

    /** What was claimed and what was left are the whole contract: the remainder becomes a title. */
    @Test
    fun `hands back the sentence with the place phrase taken out`() {
        val parsed = requireNotNull(parse("remind me to buy printer ink when i get near the office"))
        assertThat(parsed.matched).isEqualTo("when i get near the office")
        assertThat(parsed.remainder).isEqualTo("remind me to buy printer ink")
    }

    // -------------------------------------------------------------- Banglish: place precedes

    @Test
    fun `reads the Banglish arrival markers`() {
        for (marker in listOf("e pouchhale", "e pouchale", "e gele", "e giye", "er kache gele")) {
            val parsed = requireNotNull(parse("office $marker printer ink kinte remind")) { marker }
            assertThat(parsed.label).isEqualTo("office")
            assertThat(parsed.trigger).isEqualTo(PlaceTrigger.ENTER)
            assertThat(parsed.remainder).isEqualTo("printer ink kinte remind")
        }
    }

    @Test
    fun `reads the Banglish departure markers`() {
        for (marker in listOf("theke berole", "theke ber hole", "chere")) {
            val parsed = requireNotNull(parse("office $marker ahmed to call remind")) { marker }
            assertThat(parsed.label).isEqualTo("office")
            assertThat(parsed.trigger).isEqualTo(PlaceTrigger.EXIT)
            assertThat(parsed.remainder).isEqualTo("ahmed to call remind")
        }
    }

    // ---------------------------------------------------------- Bengali script: place precedes

    /**
     * Script glues the case ending onto the place — "office e" is written "অফিসে" — so there is
     * no separate particle to take out and the place is one token either way.
     */
    @Test
    fun `reads the Bengali arrival markers`() {
        val arriving = requireNotNull(parse("অফিসে পৌঁছালে printer ink কিনতে remind"))
        assertThat(arriving.label).isEqualTo("অফিসে")
        assertThat(arriving.trigger).isEqualTo(PlaceTrigger.ENTER)
        assertThat(arriving.remainder).isEqualTo("printer ink কিনতে remind")

        assertThat(parse("অফিসে গেলে printer ink কিনতে remind")?.label).isEqualTo("অফিসে")
        assertThat(parse("অফিসের কাছে গেলে printer ink কিনতে remind")?.label).isEqualTo("অফিসের")
    }

    @Test
    fun `reads the Bengali departure markers`() {
        val leaving = requireNotNull(parse("অফিস থেকে বেরোলে ahmed to call remind"))
        assertThat(leaving.label).isEqualTo("অফিস")
        assertThat(leaving.trigger).isEqualTo(PlaceTrigger.EXIT)
        assertThat(leaving.matched).isEqualTo("অফিস থেকে বেরোলে")

        assertThat(parse("অফিস ছাড়লে ahmed to call remind")?.trigger).isEqualTo(PlaceTrigger.EXIT)
    }

    /** The particle typed as its own word goes with the place, not into the reminder. */
    @Test
    fun `takes a case ending Abu typed separately out with the marker`() {
        val parsed = requireNotNull(parse("office e pouchhale printer ink kinte remind"))
        assertThat(parsed.matched).isEqualTo("office e pouchhale")
        assertThat(parsed.remainder).isEqualTo("printer ink kinte remind")
    }

    // ------------------------------------------------------------------------- the bare marker

    @Test
    fun `reads a bare near when the sentence still says something`() {
        val parsed = requireNotNull(parse("remind me to buy milk near the shop"))
        assertThat(parsed.label).isEqualTo("the shop")
        assertThat(parsed.trigger).isEqualTo(PlaceTrigger.ENTER)
        assertThat(parsed.remainder).isEqualTo("remind me to buy milk")
    }

    /**
     * A place reminder Mira invents out of an ordinary sentence never fires, and nothing tells
     * Abu it did not. Both guards are on the bare marker for that reason.
     */
    @Test
    fun `refuses a bare near that names no place or no reminder`() {
        assertThat(parse("remind me tomorrow at 10 that the deadline is near")).isNull()
        assertThat(parse("near the office")).isNull()
    }

    @Test
    fun `lets a longer marker claim a near it already accounts for`() {
        val parsed = requireNotNull(parse("remind me to buy ink when i get near the office"))
        assertThat(parsed.matched).isEqualTo("when i get near the office")
    }

    // ------------------------------------------------------------------------------- refusals

    /** A word that points at somewhere already in mind names no place Mira can resolve. */
    @Test
    fun `refuses a place Abu only pointed at`() {
        assertThat(parse("remind me to buy ink when i get there")).isNull()
        assertThat(parse("remind me to buy ink when i get to there")).isNull()
        assertThat(parse("ekhane pouchhale printer ink kinte remind")).isNull()
    }

    @Test
    fun `refuses a marker with no place beside it`() {
        assertThat(parse("pouchhale printer ink kinte remind")).isNull()
        assertThat(parse("remind me to buy ink when i reach")).isNull()
    }

    @Test
    fun `refuses a sentence that names no place at all`() {
        assertThat(parse("remind me to call ahmed at 5 pm")).isNull()
        assertThat(parse("")).isNull()
    }
}
