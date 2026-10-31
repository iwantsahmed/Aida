package com.rayluxeo.aida.data.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Test

/**
 * The envelope is the plaintext a backup is sealed around, so its encode/decode must be exact:
 * a byte lost here is a row lost on restore. Plain JUnit, no Hilt, no Android — the type is pure
 * Kotlin for exactly this reason (AGENTS.md §7.4).
 */
class BackupEnvelopeTest {

    private val dbBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2, -128, 127)
    private val prefsBytes = "mira_settings_blob".toByteArray(Charsets.UTF_8)

    @Test
    fun `encode then decode restores every field and both payloads exactly`() {
        val envelope = BackupEnvelope.create(
            miraDbVersion = 8,
            appVersion = "1.0.0",
            createdAtEpochMillis = 1_760_000_000_000L,
            deviceModel = "SM-S928B",
            databaseName = "mira.db",
            databaseBytes = dbBytes,
            preferencesFileName = "mira_settings.preferences_pb",
            preferencesBytes = prefsBytes,
            taskCount = 12,
            noteCount = 5,
            invoiceCount = 3,
        )

        val decoded = BackupEnvelope.decode(BackupEnvelope.encode(envelope))

        assertThat(decoded).isEqualTo(envelope)
        assertThat(decoded.envelopeVersion).isEqualTo(BackupEnvelope.ENVELOPE_VERSION)
        assertThat(decoded.miraDbVersion).isEqualTo(8)
        assertThat(decoded.deviceModel).isEqualTo("SM-S928B")
        assertThat(decoded.taskCount).isEqualTo(12)
        assertThat(decoded.databaseBytes()).isEqualTo(dbBytes)
        assertThat(decoded.preferenceBytes()).isEqualTo(prefsBytes)
    }

    @Test
    fun `a backup with no preferences file round trips as null, not empty bytes`() {
        val envelope = BackupEnvelope.create(
            miraDbVersion = 8,
            appVersion = "1.0.0",
            createdAtEpochMillis = 1_760_000_000_000L,
            deviceModel = "Pixel 6",
            databaseName = "mira.db",
            databaseBytes = dbBytes,
            preferencesFileName = null,
            preferencesBytes = null,
            taskCount = 0,
            noteCount = 0,
            invoiceCount = 0,
        )

        val decoded = BackupEnvelope.decode(BackupEnvelope.encode(envelope))

        assertThat(decoded.preferencesFileName).isNull()
        assertThat(decoded.preferencesBase64).isNull()
        assertThat(decoded.preferenceBytes()).isNull()
        assertThat(decoded.databaseBytes()).isEqualTo(dbBytes)
    }

    @Test
    fun `decoding something that is not an envelope throws rather than returning a shell`() {
        val garbage = "this is not a backup envelope".toByteArray(Charsets.UTF_8)

        try {
            BackupEnvelope.decode(garbage)
            throw AssertionError("expected decode to throw on malformed input")
        } catch (expected: SerializationException) {
            // The repository turns this into MiraErrorCode.VALIDATION; here we only assert it is
            // surfaced rather than swallowed into a half-built envelope.
        }
    }
}
