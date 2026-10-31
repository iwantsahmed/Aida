package com.rayluxeo.aida.domain.scanner

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.usecase.ProcessScanUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProcessScanUseCaseTest {

    private class FakeTextRecognizer : TextRecognizer {
        override suspend fun recognizeTextFromPath(imageAbsolutePath: String): AppResult<OcrResult> {
            return OcrResult("Sample Scanned Text\nTotal: 500 BDT").asSuccess()
        }
    }

    @Test
    fun `executeOcr returns fake recognizer result`() = runTest {
        val fakeRecognizer = FakeTextRecognizer()
        val useCase = ProcessScanUseCase(fakeRecognizer)

        val result = useCase.executeOcr("/path/to/image.jpg")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)

        val ocrResult = (result as AppResult.Success).value
        assertThat(ocrResult.text).contains("Sample Scanned Text")
    }

    @Test
    fun `parseHandwriting returns low confidence on blank text`() {
        val useCase = ProcessScanUseCase(FakeTextRecognizer())
        val parsed = useCase.parseHandwriting("")

        assertThat(parsed.isRecognitionLowConfidence).isTrue()
    }
}
