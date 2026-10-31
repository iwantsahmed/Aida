package com.rayluxeo.aida.ai

import com.rayluxeo.aida.di.GeminiEngine
import com.rayluxeo.aida.di.LocalEngine
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandInterpreter
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.RoutedCommand
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local first, always. Gemini is consulted only when the on-device engine either genuinely did
 * not understand or offered a pleasantry it would rather the cloud answered (see
 * [EngineResult.Interpreted.deferToCloud]), and only when it is configured and reachable. If
 * Gemini is missing, unconfigured or offline, Mira simply answers with what the local engine
 * concluded — she never stalls waiting on the network, and a deferred greeting is still a real
 * reply, so she greets with the radio off.
 */
@Singleton
class CommandRouter @Inject constructor(
    @param:LocalEngine private val local: CommandEngine,
    @param:GeminiEngine private val gemini: CommandEngine,
) : CommandInterpreter {
    override suspend fun route(request: CommandRequest): RoutedCommand {
        val localResult = local.interpret(request)
        // A firm local command wins outright. Only a miss, or a deferred pleasantry, looks past it.
        if (localResult is EngineResult.Interpreted && !localResult.deferToCloud) {
            return RoutedCommand(localResult, EngineId.LOCAL)
        }

        if (!gemini.isAvailable()) {
            return RoutedCommand(localResult, EngineId.LOCAL)
        }

        // Content another app handed to Mira stops here. Abu did not write it and has not been
        // asked whether it may leave the phone, and a share sheet gives no moment at which to ask
        // — so the local verdict stands, even when it is a miss. The rule lives at the routing
        // seam rather than at each caller because that is the one place every engine choice passes
        // through, and a caller that forgot would leak silently.
        if (request.source == InputSource.SHARED) {
            return RoutedCommand(localResult, EngineId.LOCAL)
        }

        return when (val remote = gemini.interpret(request)) {
            is EngineResult.Interpreted -> RoutedCommand(remote, EngineId.GEMINI)
            // A cloud miss must not mask the local verdict; Abu hears one clear answer.
            is EngineResult.NotUnderstood, is EngineResult.Unavailable ->
                RoutedCommand(localResult, EngineId.LOCAL)
        }
    }
}
