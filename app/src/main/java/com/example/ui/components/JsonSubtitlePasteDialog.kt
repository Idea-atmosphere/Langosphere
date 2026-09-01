package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.SubtitleJsonParser
import com.example.ui.theme.AppStrings

/**
 * Paste-JSON import dialog for the subtitle section. The content is
 * auto-detected live as the user types/pastes (see
 * SubtitleJsonParser.looksLikeSubtitleJson), and a one-tap "Sample JSON"
 * button fills the field with a valid example package so users can test the
 * import immediately. Real validation (with user-friendly errors) happens on
 * import inside AppViewModel.importJsonSubtitleText.
 */
@Composable
fun JsonSubtitlePasteDialog(
    strings: AppStrings,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    val detected = remember(jsonText) { SubtitleJsonParser.looksLikeSubtitleJson(jsonText) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.jsonPasteDialogTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(strings.jsonPastePlaceholder, style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 7,
                    maxLines = 12,
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Live auto-detection feedback
                when {
                    detected -> Text(
                        text = strings.jsonDetectedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    jsonText.isNotBlank() -> Text(
                        text = strings.jsonNotSubtitleJson,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        jsonText = SubtitleJsonParser.buildSampleJsonString()
                    }) {
                        Text(strings.jsonLoadSampleBtn, style = MaterialTheme.typography.labelMedium)
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(strings.cancel, style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                onImport(jsonText)
                                onDismiss()
                            },
                            enabled = jsonText.isNotBlank(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(strings.jsonImportBtn, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
