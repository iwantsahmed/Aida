package com.rayluxeo.aida.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * The plaintext that lives inside an encrypted `.mbak` file, before and after
 * [com.rayluxeo.aida.data.crypto.PortableBackupCipher] seals it.
 *
 * A Mira backup is a faithful byte-for-byte snapshot of the whole on-device state, not a
 * hand-serialised walk over fifty tables that could silently omit one of them: the Room database
 * file — checkpointed first so the single main file holds every committed row — and the DataStore
 * preferences file travel as base64 payloads inside this envelope, beside the versions needed to
 * decide, on restore, whether this phone can read them back.
 *
 * [miraDbVersion] is the load-bearing field. A snapshot taken on a newer schema than the running
 * build knows must be refused rather than written over Abu's live data, because Room's migrations
 * only ever run forward and there is no way to migrate a database backwards. [envelopeVersion]
 * guards this container's own shape and is independent of the Mira schema version.
 *
 * This type is pure Kotlin on purpose: it carries no Android or Room reference, so the
 * encode/decode round trip is exercised by a plain JUnit test without a device.
 */
@Serializable
data class BackupEnvelope(
    val envelopeVersion: Int,
    val miraDbVersion: Int,
    val appVersion: String,
    val createdAtEpochMillis: Long,
    val deviceModel: String,
    /** The database file name the payload was read from, e.g. `mira.db`. */
    val databaseName: String,
    val databaseBase64: String,
    /** Null when no preferences file existed at backup time (a fresh install on defaults). */
    val preferencesFileName: String?,
    val preferencesBase64: String?,
    val taskCount: Int,
    val noteCount: Int,
    val invoiceCount: Int,
) {

    /** The raw Room database bytes carried by this envelope. */
    fun databaseBytes(): ByteArray = Base64.getDecoder().decode(databaseBase64)

    /** The raw DataStore preferences bytes, or null when the backup carried none. */
    fun preferenceBytes(): ByteArray? = preferencesBase64?.let { Base64.getDecoder().decode(it) }

    companion object {

        /**
         * Bumped only when the shape of this envelope changes — never for a Mira schema bump,
         * which [miraDbVersion] already records. A reader refuses an envelope whose version is
         * higher than this.
         */
        const val ENVELOPE_VERSION = 1

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        /** Builds an envelope, base64-encoding the raw payloads so callers pass plain bytes. */
        fun create(
            miraDbVersion: Int,
            appVersion: String,
            createdAtEpochMillis: Long,
            deviceModel: String,
            databaseName: String,
            databaseBytes: ByteArray,
            preferencesFileName: String?,
            preferencesBytes: ByteArray?,
            taskCount: Int,
            noteCount: Int,
            invoiceCount: Int,
        ): BackupEnvelope = BackupEnvelope(
            envelopeVersion = ENVELOPE_VERSION,
            miraDbVersion = miraDbVersion,
            appVersion = appVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            deviceModel = deviceModel,
            databaseName = databaseName,
            databaseBase64 = Base64.getEncoder().encodeToString(databaseBytes),
            preferencesFileName = preferencesFileName,
            preferencesBase64 = preferencesBytes?.let { Base64.getEncoder().encodeToString(it) },
            taskCount = taskCount,
            noteCount = noteCount,
            invoiceCount = invoiceCount,
        )

        /** Serialises the envelope to the UTF-8 JSON bytes that the cipher then encrypts. */
        fun encode(envelope: BackupEnvelope): ByteArray =
            json.encodeToString(envelope).toByteArray(Charsets.UTF_8)

        /**
         * Parses the decrypted JSON bytes back into an envelope. Throws
         * [kotlinx.serialization.SerializationException] on anything that is not a well-formed
         * envelope, which the caller turns into a [com.rayluxeo.aida.core.common.MiraErrorCode]
         * rather than letting it escape.
         */
        fun decode(bytes: ByteArray): BackupEnvelope =
            json.decodeFromString(String(bytes, Charsets.UTF_8))
    }
}
