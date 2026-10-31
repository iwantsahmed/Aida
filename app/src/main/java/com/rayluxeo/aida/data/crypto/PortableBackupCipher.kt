package com.rayluxeo.aida.data.crypto

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortableBackupCipher @Inject constructor() {

    fun encryptPayload(passphrase: String, plainPayloadBytes: ByteArray): AppResult<ByteArray> {
        if (passphrase.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Passphrase cannot be blank")
        }

        return try {
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE_BYTES).also { random.nextBytes(it) }
            val iv = ByteArray(IV_SIZE_BYTES).also { random.nextBytes(it) }

            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))

            val encryptedPayload = cipher.doFinal(plainPayloadBytes)

            val outStream = ByteArrayOutputStream()
            val dataOut = DataOutputStream(outStream)

            dataOut.write(MAGIC_HEADER)
            dataOut.writeShort(FORMAT_VERSION)
            dataOut.writeShort(salt.size)
            dataOut.write(salt)
            dataOut.writeShort(iv.size)
            dataOut.write(iv)
            dataOut.writeInt(encryptedPayload.size)
            dataOut.write(encryptedPayload)
            dataOut.flush()

            outStream.toByteArray().asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Failed to encrypt backup: ${e.message}")
        }
    }

    fun decryptPayload(passphrase: String, backupFileBytes: ByteArray): AppResult<ByteArray> {
        if (passphrase.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Passphrase cannot be blank")
        }

        return try {
            val dataIn = DataInputStream(ByteArrayInputStream(backupFileBytes))
            val header = ByteArray(MAGIC_HEADER.size)
            dataIn.readFully(header)

            if (!header.contentEquals(MAGIC_HEADER)) {
                return failure(MiraErrorCode.VALIDATION, "Invalid backup file format or magic header mismatch")
            }

            val version = dataIn.readShort().toInt()
            if (version > FORMAT_VERSION) {
                return failure(MiraErrorCode.VALIDATION, "Unsupported backup version: $version")
            }

            val saltSize = dataIn.readShort().toInt()
            val salt = ByteArray(saltSize)
            dataIn.readFully(salt)

            val ivSize = dataIn.readShort().toInt()
            val iv = ByteArray(ivSize)
            dataIn.readFully(iv)

            val payloadSize = dataIn.readInt()
            val encryptedPayload = ByteArray(payloadSize)
            dataIn.readFully(encryptedPayload)

            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))

            val decryptedPayload = cipher.doFinal(encryptedPayload)
            decryptedPayload.asSuccess()
        } catch (authErr: javax.crypto.AEADBadTagException) {
            failure(MiraErrorCode.AUTHENTICATION_FAILED, "Incorrect password or corrupted backup payload")
        } catch (badPad: javax.crypto.BadPaddingException) {
            failure(MiraErrorCode.AUTHENTICATION_FAILED, "Incorrect password")
        } catch (e: Exception) {
            failure(MiraErrorCode.VALIDATION, "Failed to decrypt backup: ${e.message}")
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private val MAGIC_HEADER = byteArrayOf('M'.code.toByte(), 'I'.code.toByte(), 'R'.code.toByte(), 'A'.code.toByte(), '_'.code.toByte(), 'B'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte())
        private const val FORMAT_VERSION = 1
        private const val SALT_SIZE_BYTES = 16
        private const val IV_SIZE_BYTES = 12
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val ITERATIONS = 100_000
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
