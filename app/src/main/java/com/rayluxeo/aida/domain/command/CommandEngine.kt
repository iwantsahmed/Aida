package com.rayluxeo.aida.domain.command

enum class EngineId { LOCAL, GEMINI }

/**
 * Turns an utterance into a candidate [AppCommand]. An engine only *proposes*;
 * it never touches a repository, a database or an Android API. Gemini in particular
 * is confined to this interface precisely so it cannot manipulate stored records.
 */
interface CommandEngine {
    val id: EngineId

    /** Cheap, side-effect-free check used to skip engines that cannot run right now. */
    suspend fun isAvailable(): Boolean

    suspend fun interpret(request: CommandRequest): EngineResult
}

sealed interface EngineResult {
    data class Interpreted(
        val command: AppCommand,
        /** 0f..1f. Anything below [CONFIRM_THRESHOLD] is confirmed with Abu before it runs. */
        val confidence: Float,
        /**
         * A result the local engine can stand on alone but would rather the cloud answered — a
         * pleasantry, not a command. The router consults Gemini when it is reachable and keeps
         * this result only as the offline fallback, so "tumi kemon acho" becomes a real reply in
         * Abu's own words online and still a greeting with the radio off. Gemini never sets it:
         * what Gemini returns is always a firm verdict.
         */
        val deferToCloud: Boolean = false,
    ) : EngineResult

    data object NotUnderstood : EngineResult

    data class Unavailable(val reason: String) : EngineResult

    companion object {
        const val CONFIRM_THRESHOLD = 0.62f
    }
}
