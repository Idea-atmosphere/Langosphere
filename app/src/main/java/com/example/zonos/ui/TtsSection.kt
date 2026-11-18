package com.example.zonos.ui

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PillTone
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.AppStrings
import com.example.zonos.DesignSource
import com.example.zonos.EngineStatus
import com.example.zonos.ModelsLocation
import com.example.zonos.ZonoLanguages
import com.example.zonos.ZonosTtsController
import java.util.Locale

/**
 * Learning tools ▸ Text to speech.
 *
 * Top to bottom: the CORE model pack (download / complete / verify /
 * delete, plus the manual routes: find an existing folder, import a zip,
 * copy from a folder), the voice + language + speed used by the subtitle
 * cards and the book reader, and the full Voice Studio page. The Studio
 * opens full-height in place of the list (its own scrolling must not
 * fight the section's), and the system back gesture closes it again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TtsSection(
    tts: ZonosTtsController,
    strings: AppStrings,
    isFa: Boolean,
    pairSourceLanguage: String,
    modifier: Modifier = Modifier,
) {
    val core by tts.coreStatus.collectAsState()
    val folders by tts.folderStatuses.collectAsState()
    val download by tts.download.collectAsState()
    val busy by tts.busy.collectAsState()
    val engineState by tts.engine.state.collectAsState()
    val voices by tts.voices.collectAsState()
    val selectedVoice by tts.selectedVoice.collectAsState()
    val speed by tts.speed.collectAsState()
    val language by tts.language.collectAsState()
    val designSource by tts.designSource.collectAsState()

    var studioOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val locateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) tts.locateTree(uri)
    }
    val importTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) tts.importTree(uri)
    }
    val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) tts.importZip(uri)
    }

    BackHandler(enabled = studioOpen) { studioOpen = false }

    if (studioOpen) {
        StudioPane(
            tts = tts,
            strings = strings,
            isFa = isFa,
            onClose = { studioOpen = false },
            modifier = modifier,
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = strings.ttsSectionIntro,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface.copy(alpha = 0.75f),
        )

        // ── Model pack ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.ttsModelTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val total = formatBytes(core.def.totalBytes)
                when {
                    core.installed -> StatusPill(
                        text = strings.ttsModelInstalled(total),
                        tone = PillTone.Positive,
                        icon = Icons.Filled.Check,
                    )
                    core.presentFiles > 0 -> StatusPill(
                        text = strings.ttsModelIncomplete(core.presentFiles, core.def.files.size),
                        tone = PillTone.Warning,
                    )
                    else -> StatusPill(
                        text = strings.ttsModelMissing(total),
                        tone = PillTone.Neutral,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.ttsModelPathHint(ModelsLocation.MODELS_DISPLAY_PATH),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface.copy(alpha = 0.6f),
            )
            if (!core.installed && core.presentFiles > 0) {
                Spacer(Modifier.height(6.dp))
                folders.forEach { f ->
                    Text(
                        text = strings.ttsFolderRow(f.folder, f.presentFiles, f.totalFiles),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (f.complete) scheme.onSurface.copy(alpha = 0.6f) else scheme.error,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val dl = download
            if (dl != null) {
                val fraction = if (dl.total > 0) (dl.done.toFloat() / dl.total).coerceIn(0f, 1f) else 0f
                Text(
                    text = strings.ttsDownloading(dl.file),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.ttsDownloadProgress(formatBytes(dl.done), formatBytes(dl.total), formatBytes(dl.bytesPerSec)),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { tts.cancelDownload() }) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.ttsCancelDownload)
                }
            } else {
                when {
                    core.installed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { tts.verifyCore() }, enabled = !busy) {
                            Text(strings.ttsVerify)
                        }
                        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.ttsDeleteModel)
                        }
                    }
                    core.presentFiles > 0 -> {
                        val missingBytes = core.missing.sumOf { it.size }
                        GradientButton(
                            text = strings.ttsCompletePack(formatBytes(missingBytes)),
                            onClick = { tts.downloadCore(missingOnly = true) },
                            icon = Icons.Filled.Download,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { tts.verifyCore() }, enabled = !busy) {
                                Text(strings.ttsVerify)
                            }
                            OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                                Text(strings.ttsDeleteModel)
                            }
                        }
                    }
                    else -> GradientButton(
                        text = strings.ttsDownload(formatBytes(core.def.totalBytes)),
                        onClick = { tts.downloadCore(missingOnly = false) },
                        icon = Icons.Filled.Download,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Engine status (the hidden player that reads for the cards / book).
            val statusText = when (engineState.status) {
                EngineStatus.NOT_LOADED -> strings.ttsEngineStatusNotLoaded
                EngineStatus.LOADING -> strings.ttsEngineStatusLoading(
                    if (engineState.total > 0) {
                        "${(engineState.loaded * 100 / engineState.total).coerceIn(0, 100)}%"
                    } else {
                        engineState.phase
                    }
                )
                EngineStatus.READY -> strings.ttsEngineStatusReady
                EngineStatus.ERROR, EngineStatus.MISSING -> strings.ttsEngineStatusError
                EngineStatus.NO_MODEL -> strings.ttsEngineStatusNoModel
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (engineState.status == EngineStatus.ERROR) scheme.error else scheme.onSurface.copy(alpha = 0.7f),
            )
            val detail = when {
                engineState.error == "no_webgpu" -> strings.ttsEngineNoWebgpu
                engineState.status == EngineStatus.ERROR -> engineState.error ?: engineState.lastPageError
                else -> engineState.lastInterceptorError
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error.copy(alpha = 0.85f),
                )
            }
        }

        // ── Manual routes ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.ttsManualTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.ttsManualBody(ModelsLocation.MODELS_DISPLAY_PATH),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { locateLauncher.launch(null) }, enabled = !busy && download == null) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.ttsLocateFolder)
                }
                OutlinedButton(
                    onClick = { importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    enabled = !busy && download == null,
                ) {
                    Text(strings.ttsImportZip)
                }
                OutlinedButton(onClick = { importTreeLauncher.launch(null) }, enabled = !busy && download == null) {
                    Text(strings.ttsImportFolder)
                }
            }
        }

        // ── Voice ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.ttsVoiceTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.ttsVoiceHint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(8.dp))
            val grouped = remember(voices) { voices.groupBy { it.language } }
            grouped.forEach { (lang, list) ->
                Text(
                    text = lang,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { v ->
                        val chosen = v.id == selectedVoice
                        val chipIcon: (@Composable () -> Unit)? = if (chosen) {
                            { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) }
                        } else {
                            null
                        }
                        FilterChip(
                            selected = chosen,
                            onClick = { tts.selectVoice(v.id) },
                            label = { Text(v.name) },
                            leadingIcon = chipIcon,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (val d = designSource) {
                    DesignSource.Default -> strings.ttsDesignDefault
                    DesignSource.Bench -> strings.ttsDesignBench
                    is DesignSource.Persona -> strings.ttsDesignPersona(d.name)
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface.copy(alpha = 0.6f),
            )
        }

        // ── Language + speed ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.ttsLanguageTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            var menuOpen by remember { mutableStateOf(false) }
            val autoId = ZonoLanguages.resolve(pairSourceLanguage)
            fun labelFor(id: String): String {
                val l = ZonoLanguages.byId(id) ?: return id
                return if (isFa) l.nameFa else l.nameEn
            }
            val currentLabel = if (language == ZonosTtsController.LANGUAGE_AUTO) {
                strings.ttsLanguageAuto(labelFor(autoId))
            } else {
                labelFor(language)
            }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { menuOpen = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(currentLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.ttsLanguageAuto(labelFor(autoId))) },
                        onClick = { tts.setLanguage(ZonosTtsController.LANGUAGE_AUTO); menuOpen = false },
                    )
                    ZonoLanguages.ALL.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(if (isFa) l.nameFa else l.nameEn) },
                            onClick = { tts.setLanguage(l.id); menuOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.ttsSpeedTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = String.format(Locale.US, "%.2f×", speed),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = speed,
                onValueChange = { tts.setSpeed(it) },
                valueRange = ZonosTtsController.SPEED_MIN..ZonosTtsController.SPEED_MAX,
                steps = 29,
            )
        }

        // ── Studio ──
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.ttsStudioTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.ttsStudioHint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.75f),
            )
            if (!core.installed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.ttsStudioNeedsModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                text = strings.ttsStudioOpen,
                onClick = { studioOpen = true },
                enabled = core.installed,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.ttsDeleteConfirmTitle) },
            text = { Text(strings.ttsDeleteConfirmBody(ModelsLocation.MODELS_DISPLAY_PATH)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; tts.deleteCore() }) {
                    Text(strings.ttsDeleteModel, color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(strings.cancel) }
            },
        )
    }
}

/**
 * The Studio WebView, full height. The WebView instance is owned by the
 * controller and reused across openings (its page state survives); the
 * file chooser of `<input type=file>` goes through a SAF launcher.
 */
@Composable
private fun StudioPane(
    tts: ZonosTtsController,
    strings: AppStrings,
    isFa: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingChooser = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val chooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = pendingChooser.value
        pendingChooser.value = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    DisposableEffect(tts) {
        tts.setStudioDefaultLang(if (isFa) "fa" else "en")
        tts.studio.onFileChooser = { callback: ValueCallback<Array<Uri>>, intent: Intent ->
            pendingChooser.value?.onReceiveValue(null)
            pendingChooser.value = callback
            chooserLauncher.launch(intent)
        }
        tts.studio.load()
        onDispose {
            tts.studio.onFileChooser = null
            pendingChooser.value?.onReceiveValue(null)
            pendingChooser.value = null
            tts.studio.detachFromParent()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.ttsStudioTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SoftIconButton(
                icon = Icons.Filled.Close,
                contentDescription = strings.ttsStudioClose,
                onClick = onClose,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { ctx -> tts.studio.attachedWebView(ctx) },
                update = { tts.studio.injectStudioHeights() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
