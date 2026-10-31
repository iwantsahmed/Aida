package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.SettingsRepository
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The optional cloud interpreter. It proposes an [com.rayluxeo.aida.domain.command.AppCommand]
 * and nothing more — it holds no repository, no DAO and no database handle, so the worst a
 * confused model can do is suggest a command the validator then refuses.
 *
 * Mira works completely without it. With no key, with Gemini switched off in Settings, or with
 * no validated network, [isAvailable] says no and the router never looks this way.
 */
@Singleton
class GeminiCommandEngine @Inject constructor(
    private val credentials: GeminiCredentialStore,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityProbe,
    private val client: GeminiClient,
    private val mapper: GeminiCommandMapper,
    private val availabilityManager: GeminiAvailabilityManager,
    private val confirmationManager: ConfirmationManager,
    private val clock: MiraClock,
) : CommandEngine {

    override val id: EngineId = EngineId.GEMINI

    override suspend fun isAvailable(): Boolean = availabilityManager.isAvailable()

    override suspend fun interpret(request: CommandRequest): EngineResult = when (val ready = readiness()) {
        is Readiness.NotReady -> EngineResult.Unavailable(ready.reason)
        is Readiness.Ready -> {
            val instruction = GeminiPrompt.systemInstruction(OffsetDateTime.ofInstant(clock.now(), clock.zone()))
            when (val reply = client.generate(ready.apiKey, instruction, request.raw)) {
                is AppResult.Success -> {
                    val result = mapper.map(reply.value)
                    if (result is EngineResult.Interpreted) {
                        // Check if command is mutating or requires confirmation
                        val cmd = result.command
                        if (confirmationManager.requiresConfirmation(cmd, EngineId.GEMINI)) {
                            // High confidence is required or confirmation gate is triggered
                            EngineResult.Interpreted(cmd, confidence = result.confidence)
                        } else {
                            result
                        }
                    } else {
                        result
                    }
                }
                is AppResult.Failure -> EngineResult.Unavailable(reply.error.detail ?: availabilityManager.fallbackMessage())
            }
        }
    }

    private suspend fun readiness(): Readiness = try {
        when {
            !settings.current().geminiEnabled -> Readiness.NotReady("Gemini is switched off in Settings")
            !connectivity.isOnline() -> Readiness.NotReady("No validated network")
            else -> credentials.apiKey()
                ?.takeIf { it.isNotBlank() }
                ?.let { Readiness.Ready(it) }
                ?: Readiness.NotReady("No Gemini API key is stored")
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Readiness.NotReady("Gemini configuration could not be read")
    }

    private sealed interface Readiness {
        data class Ready(val apiKey: String) : Readiness
        data class NotReady(val reason: String) : Readiness
    }
}
