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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
import com.example.ui.theme.SubtitleEnOnLight
import com.example.ui.theme.SubtitleFaOnLight
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import com.example.model.JsonSubtitle
import com.example.model.JsonSubtitlePackage
import com.example.model.SubtitleEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    // JSON subtitle time sync (same feature as the EN/FA offsets, applied
    // to the JSON learning package's timestamps).
    onShiftJson: (Double) -> Unit = {},
    onResetJson: () -> Unit = {},
    jsonOffset: Double = 0.0,
    // Fired while the USER scrolls the subtitle list: `true` once they
    // scroll past the top of the list (MainScreen folds the import section
    // away to give the list more room), `false` when they scroll back to
    // the very top. Programmatic auto-scroll (following the active
    // subtitle) never triggers this.
    onUserScrollCollapse: (Boolean) -> Unit = {},
    // Focus mode: hides the top bar/tabs, the import section and the
    // subtitle time-sync cards so only the video + subtitle list remain
    // (the state itself lives in MainScreen).
    focusMode: Boolean = false,
    onFocusModeToggle: () -> Unit = {},
    onTranslateSubtitle: (Int) -> Unit = {},
    onSaveSrt: () -> Unit = {},
    isTranslatingSingle: Boolean = false,
    translatingIndex: Int = -1,
    singleTranslateError: String? = null,
    onStopTranslation: () -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.FA,
    // JSON subtitle-learning package (highest-priority subtitle source).
    // Display priority while non-null: JSON learning file > imported EN/FA
    // subtitle files > default subtitle source. When JSON subtitles are
    // shown, normal EN/FA subtitle lines are NOT rendered again (no
    // duplicated rendering); they only remain in memory as a fallback.
    jsonPackage: JsonSubtitlePackage? = null,
    // Fired when the user clicks an English subtitle SENTENCE (outside any
    // word) — opens the learning lesson for that sentence.
    onSentenceClick: (sentence: String, translation: String?) -> Unit = { _, _ -> }
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
    //
    // ── Subtitle color resolution (light/dark/beta themes) ──
    // Two sets of defaults are kept on purpose:
    //  • Overlay colors (on the video / audio backdrop, which is always
    //    dark): bright white + amber, with a soft text shadow for contrast.
    //  • List colors (on the THEME background below the player): adaptive —
    //    dark slate/gold on light themes, white/amber on dark themes — so
    //    subtitles stay readable in LIGHT mode too (previously the white
    //    text nearly disappeared against the light background). The user's
    //    custom SubtitleColorState choice always overrides both.
    val isDarkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val subtitleOverlayColorEn = SubtitleColorState.colorEn ?: Color.White
    val subtitleOverlayColorFa = SubtitleColorState.colorFa ?: AccentAmber
    val subtitleListColorEn = SubtitleColorState.colorEn ?: if (isDarkUi) Color.White else SubtitleEnOnLight
    val subtitleListColorFa = SubtitleColorState.colorFa ?: if (isDarkUi) AccentAmber else SubtitleFaOnLight
    val overlayTextShadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.6f), offset = androidx.compose.ui.geometry.Offset(0f, 1.5f), blurRadius = 6f
    )
    val listTextShadow = androidx.compose.ui.graphics.Shadow(
        color = if (isDarkUi) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.85f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1f), blurRadius = 4f
    )
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
    var isJsonSyncExpanded by remember { mutableStateOf(false) }
    var useSmartPauseControls by remember { mutableStateOf(sharedPrefs.getBoolean("smart_pause_enabled", true)) }
    // Smart-pause gear panel options (all persisted):
    //  • pauseDimEnabled: the black layer over the video while paused.
    //  • pauseHideUiEnabled: while paused, hide the subtitle text and the
    //    smart-pause buttons so the frame can be inspected precisely — the
    //    gear itself is NEVER hidden.
    //  • pauseRequireContinue: tapping the video no longer resumes playback;
    //    only the Continue button does.
    var pauseDimEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("smart_pause_dim_enabled", true)) }
    var pauseHideUiEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("smart_pause_hide_ui_enabled", false)) }
    var pauseRequireContinue by remember { mutableStateOf(sharedPrefs.getBoolean("smart_pause_require_continue", false)) }
    var skipSeconds by remember { mutableStateOf(sharedPrefs.getInt("skip_seconds", 10)) }
    var containerWidth by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    // ── User-scroll detection (for the collapsible import section) ──
    // The player auto-scrolls the list to follow the active subtitle line;
    // only USER scrolls should fold the import section, so programmatic
    // scrolls are tracked with isAutoScrolling and excluded here.
    var isAutoScrolling by remember { mutableStateOf(false) }
    var isUserScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress -> isUserScrolling = inProgress && !isAutoScrolling }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                if (isUserScrolling) {
                    when {
                        index > 0 || offset > 100 -> true   // scrolled away from the top
                        index == 0 && offset == 0 -> false  // back at the very top
                        else -> null
                    }
                } else null
            }
            .distinctUntilChanged()
            .collect { collapse -> collapse?.let(onUserScrollCollapse) }
    }
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
    // The smart-pause gear is movable like the other overlay buttons.
    val smartPauseGearTransform = remember { mutableStateOf(loadTransform("smart_pause_gear", 0f, 0f)) }
    val subtitleAlignmentMap = remember(subEnList, subFaList) { alignSubtitles(subEnList, subFaList) }
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    var isPlaying by remember { mutableStateOf(false) }
    var audioTrackGroups by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlayingChange: Boolean) { isPlaying = isPlayingChange }
            override fun onTracksChanged(tracks: Tracks) { audioTrackGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO } }
        }
        exoPlayer.addListener(listener); isPlaying = exoPlayer.isPlaying
        audioTrackGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
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
        // `armed` only becomes true once the position has been seen BEFORE
        // the target. Right after seekTo() ExoPlayer may briefly still
        // report the OLD position (which is already past the target when
        // jumping BACK to the previous subtitle); without this guard the
        // player paused instantly and the previous-subtitle button never
        // actually got there.
        var armed = false
        while (isActive) {
            currentTime = exoPlayer.currentPosition / 1000.0
            autoPauseAtTime?.let { target ->
                if (currentTime < target) armed = true
                if (armed && exoPlayer.isPlaying && currentTime >= target) {
                    exoPlayer.pause()
                    autoPauseAtTime = null
                    skipNextAutoScroll = true
                }
            }
            delay(200)
        }
    }
    val activeIndex = remember(subEnList, currentTime) { subEnList.indexOfFirst { it.start <= currentTime && it.end >= currentTime } }
    val activeJsonIndex = remember(jsonPackage, currentTime) {
        jsonPackage?.subtitles?.indexOfFirst { s -> s.start != null && s.end != null && s.start!! <= currentTime && currentTime <= s.end!! } ?: -1
    }
    val jsonModeActive = jsonPackage != null && jsonPackage!!.subtitles.isNotEmpty()
    val isAutoStoppingActive = autoPauseAtTime != null
    LaunchedEffect(activeIndex, activeJsonIndex, jsonModeActive, isAutoStoppingActive) {
        if (!isAutoStoppingActive) {
            // isAutoScrolling marks these programmatic scrolls so the
            // user-scroll detection above never folds the import section
            // because of the auto-follow behavior.
            if (jsonModeActive && activeJsonIndex >= 0) {
                if (skipNextAutoScroll) skipNextAutoScroll = false
                else { isAutoScrolling = true; listState.animateScrollToItem(activeJsonIndex); isAutoScrolling = false }
            } else if (activeIndex >= 0 && subEnList.isNotEmpty()) {
                if (skipNextAutoScroll) skipNextAutoScroll = false
                else { isAutoScrolling = true; listState.animateScrollToItem(activeIndex); isAutoScrolling = false }
            }
        }
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

    // ── JSON subtitle resolution (priority: JSON > imported files) ──
    // Synchronized by TIMESTAMP first, then by matching English text, then
    // by subtitle ID, then by list index — so JSON packages with or without
    // timing data both stay in sync with playback.
    val currentJson = remember(jsonPackage, currentTime, currentEn, activeIndex) {
        jsonPackage?.let { pkg ->
            pkg.subtitles.firstOrNull { s ->
                s.start != null && s.end != null && s.start!! <= currentTime && currentTime <= s.end!!
            } ?: currentEn?.let { en ->
                pkg.subtitles.firstOrNull { s -> s.english.trim().equals(en.text.trim(), ignoreCase = true) }
            } ?: if (activeIndex >= 0) {
                pkg.subtitles.firstOrNull { s -> s.id != null && s.id == (activeIndex + 1).toString() }
            } else null
            ?: pkg.subtitles.getOrNull(activeIndex)
        }
    }

    // Smart-pause overlay targets: follow the JSON learning package when
    // that is what is displayed, otherwise the imported EN/FA subtitles.
    val currentJsonDisplayIndex = currentJson?.let { cj -> jsonPackage?.subtitles?.indexOfFirst { it === cj } ?: -1 } ?: -1
    val prevJsonPaused = jsonPackage?.subtitles?.getOrNull(currentJsonDisplayIndex - 1)
    val currentJsonPaused = currentJson

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(if (isFullScreen) 1f else 0.38f).background(Color.Black).onGloballyPositioned { containerWidth = it.size.width }) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { PlayerView(context).apply { player = exoPlayer; useController = !useSmartPauseControls; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; subtitleView?.visibility = android.view.View.GONE } }, update = { playerView -> playerView.useController = !useSmartPauseControls })
            if (useSmartPauseControls && !isAudio && videoUri != null) {
                Box(modifier = Modifier.fillMaxSize().pointerInput(containerWidth, isPlaying, skipSeconds, pauseRequireContinue) { detectTapGestures(onTap = { if (isPlaying) exoPlayer.pause() else if (!pauseRequireContinue) exoPlayer.play() }, onDoubleTap = { offset -> val isLeft = offset.x < (containerWidth / 2); if (isLeft) exoPlayer.seekTo((exoPlayer.currentPosition - skipSeconds * 1000).coerceAtLeast(0)) else exoPlayer.seekTo(exoPlayer.currentPosition + skipSeconds * 1000) }) })
            }
            if (isAudio && videoUri != null) {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(colors = listOf(Color(0xFF1E1B4B), Color(0xFF03001C)), radius = 900f)).pointerInput(containerWidth, isPlaying, skipSeconds, pauseRequireContinue) { detectTapGestures(onTap = { if (isPlaying) exoPlayer.pause() else if (!pauseRequireContinue) exoPlayer.play() }, onDoubleTap = { offset -> val isLeft = offset.x < (containerWidth / 2); if (isLeft) exoPlayer.seekTo((exoPlayer.currentPosition - skipSeconds * 1000).coerceAtLeast(0)) else exoPlayer.seekTo(exoPlayer.currentPosition + skipSeconds * 1000) }) }, contentAlignment = Alignment.Center) {
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
                            val jsonCurrent = currentJson
                            if (jsonCurrent != null && (jsonCurrent.english.isNotBlank() || !jsonCurrent.translation.isNullOrBlank())) {
                                // JSON priority in audio mode too.
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    if (jsonCurrent.english.isNotBlank()) {
                                        ClickableWordText(text = jsonCurrent.english, style = MaterialTheme.typography.titleLarge.copy(color = subtitleOverlayColorEn, shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.1f), highlightColor = subtitleOverlayColorEn, onWordClick = { word -> exoPlayer.pause(); onWordClick(word, jsonCurrent.english, jsonCurrent.translation) }, onTextClick = { exoPlayer.pause(); onSentenceClick(jsonCurrent.english, jsonCurrent.translation) })
                                    }
                                    jsonCurrent.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                                        if (jsonCurrent.english.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = translation, color = subtitleOverlayColorFa, style = MaterialTheme.typography.titleMedium.copy(shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                                    }
                                }
                            } else if (currentEn != null || currentFa != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    currentEn?.let { enSub -> ClickableWordText(text = enSub.text, style = MaterialTheme.typography.titleLarge.copy(color = subtitleOverlayColorEn, shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.1f), highlightColor = subtitleOverlayColorEn, onWordClick = { word -> exoPlayer.pause(); onWordClick(word, enSub.text, currentFa?.text) }, onTextClick = { exoPlayer.pause(); onSentenceClick(enSub.text, currentFa?.text) }) }
                                    if (currentEn != null && currentFa != null) Spacer(modifier = Modifier.height(4.dp))
                                    currentFa?.let { faSub -> Text(text = faSub.text, color = subtitleOverlayColorFa, style = MaterialTheme.typography.titleMedium.copy(shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)) }
                                }
                            } else { Text(text = strings.audioPlayingHint, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
            if (!isPlaying && videoUri != null && useSmartPauseControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (pauseDimEnabled) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier)
                        .then(if (!pauseRequireContinue) Modifier.clickable { exoPlayer.play() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    @Composable
                    fun FreeFormButton(transformState: MutableState<ButtonTransform>, saveKey: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
                        var transform by transformState
                        Box(modifier = modifier.offset { IntOffset(transform.x.roundToInt(), transform.y.roundToInt()) }.graphicsLayer { scaleX = transform.scale; scaleY = transform.scale; rotationZ = transform.rotation }.pointerInput(Unit) { detectTransformGestures { _, pan, zoom, rotation -> transform = transform.copy(x = transform.x + pan.x, y = transform.y + pan.y, scale = (transform.scale * zoom).coerceIn(0.4f, 2.5f), rotation = transform.rotation + rotation) } }.pointerInput(Unit) { detectDragGestures(onDragEnd = { transformState.value = transform; saveTransform(saveKey, transform) }, onDrag = { change, dragAmount -> change.consume(); transform = transform.copy(x = transform.x + dragAmount.x, y = transform.y + dragAmount.y) }) }) { content() }
                    }

                    // The smart-pause gear is movable like the other overlay
                    // buttons (transform persisted) and is NEVER hidden by the
                    // "hide subtitles & buttons" option — it is the way back
                    // into the settings panel.
                    FreeFormButton(smartPauseGearTransform, "smart_pause_gear", modifier = Modifier.align(Alignment.TopCenter)) {
                        IconButton(onClick = { showOverlaySettings = !showOverlaySettings }, modifier = Modifier.padding(top = 8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).size(36.dp)) { Icon(Icons.Default.Settings, contentDescription = strings.smartPauseSettingsCd, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }

                    // Smart-pause buttons (Continue + previous/current subtitle).
                    // The targets follow the JSON learning package when it is
                    // active, otherwise the imported EN/FA subtitles.
                    val prevSubEn = if (activeIndex > 0) subEnList[activeIndex - 1] else null
                    val prevFaText = prevSubEn?.let { subtitleAlignmentMap[it]?.text }
                    val prevStartEnd: Pair<Double, Double>? = prevJsonPaused?.let { j -> j.start?.let { s -> j.end?.let { e -> s to e } } } ?: prevSubEn?.let { it.start to it.end }
                    val currentStartEnd: Pair<Double, Double>? = currentJsonPaused?.let { j -> j.start?.let { s -> j.end?.let { e -> s to e } } } ?: currentEn?.let { it.start to it.end }
                    val prevLineText = prevJsonPaused?.let { it.translation?.takeIf { t -> t.isNotBlank() } ?: it.english } ?: (prevFaText ?: prevSubEn?.text ?: "")
                    val currentLineText = currentJsonPaused?.let { it.translation?.takeIf { t -> t.isNotBlank() } ?: it.english } ?: (currentFa?.text ?: currentEn?.text ?: "")
                    // With the hide-UI option ON the buttons and the subtitle
                    // text disappear, but the Continue button must stay when
                    // tap-to-resume is off, otherwise playback could never be
                    // resumed again.
                    val showSmartButtons = !pauseHideUiEnabled || pauseRequireContinue
                    if (showSmartButtons && isFullScreen && (currentEn != null || currentJsonPaused != null)) {
                        FreeFormButton(continueTransform, "continue") { Button(onClick = { exoPlayer.play() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), contentColor = Color.White), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(strings.resumePlayBtn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
                        if (!pauseHideUiEnabled && prevStartEnd != null) {
                            val (prevStart, prevEnd) = prevStartEnd
                            FreeFormButton(autoPrevTransform, "auto_prev") { OutlinedButton(onClick = { autoPauseAtTime = prevEnd; exoPlayer.seekTo((prevStart * 1000).toLong()); exoPlayer.play() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(strings.autoStopPrevSubtitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(prevLineText, style = MaterialTheme.typography.bodySmall, color = if (prevFaText != null) AccentAmber else Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1) } } }
                        }
                        if (!pauseHideUiEnabled && currentStartEnd != null) {
                            val (currentStart, currentEnd) = currentStartEnd
                            FreeFormButton(autoCurrentTransform, "auto_current") { OutlinedButton(onClick = { autoPauseAtTime = currentEnd; exoPlayer.seekTo((currentStart * 1000).toLong()); exoPlayer.play() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(strings.autoStopCurrentSubtitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(currentLineText, style = MaterialTheme.typography.bodySmall, color = if (currentFa != null) AccentAmber else Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 1) } } }
                        }
                    } else if (showSmartButtons) {
                        Box(modifier = Modifier.size(68.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(100.dp)).clickable { exoPlayer.play() }, contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = strings.resumeCd, tint = Color.White, modifier = Modifier.size(36.dp)) }
                    }
                }

                // Gear settings panel: button-position reset + the pause-UI
                // options (dim layer / hide subtitles & buttons /
                // continue-only resume). Shown as a dialog so it works in
                // both fullscreen and normal (split) mode.
                if (showOverlaySettings) {
                    Dialog(onDismissRequest = { showOverlaySettings = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Card(modifier = Modifier.padding(16.dp).fillMaxWidth(0.94f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(strings.smartPausePanelTitle, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { showOverlaySettings = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, contentDescription = strings.close, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) { Text(strings.resetPositionsTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                                    Button(onClick = { continueTransform.value = ButtonTransform(0f, -80f, 1f, 0f); autoPrevTransform.value = ButtonTransform(0f, 0f, 1f, 0f); autoCurrentTransform.value = ButtonTransform(0f, 80f, 1f, 0f); smartPauseGearTransform.value = ButtonTransform(0f, 0f, 1f, 0f); saveTransform("continue", continueTransform.value); saveTransform("auto_prev", autoPrevTransform.value); saveTransform("auto_current", autoCurrentTransform.value); saveTransform("smart_pause_gear", smartPauseGearTransform.value); showOverlaySettings = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text(strings.resetBtn, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.12f))
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(strings.pauseDimTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.pauseDimDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall) }; Switch(checked = pauseDimEnabled, onCheckedChange = { pauseDimEnabled = it; sharedPrefs.edit().putBoolean("smart_pause_dim_enabled", it).apply() }) }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(strings.pauseHideUiTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.pauseHideUiDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall) }; Switch(checked = pauseHideUiEnabled, onCheckedChange = { pauseHideUiEnabled = it; sharedPrefs.edit().putBoolean("smart_pause_hide_ui_enabled", it).apply() }) }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text(strings.pauseRequireContinueTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold); Text(strings.pauseRequireContinueDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall) }; Switch(checked = pauseRequireContinue, onCheckedChange = { pauseRequireContinue = it; sharedPrefs.edit().putBoolean("smart_pause_require_continue", it).apply() }) }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Focus mode toggle: hides the top bar/tabs, the import
                // section and the time-sync cards (see MainScreen).
                IconButton(onClick = onFocusModeToggle, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) { Icon(imageVector = if (focusMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = strings.focusModeCd, tint = if (focusMode) MaterialTheme.colorScheme.primary else Color.White) }
                IconButton(onClick = { onFullScreenToggle(!isFullScreen) }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) { Icon(imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = if (isFullScreen) strings.exitFullscreenCd else strings.fullscreenCd, tint = Color.White) }
            }
            IconButton(onClick = { showSubtitleSettings = !showSubtitleSettings }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) { Icon(imageVector = Icons.Default.Settings, contentDescription = strings.playerSettingsCd, tint = Color.White) }
            // Overlay subtitles. The box always keeps a dark backdrop so the
            // bright overlay colors stay readable in every theme; the text
            // also carries a soft shadow for extra contrast on bright scenes.
            // With the smart-pause "hide subtitles & buttons" option ON the
            // overlay text is hidden while paused so the frame can be
            // inspected precisely.
            if (isSubtitlesEnabled && !isAudio && !(pauseHideUiEnabled && !isPlaying)) {
                val jsonCurrent = currentJson
                if (jsonCurrent != null && (jsonCurrent.english.isNotBlank() || !jsonCurrent.translation.isNullOrBlank())) {
                    // JSON priority rendering: when a JSON learning package
                    // is loaded its data replaces the normal EN/FA lines
                    // (no duplicated subtitle rendering).
                    Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = subtitleBottomPadding.dp, start = 24.dp, end = 24.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).clickable { if (isPlaying) exoPlayer.pause() else if (!pauseRequireContinue) exoPlayer.play() }.padding(vertical = 10.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (jsonCurrent.english.isNotBlank()) {
                            ClickableWordText(
                                text = jsonCurrent.english,
                                style = MaterialTheme.typography.titleLarge.copy(color = subtitleOverlayColorEn, shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn), textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleLarge.fontSize * subtitleFontSizeFactor),
                                highlightColor = subtitleOverlayColorEn,
                                onWordClick = { word -> exoPlayer.pause(); onWordClick(word, jsonCurrent.english, jsonCurrent.translation) },
                                onTextClick = { exoPlayer.pause(); onSentenceClick(jsonCurrent.english, jsonCurrent.translation) },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        jsonCurrent.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                            Text(text = translation, color = subtitleOverlayColorFa, style = MaterialTheme.typography.titleMedium.copy(shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa), fontWeight = FontWeight.Medium, fontSize = MaterialTheme.typography.titleMedium.fontSize * subtitleFontSizeFactor), textAlign = TextAlign.Center)
                        }
                    }
                } else if (currentEn != null || currentFa != null) {
                    Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = subtitleBottomPadding.dp, start = 24.dp, end = 24.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).clickable { if (isPlaying) exoPlayer.pause() else if (!pauseRequireContinue) exoPlayer.play() }.padding(vertical = 10.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        currentEn?.let { enSub -> ClickableWordText(text = enSub.text, style = MaterialTheme.typography.titleLarge.copy(color = subtitleOverlayColorEn, shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn), textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleLarge.fontSize * subtitleFontSizeFactor), highlightColor = subtitleOverlayColorEn, onWordClick = { word -> exoPlayer.pause(); onWordClick(word, enSub.text, currentFa?.text) }, onTextClick = { exoPlayer.pause(); onSentenceClick(enSub.text, currentFa?.text) }, modifier = Modifier.padding(bottom = 4.dp)) }
                        currentFa?.let { Text(text = it.text, color = subtitleOverlayColorFa, style = MaterialTheme.typography.titleMedium.copy(shadow = overlayTextShadow, fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa), fontWeight = FontWeight.Medium, fontSize = MaterialTheme.typography.titleMedium.fontSize * subtitleFontSizeFactor), textAlign = TextAlign.Center) }
                    }
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
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(strings.audioTrackTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(strings.audioTrackDesc, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (audioTrackGroups.isEmpty()) {
                                Text(strings.audioTrackUnavailable, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                            } else {
                                // List every audio track of the video (e.g.
                                // dubbed + original language) and switch on
                                // tap. Works in normal and smart-pause modes.
                                audioTrackGroups.forEach { group ->
                                    for (trackIndex in 0 until group.length) {
                                        val format = group.getTrackFormat(trackIndex)
                                        val label = format.label?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: strings.audioTrackFallbackName(trackIndex + 1)
                                        val language = format.language?.takeIf { it.isNotBlank() }
                                        val selected = group.isTrackSelected(trackIndex)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                try {
                                                    // Force-select this audio track via the stable
                                                    // Player.trackSelectionParameters API.
                                                    exoPlayer.trackSelectionParameters =
                                                        exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                                                            .build()
                                                } catch (e: Exception) { e.printStackTrace() }
                                            }.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = Color.White.copy(alpha = 0.5f)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(text = label, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                                language?.let { Text(text = it, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (SubtitleColorState.colorEn == swatch) 2.dp else 1.dp, color = if (SubtitleColorState.colorEn == swatch) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(100.dp)).clickable { SubtitleColorState.colorEn = swatch; sharedPrefs.edit().putInt("subtitle_color_en", swatch.toArgb()).apply() })
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(strings.subFaParenLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                subtitleColorOptions.forEach { swatch ->
                                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(100.dp)).background(swatch).border(width = if (SubtitleColorState.colorFa == swatch) 2.dp else 1.dp, color = if (SubtitleColorState.colorFa == swatch) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(100.dp)).clickable { SubtitleColorState.colorFa = swatch; sharedPrefs.edit().putInt("subtitle_color_fa", swatch.toArgb()).apply() })
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
                val jsonList = jsonPackage?.subtitles
                if (jsonList != null && jsonList.isNotEmpty()) {
                    // JSON priority rendering: when a JSON learning package is
                    // loaded, its subtitles replace the normal EN/FA list
                    // (no duplicated rendering). The EN/FA lists stay intact
                    // in memory as a fallback for when the JSON is removed.
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📦 ${strings.jsonActiveBadge}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    // ── JSON time sync (shift forward/back) ──
                    // Hidden in focus mode together with the other chrome.
                    if (!focusMode) {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().clickable { isJsonSyncExpanded = !isJsonSyncExpanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⏱️", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = strings.jsonSyncRowTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(text = if (isJsonSyncExpanded) strings.collapseSync else strings.expandSync, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isJsonSyncExpanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = isJsonSyncExpanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (jsonList.any { it.start != null && it.end != null }) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1.1f)) {
                                                Text(strings.subJsonLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text(strings.shiftValueLabel(offsetText(jsonOffset)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(2f, fill = false)) {
                                                TextButton(onClick = { onShiftJson(-0.5) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                                TextButton(onClick = { onShiftJson(-0.1) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(6.dp)) { Text("-0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                                TextButton(onClick = { onShiftJson(0.1) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(6.dp)) { Text("+0.1", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                                TextButton(onClick = { onShiftJson(0.5) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), modifier = Modifier.height(28.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp)) { Text("+0.5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(onClick = onResetJson, enabled = jsonOffset != 0.0, colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Text(text = strings.jsonResetBtn, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Text(text = strings.jsonSyncNoTimings, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(jsonList) { jsonSub ->
                            JsonSubtitleListItem(
                                jsonSub = jsonSub,
                                isActive = jsonSub.start != null && jsonSub.end != null && jsonSub.start!! <= currentTime && currentTime <= jsonSub.end!!,
                                isGlassmorphismEnabled = isGlassmorphismEnabled,
                                subtitleEnColor = subtitleListColorEn,
                                subtitleFaColor = subtitleListColorFa,
                                textShadow = listTextShadow,
                                fontFamilyFor = { key -> fontFamilyFor(key) },
                                subtitleFontEn = subtitleFontEn,
                                subtitleFontFa = subtitleFontFa,
                                strings = strings,
                                onSeek = { jsonSub.start?.let { start -> exoPlayer.seekTo((start * 1000).toLong()); exoPlayer.play() } },
                                onWordClick = { w -> exoPlayer.pause(); onWordClick(w, jsonSub.english, jsonSub.translation) },
                                onSentenceClick = { onSentenceClick(jsonSub.english, jsonSub.translation) }
                            )
                        }
                    }
                } else if (subEnList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.loadSubtitleHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                } else {
                    // The EN/FA time-sync card is hidden in focus mode too.
                    if (subEnList.isNotEmpty() && !focusMode) {
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
                                    // List colors adapt to the current theme
                                    // (light/dark/beta) so subtitles stay
                                    // readable on the light background; the
                                    // soft shadow adds extra contrast.
                                    ClickableWordText(text = enSub.text, style = MaterialTheme.typography.bodyLarge.copy(color = subtitleListColorEn, shadow = listTextShadow, fontFamily = fontFamilyFor(subtitleFontEn, customFontFamilyEn)), highlightColor = subtitleListColorEn, onWordClick = { w -> exoPlayer.pause(); onWordClick(w, enSub.text, faMatch?.text) }, onTextClick = { exoPlayer.pause(); onSentenceClick(enSub.text, faMatch?.text) })
                                    faMatch?.let { Spacer(Modifier.height(8.dp)); Text(text = it.text, style = MaterialTheme.typography.bodyMedium.copy(color = subtitleListColorFa, shadow = listTextShadow, fontFamily = fontFamilyFor(subtitleFontFa, customFontFamilyFa)), textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) }
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

/**
 * One list entry for a JSON subtitle-learning package line (highest-priority
 * subtitle source). Shows the original line, its translation, level /
 * difficulty / ID chips, and a lesson button; words are tappable (word
 * analysis) and the sentence itself opens the lesson.
 */
@Composable
private fun JsonSubtitleListItem(
    jsonSub: JsonSubtitle,
    isActive: Boolean,
    isGlassmorphismEnabled: Boolean,
    subtitleEnColor: Color,
    subtitleFaColor: Color,
    textShadow: androidx.compose.ui.graphics.Shadow,
    fontFamilyFor: (String) -> FontFamily,
    subtitleFontEn: String,
    subtitleFontFa: String,
    strings: AppStrings,
    onSeek: () -> Unit,
    onWordClick: (String) -> Unit,
    onSentenceClick: () -> Unit
) {
    val glowingBorderModifier = if (isGlassmorphismEnabled) Modifier.border(width = 1.dp, brush = androidx.compose.ui.graphics.Brush.linearGradient(colors = if (isActive) listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent) else listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.colorScheme.outline.copy(alpha = 0.03f))), shape = RoundedCornerShape(12.dp)) else Modifier
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).then(glowingBorderModifier),
        onClick = onSeek,
        colors = CardDefaults.cardColors(containerColor = if (isGlassmorphismEnabled) { if (isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) } else { if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) }),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    jsonSub.start?.let { start ->
                        Text(text = "⏱️ ${formatTime(start)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    jsonSub.id?.let { id ->
                        if (jsonSub.start != null) Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ID $id", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isActive) {
                        Text(text = strings.playingLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    jsonSub.difficulty?.let { difficulty ->
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f), contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(text = difficulty, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    jsonSub.level?.let { level ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(text = level, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (jsonSub.english.isNotBlank()) {
                ClickableWordText(
                    text = jsonSub.english,
                    style = MaterialTheme.typography.bodyLarge.copy(color = subtitleEnColor, shadow = textShadow, fontFamily = fontFamilyFor(subtitleFontEn)),
                    highlightColor = subtitleEnColor,
                    onWordClick = onWordClick,
                    onTextClick = onSentenceClick
                )
            }
            jsonSub.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                Spacer(Modifier.height(6.dp))
                Text(text = translation, style = MaterialTheme.typography.bodyMedium.copy(color = subtitleFaColor, shadow = textShadow, fontFamily = fontFamilyFor(subtitleFontFa)), textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onSeek,
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = strings.playFromStartBtn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = onSentenceClick,
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.7f)
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = strings.lessonSheetTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
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
