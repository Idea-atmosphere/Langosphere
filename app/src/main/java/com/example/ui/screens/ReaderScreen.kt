package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.ClickableWordText
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.brandBrush
import com.example.ui.components.fadingEdges
import com.example.ui.components.neoHardShadow
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppStrings
import com.example.ui.theme.NeoBrutalismAccent
import com.example.ui.theme.isNeobrutalismDesign

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
    val importedFiles by viewModel.importedDictFiles.collectAsState()

    var showColorPickerDialog by remember { mutableStateOf(false) }
    // The imported-dictionary list used to be permanently expanded and pushed
    // the actual reading area off screen once a few files were imported.
    var showDictFiles by remember { mutableStateOf(false) }

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
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
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
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
            },
            title = {
                Text(
                    text = strings.errorTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            text = {
                Text(
                    text = importError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
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
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = rFileName,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SoftIconButton(
                            icon = Icons.Default.FormatColorText,
                            contentDescription = strings.textColorCd,
                            onClick = { showColorPickerDialog = true },
                            tint = readerTextColor,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SoftIconButton(
                            icon = Icons.Default.FullscreenExit,
                            contentDescription = strings.exitFullscreenCd,
                            onClick = { viewModel.setReaderFullscreen(false) },
                        )
                    }

                    if (isPdf && pageCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
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
                            .fadingEdges()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ClickableWordText(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = readerTextColor ?: Color.Unspecified,
                                textAlign = TextAlign.Start
                            ),
                            highlightColor = readerTextColor ?: MaterialTheme.colorScheme.primary,
                            onWordClick = { word -> viewModel.lookupWord(word) }
                        )
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // ── Dictionary panel: state, actions and (collapsed) imported files ──
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = if (isDictLoaded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDictLoaded) strings.dictLoadedActive else strings.dictEmpty,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.dictHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                StatusPill(
                    text = strings.importedFilesCount(importedFiles.size),
                    icon = if (isDictLoaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                    tone = if (isDictLoaded) PillTone.Positive else PillTone.Negative,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientButton(
                    text = strings.addDictionary,
                    onClick = { dictFileLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1.1f),
                )

                if (isDictLoaded) {
                    OutlinedTonalAction(
                        text = strings.clearAll,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.clearAllDictionaries() },
                        modifier = Modifier.weight(0.9f),
                    )
                }
            }

            // Imported dictionary files — collapsed by default.
            if (importedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                val chevronRotation by animateFloatAsState(
                    targetValue = if (showDictFiles) 180f else 0f,
                    animationSpec = tween(durationMillis = 240),
                    label = "dict-files-chevron",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDictFiles = !showDictFiles }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.importedFilesCount(importedFiles.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
                AnimatedVisibility(visible = showDictFiles) {
                    Column {
                        importedFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                SoftIconButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = strings.deleteCd,
                                    onClick = { viewModel.removeDictionary(file.name, file.type) },
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 30.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Document bar ──
        if (rFileName.isEmpty()) {
            // Nothing loaded yet: make the import target the hero of the screen
            // instead of a plain outlined button.
            val neo = isNeobrutalismDesign()
            val heroShape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (neo) {
                            Modifier
                                .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 4.dp)
                                .background(NeoBrutalismAccent)
                                .border(2.dp, MaterialTheme.colorScheme.outline)
                        } else {
                            Modifier
                                .clip(heroShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                        )
                                    )
                                )
                                .border(1.dp, brandBrush(alpha = 0.35f), heroShape)
                        }
                    )
                    .clickable { textFileLauncher.launch(arrayOf("*/*")) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = if (neo) Color.Black else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.selectTextPdf,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (neo) Color.Black else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            val neo = isNeobrutalismDesign()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(if (neo) RoundedCornerShape(0.dp) else CircleShape)
                        .background(
                            if (neo) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                        .then(
                            if (neo) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline) else Modifier
                        )
                        .clickable { textFileLauncher.launch(arrayOf("*/*")) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = strings.selectTextPdf,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rFileName,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }

                // Text-color and fullscreen toggles — most useful once a document
                // is actually loaded, so only show them then.
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SoftIconButton(
                        icon = Icons.Default.FormatColorText,
                        contentDescription = strings.textColorCd,
                        onClick = { showColorPickerDialog = true },
                        tint = readerTextColor,
                        size = 36.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SoftIconButton(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = strings.fullscreenCd,
                        onClick = { viewModel.toggleReaderFullscreen() },
                        size = 36.dp,
                    )
                }
            }
        }

        // PDF page navigator: previous/next buttons plus a manual page number field.
        if (isPdf && pageCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
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

        // ── Reading surface (the "paper" the PDF/text is shown on) ──
        val neo = isNeobrutalismDesign()
        val readingShape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (neo) {
                        Modifier
                            // Paper-white card, ink border and a hard offset
                            // shadow — the neubrutalist page frame. Shadow is
                            // drawn before the fill so it stays visible, and
                            // there is no clip in this branch to cut it off.
                            .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 5.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(2.dp, MaterialTheme.colorScheme.outline)
                    } else {
                        Modifier
                            .clip(readingShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                readingShape
                            )
                    }
                )
        ) {
            if (text.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = strings.emptyReaderHint,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    ClickableWordText(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = readerTextColor ?: Color.Unspecified,
                            textAlign = TextAlign.Start
                        ),
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

/** Text action in a single tone, used next to the primary GradientButton. */
@Composable
private fun OutlinedTonalAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isNeobrutalismDesign()) {
        // Neobrutalism: a flat white/raised square with an ink border; the
        // tone survives only in the text so destructive stays legible.
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(2.dp, MaterialTheme.colorScheme.outline)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.32f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
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
    val neo = isNeobrutalismDesign()
    val shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp)

    // Keep page controls LTR so Prev stays left / Next stays right even
    // when the app composition is RTL (FA). Otherwise SpaceBetween flips.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (neo) {
                        // Shadow first, then the flat card and its ink border;
                        // the clip below only applies to the soft designs.
                        Modifier
                            .neoHardShadow(MaterialTheme.colorScheme.outline, offset = 3.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline)
                    } else {
                        Modifier
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                shape
                            )
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PageStepButton(
                text = strings.prevPage,
                enabled = currentPage > 0,
                onClick = onPrevious,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input -> if (input.length <= 6 && input.all { it.isDigit() }) pageInput = input },
                    modifier = Modifier.width(72.dp),
                    singleLine = true,
                    shape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val target = pageInput.toIntOrNull()
                        if (target != null) onGoToPage((target - 1).coerceIn(0, pageCount - 1))
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.pageOfCount(pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PageStepButton(
                text = strings.nextPage,
                enabled = currentPage < pageCount - 1,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun PageStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.4f)
    if (isNeobrutalismDesign()) {
        Box(
            modifier = Modifier
                .background(
                    if (enabled) {
                        scheme.surfaceContainerLowest
                    } else {
                        scheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                )
                .border(1.5.dp, scheme.outline)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
        return
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (enabled) 0.14f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Lets the user pick the reader's text color, either from a grid of preset
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
                textAlign = TextAlign.Start
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.colorDialogSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                FlowRowSwatches(
                    presets = presets,
                    selectedArgb = currentColorArgb,
                    onSelect = { onColorSelected(it); onDismiss() }
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = strings.customColorLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Live preview of the mixed color, checkered against the two
                // reading surfaces it will actually sit on.
                val neo = isNeobrutalismDesign()
                val previewShape = if (neo) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(previewShape)
                        .background(customColor)
                        .border(
                            width = if (neo) 2.dp else 1.dp,
                            color = if (neo) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            },
                            shape = previewShape
                        )
                )
                Spacer(modifier = Modifier.height(12.dp))

                ColorSlider(label = strings.redLabel, value = red, tone = AccentRed, onValueChange = { red = it })
                ColorSlider(label = strings.greenLabel, value = green, tone = AccentGreen, onValueChange = { green = it })
                ColorSlider(label = strings.blueLabel, value = blue, tone = AccentIndigo, onValueChange = { blue = it })
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rowItems.forEach { (argb, label) ->
                val swatchColor = argb?.let { Color(it) } ?: Color.Transparent
                val isSelected = argb == selectedArgb
                val ringWidth by animateFloatAsState(
                    targetValue = if (isSelected) 3f else 1f,
                    animationSpec = tween(durationMillis = 200),
                    label = "swatch-ring",
                )
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = tween(durationMillis = 200),
                    label = "swatch-scale",
                )
                val neo = isNeobrutalismDesign()
                val swatchShape = if (neo) RoundedCornerShape(0.dp) else CircleShape
                val selectionRing = if (isSelected) {
                    if (neo) NeoBrutalismAccent else MaterialTheme.colorScheme.primary
                } else {
                    if (neo) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(swatchShape)
                            .background(if (argb != null) swatchColor else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = ringWidth.dp,
                                color = selectionRing,
                                shape = swatchShape
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
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(text = label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    tone: Color,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tone,
            modifier = Modifier.width(36.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = tone,
                activeTrackColor = tone,
                inactiveTrackColor = tone.copy(alpha = 0.22f),
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
