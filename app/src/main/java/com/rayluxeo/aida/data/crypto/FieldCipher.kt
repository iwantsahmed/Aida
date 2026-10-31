package com.rayluxeo.aida.data.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One encrypted field. Not a data class: it holds byte arrays, and a generated `equals` comparing
 * them by identity would be a quietly wrong answer at every call site that asked.
 */
class SealedField(val cipherText: ByteArray, val iv: ByteArray)

/**
 * Encrypts single values with the vault key. Every record gets a fresh nonce — the keystore key is
 * created with randomised encryption required, so the IV comes from the provider and is stored
 * beside the ciphertext rather than derived from anything.
 */
@Singleton
class FieldCipher @Inject constructor(private val keys: VaultKeyProvider) {

    fun encrypt(plainText: String): AppResult<SealedField> = guarded("encrypt field") {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.secretKey())
        SealedField(
            cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            iv = cipher.iv,
        )
    }

    fun decrypt(cipherText: ByteArray, iv: ByteArray): AppResult<String> = guarded("decrypt field") {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    /**
     * A vault that crashes is a vault Abu cannot open. Key invalidation, a tampered record and a
     * missing keystore all come back as values; only programming errors are allowed through.
     */
    private inline fun <T> guarded(detail: String, block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (invalidated: KeyPermanentlyInvalidatedException) {
        failure(MiraErrorCode.AUTHENTICATION_FAILED, "$detail: vault key invalidated", invalidated)
    } catch (unrecoverable: UnrecoverableKeyException) {
        failure(MiraErrorCode.AUTHENTICATION_FAILED, "$detail: vault key unrecoverable", unrecoverable)
    } catch (badTag: AEADBadTagException) {
        failure(MiraErrorCode.AUTHENTICATION_FAILED, "$detail: record failed authentication", badTag)
    } catch (security: GeneralSecurityException) {
        failure(MiraErrorCode.STORAGE, detail, security)
    } catch (backend: ProviderException) {
        failure(MiraErrorCode.STORAGE, "$detail: keystore backend refused", backend)
    } catch (io: IOException) {
        failure(MiraErrorCode.STORAGE, "$detail: keystore unreadable", io)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
