package com.rayluxeo.aida.data.scanner

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.scanner.OcrBlock
import com.rayluxeo.aida.domain.scanner.OcrResult
import com.rayluxeo.aida.domain.scanner.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeTextFromPath(imageAbsolutePath: String): AppResult<OcrResult> {
        val file = File(imageAbsolutePath)
        if (!file.exists() || !file.canRead()) {
            return failure(MiraErrorCode.VALIDATION, "File does not exist or is not readable: $imageAbsolutePath")
        }

        return runCatching {
            val rotationDegrees = getRotationDegrees(imageAbsolutePath)
            val bitmap = BitmapFactory.decodeFile(imageAbsolutePath)
                ?: return failure(MiraErrorCode.STORAGE, "Failed to decode image at $imageAbsolutePath")

            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

            suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.map { block ->
                            val rect = block.boundingBox
                            OcrBlock(
                                text = block.text,
                                left = rect?.left ?: 0,
                                top = rect?.top ?: 0,
                                right = rect?.right ?: 0,
                                bottom = rect?.bottom ?: 0,
                            )
                        }
                        continuation.resume(OcrResult(text = visionText.text, blocks = blocks).asSuccess())
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(failure(MiraErrorCode.STORAGE, e.message ?: "OCR recognition failed"))
                    }
            }
        }.getOrElse { e ->
            failure(MiraErrorCode.STORAGE, e.message ?: "OCR error occurred")
        }
    }

    private fun getRotationDegrees(imagePath: String): Int {
        return runCatching {
            val exif = ExifInterface(imagePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }
}
