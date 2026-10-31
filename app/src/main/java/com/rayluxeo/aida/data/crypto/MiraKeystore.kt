package com.rayluxeo.aida.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The source of the vault's symmetric key. It is an interface only so that tests can run the
 * cipher against an ordinary JCE key — Robolectric has no AndroidKeyStore provider, and the
 * alternative would be leaving the encryption path untested.
 */
interface VaultKeyProvider {
    /** Returns the existing key, creating it on first use. Throws if the keystore is unusable. */
    fun secretKey(): SecretKey

    /**
     * Deletes the vault key, so every secret encrypted under it becomes permanently
     * undecryptable. Only the full local-data erase calls this; a fresh key mints on the next
     * vault write. A no-op if no key exists.
     */
    fun destroyKey()
}

/**
 * AES-256-GCM key held in the AndroidKeyStore, so the raw key material never enters Mira's
 * process memory or a backup.
 *
 * User authentication is deliberately NOT required on the key itself. The vault gate is a
 * BiometricPrompt at the UI layer; binding the key to biometrics would additionally break
 * background decryption and would destroy every stored secret the moment Abu re-enrolled a
 * fingerprint.
 */
@Singleton
class MiraKeystore @Inject constructor() : VaultKeyProvider {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    @Synchronized
    override fun secretKey(): SecretKey =
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: createKey()

    @Synchronized
    override fun destroyKey() {
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "mira.vault.aes256gcm.v1"
        const val KEY_BITS = 256
    }
}
