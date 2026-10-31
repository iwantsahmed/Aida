package com.rayluxeo.aida.ui.clients

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The Clients phrasebook is the one place the screen gets its words, so a missed translation here
 * is not a red build — it is an English label sitting in a Bengali sheet, which nobody notices
 * until Abu does.
 *
 * Every assertion walks all of the members rather than sampling one: the fault this guards against
 * is a line pasted across from English and never translated, or a member added in one language and
 * forgotten in the other.
 */
class ClientsWordingTest {

    private val english = clientsWording(SpokenLanguage.ENGLISH)
    private val bengali = clientsWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishClientsWording)
        assertThat(bengali).isSameInstanceAs(BengaliClientsWording)
    }

    @Test
    fun `every word is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            words.everything().forEach { line ->
                assertThat(line).isNotEmpty()
            }
        }
    }

    @Test
    fun `no line was left in English on the Bengali side`() {
        english.everything().zip(bengali.everything()).forEach { (englishLine, bengaliLine) ->
            assertThat(bengaliLine).isNotEqualTo(englishLine)
        }
    }

    /**
     * Bengali counts in its own digits. A chip that reads "3 প্রকল্প" in the middle of a Bengali
     * sheet is the one slip that reads as broken even to someone who cannot read the words around
     * it, so the counted lines carry no Latin digit.
     */
    @Test
    fun `nothing Bengali is written with Latin digits`() {
        val counted = listOf(
            bengali.projectsCount(3),
            bengali.invoicesCount(12),
        )

        counted.forEach { line ->
            assertThat(line.none { it in '0'..'9' }).isTrue()
            assertThat(line).isNotEmpty()
        }
    }

    /**
     * The name a client was given is data Abu typed, not a word to translate, so it survives into
     * both languages unchanged — in the undo notice and in the delete dialog alike.
     */
    @Test
    fun `a client's own name is placed, not translated`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.clientAdded(NAME)).contains(NAME)
            assertThat(words.deleteMessage(NAME)).contains(NAME)
        }
    }

    /** Every member the screen reads, with the same sample data on both sides. */
    private fun ClientsWording.everything(): List<String> = listOf(
        title,
        searchPlaceholder,
        addClient,
        noClients,
        companyDetail(COMPANY),
        emailDetail(EMAIL),
        phoneDetail(PHONE),
        projectsCount(3),
        invoicesCount(12),
        deleteClientAction,
        deleteTitle,
        deleteMessage(NAME),
        delete,
        cancel,
        undo,
        clientAdded(NAME),
        clientMovedToBin,
        newClient,
        nameLabel,
        namePlaceholder,
        companyNameLabel,
        emailLabel,
        phoneLabel,
        saveClient,
    )

    private companion object {
        const val NAME = "Ahmed Traders"
        const val COMPANY = "Acme Ltd"
        const val EMAIL = "abu@example.com"
        const val PHONE = "01700000000"
    }
}
