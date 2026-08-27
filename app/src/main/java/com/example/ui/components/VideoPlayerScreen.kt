package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AppAccentColorState
import com.example.ui.theme.SubtitleColorState
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import com.example.model.SubtitleEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

enum class OverlayButton { CONTINUE, AUTO_PREV, AUTO_CURRENT }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri?,
    videoFileName: String = "",
    subEnList: List<SubtitleEntry>,
    subFaList: List<SubtitleEntry>,
    isFullScreen: Boolean,
    onFullScreenToggle: (Boolean) -> Unit,
    onWordClick: (String, String?, String?) -> Unit,
    onShiftSubEn: (Double) -> Unit = {},
    onShiftSubFa: (Double) -> Unit = {},
    subEnOffset: Double = 0.0,
    subFaOffset: Double = 0.0,
    onTranslateSubtitle: (Int) -> Unit = {},
    onSaveSrt: () -> Unit = {},
    isTranslatingSingle: Boolean = false,
    translatingIndex: Int = -1,
    singleTranslateError: String? = null,
    onStopTranslation: () -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.FA
) {
    val context = LocalContext.current
    val strings = remember(appLanguage) { AppStrings(appLanguage) }
    fun offsetText(v: Double): String {
        val formatted = String.format("%.2f", v)
        return if (strings.isEn) formatted else formatted.replace("-", "منفی ")
    }
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isGlassmorphismEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("glassmorphism_enabled", true)) }
    var currentTime by remember { mutableStateOf(0.0) }
    val videoStateKey = remember(videoUri) { "video_state_${videoUri?.hashCode() ?: 0}" }
    var hasRestoredPosition by remember { mutableStateOf(false) }

    val isAudio = remember(videoUri, videoFileName) {
        val name = videoFileName.lowercase()
        val uriStr = videoUri?.toString()?.lowercase() ?: ""
        name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".flac") ||
        uriStr.endsWith(".mp3") || uriStr.endsWith(".m4a") || uriStr.endsWith(".wav") || uriStr.endsWith(".aac") || uriStr.endsWith(".ogg") || uriStr.endsWith(".flac") ||
        uriStr.contains("audio")
    }
    var albumArtBitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var retriever: android.media.MediaMetadataRetriever? = null
                try {
                    retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) { albumArtBitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) } else { albumArtBitmap = null }
                } catch (e: Exception) { e.printStackTrace(); albumArtBitmap = null } finally { try { retriever?.release() } catch (e: Exception) { e.printStackTrace() } }
            }
        } else { albumArtBitmap = null }
    }

    var subtitleFontSizeFactor by remember { mutableStateOf(sharedPrefs.getFloat("subtitle_font_size_factor", 1.0f)) }
    var subtitleBottomPadding by remember { mutableStateOf(sharedPrefs.getFloat("subtitle_bottom_padding", 64f)) }
    // Backed by a process-wide singleton (ui/theme/Theme.kt) instead of a
    // per-composable `remember`, matching the same proven mechanism already
    // used for the app accent color, so subtitle colors reliably update live
    // and survive this screen being disposed/recreated on tab switches.
    val subtitleColorEn = SubtitleColorState.colorEn ?: Color.White
    val subtitleColorFa = SubtitleColorState.colorFa ?: AccentAmber
    var subtitleFontEn by remember { mutableStateOf(sharedPrefs.getString("subtitle_font_en", "default") ?: "default") }
    var subtitleFontFa by remember { mutableStateOf(sharedPrefs.getString("subtitle_font_fa", "default") ?: "default") }
    var customFontPathEn by remember { mutableStateOf(sharedPrefs.getString("subtitle_custom_font_path_en", null)) }
    var customFontPathFa by remember { mutableStateOf(sharedPrefs.getString("subtitle_custom_font_path_fa", null)) }
    val customFontFamilyEn = remember(customFontPathEn) { customFontPathEn?.let { path -> try { if (File(path).exists()) FontFamily(Font(File(path))) else null } catch (e: Exception) { null } } }
    val customFontFamilyFa = remember(customFontPathFa) { customFontPathFa?.let { path -> try { if (File(path).exists()) FontFamily(Font(File(path))) else null } catch (e: Exception) { null } } }
    val fontImportScope = rememberCoroutineScope()
    fun importCustomFont(uri: Uri, isEnglish: Boolean) {
        fontImportScope.launch(Dispatchers.IO) {
            try {
                val destFile = File(context.filesDir, if (isEnglish) "custom_subtitle_font_en.ttf" else "custom_subtitle_font_fa.ttf")
                context.contentResolver.openInputStream(uri)?.use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
                withContext(Dispatchers.Main) {
                    if (isEnglish) {
                        customFontPathEn = destFile.absolutePath; subtitleFontEn = "custom"
                        sharedPrefs.edit().putString("subtitle_custom_font_path_en", destFile.absolutePath).putString("subtitle_font_en", "custom").apply()
                    } else {
                        customFontPathFa = destFile.absolutePath; subtitleFontFa = "custom"
                        sharedPrefs.edit().putString("subtitle_custom_font_path_fa", destFile.absolutePath).putString("subtitle_font_fa", "custom").apply()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    val customFontLauncherEn = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { importCustomFont(it, true) } }
    val customFontLauncherFa = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { importCustomFont(it, false) } }
    var manualShiftEnText by remember { mutableStateOf("") }
    var manualShiftFaText by remember { mutableStateOf("") }
    fun fontFamilyFor(key: String, customFamily: FontFamily? = null): FontFamily = when (key) {
        "serif" -> FontFamily.Serif
        "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "custom" -> customFamily ?: FontFamily.Default
        else -> FontFamily.Default
    }
    var isSubtitlesEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("subtitles_enabled", true)) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var isSyncExpanded by remember { mutableStateOf(false) }
    var useSmartPauseControls by remember { mutableStateOf(sharedPrefs.getBoolean("smart_pause_enabled", true)) }
    var skipSeconds by remember { mutableStateOf(sharedPrefs.getInt("skip_seconds", 10)) }
    var containerWidth by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    var autoPauseAtTime by remember { mutableStateOf<Double?>(null) }
    var skipNextAutoScroll by remember { mutableStateOf(false) }

    data class ButtonTransform(var x: Float, var y: Float, var scale: Float, var rotation: Float)
    fun loadTransform(key: String, defaultX: Float, defaultY: Float): ButtonTransform {
        val raw = sharedPrefs.getString("overlay_$key", null)
        if (raw != null) { val parts = raw.split("|"); if (parts.size == 4) { return ButtonTransform(parts[0].toFloatOrNull() ?: defaultX, parts[1].toFloatOrNull() ?: defaultY, parts[2].toFloatOrNull() ?: 1f, parts[3].toFloatOrNull() ?: 0f) } }
        return ButtonTransform(defaultX, defaultY, 1f, 0f)
    }
    fun saveTransform(key: String, t: ButtonTransform) { sharedPrefs.edit().putString("overlay_$key", "${t.x}|${t.y}|${t.scale}|${t.rotation}").apply() }
    var showOverlaySettings by remember { mutableStateOf(false) }
    val continueTransform = remember { mutableStateOf(loadTransform("continue", 0f, -80f)) }
    val autoPrevTransform = remember { mutableStateOf(loadTransform("auto_prev", 0f, 0f)) }
    val autoCurrentTransform = remember { mutableStateOf(loadTransform("auto_current", 0f, 80f)) }
    val subtitleAlignmentMap = remember(subEnList, subFaList) { alignSubtitles(subEnList, subFaList) }
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener { override fun onIsPlayingChanged(isPlayingChange: Boolean) { isPlaying = isPlayingChange } }
        exoPlayer.addListener(listener); isPlaying = exoPlayer.isPlaying
        onDispose { exoPlayer.removeListener(listener) }
    }
    LaunchedEffect(videoUri) {
        videoUri?.let {
            exoPlayer.setMediaItem(MediaItem.fromUri(it))
            val savedPos = sharedPrefs.getLong("${videoStateKey}_position", 0L)
            val wasPlaying = sharedPrefs.getBoolean("${videoStateKey}_was_playing", true)
            val readyListener = object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) { if (savedPos > 0) exoPlayer.seekTo(savedPos); exoPlayer.playWhenReady = wasPlaying; exoPlayer.removeListener(this) }
                }
            }
            exoPlayer.addListener(readyListener); exoPlayer.prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (videoUri != null) { val editor = sharedPrefs.edit(); editor.putLong("${videoStateKey}_position", exoPlayer.currentPosition); editor.putBoolean("${videoStateKey}_was_playing", isPlaying); editor.apply() } }
    }
    val activity = remember { context.findActivity() }
    DisposableEffect(isFullScreen) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if (isFullScreen) { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; controller?.let { it.hide(WindowInsetsCompat.Type.systemBars()); it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } }
        else { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; controller?.let { it.show(WindowInsetsCompat.Type.navigationBars()); it.hide(WindowInsetsCompat.Type.statusBars()) } }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; controller?.let { it.show(WindowInsetsCompat.Type.navigationBars()); it.hide(WindowInsetsCompat.Type.statusBars()) } }
    }
    LaunchedEffect(exoPlayer, autoPauseAtTime) {
        while (isActive) {
            currentTime = exoPlayer.currentPosition / 1000.0
            autoPauseAtTime?.let { target -> if (currentTime >= target) { exoPlayer.pause(); autoPauseAtTime = null; skipNextAutoScroll = true } }
            delay(200)
        }
    }
    val activeIndex = remember(subEnList, currentTime) { subEnList.indexOfFirst { it.start <= currentTime && it.end >= currentTime } }
    val isAutoStoppingActive = autoPauseAtTime != null
    LaunchedEffect(activeIndex, isAutoStoppingActive) {
        if (!isAutoStoppingActive && activeIndex >= 0 && subEnList.isNotEmpty()) { if (skipNextAutoScroll) skipNextAutoScroll = false else listState.animateScrollToItem(activeIndex) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) exoPlayer.pause() else if (event == Lifecycle.Event.ON_DESTROY) exoPlayer.release() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); exoPlayer.release() }
    }
    val currentEn = subEnList.find { it.start <= currentTime && it.end >= currentTime }
    val effectiveFaList = subFaList
    val currentFa = effectiveFaList.find { it.start <= currentTime && it.end >= currentTime }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(if (isFullScreen) 1f else 0.38f).background(Color.Black).onGloballyPositioned { containerWidth = it.size.width }) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { PlayerView(context).apply { player = exoPlayer; useController = !useSmartPauseControls; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; subtitleView?.visibility = android.view.View.GONE } }, update = { playerView -> playerView.useController = !useSmartPauseControls })
            if (useSmartPauseControls && !isAudio && videoUri != null) {
                Box(modifier = Modifier.fillMaxSize().pointerInput(containerWidth, isPlaying, skipSeconds) { detectTapGestures(onTap = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, onDoubleTap = { offset -> val isLeft = offset.x < (containerWidth / 2); if (isLeft) exoPlayer.seekTo((exoPlayer.currentPosition - skipSeconds * 1000).coerceAtLeast(0)) else exoPlayer.seekTo(exoPlayer.currentPosition + skipSeconds * 1000) }) })
            }
            if (isAudio && videoUri != null) {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(colors = listOf(Color(0xFF1E1B4B), Color(0xFF03001C)), radius = 900f)).pointerInput(containerWidth, isPlaying, skipSeconds) { detectTapGestures(onTap = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, onDoubleTap = { offset -> val isLeft = offset.x < (containerWidth / 2); if (isLeft) exoPlayer.seekTo((exoPlayer.currentPosition - skipSeconds * 1000).coerceAtLeast(0)) else exoPlayer.seekTo(exoPlayer.currentPosition + skipSeconds * 1000) }) }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(16.dp)) {
                        val infiniteTransition = rememberInfiniteTransition(label = "record_rotation")
                        val rotationAngle by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(15000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "rotation")
                        val currentRotation = if (isPlaying) rotationAngle else 0f
                        Box(modifier = Modifier.size(if (isFullScreen) 170.dp else 115.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.fillMaxSize().border(width = 3.dp, brush = androidx.compose.ui.graphics.Brush.sweepGradient(colors = listOf(AccentIndigo, AccentRed, AccentCyan, AccentIndigo)), shape = RoundedCornerShape(100.dp)).graphicsLayer { rotationZ = currentRotation })
                            if (albumArtBitmap != null) { Image(bitmap = albumArtBitmap!!.asImageBitmap(), contentDescription = "Cover", modifier = Modifier.size(if (isFullScreen) 152.dp else 102.dp).graphicsLayer { rotationZ = if (isPlaying) rotationAngle * 0.3f else 0f }.clip(RoundedCornerShape(100.dp)).border(2.dp, Color.Black, RoundedCornerShape(100.dp)), contentScale = ContentScale.Crop) }
                            else { Image(painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_rhythm_cover), contentDescription = "Cover", modifier = Modifier.size(if (isFullScreen) 152.dp else 102.dp).graphicsLayer { rotationZ = if (isPlaying) rotationAngle * 0.3f else 0f }.clip(RoundedCornerShape(100.dp)).border(2.dp, Color.Black, RoundedCornerShape(100.dp)), contentScale = ContentScale.Crop) }
                            Box(modifier = Modifier.size(24.dp).background(Color.Black, RoundedCornerShape(100.dp)).border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(100.dp)))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (videoFileName.isNotEmpty()) { Text(text = videoFileName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp), maxLines = 1) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(if (isFullScreen) 150.dp else 95.dp).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(vertical = 8.dp, horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            if (currentEn != null || currentFa != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    currentEn?.let { enSub -> ClickableWordText(text = enSub.text, style = MaterialTheme.typography.titleLarge.copy(color = subtitleColorEn, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.1f), highlightColor = subtitleColorEn, onWordClick = { word -> exoPlayer.pause(); onWordClick(word, enSub.text, currentFa?.text) }) }
                                    if (currentEn != null && currentFa != null) Spacer(modifier = Modifier.height(4.dp))
                                    currentFa?.let { faSub -> Text(text = faSub.text, color = subtitleColorFa, style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)) }
                                }
                            } else { Text(text = strings.audioPlayingHint, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
            if (!isPlaying && videoUri != null && useSmartPauseControls) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { exoPlayer.play() }, contentAlignment = Alignment.Center) {
                    if (isFullScreen && currentEn != null) {
                        IconButton(onClick = { showOverlaySettings = !showOverlaySettings }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).size(36.dp)) { Icon(Icons.Default.Settings, contentDescription = strings.playerSettingsCd, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        if (showOverlaySettings) {
                            Card(modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(strings.resetPositionsTitle, color = Color.White, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp))
                                    Button(onClick = { continueTransform.value = ButtonTransform(0f, -80f, 1f, 0f); autoPrevTransform.value = ButtonTransform(0f, 0f, 1f, 0f); autoCurrentTransform.value = ButtonTransform(0f, 80f, 1f, 0f); saveTransform("continue", continueTransform.value); saveTransform("auto_prev", autoPrevTransform.value); saveTransform("auto_current", autoCurrentTransform.value); showOverlaySettings = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(8.dp)) { Text(strings.resetBtn, color = Color.White) }
                                }
                            }
                        }
                        @Composable
                        fun FreeFormButton(transformState: MutableState<ButtonTransform>, saveKey: String, content: @Composable () -> Unit) {
                            var transform by transformState
                            Box(modifier = Modifier.offset { IntOffset(transform.x.roundToInt(), transform.y.roundToInt()) }.graphicsLayer { scaleX = transform.scale; scaleY = transform.scale; rotationZ = transform.rotation }.pointerInput(Unit) { detectTransformGestures { _, pan, zoom, rotation -> transform = transform.copy(x = transform.x + pan.x, y = transform.y + pan.y, scale = (transform.scale * zoom).coerceIn(0.4f, 2.5f), rotation = transform.rotation + rotation) } }.pointerInput(Unit) { detectDragGestures(onDragEnd = { transformState.value = transform; saveTransform(saveKey, transform) }, onDrag = { change, dragAmount -> change.consume(); transform = transform.copy(x = transform.x + dragAmount.x, y = transform.y + dragAmount.y) }) }) { content() }
                        }
                        FreeFormButton(continueTransform, "continue") { Button(onClick = { exoPlayer.play() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), contentColor = Color.White), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(strings.resumePlayBtn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
                        if (activeIndex > 0) { val prevSub = subEnList[activeIndex - 1]; val prevFa = subtitleAlignmentMap[prevSub]; FreeFormButton(autoPrevTransform, "auto_prev") { OutlinedButton(onClick = { autoPauseAtTime = prevSub.end; exoPlayer.seekTo((prevSub.start * 1000).toLong()); exoPlayer.play() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(strings.autoStopPrevSubtitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(prevFa?.text ?: prevSub.text, style = MaterialTheme.typography.bodySmall, color = if (prevFa != null) AccentAmber else Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1) } } } }
                        FreeFormButton(autoCurrentTransform, "auto_current") { OutlinedButton(onClick = { autoPauseAtTime = currentEn.end; exoPlayer.seekTo((currentEn.start * 1000).toLong()); exoPlayer.play() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(strings.autoStopCurrentSubtitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(currentFa?.text ?: currentEn.text, style = MaterialTheme.typography.bodySmall, color = if (currentFa != null) AccentAmber else Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1) } } }
                    } else {
                        Box(modifier = Modifier.size(68.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(100.dp)).clickable { exoPlayer.play() }, contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = strings.resumeCd, tint = Color.White, modifier = Modifier.size(36.dp)) }
                    }
                }
            }
            IconButton(onClick = { onFullScreenToggle(!isFullScreen) }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) { Icon(imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = if (isFullScreen) strings.exitFullscreenCd else strings.fullscreenCd, tint = Color.White) }
            IconButton(onClick = { showSubtitleSettings = !showSubtitleSettings }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) { Icon(imageVector = Icons.Default.Settings, contentDescription = strings.playerSettingsCd, tint = Color.White) }
            if (isSubtitlesEnabled && !isAudio && (currentEn != null || currentFa != null)) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = subtitleBottomPadding.dp, start = 24.dp, end = 24.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).clickable { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }.padding(vertical = 10.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    currentEn?.let { enSub -> ClickableWordText(text = enSub.text, style = MaterialTheme.typography.titleLarge.copy(color = subtitleColorEn, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn), textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleLarge.fontSize * subtitleFontSizeFactor), highlightColor = subtitleColorEn, onWordClick = { word -> exoPlayer.pause(); onWordClick(word, enSub.text, currentFa?.text) }, modifier = Modifier.padding(bottom = 4.dp)) }
                    currentFa?.let { Text(text = it.text, color = subtitleColorFa, style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa), fontWeight = FontWeight.Medium, fontSize = MaterialTheme.typography.titleMedium.fontSize * subtitleFontSizeFactor), textAlign = TextAlign.Center) }
                }
            }
            if (showSubtitleSettings) {
                Dialog(onDismissRequest = { showSubtitleSettings = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Card(modifier = Modifier.padding(16.dp).fillMaxWidth(0.96f).fillMaxHeight(0.9f).let { m -> if (isGlassmorphismEnabled) m.border(width = 1.dp, brush = androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))), shape = RoundedCornerShape(24.dp)) else m }, colors = CardDefaults.cardColors(containerColor = if (isGlassmorphismEnabled) Color(0xFF1C1C1E).copy(alpha = 0.97f) else Color(0xFE1A1A1A)), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                    Column(modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(bottom = 14.dp)) { Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(text = strings.playerSettingsTitle, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(strings.design1Title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.design1Desc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall) }; Switch(checked = isGlassmorphismEnabled, onCheckedChange = { isGlassmorphismEnabled = it; sharedPrefs.edit().putBoolean("glassmorphism_enabled", it).apply() }) }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(strings.appAccentColorTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(strings.appAccentColorDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            val appAccentOptions = remember { listOf<Color?>(null, Color(0xFF3D6E63), AccentAmber, AccentCyan, AccentGreen, AccentRed, AccentIndigo, Color(0xFFE91E8C), Color(0xFF5C6BC0), Color(0xFFFF7043)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                appAccentOptions.forEach { swatch ->
                                    val isSelected = AppAccentColorState.color == swatch
                                    if (swatch == null) {
                                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = 0.1f)).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(100.dp)).clickable { AppAccentColorState.color = null; sharedPrefs.edit().remove("app_accent_color").apply() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = strings.defaultCd, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(14.dp)) }
                                    } else {
                                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(100.dp)).clickable { AppAccentColorState.color = swatch; sharedPrefs.edit().putInt("app_accent_color", swatch.toArgb()).apply() })
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(strings.showSubtitlesTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.showSubtitlesDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall) }; Switch(checked = isSubtitlesEnabled, onCheckedChange = { isSubtitlesEnabled = it; sharedPrefs.edit().putBoolean("subtitles_enabled", it).apply() }) }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(strings.smartPauseTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.smartPauseDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall) }; Switch(checked = useSmartPauseControls, onCheckedChange = { useSmartPauseControls = it; sharedPrefs.edit().putBoolean("smart_pause_enabled", it).apply() }) }
                        if (useSmartPauseControls) { HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f)); Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(strings.doubleTapSkipTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.secondsLabel(skipSeconds), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(6.dp)); Slider(value = skipSeconds.toFloat(), onValueChange = { skipSeconds = it.toInt(); sharedPrefs.edit().putInt("skip_seconds", it.toInt()).apply() }, valueRange = 2f..30f, steps = 28, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.24f))) } }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(strings.subtitleFontSizeTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text("${(subtitleFontSizeFactor * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(6.dp)); Slider(value = subtitleFontSizeFactor, onValueChange = { subtitleFontSizeFactor = it; sharedPrefs.edit().putFloat("subtitle_font_size_factor", it).apply() }, valueRange = 0.6f..2.0f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.24f))) }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(strings.subtitlePositionTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text("${subtitleBottomPadding.toInt()}dp", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(6.dp)); Slider(value = subtitleBottomPadding, onValueChange = { subtitleBottomPadding = it; sharedPrefs.edit().putFloat("subtitle_bottom_padding", it).apply() }, valueRange = 16f..250f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.24f))) }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(strings.subtitleColorTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            val subtitleColorOptions = remember { listOf(Color.White, AccentAmber, AccentCyan, AccentGreen, AccentRed, AccentIndigo, Color(0xFFFFD54F), Color(0xFF64B5F6)) }
                            Text(strings.subEnParenLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                subtitleColorOptions.forEach { swatch ->
                                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (subtitleColorEn == swatch) 2.dp else 1.dp, color = if (subtitleColorEn == swatch) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(100.dp)).clickable { SubtitleColorState.colorEn = swatch; sharedPrefs.edit().putInt("subtitle_color_en", swatch.toArgb()).apply() })
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(strings.subFaParenLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                subtitleColorOptions.forEach { swatch ->
                                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (subtitleColorFa == swatch) 2.dp else 1.dp, color = if (subtitleColorFa == swatch) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(100.dp)).clickable { SubtitleColorState.colorFa = swatch; sharedPrefs.edit().putInt("subtitle_color_fa", swatch.toArgb()).apply() })
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(strings.subtitleFontTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(strings.subtitleFontDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            val fontOptions = remember(strings) { listOf("default" to strings.fontDefault, "serif" to "Serif", "sansserif" to "Sans", "monospace" to "Mono", "cursive" to "Cursive") }
                            Text(strings.fontEnLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                fontOptions.forEach { (key, label) ->
                                    val selected = subtitleFontEn == key
                                    Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { subtitleFontEn = key; sharedPrefs.edit().putString("subtitle_font_en", key).apply() }) {
                                        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamilyFor(key)), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                                if (customFontPathEn != null) {
                                    val selected = subtitleFontEn == "custom"
                                    Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { subtitleFontEn = "custom"; sharedPrefs.edit().putString("subtitle_font_en", "custom").apply() }) {
                                        Text(strings.fontCustomLabel, color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall.copy(fontFamily = customFontFamilyEn ?: FontFamily.Default), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { customFontLauncherEn.launch("*/*") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(strings.importCustomFontBtn, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                if (customFontPathEn != null) { TextButton(onClick = { customFontPathEn = null; if (subtitleFontEn == "custom") { subtitleFontEn = "default"; sharedPrefs.edit().putString("subtitle_font_en", "default").apply() }; sharedPrefs.edit().remove("subtitle_custom_font_path_en").apply() }) { Text(strings.removeCustomFontBtn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(strings.fontFaLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                fontOptions.forEach { (key, label) ->
                                    val selected = subtitleFontFa == key
                                    Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { subtitleFontFa = key; sharedPrefs.edit().putString("subtitle_font_fa", key).apply() }) {
                                        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontFamilyFor(key)), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                                if (customFontPathFa != null) {
                                    val selected = subtitleFontFa == "custom"
                                    Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { subtitleFontFa = "custom"; sharedPrefs.edit().putString("subtitle_font_fa", "custom").apply() }) {
                                        Text(strings.fontCustomLabel, color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall.copy(fontFamily = customFontFamilyFa ?: FontFamily.Default), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { customFontLauncherFa.launch("*/*") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(strings.importCustomFontBtn, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                if (customFontPathFa != null) { TextButton(onClick = { customFontPathFa = null; if (subtitleFontFa == "custom") { subtitleFontFa = "default"; sharedPrefs.edit().putString("subtitle_font_fa", "default").apply() }; sharedPrefs.edit().remove("subtitle_custom_font_path_fa").apply() }) { Text(strings.removeCustomFontBtn, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(strings.syncTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(strings.syncCurrentOffset(strings.langCodeEn, offsetText(subEnOffset)), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onShiftSubEn(-0.5) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShiftSubEn(-0.1) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShiftSubEn(0.1) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(6.dp)) { Text("+0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShiftSubEn(0.5) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp)) { Text("+0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = manualShiftEnText, onValueChange = { manualShiftEnText = it }, label = { Text(strings.exactTimeLabel, style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.3f), focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = Color.White.copy(alpha = 0.5f)))
                                Button(onClick = { val target = manualShiftEnText.toDoubleOrNull(); if (target != null) { onShiftSubEn(target - subEnOffset) } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) { Text(strings.applyBtn, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(strings.syncCurrentOffset(strings.langCodeFa, offsetText(subFaOffset)), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onShiftSubFa(-0.5) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShiftSubFa(-0.1) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShiftSubFa(0.1) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(6.dp)) { Text("+0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShiftSubFa(0.5) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp)) { Text("+0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = manualShiftFaText, onValueChange = { manualShiftFaText = it }, label = { Text(strings.exactTimeLabel, style = MaterialTheme.typography.labelSmall) }, singleLine = true, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.3f), focusedLabelColor = MaterialTheme.colorScheme.primary, unfocusedLabelColor = Color.White.copy(alpha = 0.5f)))
                                Button(onClick = { val target = manualShiftFaText.toDoubleOrNull(); if (target != null) { onShiftSubFa(target - subFaOffset) } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) { Text(strings.applyBtn, fontWeight = FontWeight.Bold) }
                            }
                            Text(strings.syncHint, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onSaveSrt() }, enabled = subFaList.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) { Text(strings.saveSrtBtn, color = Color.White, fontWeight = FontWeight.Bold) }
                        if (subFaList.isEmpty()) { Text(strings.noFaSubtitleToSave, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = { showSubtitleSettings = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) { Text(strings.confirmReturnBtn, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
                }
            }
        }
        if (!isFullScreen) {
            Column(modifier = Modifier.weight(0.62f).fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 8.dp)) {
                Text(text = strings.allSubtitlesListTitle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                if (singleTranslateError != null) { Text(text = singleTranslateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
                if (subEnList.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.loadSubtitleHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) } }
                else {
                    if (subEnList.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).let { m -> if (isGlassmorphismEnabled) m.border(width = 1.dp, brush = androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))), shape = RoundedCornerShape(14.dp)) else m }, colors = CardDefaults.cardColors(containerColor = if (isGlassmorphismEnabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(14.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().clickable { isSyncExpanded = !isSyncExpanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Text("⏱️", style = MaterialTheme.typography.bodyMedium); Spacer(modifier = Modifier.width(6.dp)); Text(text = strings.syncSettingsRowTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }; Text(text = if (isSyncExpanded) strings.collapseSync else strings.expandSync, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isSyncExpanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)) }
                                androidx.compose.animation.AnimatedVisibility(visible = isSyncExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1.1f)) { Text(strings.langCodeEn, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(strings.shiftValueLabel(offsetText(subEnOffset)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }; Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(2f, fill = false)) { TextButton(onClick = { onShiftSubEn(-0.5) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; TextButton(onClick = { onShiftSubEn(-0.1) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; TextButton(onClick = { onShiftSubEn(0.1) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(6.dp)) { Text("+0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; TextButton(onClick = { onShiftSubEn(0.5) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp)) { Text("+0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } } }
                                        Spacer(modifier = Modifier.height(6.dp)); HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)); Spacer(modifier = Modifier.height(4.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1.1f)) { Text(strings.langCodeFa, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(strings.shiftValueLabel(offsetText(subFaOffset)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }; Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(2f, fill = false)) { TextButton(onClick = { onShiftSubFa(-0.5) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; TextButton(onClick = { onShiftSubFa(-0.1) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; TextButton(onClick = { onShiftSubFa(0.1) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(6.dp)) { Text("+0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; TextButton(onClick = { onShiftSubFa(0.5) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp)) { Text("+0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } } }
                                    }
                                }
                            }
                        }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(subEnList) { index, enSub ->
                            val faMatch = subtitleAlignmentMap[enSub]
                            val isActive = enSub.start <= currentTime && enSub.end >= currentTime
                            val glowingBorderModifier = if (isGlassmorphismEnabled) Modifier.border(width = 1.dp, brush = androidx.compose.ui.graphics.Brush.linearGradient(colors = if (isActive) listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent) else listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.colorScheme.outline.copy(alpha = 0.03f))), shape = RoundedCornerShape(12.dp)) else Modifier
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).then(glowingBorderModifier), onClick = { exoPlayer.seekTo((enSub.start * 1000).toLong()); exoPlayer.play() }, colors = CardDefaults.cardColors(containerColor = if (isGlassmorphismEnabled) { if (isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) } else { if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) }), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(text = "⏱️ ${formatTime(enSub.start)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); if (isActive) { Text(text = strings.playingLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) } }
                                    Spacer(Modifier.height(8.dp))
                                    ClickableWordText(text = enSub.text, style = MaterialTheme.typography.bodyLarge.copy(color = subtitleColorEn, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn)), highlightColor = subtitleColorEn, onWordClick = { w -> exoPlayer.pause(); onWordClick(w, enSub.text, faMatch?.text) })
                                    faMatch?.let { Spacer(Modifier.height(8.dp)); Text(text = it.text, style = MaterialTheme.typography.bodyMedium.copy(color = subtitleColorFa, fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa)), textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) }
                                    Spacer(Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { autoPauseAtTime = null; exoPlayer.seekTo((enSub.start * 1000).toLong()); exoPlayer.play() }, colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1.3f)) { Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(4.dp)); Text(text = strings.playFromStartBtn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
                                        TextButton(onClick = { autoPauseAtTime = enSub.end; exoPlayer.seekTo((enSub.start * 1000).toLong()); exoPlayer.play() }, colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1.7f)) { Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Row(modifier = Modifier.size(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(0.5.dp))); Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(0.5.dp))) }; Spacer(modifier = Modifier.width(6.dp)); Text(text = strings.playAutoStopBtn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) } }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    if (isTranslatingSingle && translatingIndex == index) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(strings.translatingLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            IconButton(onClick = onStopTranslation, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(8.dp))) {
                                                Icon(Icons.Filled.Close, strings.stopCd, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    } else {
                                        TextButton(onClick = { onTranslateSubtitle(index) }, enabled = !isTranslatingSingle, colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.tertiary), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text(text = strings.aiTranslateBtn, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(seconds: Double): String {
    val mins = (seconds / 60).toLong()
    val secs = (seconds % 60).toLong()
    return String.format("%02d:%02d", mins, secs)
}

fun alignSubtitles(enList: List<SubtitleEntry>, faList: List<SubtitleEntry>): Map<SubtitleEntry, SubtitleEntry> {
    val sortedFaList = faList.sortedBy { it.start }
    val matched = mutableMapOf<SubtitleEntry, SubtitleEntry>()
    for (enSub in enList) {
        var bestFa: SubtitleEntry? = null
        var bestDiff = Double.MAX_VALUE
        for (faSub in sortedFaList) {
            val diff = Math.abs(faSub.start - enSub.start)
            if (diff < 1.0) {
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestFa = faSub
                }
            } else if (faSub.start > enSub.start + 1.0) {
                break
            }
        }
        if (bestFa != null) {
            matched[enSub] = bestFa
        }
    }
    return matched
}
