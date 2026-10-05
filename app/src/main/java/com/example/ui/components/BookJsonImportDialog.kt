package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Import AI Lesson": the two ways a model reply actually arrives — as a saved
 * file, or on the clipboard — in one dialog.
 *
 * Parsing is not done here. The raw text is handed to [onImport] so the caller
 * can run it through [com.example.logic.BookJsonIngest], merge it and persist
 * it, then report back through [statusMessage] ("Loaded 35 sentences for pages
 * 1–5", or a drift warning). That keeps repair, drift detection and storage in
 * one place instead of splitting them across a dialog.
 *
 * The dialog stays open after an import so a rejected or drifting payload can
 * be corrected and retried without reopening it; [onDismiss] is the only exit.
 */
@Composable
fun BookJsonImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    statusMessage: String? = null,
    isError: Boolean = false,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var pasted by remember { mutableStateOf("") }
    var readError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().decodeToString()
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                readError = "فایل خوانده نشد یا خالی بود."
            } else {
                readError = null
                pasted = text
                onImport(text)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ورود درس هوش مصنوعی") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { filePicker.launch(LESSON_JSON_MIME_TYPES) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("انتخاب فایل JSON")
                    }
                    OutlinedButton(
                        onClick = {
                            val text = clipboard.getText()?.text
                            if (text.isNullOrBlank()) {
                                readError = "کلیپ‌بورد خالی است."
                            } else {
                                readError = null
                                pasted = text
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("الصاق از کلیپ‌بورد")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("متن JSON") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                val message = readError ?: statusMessage
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (readError != null || isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(pasted) },
                enabled = pasted.isNotBlank(),
            ) {
                Text("ورود")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        },
    )
}
