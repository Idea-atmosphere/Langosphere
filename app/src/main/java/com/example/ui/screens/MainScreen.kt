package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.logic.AppUpdateManager
import com.example.logic.SentenceCardFactory
import com.example.ui.components.AboutDialog
import com.example.ui.components.DictionaryBottomSheet
import com.example.ui.components.DonatePopupDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.JsonChunkPanel
import com.example.ui.components.JsonQuizBetaDialog
import com.example.ui.components.LiquidTabBar
import com.example.ui.components.LiquidTabItem
import com.example.ui.components.M3NavigationBar
import com.example.ui.components.M3NavigationRail
import com.example.ui.components.PillTone
import com.example.ui.components.SentenceStudyLabels
import com.example.ui.components.SoftIconButton
import com.example.ui.components.StatusPill
import com.example.ui.components.SubtitleLearningSheet
import com.example.ui.components.UpdateDialog
import com.example.ui.components.VideoPlayerScreen
import com.example.ui.screens.AgentScreen
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AppDesignStyleState
import com.example.ui.theme.AppFontState
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppTab
import com.example.ui.theme.AppTabOrderState
import com.example.ui.theme.icon
import com.example.ui.theme.tabsAtBottom
import com.example.ui.theme.titleIn
import com.example.ui.theme.AppStrings
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.neoAccent
import com.example.ui.theme.isMaterial3Design
import com.example.ui.components.ToonNavigationRail
import com.example.ui.components.anime.SoraAvatar
import com.example.ui.components.anime.ToonOutlinedTitle
import com.example.ui.components.anime.ToonTabBar
import com.example.ui.components.anime.halftone
import com.example.ui.components.toToonItems
import com.example.ui.theme.AnimeColors
import com.example.ui.theme.isAnimeDesign
import com.example.ui.theme.isFriendlyNeobrutalismDesign
import com.example.ui.theme.isMaterialYouDesign
import com.example.ui.theme.isNeobrutalismDesign
import com.example.ui.theme.Typography as AppTypography
import com.example.ui.theme.forAppLanguage
import com.example.ui.theme.withFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Root of the UI. When the app language is Persian the whole layout is
 * mirrored to RTL (rows, paddings, alignments) — only the tab bar itself
 * and the swipe between tabs stay LTR so tab order and gestures never flip.
 * Persian copy keeps an RTL paragraph direction so mixed Fa/En sentences keep
 * their order.
 */
@Composable
fun MainScreen(
    onThemeToggle: (AppThemeMode) -> Unit = {},
    currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM
) {
    val viewModel: AppViewModel = viewModel()
    val appLanguage by viewModel.appLanguage.collectAsState()
    // The type scale depends on the UI language, the active design language
    // (forAppLanguage reads LocalDesignStyle) AND the user's "whole app"
    // font choice (Settings ▸ Theme ▸ Font), which is layered on top of the
    // design's own scale. Keying on all three makes switching any of them
    // live swap the scale immediately instead of keeping the previous
    // design's line-heights/tracking (which garbles wrapping).
    val appFontEn = AppFontState.app
    val appFontFa = AppFontState.appFa
    val typography = remember(appLanguage, AppDesignStyleState.style, appFontEn, appFontFa) {
        AppTypography.forAppLanguage(appLanguage).withFontFamily(
            AppFontState.resolvedAppFamily(fa = appLanguage == AppLanguage.FA)
        )
    }

    // The structure of the whole app is left-to-right, in every UI language.
    // Mirroring rows, rails and start/end paddings for Persian copy is what
    // made some of them collide, so the layout stays LTR and only the text
    // resolves its own direction, per string, from its content (see
    // String.autoTextDirection / TextDirectionUtils.isRtl) — a Persian line
    // still reads right-to-left inside itself, and no wording changes.
    val appLayoutDirection = LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides appLayoutDirection) {
        MaterialTheme(typography = typography) {
            MainScreenContent(
                viewModel = viewModel,
                appLanguage = appLanguage,
                onThemeToggle = onThemeToggle,
                currentThemeMode = currentThemeMode
            )
        }
    }
}

/**
 * The pager's pages are keyed on [AppTab] rather than on fixed indices,
 * because the user can reorder the app's sections (Settings ▸ Theme ▸
 * Customize ▸ Layout & shapes) — the page at index 1 is whatever the user
 * put there, so every lookup goes through the active order.
 */
private fun AppTab.tabItem(strings: AppStrings): LiquidTabItem =
    LiquidTabItem(titleIn(strings), icon())

/**
 * The app's primary navigation, in one place.
 *
 * Which bar is drawn is still the active design's business — the toon
 * sticker pill, the Material You NavigationBar or the shared
 * [LiquidTabBar] (which itself picks the M3 TabRow / the neo block bar) —
 * but WHERE it is drawn is the user's (Settings ▸ Theme ▸ Customize ▸
 * Layout & shapes), so MainScreen renders this same composable in the
 * Scaffold's `topBar` or `bottomBar` slot. It always stays LTR so the tab
 * order never flips under a right-to-left UI language.
 *
 * [showRailInstead] is true on wide windows where the design replaced the
 * bar with a side rail; nothing is drawn then.
 */
