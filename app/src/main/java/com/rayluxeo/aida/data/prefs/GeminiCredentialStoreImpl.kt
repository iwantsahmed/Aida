package com.rayluxeo.aida.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.data.crypto.FieldCipher
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The Gemini key is sealed with the same Keystore-backed cipher as the vault before it reaches
 * disk, and is decoded only inside [apiKey]. It is never written to a log, never included in an
 * error detail, and never returned by any observation — [hasKey] answers presence alone.
 *
 * On disk it is `base64(iv):base64(ciphertext)`; the colon is safe because it is outside the
 * Base64 alphabet.
 */
@Singleton
class GeminiCredentialStoreImpl @Inject constructor(
    private val store: DataStore<Preferences>,
    private val cipher: FieldCipher,
    private val dispatchers: DispatcherProvider,
) : GeminiCredentialStore {

    override val hasKey: Flow<Boolean> = preferences().map { it[MiraPreferenceKeys.GEMINI_SEALED_KEY] != null }

    override suspend fun apiKey(): String? = withContext(dispatchers.io) {
        val stored = preferences().first()[MiraPreferenceKeys.GEMINI_SEALED_KEY] ?: return@withContext null
        val parts = stored.split(SEPARATOR)
        if (parts.size != 2) return@withContext null
        val iv = decodeOrNull(parts[0]) ?: return@withContext null
        val cipherText = decodeOrNull(parts[1]) ?: return@withContext null
        when (val plain = cipher.decrypt(cipherText, iv)) {
            is AppResult.Success -> plain.value
            is AppResult.Failure -> null
        }
    }

    /**
     * A key that cannot be sealed is not stored at all. Leaving a half-written or plaintext value
     * behind would be worse than Mira quietly staying on-device, which is her working default.
     */
    override suspend fun setApiKey(key: String?) {
        val cleaned = key?.trim()?.takeIf { it.isNotEmpty() }
        withContext(dispatchers.io) {
            val encrypted = cleaned?.let { cipher.encrypt(it) }
            if (encrypted is AppResult.Success) {
                val encoder = Base64.getEncoder()
                val value = encoder.encodeToString(encrypted.value.iv) +
                    SEPARATOR +
                    encoder.encodeToString(encrypted.value.cipherText)
                store.edit { it[MiraPreferenceKeys.GEMINI_SEALED_KEY] = value }
            } else {
                store.edit { it.remove(MiraPreferenceKeys.GEMINI_SEALED_KEY) }
            }
        }
    }

    private fun preferences(): Flow<Preferences> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    private fun decodeOrNull(value: String): ByteArray? = try {
        Base64.getDecoder().decode(value)
    } catch (malformed: IllegalArgumentException) {
        null
    }

    private companion object {
        const val SEPARATOR = ":"
    }
}
