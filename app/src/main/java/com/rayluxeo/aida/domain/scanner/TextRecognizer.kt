package com.rayluxeo.aida.domain.scanner

import com.rayluxeo.aida.core.common.AppResult

/**
 * Text recognition / OCR contract.
 *
 * Implemented in the data layer using on-device ML Kit Text Recognition.
 */
interface TextRecognizer {
    suspend fun recognizeTextFromPath(imageAbsolutePath: String): AppResult<OcrResult>
}
