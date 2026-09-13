package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.logic.SubtitleJsonParser
import com.example.logic.autoTextDirection
import com.example.model.JsonSubtitlePackage
import com.example.ui.theme.AppStrings
import com.example.ui.theme.isNeobrutalismDesign

/**
 * Paste-JSON import dialog for the subtitle section. The content is
 * auto-detected live as the user types/pastes (see
 * SubtitleJsonParser.looksLikeSubtitleJson), and a one-tap "Sample JSON"
 * button fills the field with a valid example package so users can test the
 * import immediately. Real validation (with user-friendly errors) happens on
 * import inside AppViewModel.importJsonSubtitleText.
 *
 * A whole AI answer can be pasted as-is: when the model wrote its `CHUNK 1-50`
 * marker line (as the app's own prompt templates ask it to), the dialog says so
 * live and tells the user whether that chunk will be merged into the JSON that
 * is already loaded or replace it.
 *
 * @param loadedPackage the JSON that is currently imported, used only to
 *   explain what the import will do (merge vs. replace).
 */
@Composable
fun JsonSubtitlePasteDialog(
    strings: AppStrings,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
    loadedPackage: JsonSubtitlePackage? = null
) {
    var jsonText by remember { mutableStateOf("") }
    val detected = remember(jsonText) { SubtitleJsonParser.looksLikeSubtitleJson(jsonText) }
    // Live CHUNK detection, so the user sees that the marker line is handled
    // instead of wondering whether the paste will fail.
    val chunk = remember(jsonText) {
        if (jsonText.isBlank()) null else SubtitleJsonParser.detectChunkMarker(jsonText)
    }

    val neo = isNeobrutalismDesign()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = if (neo) RoundedCornerShape(0.dp) else MaterialTheme.shapes.extraLarge,
            elevation = if (neo) {
                CardDefaults.cardElevation(defaultElevation = 0.dp)
            } else {
                CardDefaults.cardElevation(defaultElevation = 8.dp)
            },
            border = if (neo) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            } else {
                null
            }
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
                    // Keep input auto-detected per content; JSON is usually LTR.
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        textDirection = jsonText.autoTextDirection()
                    ),
                    minLines = 7,
                    maxLines = 12,
                    shape = if (neo) RoundedCornerShape(0.dp) else MaterialTheme.shapes.medium,
                    colors = if (neo) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors()
                    }
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

                // Live CHUNK (AI answer) feedback: which chunk this is, and
                // whether it will be merged into the loaded JSON.
                if (chunk != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = if (neo) RoundedCornerShape(0.dp) else MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = "${chunk.shortLabel}  •  ${strings.jsonChunkDetectedLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.jsonChunkAutoStripHint,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = if (loadedPackage != null) {
                                    strings.jsonChunkMergeHint
                                } else {
                                    strings.jsonChunkReplaceHint
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
