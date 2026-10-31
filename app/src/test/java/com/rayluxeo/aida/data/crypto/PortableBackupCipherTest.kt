package com.rayluxeo.aida.data.crypto

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import org.junit.Test

class PortableBackupCipherTest {

    private val cipher = PortableBackupCipher()
    private val samplePayload = "{\"version\":1,\"data\":\"sample_mira_backup\"}".toByteArray(Charsets.UTF_8)
    private val passphrase = "StrongUserPassphrase#2026"

    @Test
    fun `encrypt and decrypt round trip succeeds with valid passphrase`() {
        val encryptedRes = cipher.encryptPayload(passphrase, samplePayload)
        assertThat(encryptedRes).isInstanceOf(AppResult.Success::class.java)

        val encryptedBytes = (encryptedRes as AppResult.Success).value
        val decryptedRes = cipher.decryptPayload(passphrase, encryptedBytes)

        assertThat(decryptedRes).isInstanceOf(AppResult.Success::class.java)
        val decryptedBytes = (decryptedRes as AppResult.Success).value
        assertThat(String(decryptedBytes, Charsets.UTF_8)).isEqualTo(String(samplePayload, Charsets.UTF_8))
    }

    @Test
    fun `decryption fails with wrong passphrase`() {
        val encryptedRes = cipher.encryptPayload(passphrase, samplePayload)
        val encryptedBytes = (encryptedRes as AppResult.Success).value

        val wrongDecrypted = cipher.decryptPayload("WrongPassphrase#123", encryptedBytes)
        assertThat(wrongDecrypted).isInstanceOf(AppResult.Failure::class.java)

        val error = (wrongDecrypted as AppResult.Failure).error
        assertThat(error.code).isEqualTo(MiraErrorCode.AUTHENTICATION_FAILED)
    }

    @Test
    fun `decryption fails with corrupted header or tampered bytes`() {
        val encryptedRes = cipher.encryptPayload(passphrase, samplePayload)
        val encryptedBytes = (encryptedRes as AppResult.Success).value

        // Tamper payload byte
        encryptedBytes[encryptedBytes.size - 1] = (encryptedBytes[encryptedBytes.size - 1] + 1).toByte()

        val tamperedRes = cipher.decryptPayload(passphrase, encryptedBytes)
        assertThat(tamperedRes).isInstanceOf(AppResult.Failure::class.java)
    }
}
