package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.core.common.AppResult

/**
 * Executes a validated [AppCommand] by delegating to use cases. Implementations must be
 * exhaustive over the sealed hierarchy, so adding a command without a handler is a
 * compile error rather than a silent no-op.
 */
interface CommandDispatcher {
    suspend fun dispatch(command: AppCommand): AppResult<MiraResponse>
}
