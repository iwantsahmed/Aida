package com.rayluxeo.aida.ui.invoices.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders pages from an on-device PDF into Android [Bitmap]s using native [PdfRenderer].
 * Zero dependencies, completely offline.
 */
@Singleton
class PdfPreviewRenderer @Inject constructor() {

    fun renderPages(pdfFile: File, scale: Float = 1.5f): AppResult<List<Bitmap>> {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return failure(MiraErrorCode.NOT_FOUND, "PDF file does not exist or is empty")
        }

        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)
                }
            } finally {
                renderer.close()
                pfd.close()
            }
            return AppResult.Success(bitmaps)
        } catch (e: Exception) {
            return failure(MiraErrorCode.STORAGE, "Failed to render PDF preview: ${e.message}")
        }
    }
}
