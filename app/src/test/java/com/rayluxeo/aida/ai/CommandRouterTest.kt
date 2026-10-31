package com.rayluxeo.aida.ai

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.model.InputSource
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The routing rule is the promise that Mira keeps working when the cloud does not:
 * local first, Gemini only on a genuine local miss, and the local verdict wins if Gemini
 * also misses so Abu hears one clear answer rather than two.
 */
class CommandRouterTest {

    private val request = CommandRequest(
        raw = "add a task to call Ahmed",
        normalized = "add a task to call ahmed",
        language = SpokenLanguage.ENGLISH,
        script = TextScript.LATIN,
        source = InputSource.TEXT,
    )

    private class FakeEngine(
        override val id: EngineId,
        private val available: Boolean,
        private val result: EngineResult,
    ) : CommandEngine {
        var interpretCalls = 0
            private set

        override suspend fun isAvailable(): Boolean = available

        override suspend fun interpret(request: CommandRequest): EngineResult {
            interpretCalls++
            return result
        }
    }

    private val localHit = EngineResult.Interpreted(AppCommand.CreateTask("call Ahmed"), confidence = 0.9f)
    private val geminiHit = EngineResult.Interpreted(AppCommand.CreateTask("call Ahmed later"), confidence = 0.8f)

    /** A local greeting: a reply Mira can give alone, but would rather Gemini answered. */
    private val localGreeting =
        EngineResult.Interpreted(AppCommand.Greet, confidence = 0.95f, deferToCloud = true)
    private val geminiChat =
        EngineResult.Interpreted(AppCommand.Conversational("Ami bhalo achi, Abu."), confidence = 0.9f)

    @Test
    fun `a local hit never reaches Gemini`() = runTest {
        val gemini = FakeEngine(EngineId.GEMINI, available = true, result = geminiHit)
        val routed = CommandRouter(FakeEngine(EngineId.LOCAL, true, localHit), gemini).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(localHit)
        assertThat(gemini.interpretCalls).isEqualTo(0)
    }

    @Test
    fun `a deferred greeting hands off to Gemini when it is available`() = runTest {
        // "tumi kemon acho" is small talk the local engine can meet with a greeting, but online
        // Abu should get a real reply in his own words rather than the canned pleasantry.
        val gemini = FakeEngine(EngineId.GEMINI, available = true, result = geminiChat)
        val routed = CommandRouter(FakeEngine(EngineId.LOCAL, true, localGreeting), gemini).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.GEMINI)
        assertThat(routed.result).isEqualTo(geminiChat)
        assertThat(gemini.interpretCalls).isEqualTo(1)
    }

    @Test
    fun `a deferred greeting still greets when Gemini is unavailable`() = runTest {
        val gemini = FakeEngine(EngineId.GEMINI, available = false, result = geminiChat)
        val routed = CommandRouter(FakeEngine(EngineId.LOCAL, true, localGreeting), gemini).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(localGreeting)
        assertThat(gemini.interpretCalls).isEqualTo(0)
    }

    @Test
    fun `a deferred greeting falls back to the local greeting when Gemini misses`() = runTest {
        val routed = CommandRouter(
            FakeEngine(EngineId.LOCAL, true, localGreeting),
            FakeEngine(EngineId.GEMINI, true, EngineResult.NotUnderstood),
        ).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(localGreeting)
    }

    @Test
    fun `a deferred greeting from shared content never reaches Gemini`() = runTest {
        val gemini = FakeEngine(EngineId.GEMINI, available = true, result = geminiChat)
        val routed = CommandRouter(FakeEngine(EngineId.LOCAL, true, localGreeting), gemini)
            .route(request.copy(source = InputSource.SHARED))

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(localGreeting)
        assertThat(gemini.interpretCalls).isEqualTo(0)
    }

    @Test
    fun `a local miss falls through to Gemini when it is available`() = runTest {
        val routed = CommandRouter(
            FakeEngine(EngineId.LOCAL, true, EngineResult.NotUnderstood),
            FakeEngine(EngineId.GEMINI, true, geminiHit),
        ).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.GEMINI)
        assertThat(routed.result).isEqualTo(geminiHit)
    }

    @Test
    fun `an unavailable Gemini is never asked and the local verdict stands`() = runTest {
        val gemini = FakeEngine(EngineId.GEMINI, available = false, result = geminiHit)
        val routed = CommandRouter(
            FakeEngine(EngineId.LOCAL, true, EngineResult.NotUnderstood),
            gemini,
        ).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(EngineResult.NotUnderstood)
        assertThat(gemini.interpretCalls).isEqualTo(0)
    }

    @Test
    fun `content shared from another app never reaches Gemini, even on a local miss`() = runTest {
        // Abu did not write this and has not been asked whether it may leave the phone. A local
        // miss is a worse answer than a cloud hit, and it is the right one: the alternative is
        // another app's content going to a third party without him ever being told.
        val gemini = FakeEngine(EngineId.GEMINI, available = true, result = geminiHit)
        val routed = CommandRouter(
            FakeEngine(EngineId.LOCAL, true, EngineResult.NotUnderstood),
            gemini,
        ).route(request.copy(source = InputSource.SHARED))

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(EngineResult.NotUnderstood)
        assertThat(gemini.interpretCalls).isEqualTo(0)
    }

    @Test
    fun `a cloud miss does not mask the local verdict`() = runTest {
        val routed = CommandRouter(
            FakeEngine(EngineId.LOCAL, true, EngineResult.NotUnderstood),
            FakeEngine(EngineId.GEMINI, true, EngineResult.NotUnderstood),
        ).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `a cloud failure mid-flight still leaves Abu with the local answer`() = runTest {
        val routed = CommandRouter(
            FakeEngine(EngineId.LOCAL, true, EngineResult.NotUnderstood),
            FakeEngine(EngineId.GEMINI, true, EngineResult.Unavailable("timed out")),
        ).route(request)

        assertThat(routed.engine).isEqualTo(EngineId.LOCAL)
        assertThat(routed.result).isEqualTo(EngineResult.NotUnderstood)
    }
}
