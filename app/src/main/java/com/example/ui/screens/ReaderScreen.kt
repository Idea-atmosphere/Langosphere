package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.ClickableWordText
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppStrings

@Composable
fun ReaderScreen(viewModel: AppViewModel) {
    val text by viewModel.readerText.collectAsState()
    val rFileName by viewModel.readerFileName.collectAsState()
    val isDictLoaded by viewModel.isDictionaryLoaded.collectAsState()
    val isPdf by viewModel.readerIsPdf.collectAsState()
    val pageCount by viewModel.readerPageCount.collectAsState()
    val currentPage by viewModel.readerCurrentPage.collectAsState()
    val isFullscreen by viewModel.isReaderFullscreen.collectAsState()
    val readerTextColorArgb by viewModel.readerTextColor.collectAsState()
    val readerTextColor = readerTextColorArgb?.let { Color(it) }
    val appLanguage by viewModel.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    val isImporting by viewModel.isImportingDict.collectAsState()
    val importCount by viewModel.importCount.collectAsState()
    val importError by viewModel.importError.collectAsState()

    var showColorPickerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val textFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            viewModel.loadTextFile(it, mimeType)
        }
    }

    val dictFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.loadDictionary(it)
        }
    }

    // Importing Progress Dialog
    if (isImporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = strings.importingDbTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.importingWordsCount(importCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Import Error Dialog
    if (importError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportError() },
            title = {
                Text(
                    text = strings.errorTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = importError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { viewModel.clearImportError() }) {
                    Text(strings.ok)
                }
            }
        )
    }

    // Text Color Picker Dialog
    if (showColorPickerDialog) {
        TextColorPickerDialog(
            currentColorArgb = readerTextColorArgb,
            onColorSelected = { viewModel.setReaderTextColor(it) },
            onDismiss = { showColorPickerDialog = false },
            strings = strings
        )
    }

    // Fullscreen reading mode — shown over everything else when active, with
    // the same PDF page navigator (when applicable) and a close button.
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { viewModel.setReaderFullscreen(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rFileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showColorPickerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.FormatColorText,
                                    contentDescription = strings.textColorCd,
                                    tint = readerTextColor ?: MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.setReaderFullscreen(false) }) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = strings.exitFullscreenCd,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isPdf && pageCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        PdfPageNavigator(
                            currentPage = currentPage,
                            pageCount = pageCount,
                            onGoToPage = { viewModel.goToReaderPage(it) },
                            onPrevious = { viewModel.previousReaderPage() },
                            onNext = { viewModel.nextReaderPage() },
                            strings = strings
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ClickableWordText(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge.copy(color = readerTextColor ?: Color.Unspecified),
                            highlightColor = readerTextColor ?: MaterialTheme.colorScheme.primary,
                            onWordClick = { word -> viewModel.lookupWord(word) }
                        )
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Dictionary Status Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDictLoaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isDictLoaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isDictLoaded) AccentGreen else AccentRed,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (isDictLoaded) strings.dictLoadedActive else strings.dictEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.dictHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { dictFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1.1f),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(strings.addDictionary, style = MaterialTheme.typography.labelLarge)
                    }

                    if (isDictLoaded) {
                        OutlinedButton(
                            onClick = { viewModel.clearAllDictionaries() },
                            modifier = Modifier.weight(0.9f),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(strings.clearAll, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Imported Dictionary Files List ──
        val importedFiles by viewModel.importedDictFiles.collectAsState()
        if (importedFiles.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = strings.importedFilesCount(importedFiles.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    importedFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (file.type) {
                                        "mdx" -> Icons.Default.CheckCircle
                                        "mdd" -> Icons.Default.Warning
                                        "db" -> Icons.Default.Storage
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when (file.type) {
                                        "mdx" -> MaterialTheme.colorScheme.primary
                                        "mdd" -> MaterialTheme.colorScheme.tertiary
                                        "db" -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removeDictionary(file.name, file.type) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.deleteCd,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Document Selection
        OutlinedButton(
            onClick = { textFileLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.selectTextPdf, fontWeight = FontWeight.Bold)
        }

        if (rFileName.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📄 $rFileName",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                // Text-color and fullscreen toggles — most useful once a document
                // is actually loaded, so only show them then.
                if (text.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showColorPickerDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatColorText,
                                contentDescription = strings.textColorCd,
                                tint = readerTextColor ?: MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleReaderFullscreen() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = strings.fullscreenCd,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // PDF page navigator: previous/next buttons plus a manual page number field.
        if (isPdf && pageCount > 0) {
            PdfPageNavigator(
                currentPage = currentPage,
                pageCount = pageCount,
                onGoToPage = { viewModel.goToReaderPage(it) },
                onPrevious = { viewModel.previousReaderPage() },
                onNext = { viewModel.nextReaderPage() },
                strings = strings,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Text Display Area
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                if (text.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.emptyReaderHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    ClickableWordText(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge.copy(color = readerTextColor ?: Color.Unspecified),
                        highlightColor = readerTextColor ?: MaterialTheme.colorScheme.primary,
                        onWordClick = { word ->
                            viewModel.lookupWord(word)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Prev/next buttons plus a manual page-number field for navigating an
 * imported PDF's pages (1-based in the UI, 0-based internally in the
 * ViewModel). Used both in the normal reader layout and the fullscreen
 * reading dialog.
 */
@Composable
private fun PdfPageNavigator(
    currentPage: Int,
    pageCount: Int,
    onGoToPage: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    var pageInput by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = currentPage > 0,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(strings.prevPage, style = MaterialTheme.typography.labelMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pageInput,
                onValueChange = { input -> if (input.length <= 6 && input.all { it.isDigit() }) pageInput = input },
                modifier = Modifier.width(64.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val target = pageInput.toIntOrNull()
                    if (target != null) onGoToPage((target - 1).coerceIn(0, pageCount - 1))
                })
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.pageOfCount(pageCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedButton(
            onClick = onNext,
            enabled = currentPage < pageCount - 1,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(strings.nextPage, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Lets the user pick the reader's text color, either from a row of preset
 * swatches (including a "default" option that clears the override back to
 * the theme's normal text color) or by mixing a custom color with RGB
 * sliders. Selecting any option applies and persists it immediately via
 * onColorSelected; there's no separate "save" step besides closing the dialog.
 */
@Composable
private fun TextColorPickerDialog(
    currentColorArgb: Int?,
    onColorSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
    strings: AppStrings
) {
    val defaultTextColor = MaterialTheme.colorScheme.onSurface
    val baseColor = currentColorArgb?.let { Color(it) } ?: defaultTextColor
    var red by remember { mutableFloatStateOf(baseColor.red) }
    var green by remember { mutableFloatStateOf(baseColor.green) }
    var blue by remember { mutableFloatStateOf(baseColor.blue) }
    val customColor = Color(red = red, green = green, blue = blue)

    val presets = listOf(
        null to strings.presetDefault,
        Color(0xFF000000).toArgb() to strings.presetBlack,
        Color(0xFFFFFFFF).toArgb() to strings.presetWhite,
        AccentGreen.toArgb() to strings.presetGreen,
        AccentRed.toArgb() to strings.presetRed,
        AccentCyan.toArgb() to strings.presetCyan,
        AccentIndigo.toArgb() to strings.presetIndigo,
        AccentAmber.toArgb() to strings.presetAmber
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.textColorCd,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.colorDialogSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                FlowRowSwatches(
                    presets = presets,
                    selectedArgb = currentColorArgb,
                    onSelect = { onColorSelected(it); onDismiss() }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.customColorLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(customColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))

                ColorSlider(label = strings.redLabel, value = red, onValueChange = { red = it })
                ColorSlider(label = strings.greenLabel, value = green, onValueChange = { green = it })
                ColorSlider(label = strings.blueLabel, value = blue, onValueChange = { blue = it })
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onColorSelected(customColor.toArgb()); onDismiss() }) {
                Text(strings.applyCustomColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.ok)
            }
        }
    )
}

@Composable
private fun FlowRowSwatches(
    presets: List<Pair<Int?, String>>,
    selectedArgb: Int?,
    onSelect: (Int?) -> Unit
) {
    // Two simple rows of four swatches instead of a real FlowRow, to avoid
    // pulling in the experimental foundation-layout FlowRow API.
    presets.chunked(4).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rowItems.forEach { (argb, label) ->
                val swatchColor = argb?.let { Color(it) } ?: Color.Transparent
                val isSelected = argb == selectedArgb
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (argb != null) swatchColor else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { onSelect(argb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (argb == null) {
                            Icon(
                                imageVector = Icons.Default.FormatColorText,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatFileDate(timestamp: Long): String {
    val date = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
    return date.format(java.util.Date(timestamp))
}
