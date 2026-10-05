package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.logic.EpubParser
import com.example.logic.PdfPageImageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the reader currently has open. */
enum class BookSourceKind { NONE, TEXT, PDF, EPUB }

/**
 * Everything the Book tab needs that is about the *document* rather than about
 * the app: the EPUB's chapters, the PDF's rendered page images, and which one
 * of them is on screen.
 *
 * It lives next to the screen instead of inside AppViewModel on purpose — it is
 * per-screen, throwaway state (bitmaps especially), and keeping it here means
 * the shared view model does not grow another set of fields to maintain.
 */
class BookReaderState(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    var kind by mutableStateOf(BookSourceKind.NONE)
        private set
    var docUri by mutableStateOf<Uri?>(null)
        private set
    var docName by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorKey by mutableStateOf<String?>(null)
        private set

    // ── EPUB ──
    var book by mutableStateOf<EpubParser.Book?>(null)
        private set
    var chapterIndex by mutableStateOf(0)
        private set

    // ── PDF page images ──
    var pdfPageCount by mutableStateOf(0)
        private set
    /**
     * Page-image mode: the page exactly as printed, with its pictures, instead
     * of extracted text. Backed by a private underscore-named property so its
     * generated setter cannot collide on the JVM with [setPageImageMode].
     */
    private var _pageImageMode by mutableStateOf(false)
    val pageImageMode: Boolean
        get() = _pageImageMode
    var pageBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var isRenderingPage by mutableStateOf(false)
        private set
    private var renderedKey: String? = null

    private val epubImages = mutableStateMapOf<String, Bitmap>()
    private val epubImagesLoading = HashSet<String>()

    /** Stable per-document key, so highlights come back when the same file is reopened.
     *  Includes URI hash to disambiguate two files with same name but different content.
     *  Backward compatible: old key without hash still tried as fallback in BookSessionStore.
     */
    val docKey: String
        get() {
            if (docName.isEmpty()) return ""
            val uriHash = docUri?.toString()?.hashCode()?.let { Integer.toHexString(it) } ?: "0"
            // New format: KIND:name:uriHash — still human readable, but unique for same name different URI
            return "${kind.name}:$docName:$uriHash"
        }

    /** Legacy key without URI hash, for migration / fallback lookup. */
    val legacyDocKey: String
        get() = if (docName.isEmpty()) "" else "${kind.name}:$docName"

    val chapterCount: Int
        get() = book?.chapters?.size ?: 0

    fun chapter(): EpubParser.Chapter? = book?.chapters?.getOrNull(chapterIndex)

    fun openEpub(uri: Uri, name: String) {
        reset()
        kind = BookSourceKind.EPUB
        docUri = uri
        docName = name
        isLoading = true
        scope.launch {
            val parsed = withContext(Dispatchers.IO) { EpubParser.parse(context, uri) }
            isLoading = false
            if (parsed == null) {
                errorKey = "epub_failed"
                kind = BookSourceKind.NONE
            } else {
                book = parsed
                chapterIndex = 0
            }
        }
    }

    fun openPdf(uri: Uri, name: String) {
        reset()
        kind = BookSourceKind.PDF
        docUri = uri
        docName = name
        scope.launch {
            val count = withContext(Dispatchers.IO) { PdfPageImageRenderer.pageCount(context, uri) }
            pdfPageCount = count
        }
    }

    fun openPlainText(name: String) {
        reset()
        kind = BookSourceKind.TEXT
        docName = name
    }

    fun reset() {
        kind = BookSourceKind.NONE
        docUri = null
        docName = ""
        isLoading = false
        errorKey = null
        book = null
        chapterIndex = 0
        pdfPageCount = 0
        _pageImageMode = false
        pageBitmap = null
        isRenderingPage = false
        renderedKey = null
        epubImages.clear()
        epubImagesLoading.clear()
        EpubParser.releaseCache()
    }

    fun clearError() {
        errorKey = null
    }

    fun goToChapter(index: Int) {
        val count = chapterCount
        if (count == 0) return
        chapterIndex = index.coerceIn(0, count - 1)
    }

    fun nextChapter() = goToChapter(chapterIndex + 1)

    fun previousChapter() = goToChapter(chapterIndex - 1)

    fun setPageImageMode(enabled: Boolean) {
        _pageImageMode = enabled
        if (!enabled) {
            pageBitmap = null
            renderedKey = null
        }
    }

    /**
     * Renders (or re-renders) a PDF page. Cheap to call from a composable: it
     * returns immediately when that page is already on screen at that width.
     */
    fun requestPage(pageIndex: Int, widthPx: Int) {
        val uri = docUri ?: return
        if (kind != BookSourceKind.PDF || widthPx <= 0) return
        // Re-render only on a real change, and snap the width to 200px steps so a
        // few pixels of layout jitter cannot start a render loop.
        val key = "$pageIndex@${widthPx / 200}"
        if (key == renderedKey) return
        renderedKey = key
        isRenderingPage = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                PdfPageImageRenderer.renderPage(context, uri, pageIndex, widthPx)
            }
            isRenderingPage = false
            if (bitmap != null) {
                pageBitmap = bitmap
            } else {
                renderedKey = null
                errorKey = "page_render_failed"
            }
        }
    }

    /** An illustration from inside the EPUB, decoded lazily and kept for the session. */
    fun epubImage(entry: String): Bitmap? {
        epubImages[entry]?.let { return it }
        val uri = docUri ?: return null
        if (kind != BookSourceKind.EPUB || entry in epubImagesLoading) return null
        epubImagesLoading += entry
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val bytes = EpubParser.imageBytes(context, uri, entry) ?: return@withContext null
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    // Illustrations in books are often far larger than any phone
                    // screen, so downsample before decoding for real.
                    var sample = 1
                    while (options.outWidth / sample > 1600) sample *= 2
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
            epubImagesLoading -= entry
            if (bitmap != null) epubImages[entry] = bitmap
        }
        return null
    }
}

@Composable
fun rememberBookReaderState(): BookReaderState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { BookReaderState(context.applicationContext, scope) }
}
