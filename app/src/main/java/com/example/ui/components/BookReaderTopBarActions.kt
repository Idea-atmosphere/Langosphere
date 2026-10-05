package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * MIME types the document switcher accepts. `application/epub+zip` is the
 * registered EPUB type, but plenty of providers hand back
 * `application/octet-stream` for a sideloaded `.epub`, so that is accepted too
 * and the real format is decided by [com.example.logic.EpubParser.isEpub].
 */
val BOOK_DOCUMENT_MIME_TYPES: Array<String> = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/zip",
    "application/octet-stream",
)

/** MIME types the AI lesson importer accepts. */
val LESSON_JSON_MIME_TYPES: Array<String> = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
)

/**
 * The reader's discoverable toolbar actions, meant to be dropped straight into
 * a Material 3 `TopAppBar(actions = { BookReaderTopBarActions(...) })`.
 *
 * ## Why these actions live here
 *
 * Opening a document used to require knowing that the filename text was
 * tappable, which is an invisible affordance. These are three plain
 * [IconButton]s with content descriptions, so they are reachable by touch,
 * by TalkBack and by keyboard.
 *
 * ## A note on the icons
 *
 * Only `material-icons-core` is on the classpath, so `FolderOpen`,
 * `FileUpload`, `AutoAwesome` and `ContentCopy` are not available. The nearest
 * core glyphs are used and the meaning is carried by `contentDescription`,
 * which is what assistive tech reads anyway. Swapping in the extended icon
 * artwork later is a one-line change per action.
 *
 * The SAF launcher is owned here rather than by the screen: the picker result
 * is only ever needed to hand a [Uri] back, and keeping it local means the
 * caller cannot forget to persist read permission.
 */
@Composable
fun BookReaderTopBarActions(
    onDocumentPicked: (Uri) -> Unit,
    onImportLessonClick: () -> Unit,
    onExtractChunkClick: () -> Unit,
    openDocumentLabel: String = "باز کردن فایل (PDF / EPUB)",
    importLessonLabel: String = "ورود درس هوش مصنوعی (JSON)",
    extractChunkLabel: String = "استخراج متن برای هوش مصنوعی",
) {
    val context = LocalContext.current

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Without this the grant dies with the activity, and reopening the book
        // after a process death fails with a SecurityException.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onDocumentPicked(uri)
    }

    IconButton(onClick = { documentPicker.launch(BOOK_DOCUMENT_MIME_TYPES) }) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = openDocumentLabel,
        )
    }
    IconButton(onClick = onImportLessonClick) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = importLessonLabel,
        )
    }
    IconButton(onClick = onExtractChunkClick) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = extractChunkLabel,
        )
    }
}
