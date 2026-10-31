package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.core.common.AppResult

/**
 * Last gate before execution. Every command — whether it came from the local engine or
 * from Gemini — passes through here, so an engine cannot smuggle an invalid record through.
 */
interface CommandValidator {
    fun validate(command: AppCommand): AppResult<AppCommand>
}
