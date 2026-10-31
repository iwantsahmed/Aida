package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ConfirmationRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.MiraResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfirmationManager @Inject constructor() {

    fun requiresConfirmation(
        command: AppCommand,
        engineId: EngineId = EngineId.GEMINI,
    ): Boolean {
        if (engineId == EngineId.GEMINI && command.isMutating) return true

        return when (command) {
            is AppCommand.CreateTask -> false
            is AppCommand.UpdateTask -> true
            is AppCommand.CompleteTask -> true
            is AppCommand.CreateReminder -> false
            is AppCommand.CreateAlarm -> false
            is AppCommand.CreateTimer -> false
            is AppCommand.CreateNote -> false
            is AppCommand.StoreMemory -> true
            is AppCommand.CreateClient -> true
            is AppCommand.CreateInvoiceDraft -> true
            is AppCommand.RecordPayment -> true
            is AppCommand.RecordExpense -> true
            is AppCommand.RecordIncome -> true
            is AppCommand.PrepareCall -> true
            is AppCommand.PrepareMessage -> true
            is AppCommand.ForgetMemory -> true
            else -> command.isMutating
        }
    }

    fun buildConfirmationRequest(
        command: AppCommand,
        summaryResponse: MiraResponse,
        engineId: EngineId = EngineId.GEMINI,
    ): ConfirmationRequest {
        return ConfirmationRequest(
            command = command,
            engine = engineId,
            summary = summaryResponse,
        )
    }
}
