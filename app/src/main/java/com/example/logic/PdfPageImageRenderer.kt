package com.example.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Renders PDF pages as bitmaps with the platform's own PdfRenderer (API 21+,
 * so well below the app's minSdk 24) and no extra dependency.
 *
 * The reader extracts PDF text with PDFBox for the tap-a-word dictionary, but
 * text extraction throws away everything that is not a glyph: diagrams,
 * photos, tables, math typeset as an image, and every page of a scanned book.
 * This renderer is the other half — the page exactly as the author laid it out,
 * pictures included.
 */
object PdfPageImageRenderer {

    /** Page count, or 0 when the file cannot be opened as a PDF. */
    fun pageCount(context: Context, uri: Uri): Int = withRenderer(context, uri) { renderer ->
        renderer.pageCount
    } ?: 0

    /**
     * Renders one page at [targetWidthPx] wide (height follows the page's own
     * aspect ratio). Returns null when the page cannot be rendered.
     */
    fun renderPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidthPx: Int,
    ): Bitmap? = withRenderer(context, uri) { renderer ->
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withRenderer null
        renderer.openPage(pageIndex).use { page ->
            val width = targetWidthPx.coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX)
            val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.4f
            val height = (width * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PdfRenderer draws nothing where the page is transparent, which on a
            // dark surface would show as black page "paper". Fill white first.
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    private fun <T> withRenderer(context: Context, uri: Uri, block: (PdfRenderer) -> T?): T? {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(descriptor)
            block(renderer)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } finally {
            try {
                renderer?.close()
            } catch (e: Exception) {
                // Already closed; nothing to do.
            }
            try {
                descriptor?.close()
            } catch (e: Exception) {
                // Already closed; nothing to do.
            }
        }
    }

    private const val MIN_WIDTH_PX = 320
    private const val MAX_WIDTH_PX = 2400
}
