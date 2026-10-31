package com.rayluxeo.aida.domain.command

/** Which engine answered, alongside what it concluded. */
data class RoutedCommand(
    val result: EngineResult,
    val engine: EngineId,
)

/**
 * Turns an utterance into a candidate command by whatever means are available.
 *
 * The domain states the need; the ai/ layer decides how engines are tried and in what order.
 * Keeping the port here is what lets a use case orchestrate interpretation without the domain
 * depending on the interpreter's machinery.
 */
interface CommandInterpreter {
    suspend fun route(request: CommandRequest): RoutedCommand
}
