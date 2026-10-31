package com.rayluxeo.aida.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class StoredScanFile(
    val file: File,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
)

@Singleton
class ScanImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scansDir: File
        get() {
            val dir = File(context.filesDir, "scans")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun saveBitmap(bitmap: Bitmap, prefix: String = "scan"): StoredScanFile {
        val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
        val destination = File(scansDir, fileName)

        FileOutputStream(destination).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val relativePath = "scans/$fileName"
        return StoredScanFile(
            file = destination,
            relativePath = relativePath,
            absolutePath = destination.absolutePath,
            sizeBytes = destination.length(),
        )
    }

    fun saveInputStream(inputStream: InputStream, prefix: String = "imported"): StoredScanFile? {
        return runCatching {
            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val destination = File(scansDir, fileName)

            FileOutputStream(destination).use { out ->
                inputStream.copyTo(out)
            }

            val relativePath = "scans/$fileName"
            StoredScanFile(
                file = destination,
                relativePath = relativePath,
                absolutePath = destination.absolutePath,
                sizeBytes = destination.length(),
            )
        }.getOrNull()
    }

    fun cropBitmap(source: Bitmap, leftFraction: Float, topFraction: Float, rightFraction: Float, bottomFraction: Float): Bitmap {
        val width = source.width
        val height = source.height

        val cropX = (leftFraction.coerceIn(0f, 1f) * width).toInt()
        val cropY = (topFraction.coerceIn(0f, 1f) * height).toInt()
        val cropW = ((rightFraction - leftFraction).coerceIn(0.01f, 1f) * width).toInt().coerceAtMost(width - cropX)
        val cropH = ((bottomFraction - topFraction).coerceIn(0.01f, 1f) * height).toInt().coerceAtMost(height - cropY)

        return Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
    }

    fun cleanImageForOcr(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        val bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bwBitmap)
        val paint = Paint()

        // High contrast grayscale filter matrix
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
            // Increase contrast
            val contrast = 1.4f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val matrix = floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(matrix))
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return bwBitmap
    }
}
