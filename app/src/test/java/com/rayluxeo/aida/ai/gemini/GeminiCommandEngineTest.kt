package com.rayluxeo.aida.ai.gemini

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.repository.MiraSettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GeminiCommandEngineTest {

    private val clock = FixedClock(
        at = Instant.parse("2026-09-24T10:00:00Z"),
        timeZone = ZoneId.of("Asia/Dhaka"),
    )

    @Test
    fun `the engine identifies itself as Gemini`() {
        assertThat(engine().id).isEqualTo(EngineId.GEMINI)
    }

    @Test
    fun `everything configured and online means available`() = runTest {
        assertThat(engine().isAvailable()).isTrue()
    }

    @Test
    fun `switched off in settings means unavailable`() = runTest {
        assertThat(engine(enabled = false).isAvailable()).isFalse()
    }

    @Test
    fun `no stored key means unavailable`() = runTest {
        assertThat(engine(apiKey = null).isAvailable()).isFalse()
        assertThat(engine(apiKey = "   ").isAvailable()).isFalse()
    }

    @Test
    fun `no validated network means unavailable`() = runTest {
        assertThat(engine(online = false).isAvailable()).isFalse()
    }

    @Test
    fun `the availability check never calls out to the network`() = runTest {
        val client = FakeGeminiClient(AppResult.Success(TASK_JSON))
        engine(client = client).isAvailable()
        assertThat(client.calls).isEqualTo(0)
    }

    @Test
    fun `a good answer becomes a command`() = runTest {
        val result = engine().interpret(request("kal Ahmed ke call korte hobe"))
        assertThat(result).isInstanceOf(EngineResult.Interpreted::class.java)
        assertThat((result as EngineResult.Interpreted).command)
            .isEqualTo(AppCommand.CreateTask(title = "Call Ahmed"))
    }

    @Test
    fun `the utterance reaches the model exactly as Abu said it`() = runTest {
        val client = FakeGeminiClient(AppResult.Success(TASK_JSON))
        engine(client = client).interpret(request("kal Ahmed ke call korte hobe"))
        assertThat(client.lastUtterance).isEqualTo("kal Ahmed ke call korte hobe")
    }

    @Test
    fun `the system instruction carries Abu's current local time`() = runTest {
        val client = FakeGeminiClient(AppResult.Success(TASK_JSON))
        engine(client = client).interpret(request("kal Ahmed ke call korte hobe"))
        assertThat(client.lastSystemInstruction).contains("2026-09-24T16:00")
    }

    @Test
    fun `a transport failure is reported as unavailable, never as a miss`() = runTest {
        val client = FakeGeminiClient(
            AppResult.Failure(MiraError(MiraErrorCode.ENGINE_UNAVAILABLE, "Gemini could not be reached")),
        )
        val result = engine(client = client).interpret(request("kal Ahmed ke call korte hobe"))
        assertThat(result).isInstanceOf(EngineResult.Unavailable::class.java)
    }

    @Test
    fun `a reply Mira cannot use is a miss rather than a half-built command`() = runTest {
        val client = FakeGeminiClient(AppResult.Success("""{"command":"CREATE_TASK","confidence":0.9}"""))
        val result = engine(client = client).interpret(request("kal Ahmed ke call korte hobe"))
        assertThat(result).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `interpreting while switched off never reaches the network`() = runTest {
        val client = FakeGeminiClient(AppResult.Success(TASK_JSON))
        val result = engine(enabled = false, client = client).interpret(request("kichu ekta"))
        assertThat(result).isInstanceOf(EngineResult.Unavailable::class.java)
        assertThat(client.calls).isEqualTo(0)
    }

    @Test
    fun `interpreting while offline never reaches the network`() = runTest {
        val client = FakeGeminiClient(AppResult.Success(TASK_JSON))
        val result = engine(online = false, client = client).interpret(request("kichu ekta"))
        assertThat(result).isInstanceOf(EngineResult.Unavailable::class.java)
        assertThat(client.calls).isEqualTo(0)
    }

    @Test
    fun `interpreting without a key never reaches the network`() = runTest {
        val client = FakeGeminiClient(AppResult.Success(TASK_JSON))
        val result = engine(apiKey = null, client = client).interpret(request("kichu ekta"))
        assertThat(result).isInstanceOf(EngineResult.Unavailable::class.java)
        assertThat(client.calls).isEqualTo(0)
    }

    private fun engine(
        enabled: Boolean = true,
        apiKey: String? = "test-key",
        online: Boolean = true,
        client: GeminiClient = FakeGeminiClient(AppResult.Success(TASK_JSON)),
    ): GeminiCommandEngine {
        val creds = FakeGeminiCredentialStore(apiKey)
        val setts = FakeSettingsRepository(MiraSettings(geminiEnabled = enabled))
        val conn = FakeConnectivityProbe(online)
        val availManager = GeminiAvailabilityManager(creds, setts, conn, clock)
        val confirmManager = ConfirmationManager()

        return GeminiCommandEngine(
            credentials = creds,
            settings = setts,
            connectivity = conn,
            client = client,
            mapper = GeminiCommandMapper(clock),
            availabilityManager = availManager,
            confirmationManager = confirmManager,
            clock = clock,
        )
    }

    private fun request(raw: String) = CommandRequest(
        raw = raw,
        normalized = raw.lowercase(),
        language = SpokenLanguage.BENGALI,
        script = TextScript.LATIN,
        source = InputSource.TEXT,
    )

    private companion object {
        const val TASK_JSON = """{"command":"CREATE_TASK","title":"Call Ahmed","confidence":0.88}"""
    }
}
