package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.repository.LocalDataEraser
import javax.inject.Inject

/**
 * Erases every local store on the device. The one place the whole-device wipe is invoked from, so
 * the privacy dashboard never reaches the eraser port directly (AGENTS.md §5: state changes go
 * through a use case). Abu's confirmation gate lives above this; by the time it runs, he has said
 * yes to losing everything.
 */
class EraseAllDataUseCase @Inject constructor(
    private val eraser: LocalDataEraser,
) {
    suspend operator fun invoke(): AppResult<Unit> = eraser.eraseEverything()
}