@Composable
private fun PrimaryNavBar(
    tabs: List<LiquidTabItem>,
    assistantIndex: Int,
    animeChrome: Boolean,
    materialYou: Boolean,
    showRailInstead: Boolean,
    selectedTab: Int,
    indicatorPosition: () -> Float,
    onTabSelected: (Int) -> Unit,
) {
    if (showRailInstead) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        when {
            animeChrome -> ToonTabBar(
                items = tabs.toToonItems(avatarIndex = assistantIndex),
                // The fractional pager position, so the Sunny blob tracks
                // the finger during a swipe instead of snapping once it
                // settles.
                indicatorPosition = indicatorPosition,
                onTabSelected = onTabSelected,
            )
            materialYou -> M3NavigationBar(
                items = tabs,
                selectedIndex = selectedTab,
                onTabSelected = onTabSelected,
            )
            else -> LiquidTabBar(
                items = tabs,
                indicatorPosition = indicatorPosition,
                onTabSelected = onTabSelected,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    viewModel: AppViewModel,
    appLanguage: AppLanguage,
    onThemeToggle: (AppThemeMode) -> Unit,
    currentThemeMode: AppThemeMode
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val strings = remember(appLanguage, context) { AppStrings(appLanguage, context) }
    // The same study labels the book reader uses, so a film line and a book
    // sentence describe their shared controls with identical wording.
    val studyLabels = remember(appLanguage) { SentenceStudyLabels(appLanguage == AppLanguage.FA) }
    val savedSentenceTexts by viewModel.savedSentenceTexts.collectAsState()

    // The user's own section order (or the app's default one).
    val tabOrder = AppTabOrderState.order
    val tabs = remember(strings, tabOrder) { tabOrder.map { it.tabItem(strings) } }
    // Pages that host a media player: the fullscreen / focus-mode chrome
    // rules below apply to both of them (the Video tab and the Online tab).
    val playerPages = remember(tabOrder) {
        setOf(tabOrder.indexOf(AppTab.VIDEO), tabOrder.indexOf(AppTab.ONLINE))
    }
    // Where the assistant currently sits, so the toon bar keeps drawing
    // Sora's headshot on her own tab after a reorder.
    val assistantIndex = remember(tabOrder) { tabOrder.indexOf(AppTab.AGENT) }

    // Tabs are pages of a HorizontalPager now, so the user can either tap a
    // tab or simply swipe left/right to move between sections. The pager is
    // the single source of truth for "which tab is open".
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val pagerScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage

    var showThemeMenu by remember { mutableStateOf(false) }
    var showMaxWordsDialog by remember { mutableStateOf(false) }
    var showFileManager by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var maxWords by remember { mutableIntStateOf(sharedPrefs.getInt("max_words", 0)) }

    // Donate popup: shown on every app launch until the user taps "دیگر نمایش نده".
    // The flag is stored in app_prefs under "dont_show_donate_dialog".
    var showDonatePopup by remember { mutableStateOf(!sharedPrefs.getBoolean("dont_show_donate_dialog", false)) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val appVersionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }

    // ── In-app update ──
    // The latest GitHub release, once a check finds one newer than the
    // installed build; null means "no update known". Two paths lead here:
    // a silent check ~1.5s after launch, and the settings-menu item, which
    // always re-checks and reports "up to date" / failures with a toast.
    var availableUpdate by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }

    fun checkForUpdates(manual: Boolean) {
        pagerScope.launch {
            AppUpdateManager.checkForUpdate().fold(
                onSuccess = { info ->
                    if (info != null) {
                        availableUpdate = info
                    } else if (manual) {
                        Toast.makeText(context, strings.updateUpToDateToast, Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { error ->
                    // The automatic check stays silent when offline; a manual
                    // one should tell the user what happened.
                    if (manual) {
                        Toast.makeText(
                            context,
                            strings.updateCheckFailedToast(error.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    // Silent update check shortly after launch, so it never competes with
    // first-frame composition. A tag the user already dismissed with
    // "بعداً" is skipped until a NEWER release appears.
    LaunchedEffect(Unit) {
        delay(1500)
        AppUpdateManager.checkForUpdate().onSuccess { info ->
            val skippedTag = sharedPrefs.getString(AppUpdateManager.PREFS_SKIPPED_TAG_KEY, null)
            if (info != null && info.tagName != skippedTag) {
                availableUpdate = info
            }
        }
    }

    val activeWord by viewModel.activeWord.collectAsState()
    val activeEnglishContext by viewModel.activeEnglishContext.collectAsState()
    val activePersianContext by viewModel.activePersianContext.collectAsState()
    val dictionaryResults by viewModel.dictionaryResults.collectAsState()
    val isActiveWordInLeitner by viewModel.isActiveWordInLeitner.collectAsState()
    // Imported dictionary file names, used by the dictionary popup to show
    // per-file filter buttons once 3+ dictionaries are loaded (see
    // DictionaryBottomSheet.kt's DictionarySourceFilterRow).
    val importedDictFiles by viewModel.importedDictFiles.collectAsState()

    val videoUri by viewModel.videoUri.collectAsState()
    val videoFileName by viewModel.videoFileName.collectAsState()
    val subEnFileName by viewModel.subEnFileName.collectAsState()
    val subFaFileName by viewModel.subFaFileName.collectAsState()
    val subEnList by viewModel.subEnList.collectAsState()
    val subFaList by viewModel.subFaList.collectAsState()
    val jsonSubtitles by viewModel.jsonSubtitles.collectAsState()
    val jsonSubFileName by viewModel.jsonSubFileName.collectAsState()
    val useDictionaryWithJson by viewModel.useDictionaryWithJson.collectAsState()
    val learningLevel by viewModel.learningLevel.collectAsState()
    val learningSheet by viewModel.learningSheet.collectAsState()
    val activeJsonWord by viewModel.activeJsonWord.collectAsState()

    val isTranslatingSingle by viewModel.isTranslatingSingle.collectAsState()
    val translatingIndex by viewModel.translatingIndex.collectAsState()
    val singleTranslateError by viewModel.singleTranslateError.collectAsState()
    val isLearning by viewModel.isLearning.collectAsState()
    val learnResult by viewModel.learnResult.collectAsState()
    val learnProgress by viewModel.learnProgress.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val leitnerMessage by viewModel.leitnerMessage.collectAsState()

    var isFullScreen by remember { mutableStateOf(false) }

    // Focus mode (video tab): hides the top bar/tabs, the import section and
    // the subtitle time-sync cards so only the video + subtitle list remain.
    var focusMode by remember { mutableStateOf(false) }

    // Keep the Video tab focused on playback by default. Import is one tap
    // away in its compact header, and the user's explicit expanded/collapsed
    // choice remains persisted. Scrolling the subtitle list may also fold it
    // (importSectionCollapsedByScroll marks those temporary folds).
    var isImportSectionExpanded by remember { mutableStateOf(sharedPrefs.getBoolean("video_import_section_expanded", false)) }
    var importSectionCollapsedByScroll by remember { mutableStateOf(false) }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.setVideo(it) }
    }
    val subEnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.loadSubEn(it) }
    }
    val subFaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.loadSubFa(it) }
    }

    // JSON subtitle-learning file picker + paste dialog + remove confirmation.
    val jsonSubLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.loadJsonSubtitleFromUri(it) }
    }
    var showRemoveSubsConfirm by remember { mutableStateOf(false) }

    // Settings sections: Theme (its own small back stack, below),
    // Prompts (prompts + JSON learning instructions) and the
    // App guide (a walkthrough of every tab and section).
    //
    // The theme area's sections (App design, App colors, Font) all open from
    // buttons INSIDE the Theme dialog — the settings menu keeps a single
    // 🎨 Theme entry — and each of them can walk back to it. The nav object
    // owns that stack; it is saveable, so the section stays open across the
    // activity recreate that switching design triggers.
    val themeNav = rememberThemeSettingsNav()
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showAppGuide by remember { mutableStateOf(false) }

    // Add-subtitle chooser: tapping a subtitle button first opens this popup,
    // where the user either picks a subtitle file or pastes one straight from
    // the clipboard (copied file contents / a copied .srt file). null = hidden.
    // Targets: 0 = English subtitle, 1 = Persian subtitle, 2 = JSON subtitle.
    var subtitleChooserTarget by remember { mutableStateOf<Int?>(null) }
    // The JSON quiz fullscreen overlay. Saveable AND hosted here in the
    // activity's own composition (not a Dialog window), so a running quiz
    // survives Recents, recreation and process death.
    var showQuizOverlay by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSaveMessage()
        }
    }

    // Leitner box messages (added/updated/exported/empty-box warnings) are
    // surfaced here at the top level so they show as a Toast no matter which
    // tab is active — e.g. adding a word from the dictionary popup while on
    // the "پخش‌کننده ویدیو" tab still confirms the save.
    LaunchedEffect(leitnerMessage) {
        leitnerMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearLeitnerMessage()
        }
    }

    val themeIcon = when (currentThemeMode) {
        AppThemeMode.LIGHT -> Icons.Outlined.LightMode
        AppThemeMode.DARK -> Icons.Outlined.DarkMode
        AppThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
    }

    // The navigation chrome is hidden in fullscreen, in focus mode, and while
    // a learning popup (dictionary / lesson sheet) is open, so the popup gets
    // the full attention.
    val chromeVisible = (!isFullScreen || selectedTab !in playerPages) &&
        !focusMode && activeWord == null && learningSheet == null && !showQuizOverlay

    // Authentic Material 3 chrome for the Material designs (M3 / Material
    // You), per m3.material.io: a real TopAppBar (surface color, plain
    // on-surface title, a standard M3 IconButton), and M3 SegmentedButtons in
    // the settings menu. The Langosphere design keeps its own branded glass
    // header.
    val material3Chrome = isMaterial3Design()

    // Neobrutalist chrome: like the Material designs it is a flat, solid bar
    // with no glass/gradient decoration — but it is drawn with ink borders
    // and flat ink icons by the neo branches below (never M3 TopAppBar).
    val neoChrome = isNeobrutalismDesign()

    // Toon chrome: a halftone paper band carrying the outlined "Langosphere"
    // logotype and a circular Sora badge, with navigation moved to the
    // floating bottom sticker bar (or a vertical rail on wide windows).
    val animeChrome = isAnimeDesign()

    // M3 navigation presentation per the material-design-3-ui skill: only the
    // Material You design uses a bottom NavigationBar on compact (phone)
    // windows and a side NavigationRail on medium / expanded (tablet /
    // desktop) windows. The Material Design 3 baseline keeps its top M3 tab
    // bar, and the Langosphere design keeps its own top liquid tab bar.
    val materialYou = isMaterialYouDesign()
    val compact = LocalConfiguration.current.screenWidthDp < 600

    // Where the primary navigation goes: the user's override (Settings ▸
    // Theme ▸ Customize ▸ Layout & shapes) or, by default, whatever the
    // active design does by itself. The rails below are a wide-window
    // presentation and are not affected by it.
    val navAtBottom = tabsAtBottom()

    // Medium / expanded windows place the Material You NavigationRail on the
    // leading edge, with the app content (a Scaffold) beside it. Compact
    // windows and the other designs use the plain Scaffold layout.
    // The layout is LTR in every UI language (see the note on the app's root
    // provider): the rail sits on the left and the content beside it, whatever
    // the language of the copy inside them.
    val appLayoutDirection = LayoutDirection.Ltr
    // The toon rail appears on genuinely wide windows only (>= 840dp, the
    // M3 "expanded" breakpoint); narrower windows keep the bottom sticker
    // bar, which is easier to reach one-handed.
    val toonExpanded = LocalConfiguration.current.screenWidthDp >= 840

    Row(modifier = Modifier.fillMaxSize()) {
        if (animeChrome && toonExpanded && chromeVisible) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ToonNavigationRail(
                    items = tabs,
                    selectedIndex = selectedTab,
                    onTabSelected = { index ->
                        pagerScope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
            }
        }

        if (materialYou && !compact && chromeVisible) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                M3NavigationRail(
                    items = tabs,
                    selectedIndex = selectedTab,
                    onTabSelected = { index ->
                        pagerScope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
            }
        }

        Scaffold(
            topBar = {
            if (chromeVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                neoChrome -> SolidColor(MaterialTheme.colorScheme.surfaceContainerLowest)
                                // The toon band is flat paper; its texture is
                                // the halftone drawn over it just below.
                                animeChrome -> SolidColor(MaterialTheme.colorScheme.background)
                                material3Chrome -> SolidColor(MaterialTheme.colorScheme.surface)
                                else -> Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.background,
                                    )
                                )
                            }
                        )
                        .then(if (animeChrome) Modifier.halftone(alpha = 0.10f) else Modifier)
                ) {
                    // ── Brand header ──
                    // Header row stays LTR so the settings gear keeps its
                    // position (top-right); only the dropdown contents are
                    // RTL when FA per user request.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        // The neobrutalist bar swaps the animated gradient
                        // "sphere" for a flat square yellow mark.
                        if (animeChrome) {
                            // Sora leads the band as the app's face. She is
                            // decorative here, so she honours "Show Sora".
                            SoraAvatar(size = 40.dp, borderWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                        } else if (!material3Chrome && !neoChrome) {
                            LangosphereMark(modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                        } else if (neoChrome) {
                            val neoMarkShape = if (isFriendlyNeobrutalismDesign()) CircleShape else RoundedCornerShape(0.dp)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(neoMarkShape)
                                    .background(neoAccent())
                                    .border(2.dp, MaterialTheme.colorScheme.outline, neoMarkShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (animeChrome) {
                                // Manga logotype: ink-stroked glyphs with a
                                // Sakura fill.
                                ToonOutlinedTitle(
                                    text = "Langosphere",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fillColor = AnimeColors.Sakura,
                                    strokeWidth = 7f,
                                )
                            } else {
                            Text(
                                // The product name is always "Langosphere",
                                // in every language.
                                text = "Langosphere",
                                maxLines = 1,
                                style = if (material3Chrome || neoChrome) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.titleLarge.copy(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary,
                                                MaterialTheme.colorScheme.secondary,
                                            )
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 21.sp,
                                        letterSpacing = 0.4.sp,
                                    )
                                },
                                color = if (material3Chrome || neoChrome) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.Unspecified
                                },
                            )
                            }
                            // Live subtitle: the section the user is currently in.
                            Text(
                                text = tabs[selectedTab.coerceIn(0, tabs.lastIndex)].title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Box {
                            SoftIconButton(
                                icon = themeIcon,
                                contentDescription = strings.changeThemeCd,
                                onClick = { showThemeMenu = true },
                                size = 38.dp
                            )
                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false },
                                containerColor = if (neoChrome) {
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                } else {
                                    // The M3 dropdown default container (surface
                                    // container) — stated explicitly so the neo
                                    // skin can override it without referencing
                                    // the version-renamed defaults object.
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                border = if (neoChrome) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                                } else {
                                    null
                                },
                                shape = when {
                                    neoChrome -> if (isFriendlyNeobrutalismDesign()) MaterialTheme.shapes.large else RoundedCornerShape(0.dp)
                                    material3Chrome -> MaterialTheme.shapes.extraLarge
                                    else -> RoundedCornerShape(22.dp)
                                }
                            ) {
                                // The settings menu is the ONE chrome
                                // section that mirrors for Persian: its rows
                                // read right-to-left with the leading icons
                                // on the right, while the anchor gear and
                                // every other part of the app chrome keep
                                // their LTR placement.
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides
                                        if (appLanguage == AppLanguage.FA) LayoutDirection.Rtl
                                        else appLayoutDirection
                                ) {
                                    // Day / night / system as one visual picker
                                    // instead of three identical text rows. The
                                    // Material designs use the real M3
                                    // SingleChoiceSegmentedButtonRow; Langosphere
                                    // uses its own quick chips.
                                    if (material3Chrome) {
                                    ThemeModeSegmentedRow(
                                        strings = strings,
                                        currentThemeMode = currentThemeMode,
                                        onThemeToggle = onThemeToggle,
                                        onDismiss = { showThemeMenu = false },
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ThemeQuickChip(
                                            icon = Icons.Outlined.LightMode,
                                            label = strings.themeLightMenu,
                                            selected = currentThemeMode == AppThemeMode.LIGHT,
                                            onClick = {
                                                onThemeToggle(AppThemeMode.LIGHT)
                                                showThemeMenu = false
                                            }
                                        )
                                        ThemeQuickChip(
                                            icon = Icons.Outlined.DarkMode,
                                            label = strings.themeDarkMenu,
                                            selected = currentThemeMode == AppThemeMode.DARK,
                                            onClick = {
                                                onThemeToggle(AppThemeMode.DARK)
                                                showThemeMenu = false
                                            }
                                        )
                                        ThemeQuickChip(
                                            icon = Icons.Outlined.SettingsBrightness,
                                            label = strings.themeSystemMenu,
                                            selected = currentThemeMode == AppThemeMode.SYSTEM,
                                            onClick = {
                                                onThemeToggle(AppThemeMode.SYSTEM)
                                                showThemeMenu = false
                                            }
                                        )
                                    }
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(strings.appLanguageMenu) },
                                    leadingIcon = { MenuIcon(Icons.Filled.Language) },
                                    onClick = {
                                        showThemeMenu = false
                                        showLanguageDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.maxWordsMenuLabel(maxWords)) },
                                    leadingIcon = { MenuIcon(Icons.Filled.Style) },
                                    onClick = {
                                        showThemeMenu = false
                                        showMaxWordsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.fileManagerMenu) },
                                    leadingIcon = { MenuIcon(Icons.Filled.Download) },
                                    onClick = {
                                        showThemeMenu = false
                                        viewModel.refreshManagedFiles()
                                        showFileManager = true
                                    }
                                )
                                HorizontalDivider()
                                // Settings ▸ Theme section (main theme mode
                                // picker).
                                DropdownMenuItem(
                                    text = { Text(strings.themeSectionMenu) },
                                    leadingIcon = { MenuIcon(Icons.Filled.Settings) },
                                    onClick = {
                                        showThemeMenu = false
                                        themeNav.open(ThemeSettingsSection.HUB)
                                    }
                                )
                                // Settings ▸ Prompts section
                                // (AI prompts + JSON learning instructions).
                                DropdownMenuItem(
                                    text = { Text(strings.tutorialMenu) },
                                    leadingIcon = { MenuIcon(Icons.Outlined.MenuBook) },
                                    onClick = {
                                        showThemeMenu = false
                                        showTutorialDialog = true
                                    }
                                )
                                // Settings ▸ App guide — a walkthrough that
                                // explains every tab and every section on its
                                // own, for someone opening the app for the
                                // first time.
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            strings.appGuideMenu
                                        )
                                    },
                                    leadingIcon = { MenuIcon(Icons.Filled.Menu) },
                                    onClick = {
                                        showThemeMenu = false
                                        showAppGuide = true
                                    }
                                )
                                HorizontalDivider()
                                // Manual update check: always re-checks the
                                // latest GitHub release, whatever was
                                // dismissed before, and toasts the outcome.
                                DropdownMenuItem(
                                    text = { Text(strings.updateMenu) },
                                    leadingIcon = { MenuIcon(Icons.Filled.SystemUpdateAlt) },
                                    onClick = {
                                        showThemeMenu = false
                                        checkForUpdates(manual = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.aboutMenu) },
                                    leadingIcon = { MenuIcon(Icons.Filled.Info) },
                                    onClick = {
                                        showThemeMenu = false
                                        showAboutDialog = true
                                    }
                                )
                                }
                            }
                        }
                    }
                    }

                    // ── Primary navigation ──
                    // The Langosphere design and the Material Design 3 baseline
                    // both use a TOP tab bar (LiquidTabBar picks the M3 TabRow
                    // for the Material Design 3 design). Only the Material You
                    // design moves navigation to a bottom NavigationBar / side
                    // NavigationRail (see bottomBar below), so no tab bar is
                    // rendered here for it. The bar itself always stays LTR
                    // so tab order never flips, even when the surrounding
                    // chrome is RTL.
                    // Neither the Material You design nor the toon skin keeps
                    // a top tab bar: both move primary navigation to the
                    // bottom (see bottomBar), and the toon rail replaces even
                    // that on expanded windows.
                    // Each design keeps its own bar; only WHERE it is drawn
                    // is up to the user.
                    if (!navAtBottom) {
                        PrimaryNavBar(
                            tabs = tabs,
                            assistantIndex = assistantIndex,
                            animeChrome = animeChrome,
                            materialYou = materialYou,
                            showRailInstead = (animeChrome && toonExpanded) || (materialYou && !compact),
                            selectedTab = selectedTab,
                            indicatorPosition = {
                                pagerState.currentPage + pagerState.currentPageOffsetFraction
                            },
                            onTabSelected = { index ->
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            // The same per-design bar as above, drawn at the bottom edge
            // instead — the toon skin and Material You land here by default,
            // and any design lands here once the user moves the tabs down.
            if (chromeVisible && navAtBottom) {
                PrimaryNavBar(
                    tabs = tabs,
                    assistantIndex = assistantIndex,
                    animeChrome = animeChrome,
                    materialYou = materialYou,
                    showRailInstead = (animeChrome && toonExpanded) || (materialYou && !compact),
                    selectedTab = selectedTab,
                    indicatorPosition = {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    },
                    onTabSelected = { index ->
                        pagerScope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
            }
        }
    ) { innerPadding ->
        val contentPadding = if (isFullScreen && selectedTab in playerPages) PaddingValues(0.dp) else innerPadding

        // The pager itself always stays LTR so swipe direction and tab order
        // never flip; each page's content restores the app's RTL when Persian.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                key = { it },
                // Swiping must not fight with the player gestures, the fullscreen
                // video or an open learning popup.
                userScrollEnabled = chromeVisible,
            ) { page ->
                CompositionLocalProvider(LocalLayoutDirection provides appLayoutDirection) {
                    // Depth transition. It lives inside a graphicsLayer *block* on
                    // purpose: the block is re-evaluated on every frame without
                    // recomposing the (expensive) screens below it.
                    // The video page is left untransformed because ExoPlayer renders
                    // into a SurfaceView, which does not scale/fade cleanly.
                    val pageModifier = if (page in playerPages) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val distance = ((pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                val closeness = 1f - distance
                                alpha = 0.55f + 0.45f * closeness
                                val scale = 0.93f + 0.07f * closeness
                                scaleX = scale
                                scaleY = scale
                            }
                    }

                    Box(modifier = pageModifier) {
                        when (tabOrder.getOrNull(page)) {
                            AppTab.READER -> RtledForFa(appLanguage) {
                                ReaderScreen(viewModel = viewModel)
                            }
                            AppTab.VIDEO -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                        if (!isFullScreen && !focusMode) {
                            // Collapsible import panel: a wide tile for the
                            // media file and three tiles for the subtitle
                            // slots, each showing its own state.
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (isImportSectionExpanded) 180f else 0f,
                                animationSpec = tween(durationMillis = 240),
                                label = "importChevron"
                            )
                            // The whole import drawer (header, media tile, subtitle slots,
                            // JSON chunks) mirrors for Persian; the player below keeps its
                            // deliberate LTR flow. English keeps LTR everywhere.
                            RtledForFa(appLanguage) {
                            GlassCard(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                cornerRadius = 24.dp,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(if (neoChrome) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp))
                                        .then(
                                            if (neoChrome) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline) else Modifier
                                        )
                                        .clickable {
                                            isImportSectionExpanded = !isImportSectionExpanded
                                            // A manual toggle always overrides any scroll-driven fold.
                                            importSectionCollapsedByScroll = false
                                            sharedPrefs.edit().putBoolean("video_import_section_expanded", isImportSectionExpanded).apply()
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.videoImportSectionTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (neoChrome) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    // While folded, the loaded files are still
                                    // summarised so nothing is hidden.
                                    if (!isImportSectionExpanded && videoFileName.isNotEmpty()) {
                                        StatusPill(text = videoFileName.take(18), tone = PillTone.Accent)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (isImportSectionExpanded) strings.collapseImportSection else strings.expandImportSection,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .graphicsLayer { rotationZ = chevronRotation }
                                    )
                                }

                                androidx.compose.animation.AnimatedVisibility(visible = isImportSectionExpanded) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        // Media file: the widest tile, since
                                        // it is the one that is always needed.
                                        MediaImportTile(
                                            label = strings.videoAudioLabel,
                                            fileName = videoFileName,
                                            onClick = { videoLauncher.launch(arrayOf("video/*", "audio/*")) }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ImportSlot(
                                                modifier = Modifier.weight(1f),
                                                icon = Icons.Filled.Subtitles,
                                                label = strings.subEnLabel,
                                                fileName = subEnFileName,
                                                accent = AccentCyan,
                                                onClick = { subtitleChooserTarget = 0 }
                                            )
                                            ImportSlot(
                                                modifier = Modifier.weight(1f),
                                                icon = Icons.Filled.Subtitles,
                                                label = strings.subFaLabel,
                                                fileName = subFaFileName,
                                                accent = AccentAmber,
                                                onClick = { subtitleChooserTarget = 1 }
                                            )
                                            ImportSlot(
                                                modifier = Modifier.weight(1f),
                                                icon = Icons.Filled.Style,
                                                label = strings.subJsonLabel,
                                                fileName = jsonSubFileName,
                                                accent = AccentIndigo,
                                                forceActive = jsonSubtitles != null,
                                                onClick = { subtitleChooserTarget = 2 }
                                            )
                                        }

                                        // JSON chunk management: shows which
                                        // AI answers ("CHUNK 1-50") are merged
                                        // into the loaded JSON, lets one chunk
                                        // be removed again, and exports the
                                        // merged result to Downloads.
                                        if (jsonSubtitles != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            JsonChunkPanel(
                                                strings = strings,
                                                pkg = jsonSubtitles!!,
                                                fileName = jsonSubFileName,
                                                onRemoveChunk = { viewModel.removeJsonChunk(it) },
                                                onRemoveJson = { viewModel.removeJsonSubtitle() },
                                                onExportJson = { viewModel.exportJsonSubtitleToDownloads() }
                                            )
                                        }


                                        // "Remove Imported Subtitles" — clears English,
                                        // Persian AND JSON subtitle data in one tap.
                                        if (subEnFileName.isNotEmpty() || subFaFileName.isNotEmpty() || jsonSubtitles != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                                contentColor = MaterialTheme.colorScheme.error,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = { showRemoveSubsConfirm = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = strings.removeImportedSubtitlesBtn,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }  // RtledForFa — the import drawer mirrors for Persian
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            VideoPlayerScreen(
                                    videoUri = videoUri,
                                    videoFileName = videoFileName,
                                    subEnList = subEnList,
                                    subFaList = subFaList,
                                    isFullScreen = isFullScreen,
                                    onFullScreenToggle = { isFullScreen = it },
                                    // Word-click behavior follows the
                                    // "Use Dictionary When JSON Learning Data
                                    // Exists" setting: with a JSON package loaded
                                    // and the toggle OFF, the JSON learning
                                    // explanation is used instead of the
                                    // dictionary.
                                    onWordClick = { word, enContext, faContext ->
                                        if (jsonSubtitles != null && !useDictionaryWithJson) {
                                            viewModel.openWordLesson(word, enContext ?: "", faContext)
                                        } else {
                                            viewModel.lookupWord(word, enContext, faContext)
                                        }
                                    },
                                    onSentenceClick = { sentence, translation ->
                                        viewModel.openSentenceLesson(sentence, translation)
                                    },
                                    // "[+ لایتنر جمله]" on every JSON line:
                                    // the same card the book reader builds,
                                    // tagged to the film side.
                                    studyLabels = studyLabels,
                                    savedSentenceTexts = savedSentenceTexts,
                                    onAddSentenceToLeitner = { sub, timeLabel, _ ->
                                        viewModel.addSentenceToLeitner(
                                            SentenceCardFactory.fromSubtitle(sub, timeLabel)
                                        )
                                    },
                                    onShiftSubEn = { viewModel.shiftSubEn(it) },
                                    onShiftSubFa = { viewModel.shiftSubFa(it) },
                                    subEnOffset = viewModel.subEnOffset.collectAsState().value,
                                    subFaOffset = viewModel.subFaOffset.collectAsState().value,
                                    onTranslateSubtitle = { index ->
                                        val aiPrefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
                                        viewModel.translateSingleSubtitle(
                                            index = index,
                                            baseUrl = aiPrefs.getString("base_url", "http://localhost:20128/v1") ?: "http://localhost:20128/v1",
                                            apiKey = aiPrefs.getString("api_key", "") ?: "",
                                            model = aiPrefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini",
                                            targetLang = aiPrefs.getString("target_lang", "Persian") ?: "Persian"
                                        )
                                    },
                                    onSaveSrt = { viewModel.exportSrtToDownloads() },
                                    isTranslatingSingle = isTranslatingSingle,
                                    translatingIndex = translatingIndex,
                                    singleTranslateError = singleTranslateError,
                                    onStopTranslation = { viewModel.stopTranslation() },
                                    appLanguage = appLanguage,
                                    jsonPackage = jsonSubtitles,
                                    jsonOffset = viewModel.jsonOffset.collectAsState().value,
                                    onShiftJson = { viewModel.shiftJson(it) },
                                    onResetJson = { viewModel.resetJson() },
                                    focusMode = focusMode,
                                    onFocusModeToggle = { focusMode = !focusMode },
                                    pauseForLesson = learningSheet != null,
                                    // Scrolling the subtitle list up folds the
                                    // import section away; scrolling back to the
                                    // very top brings it back.
                                    onUserScrollCollapse = { shouldCollapse ->
                                        if (shouldCollapse) {
                                            if (isImportSectionExpanded) {
                                                isImportSectionExpanded = false
                                                importSectionCollapsedByScroll = true
                                                sharedPrefs.edit().putBoolean("video_import_section_expanded", false).apply()
                                            }
                                        } else if (importSectionCollapsedByScroll) {
                                            isImportSectionExpanded = true
                                            importSectionCollapsedByScroll = false
                                            sharedPrefs.edit().putBoolean("video_import_section_expanded", true).apply()
                                        }
                                    }
                                )
                        }
                    }
                }
                            AppTab.ONLINE -> {
                                OnlineVideoScreen(
                                    appViewModel = viewModel,
                                    appLanguage = appLanguage,
                                    isFullScreen = isFullScreen,
                                    onFullScreenToggle = { isFullScreen = it },
                                    focusMode = focusMode,
                                    onFocusModeToggle = { focusMode = !focusMode },
                                    // No jsonPackage here on purpose: the Online tab
                                    // reads the JSON slot only after switching it to
                                    // the open clip (AppViewModel.useOnlineJsonScope),
                                    // so the film's lesson never shows up online.
                                    useDictionaryWithJson = useDictionaryWithJson,
                                    pauseForLesson = learningSheet != null
                                )
                            }
                            AppTab.AGENT -> {
                                RtledForFa(appLanguage) {
                                AgentScreen(
                                    subEnList = subEnList,
                                    subFaList = subFaList,
                                    subEnFileName = viewModel.subEnFileName.collectAsState().value,
                                    subFaFileName = viewModel.subFaFileName.collectAsState().value,
                                    readerText = viewModel.readerText.collectAsState().value,
                                    readerFileName = viewModel.readerFileName.collectAsState().value,
                                    onUpdateSubFa = { newList -> viewModel.updateSubFaList(newList) },
                                    onUpdateReaderText = { newText -> viewModel.updateReaderText(newText) },
                                    onLearnFromSubtitles = { viewModel.learnFromSubtitlesOnly() },
                                    onLearnFromDictionary = { viewModel.learnFromDictionaryOnly() },
                                    isLearning = isLearning,
                                    learnResult = learnResult,
                                    learnProgress = learnProgress,
                                    onStopLearning = { viewModel.stopLearning() },
                                    viewModel = viewModel
                                )
                                }
                            }
                            AppTab.LEITNER -> {
                                LeitnerScreen(
                                    viewModel = viewModel,
                                    onOpenQuiz = { showQuizOverlay = true }
                                )
                            }
                            null -> Unit
                        }
                    }
                }
            }
        }

        if (activeWord != null) {
            DictionaryBottomSheet(
                searchedWord = activeWord!!,
                results = dictionaryResults,
                contextEnglish = activeEnglishContext,
                contextPersian = activePersianContext,
                isAddedToLeitner = isActiveWordInLeitner,
                onAddToLeitner = { viewModel.addActiveWordToLeitner() },
                importedFileNames = importedDictFiles.map { it.name },
                onSearchQueryChange = { newQuery ->
                    viewModel.lookupWord(newQuery, activeEnglishContext, activePersianContext)
                },
                onDismissRequest = { viewModel.clearActiveWord() },
                appLanguage = appLanguage,
                // JSON learning data follows the dictionary system: when the
                // dictionary opens for a word that also exists in the JSON
                // learning file, the JSON word data is shown on top.
                jsonWord = activeJsonWord
            )
        }

        // Subtitle learning sheet — sentence lesson / word analysis
        // (opened by sentence clicks and by word clicks while the
        // dictionary toggle is disabled and a JSON package is loaded).
        learningSheet?.let { sheet ->
            SubtitleLearningSheet(
                state = sheet,
                strings = strings,
                learningLevel = learningLevel,
                onWordClick = { word, sentence, translation ->
                    viewModel.openWordLesson(word, sentence, translation)
                },
                onDismiss = { viewModel.clearLearningSheet() }
            )
        }

        // Max Words Dialog
        if (showMaxWordsDialog) {
            var sliderValue by remember { mutableFloatStateOf(maxWords.toFloat()) }
            AlertDialog(
                onDismissRequest = { showMaxWordsDialog = false },
                title = { Text(strings.maxWordsDialogTitle) },
                text = {
                    Column {
                        Text(
                            text = if (sliderValue == 0f) strings.maxWordsUnlimitedAll else strings.maxWordsCountText(sliderValue.toInt()),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 0f..10000f,
                            steps = 99,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.rangeStart, style = MaterialTheme.typography.bodySmall)
                            Text(strings.rangeMid, style = MaterialTheme.typography.bodySmall)
                            Text(strings.rangeEnd, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            strings.maxWordsHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = sliderValue.toInt()
                        maxWords = value
                        sharedPrefs.edit().putInt("max_words", value).apply()
                        showMaxWordsDialog = false
                    }) {
                        Text(strings.save)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMaxWordsDialog = false }) {
                        Text(strings.cancel)
                    }
                }
            )
        }

        // App language dialog — lets the user switch the UI between Persian
        // and English at any time; the choice is persisted by AppViewModel.
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(strings.appLanguageDialogTitle) },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAppLanguage(AppLanguage.FA); showLanguageDialog = false }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.languageFaLabel, style = MaterialTheme.typography.bodyLarge)
                            RadioButton(
                                selected = appLanguage == AppLanguage.FA,
                                onClick = { viewModel.setAppLanguage(AppLanguage.FA); showLanguageDialog = false }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAppLanguage(AppLanguage.EN); showLanguageDialog = false }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.languageEnLabel, style = MaterialTheme.typography.bodyLarge)
                            RadioButton(
                                selected = appLanguage == AppLanguage.EN,
                                onClick = { viewModel.setAppLanguage(AppLanguage.EN); showLanguageDialog = false }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) { Text(strings.close) }
                }
            )
        }

        // File Manager Dialog
        if (showFileManager) {
            val managedFiles by viewModel.managedFiles.collectAsState()
            val exportResult by viewModel.exportResult.collectAsState()
            AlertDialog(
                onDismissRequest = { showFileManager = false; viewModel.clearExportResult() },
                title = { Text(strings.fileManagerTitle) },
                text = {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.fileManagerSavedLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (managedFiles.isNotEmpty()) {
                                TextButton(onClick = { viewModel.exportAllToDownloads() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                    Icon(Icons.Filled.Download, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportAll, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (managedFiles.isEmpty()) {
                            Text(strings.noFilesSaved, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(managedFiles) { file ->
                                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                                Text("${formatFileSize(file.size)} • ${formatFileDate(file.lastModified)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Row {
                                                IconButton(onClick = { viewModel.exportToDownloads(file.name) }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Filled.Download, strings.exportCd, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(onClick = { viewModel.deleteManagedFile(file.name) }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Filled.Delete, strings.deleteCd, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        exportResult?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showFileManager = false; viewModel.clearExportResult() }) { Text(strings.close) } }
            )
        }

        // «انتخاب JSON یادگیری»: the JSON slot (target 2 — the Video tab's
        // import slot and the Online tab's action drawer both land here) opens
        // the same two-option chooser as the EN/FA subtitle slots: pick a file
        // or paste from the clipboard. The quiz keeps its own import panel
        // inside the quiz overlay, so attaching a JSON to a player never reads
        // as "import and build the quiz".
        if (subtitleChooserTarget == 2) {
            RtledForFa(appLanguage) {
            AlertDialog(
                onDismissRequest = { subtitleChooserTarget = null },
                title = { Text(strings.addJsonSubtitleTitle(strings.subJsonLabel), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            strings.chooseSubtitleSourceTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SourceOptionCard(
                            icon = Icons.Filled.AttachFile,
                            accent = MaterialTheme.colorScheme.primary,
                            title = strings.selectJsonFileOption,
                            description = strings.selectJsonFileDesc,
                            onClick = {
                                subtitleChooserTarget = null
                                jsonSubLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SourceOptionCard(
                            icon = Icons.Filled.ContentPaste,
                            accent = MaterialTheme.colorScheme.secondary,
                            title = strings.pasteJsonOption,
                            description = strings.pasteJsonDesc,
                            onClick = {
                                subtitleChooserTarget = null
                                viewModel.loadJsonFromClipboard()
                            }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { subtitleChooserTarget = null }) { Text(strings.cancel) }
                }
            )
            }
        }

        // Add-subtitle chooser popup: lets the user either pick a subtitle
        // file from storage or paste a copied subtitle (file contents or a
        // copied subtitle file) straight from the clipboard.
        if (subtitleChooserTarget == 0 || subtitleChooserTarget == 1) {
            RtledForFa(appLanguage) {
            val target = subtitleChooserTarget!!
            val isEnglish = target == 0
            val subtitleLabel = if (isEnglish) strings.subEnLabel else strings.subFaLabel
            AlertDialog(
                onDismissRequest = { subtitleChooserTarget = null },
                title = { Text(strings.addSubtitleTitle(subtitleLabel), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            strings.chooseSubtitleSourceTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SourceOptionCard(
                            icon = Icons.Filled.AttachFile,
                            accent = MaterialTheme.colorScheme.primary,
                            title = strings.selectSubtitleFileOption,
                            description = strings.selectSubtitleFileDesc,
                            onClick = {
                                subtitleChooserTarget = null
                                if (isEnglish) subEnLauncher.launch(arrayOf("*/*"))
                                else subFaLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SourceOptionCard(
                            icon = Icons.Filled.ContentPaste,
                            accent = MaterialTheme.colorScheme.secondary,
                            title = strings.pasteFromClipboardOption,
                            description = strings.pasteFromClipboardDesc,
                            onClick = {
                                subtitleChooserTarget = null
                                if (isEnglish) viewModel.loadSubEnFromClipboard()
                                else viewModel.loadSubFaFromClipboard()
                            }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { subtitleChooserTarget = null }) { Text(strings.cancel) }
                }
            )
            }
        }

        // Paste-JSON import dialog (auto-detects the format live and offers
        // a one-tap sample JSON for testing the import).

        // Confirmation before "Remove Imported Subtitles" clears EN/FA/JSON data.
        if (showRemoveSubsConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveSubsConfirm = false },
                title = { Text(strings.removeSubsConfirmTitle, fontWeight = FontWeight.Bold) },
                text = { Text(strings.removeSubsConfirmDesc) },
                confirmButton = {
                    TextButton(onClick = {
                        showRemoveSubsConfirm = false
                        viewModel.removeAllSubtitles()
                    }) {
                        Text(strings.removeImportedSubtitlesBtn, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveSubsConfirm = false }) { Text(strings.cancel) }
                }
            )
        }

        // Donate popup — shown every time the app opens, unless the user has
        // tapped "دیگر نمایش نده" (stored in app_prefs.dont_show_donate_dialog).
        if (showDonatePopup) {
            DonatePopupDialog(
                onDismiss = { showDonatePopup = false },
                onDontShowAgain = {
                    sharedPrefs.edit().putBoolean("dont_show_donate_dialog", true).apply()
                    showDonatePopup = false
                },
                strings = strings
            )
        }

        // About section (settings) — includes the same donate info.
        if (showAboutDialog) {
            AboutDialog(
                onDismiss = { showAboutDialog = false },
                versionName = appVersionName,
                strings = strings
            )
        }

        // In-app update: shown when the latest GitHub release is newer than
        // the installed build (silent launch check, or the settings-menu
        // item, which ignores the skipped tag). Dismissing it remembers the
        // skipped tag, so the launch check stops asking until a NEWER
        // release appears.
        availableUpdate?.let { updateInfo ->
            UpdateDialog(
                updateInfo = updateInfo,
                currentVersionName = appVersionName,
                strings = strings,
                onDismiss = {
                    sharedPrefs.edit()
                        .putString(AppUpdateManager.PREFS_SKIPPED_TAG_KEY, updateInfo.tagName)
                        .apply()
                    availableUpdate = null
                }
            )
        }

        // Settings ▸ Theme — the hub (theme mode + one button per section)
        // and its sections (App design / App colors / Font), each on its own
        // screen with a Back control that returns to the hub. The host picks
        // the dialog from the shared back stack.
        ThemeSettingsHost(
            nav = themeNav,
            strings = strings,
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeToggle,
        )

        // Settings ▸ Prompts section — learning level,
        // dictionary-vs-JSON toggle, and the JSON prompt generator with the
        // six prompt modes.
        if (showTutorialDialog) {
            TutorialAiDialog(
                strings = strings,
                learningLevel = learningLevel,
                useDictionaryWithJson = useDictionaryWithJson,
                onLearningLevelChange = { viewModel.setLearningLevel(it) },
                onDictionaryToggleChange = { viewModel.setUseDictionaryWithJson(it) },
                onDismiss = { showTutorialDialog = false }
            )
        }

        // Settings ▸ App guide — one chapter per tab and per section.
        if (showAppGuide) {
            AppGuideDialog(
                strings = strings,
                onDismiss = { showAppGuide = false }
            )
        }
        }
    }

    // The quiz overlay lives HERE — the last child of MainScreenContent,
    // OUTSIDE the Scaffold (whose navigation bars drew over the content
    // slot and hid the quiz's docked start button) and outside its Row.
    // Nothing can cover it, the chrome hides while it runs (chromeVisible),
    // and its saveable state survives Recents and process death.
    if (showQuizOverlay) {
        RtledForFa(appLanguage) {
            JsonQuizBetaDialog(
                viewModel = viewModel,
                strings = strings,
                onDismiss = { showQuizOverlay = false },
            )
        }
    }
}

/** Small leading icon used by the top-right settings menu. */
@Composable
private fun MenuIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        // In the neubrutalist skin menu icons are flat ink glyphs (never the
        // yellow accent, which would vanish on the white menu panel).
        tint = if (isNeobrutalismDesign()) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier.size(19.dp)
    )
}

/**
 * The Material 3 light / dark / system selector inside the settings menu —
 * a real [SingleChoiceSegmentedButtonRow] with the spec's connected item
 * shapes, matching m3.material.io. Only used by the Material designs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSegmentedRow(
    strings: AppStrings,
    currentThemeMode: AppThemeMode,
    onThemeToggle: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val modes = listOf(
        AppThemeMode.LIGHT to strings.themeLightMenu,
        AppThemeMode.DARK to strings.themeDarkMenu,
        AppThemeMode.SYSTEM to strings.themeSystemMenu,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = currentThemeMode == mode,
                onClick = {
                    onThemeToggle(mode)
                    onDismiss()
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

/**
 * Light / dark / system picker shown at the top of the settings menu.
 * Langosphere shows soft tinted pills; neobrutalism shows square,
 * ink-outlined chips with the active mode as a flat yellow block.
 */
@Composable
private fun ThemeQuickChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    if (isNeobrutalismDesign()) {
        val shape = if (isFriendlyNeobrutalismDesign()) MaterialTheme.shapes.medium else RoundedCornerShape(0.dp)
        Box(
            modifier = Modifier
                .width(76.dp)
                .clip(shape)
                .background(if (selected) neoAccent() else scheme.surfaceContainerLowest)
                .border(2.dp, scheme.outline, shape)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected && isFriendlyNeobrutalismDesign()) scheme.onPrimary else if (selected) Color.Black else scheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected && isFriendlyNeobrutalismDesign()) scheme.onPrimary else if (selected) Color.Black else scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        return
    }
    Surface(
        color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) scheme.primary else scheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(76.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** Wide tile for the video / audio file itself. */
@Composable
private fun MediaImportTile(
    label: String,
    fileName: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val neo = isNeobrutalismDesign()
    val loaded = fileName.isNotEmpty()
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = if (loaded) scheme.primary else null,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(if (neo) MaterialTheme.shapes.extraSmall else CircleShape)
                    .background(
                        when {
                            neo && loaded -> neoAccent()
                            neo -> scheme.surfaceVariant
                            loaded -> scheme.primary.copy(alpha = 0.18f)
                            else -> scheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    )
                    .then(
                        if (neo) Modifier.border(1.5.dp, scheme.outline) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = when {
                        neo && loaded -> Color.Black
                        neo -> scheme.onSurfaceVariant
                        loaded -> scheme.primary
                        else -> scheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1
                )
                if (loaded) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (loaded) {
                StatusPill(text = "✓", tone = PillTone.Positive)
            }
        }
    }
}

/** Compact tile for one subtitle slot (English / Persian / JSON). */
@Composable
private fun ImportSlot(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    fileName: String,
    accent: Color,
    forceActive: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val neo = isNeobrutalismDesign()
    val loaded = fileName.isNotEmpty() || forceActive
    GlassCard(
        modifier = modifier,
        tint = if (loaded) accent else null,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(if (neo) MaterialTheme.shapes.extraSmall else CircleShape)
                    .background(
                        when {
                            neo && loaded -> accent
                            neo -> scheme.surfaceVariant
                            loaded -> accent.copy(alpha = 0.20f)
                            else -> scheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    )
                    .then(
                        if (neo) Modifier.border(1.5.dp, scheme.outline) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        neo && loaded -> Color.Black
                        neo -> scheme.onSurfaceVariant
                        loaded -> accent
                        else -> scheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (loaded) FontWeight.Bold else FontWeight.Medium,
                color = if (loaded) accent else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (fileName.isNotEmpty()) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** "Pick a file" / "paste from clipboard" option row in the add-subtitle popup. */
@Composable
private fun SourceOptionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val neo = isNeobrutalismDesign()
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = accent,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(if (neo) MaterialTheme.shapes.extraSmall else CircleShape)
                    .background(if (neo) accent else accent.copy(alpha = 0.16f))
                    .then(
                        if (neo) Modifier.border(1.5.dp, scheme.outline) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (neo) Color.Black else accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The animated Langosphere mark shown in the header: a slowly orbiting
 * gradient ring around a soft core, echoing the "sphere" in the name.
 */
@Composable
private fun LangosphereMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "langosphere-mark")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 9000, easing = LinearEasing)),
        label = "orbit",
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(primary.copy(alpha = 0.32f), tertiary.copy(alpha = 0.16f)),
            ),
            radius = radius,
        )

        rotate(angle) {
            drawArc(
                brush = Brush.sweepGradient(listOf(primary, tertiary, secondary, primary)),
                startAngle = 0f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(radius * 0.16f, radius * 0.16f),
                size = Size(size.width - radius * 0.32f, size.height - radius * 0.32f),
                style = Stroke(width = radius * 0.22f, cap = StrokeCap.Round),
            )
        }

        rotate(-angle * 0.65f) {
            drawArc(
                color = primary.copy(alpha = 0.80f),
                startAngle = 200f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(radius * 0.46f, radius * 0.46f),
                size = Size(size.width - radius * 0.92f, size.height - radius * 0.92f),
                style = Stroke(width = radius * 0.15f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        // Locale.US keeps the decimal separator stable regardless of the
        // device language (the default locale produced "1٫5 MB" on Persian
        // devices).
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatFileDate(timestamp: Long): String {
    val date = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
    return date.format(java.util.Date(timestamp))
}

/**
 * Tabs, overlays and chooser popups whose whole chrome mirrors for Persian:
 * the book reader, the assistant, the Leitner toolbox, the quiz overlay and
 * the subtitle/JSON attach choosers. English keeps the app-wide LTR flow.
 */
@Composable
private fun RtledForFa(appLanguage: AppLanguage, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides if (appLanguage == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) { content() }
}
