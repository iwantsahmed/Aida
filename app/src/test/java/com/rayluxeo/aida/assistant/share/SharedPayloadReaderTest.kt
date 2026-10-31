package com.rayluxeo.aida.assistant.share

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.assistant.SharedPayload
import org.junit.Test

/**
 * The decisions a share turns on, separated from the `Intent` that carries them.
 *
 * `IntentSharedPayloadReader` itself is not covered here and cannot be: an `Intent`, a `Uri` and a
 * `ContentResolver` are all framework classes that throw under a local unit test. What it does is
 * pull extras out and hand them to [SharedText] and [SharedItems], which is why those two hold
 * every judgement worth arguing with. Reading a real share end to end — a screenshot from the
 * system UI, a PDF from Files, a selection from Samsung Internet — is a device check, and the
 * report says so.
 */
class SharedPayloadReaderTest {

    // ------------------------------------------------------------------ text and links

    @Test
    fun `nothing at all is an empty share rather than empty text`() {
        assertThat(SharedText.classify(text = null, subject = null))
            .isEqualTo(SharedIntake.Unsupported(UnsupportedShare.EMPTY_TEXT))
    }

    @Test
    fun `whitespace is not content`() {
        assertThat(SharedText.classify(text = "   \n  ", subject = "Subject"))
            .isEqualTo(SharedIntake.Unsupported(UnsupportedShare.EMPTY_TEXT))
    }

    @Test
    fun `a sentence is text and keeps its own words`() {
        val intake = SharedText.classify(text = "  Ahmed ke kal call koro  ", subject = null)

        assertThat(intake).isEqualTo(SharedIntake.Ready(SharedPayload.Text("Ahmed ke kal call koro")))
    }

    @Test
    fun `Bengali script survives untouched`() {
        val intake = SharedText.classify(text = "কাল সকাল ১০টায় মিটিং", subject = null)

        assertThat(intake).isEqualTo(SharedIntake.Ready(SharedPayload.Text("কাল সকাল ১০টায় মিটিং")))
    }

    @Test
    fun `a bare address is a link with no title`() {
        val intake = SharedText.classify(text = "https://developer.android.com/guide", subject = null)

        assertThat(intake).isEqualTo(
            SharedIntake.Ready(SharedPayload.Link("https://developer.android.com/guide")),
        )
    }

    @Test
    fun `the words wrapped round an address become its title`() {
        val intake = SharedText.classify(
            text = "Assistants on Android\nhttps://developer.android.com/guide",
            subject = null,
        )

        assertThat(intake).isEqualTo(
            SharedIntake.Ready(
                SharedPayload.Link(
                    url = "https://developer.android.com/guide",
                    title = "Assistants on Android",
                ),
            ),
        )
    }

    @Test
    fun `the sender's own subject wins over the surrounding words`() {
        val intake = SharedText.classify(
            text = "shared from the app\nhttps://example.com/a",
            subject = "Quarterly figures",
        )

        assertThat((intake as SharedIntake.Ready).payload)
            .isEqualTo(SharedPayload.Link("https://example.com/a", "Quarterly figures"))
    }

    @Test
    fun `a subject that only repeats the address is not a title`() {
        val intake = SharedText.classify(
            text = "https://example.com/a",
            subject = "https://example.com/a",
        )

        assertThat((intake as SharedIntake.Ready).payload)
            .isEqualTo(SharedPayload.Link("https://example.com/a"))
    }

    @Test
    fun `an address with no scheme still reads as a link`() {
        val intake = SharedText.classify(text = "www.example.com/a", subject = null)

        assertThat((intake as SharedIntake.Ready).payload)
            .isEqualTo(SharedPayload.Link("www.example.com/a"))
    }

    @Test
    fun `the full stop that ended the sentence is not part of the address`() {
        assertThat(SharedText.soleLinkIn("Read https://example.com/a. It is short."))
            .isEqualTo("https://example.com/a")
    }

