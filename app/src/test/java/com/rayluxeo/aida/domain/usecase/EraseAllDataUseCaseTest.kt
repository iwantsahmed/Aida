package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.repository.LocalDataEraser
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EraseAllDataUseCaseTest {

    private class FakeEraser(private val result: AppResult<Unit>) : LocalDataEraser {
        var calls = 0
        override suspend fun eraseEverything(): AppResult<Unit> {
            calls++
            return result
        }
    }

    @Test
    fun `invokes the eraser once and passes its success through`() = runTest {
        val eraser = FakeEraser(AppResult.Success(Unit))

        val result = EraseAllDataUseCase(eraser)()

        assertThat(eraser.calls).isEqualTo(1)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `passes a failure through unchanged`() = runTest {
        val eraser = FakeEraser(failure(MiraErrorCode.STORAGE, "boom"))

        val result = EraseAllDataUseCase(eraser)()

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
    }
}
