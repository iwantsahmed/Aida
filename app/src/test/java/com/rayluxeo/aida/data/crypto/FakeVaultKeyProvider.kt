package com.rayluxeo.aida.data.crypto

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * An ordinary JCE AES-256 key. Robolectric ships no AndroidKeyStore provider, so the real
 * [MiraKeystore] cannot run here; swapping the key source is what keeps [FieldCipher] itself —
 * the part with the interesting failure modes — under test.
 *
 * The key is generated once per instance, so a single instance round-trips and separate
 * instances do not.
 */
class FakeVaultKeyProvider : VaultKeyProvider {

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** Set when [destroyKey] has been called, so a test can assert the erase reached the key. */
    var destroyed: Boolean = false
        private set

    override fun secretKey(): SecretKey = key

    override fun destroyKey() {
        destroyed = true
    }
}