    @Test
    fun `two addresses make it prose, not a link`() {
        val intake = SharedText.classify(
            text = "https://example.com/a and https://example.com/b",
            subject = null,
        )

        assertThat(intake).isInstanceOf(SharedIntake.Ready::class.java)
        assertThat((intake as SharedIntake.Ready).payload).isInstanceOf(SharedPayload.Text::class.java)
    }

    @Test
    fun `a paragraph with a link in it is still the paragraph`() {
        val paragraph = "x".repeat(400) + " https://example.com/a"

        assertThat(SharedText.soleLinkIn(paragraph)).isNull()
    }

    // ------------------------------------------------------------------ the host of a link

    @Test
    fun `a host drops the scheme, the path and the www`() {
        assertThat(SharedText.hostOf("https://www.example.com/a/b?c=d#e")).isEqualTo("example.com")
    }

    @Test
    fun `a host drops a port and any credentials in front of it`() {
        assertThat(SharedText.hostOf("http://abu@example.com:8080/a")).isEqualTo("example.com")
    }

    @Test
    fun `a schemeless address still yields its host`() {
        assertThat(SharedText.hostOf("www.example.com/a")).isEqualTo("example.com")
    }

    // ------------------------------------------------------------------ files and pictures

    @Test
    fun `no items at all is an empty share`() {
        assertThat(SharedItems.judge(emptyList()))
            .isEqualTo(ItemVerdict.Unusable(UnsupportedShare.NOTHING_SHARED, null))
    }

    @Test
    fun `items whose type the resolver would not give up are unreadable, not unsupported`() {
        assertThat(SharedItems.judge(listOf("", "")))
            .isEqualTo(ItemVerdict.Unusable(UnsupportedShare.UNREADABLE_ITEM, null))
    }

    @Test
    fun `a kind Mira has nothing to say about is refused by name`() {
        assertThat(SharedItems.judge(listOf("video/mp4")))
            .isEqualTo(ItemVerdict.Unusable(UnsupportedShare.UNSUPPORTED_TYPE, "video/mp4"))
    }

    @Test
    fun `a screenshot is taken whole`() {
        assertThat(SharedItems.judge(listOf("image/png")))
            .isEqualTo(ItemVerdict.Usable("image/png", listOf(0)))
    }

    @Test
    fun `pictures of different types share a family`() {
        assertThat(SharedItems.judge(listOf("image/png", "image/jpeg")))
            .isEqualTo(ItemVerdict.Usable("image/*", listOf(0, 1)))
    }

    @Test
    fun `a picture and a document agree on nothing narrower than a wildcard`() {
        assertThat(SharedItems.judge(listOf("image/png", "application/pdf")))
            .isEqualTo(ItemVerdict.Usable(SharedItems.WILDCARD, listOf(0, 1)))
    }

    @Test
    fun `the items she cannot use are dropped rather than failing the whole share`() {
        assertThat(SharedItems.judge(listOf("video/mp4", "image/png", "application/zip")))
            .isEqualTo(ItemVerdict.Usable("image/png", listOf(1)))
    }

    @Test
    fun `a charset on the end of a type does not hide it`() {
        assertThat(SharedItems.isSupported("text/plain; charset=utf-8")).isTrue()
        assertThat(SharedItems.judge(listOf("text/plain; charset=utf-8")))
            .isEqualTo(ItemVerdict.Usable("text/plain", listOf(0)))
    }

    @Test
    fun `the document types are an explicit list, not the whole application family`() {
        assertThat(SharedItems.isSupported("application/pdf")).isTrue()
        assertThat(
            SharedItems.isSupported(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        ).isTrue()
        assertThat(SharedItems.isSupported("application/zip")).isFalse()
        assertThat(SharedItems.isSupported("application/vnd.android.package-archive")).isFalse()
        assertThat(SharedItems.isSupported("audio/mpeg")).isFalse()
    }
}
