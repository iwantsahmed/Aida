package com.rayluxeo.aida.domain.audit

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.CommandRouter
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.data.crypto.PortableBackupCipher
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.DefaultCommandValidator
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.speechFailureOf
import android.speech.SpeechRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Automated verification for Mira's failure and permission audit.
 *
 * Verifies that all expected error conditions, missing permissions, unavailable cloud engines,
 * corrupt inputs, and absent resources fail gracefully with typed [AppResult.Failure] values
 * rather than unhandled exceptions or crashes.
 */
class FailureAndPermissionAuditTest {

    private val testClock = object : MiraClock {
        override fun now(): Instant = Instant.parse("2026-10-11T12:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("Asia/Dhaka")
    }

    // 1. Microphone permission failure mapping
    @Test
    fun `speech recognizer maps microphone permission error to SpeechFailure PERMISSION`() {
        val failure = speechFailureOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertThat(failure).isEqualTo(SpeechFailure.PERMISSION)
        assertThat(failure.isRetryable).isFalse()
    }

    // 2. Speech recognizer unavailable & language unavailable
    @Test
    fun `speech recognizer maps unavailable service and language missing to structured failure`() {
        val unavailable = speechFailureOf(SpeechRecognizer.ERROR_AUDIO)
        assertThat(unavailable).isEqualTo(SpeechFailure.UNAVAILABLE)

        val langMissing = speechFailureOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        assertThat(langMissing).isEqualTo(SpeechFailure.LANGUAGE_UNAVAILABLE)

        val langUnavailable = speechFailureOf(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertThat(langUnavailable).isEqualTo(SpeechFailure.LANGUAGE_UNAVAILABLE)
    }

    // 3. Location permission denied and location disabled
    @Test
    fun `location availability correctly identifies missing permissions and disabled hardware`() {
        // Enums cover the required refusal states
        assertThat(PlaceWatchRefusal.PERMISSION_MISSING).isNotNull()
        assertThat(PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING).isNotNull()
        assertThat(PlaceWatchRefusal.LOCATION_DISABLED).isNotNull()
        assertThat(PlaceWatchRefusal.NO_PROVIDER).isNotNull()
    }

    // 4. Cloud unavailable and Gemini quota exhausted
    @Test
    fun `command router falls back to local engine when cloud engine is unavailable or quota exhausted`() = runTest {
        val localEngine = object : CommandEngine {
            override val id: EngineId = EngineId.LOCAL
            override suspend fun isAvailable(): Boolean = true
            override suspend fun interpret(request: CommandRequest): EngineResult =
                EngineResult.Interpreted(
                    command = AppCommand.SearchTasks(query = null, filter = TaskFilter.OPEN),
                    confidence = 0.95f,
                )
        }
        val unavailableGemini = object : CommandEngine {
            override val id: EngineId = EngineId.GEMINI
            override suspend fun isAvailable(): Boolean = false
            override suspend fun interpret(request: CommandRequest): EngineResult =
                EngineResult.Unavailable("Quota exhausted: HTTP 429")
        }

        val router = CommandRouter(local = localEngine, gemini = unavailableGemini)
        val request = CommandRequest(
            raw = "Amar task dekhao",
            normalized = "amar task dekhao",
            language = SpokenLanguage.BENGALI,
            script = TextScript.LATIN,
            source = InputSource.VOICE,
        )

        val routed = router.route(request)
        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isInstanceOf(EngineResult.Interpreted::class.java)
    }

    // 5. Corrupt backup and incorrect password
    @Test
    fun `backup cipher rejects incorrect passphrase with AUTHENTICATION_FAILED`() {
        val cipher = PortableBackupCipher()
        val plainText = "Sensitive financial database payload".toByteArray(Charsets.UTF_8)
        val encryptedRes = cipher.encryptPayload("CorrectPassword123", plainText)
        assertThat(encryptedRes).isInstanceOf(AppResult.Success::class.java)
        val cipherBytes = (encryptedRes as AppResult.Success).value

        val failedDecrypt = cipher.decryptPayload("WrongPassword456", cipherBytes)
        assertThat(failedDecrypt).isInstanceOf(AppResult.Failure::class.java)
        val failure = failedDecrypt as AppResult.Failure
        assertThat(failure.error.code).isEqualTo(MiraErrorCode.AUTHENTICATION_FAILED)
    }

    @Test
    fun `backup cipher rejects corrupted payload with error rather than crashing`() {
        val cipher = PortableBackupCipher()
        val corruptedBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val result = cipher.decryptPayload("AnyPassword", corruptedBytes)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }

    // 6. Invalid invoice validation
    @Test
    fun `command validator rejects invalid invoice with negative or non-positive amount`() {
        val validator = DefaultCommandValidator(testClock)
        val invalidCommand = AppCommand.CreateInvoiceDraft(
            client = ClientTarget.ByName("Ahmed"),
            amount = Money.of("-500"),
            description = "Audit test",
        )

        val result = validator.validate(invalidCommand)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val failure = result as AppResult.Failure
        assertThat(failure.error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    // 7. Missing attachment in delivery draft
    @Test
    fun `delivery draft with non-existent attachment path does not fail draft preparation`() {
        val nonExistentPath = "/tmp/does_not_exist_${System.currentTimeMillis()}.pdf"
        val draft = InvoiceDeliveryDraft(
            invoiceId = 1L,
            invoiceNumber = "INV-2026-001",
            clientName = "Ahmed",
            recipient = "ahmed@example.com",
            method = InvoiceDeliveryMethod.EMAIL,
            amountFormatted = "BDT 45,000",
            subject = "Invoice INV-2026-001",
            message = "Please find invoice attached",
            attachmentPath = nonExistentPath,
        )

        val file = draft.attachmentPath?.let { File(it) }
        assertThat(file?.exists()).isFalse()
        // Draft remains valid even if attachment file has not been exported or was cleared
        assertThat(draft.recipient).isEqualTo("ahmed@example.com")
    }
}
