package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AboutDialog
import com.example.ui.components.DictionaryBottomSheet
import com.example.ui.components.DonatePopupDialog
import com.example.ui.components.JsonSubtitlePasteDialog
import com.example.ui.components.SubtitleLearningSheet
import com.example.ui.components.VideoPlayerScreen
import com.example.ui.screens.AgentScreen
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import com.example.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onThemeToggle: (AppThemeMode) -> Unit = {},
    currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM
) {
    val viewModel: AppViewModel = viewModel()

    val appLanguage by viewModel.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AppStrings(appLanguage) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(strings.tabReader, strings.tabVideo, strings.tabAgent, strings.tabLeitner)
    var showThemeMenu by remember { mutableStateOf(false) }
    var showMaxWordsDialog by remember { mutableStateOf(false) }
    var showFileManager by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var maxWords by remember { mutableIntStateOf(sharedPrefs.getInt("max_words", 0)) }

    // Donate popup: shown on every app launch until the user taps "دیگر نمایش نده".
    // The flag is stored in app_prefs under "dont_show_donate_dialog".
    var showDonatePopup by remember { mutableStateOf(!sharedPrefs.getBoolean("dont_show_donate_dialog", false)) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val appVersionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
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

    // Collapsible video/subtitle import section — folded up to give the
    // player and subtitle list more room. The choice is persisted. Besides
    // the header tap, scrolling the subtitle list up also folds it
    // (importSectionCollapsedByScroll marks scroll-driven folds so only
    // those are auto-reopened when the list scrolls back to the top).
    var isImportSectionExpanded by remember { mutableStateOf(!sharedPrefs.getBoolean("video_import_section_collapsed", false)) }
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
    var showJsonPasteDialog by remember { mutableStateOf(false) }
    var showRemoveSubsConfirm by remember { mutableStateOf(false) }

    // Settings sections: Theme (main theme mode picker) and
    // Tutorial & AI Learning (prompts + JSON learning instructions).
    var showThemeSettings by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }

    // Add-subtitle chooser: tapping a subtitle button first opens this popup,
    // where the user either picks a subtitle file or pastes one straight from
    // the clipboard (copied file contents / a copied .srt file). null = hidden.
    // Targets: 0 = English subtitle, 1 = Persian subtitle, 2 = JSON subtitle.
    var subtitleChooserTarget by remember { mutableStateOf<Int?>(null) }

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
    val themeLabel = when (currentThemeMode) {
        AppThemeMode.LIGHT -> "روشن"
        AppThemeMode.DARK -> "تاریک"
        AppThemeMode.SYSTEM -> "سیستم"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // The navigation bar is hidden in fullscreen, in focus mode, and
            // while a learning popup (dictionary / lesson sheet) is open, so
            // the popup gets the full attention.
            if ((!isFullScreen || selectedTab != 1) && !focusMode && activeWord == null && learningSheet == null) {
                // NOTE: Both the TopAppBar and TabRow must be wrapped in a single
                // Column here. Scaffold's `topBar` slot lays out multiple direct
                // sibling composables stacked on top of each other (not one below
                // the other), so without this Column the TabRow was overlapping
                // the TopAppBar (hiding half of the theme toggle icon) and the
                // Scaffold's reserved top inset only accounted for part of the
                // combined height, leaving the TabRow overlapping the screen
                // content below it as well.
                Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Box {
                            IconButton(onClick = { showThemeMenu = true }) {
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = strings.changeThemeCd,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.themeLightMenu) },
                                    onClick = {
                                        onThemeToggle(AppThemeMode.LIGHT)
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.themeDarkMenu) },
                                    onClick = {
                                        onThemeToggle(AppThemeMode.DARK)
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.themeSystemMenu) },
                                    onClick = {
                                        onThemeToggle(AppThemeMode.SYSTEM)
                                        showThemeMenu = false
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(strings.appLanguageMenu) },
                                    onClick = {
                                        showThemeMenu = false
                                        showLanguageDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(strings.maxWordsMenuLabel(maxWords)) },
                                    onClick = {
                                        showThemeMenu = false
                                        showMaxWordsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.fileManagerMenu) },
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
                                    onClick = {
                                        showThemeMenu = false
                                        showThemeSettings = true
                                    }
                                )
                                // Settings ▸ Tutorial & AI Learning section
                                // (AI prompts + JSON learning instructions).
                                DropdownMenuItem(
                                    text = { Text(strings.tutorialMenu) },
                                    onClick = {
                                        showThemeMenu = false
                                        showTutorialDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(strings.aboutMenu) },
                                    onClick = {
                                        showThemeMenu = false
                                        showAboutDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                val tabIcon = when (index) {
                                    0 -> Icons.Outlined.MenuBook
                                    1 -> Icons.Filled.PlayArrow
                                    2 -> Icons.Filled.Language
                                    else -> Icons.Filled.Style
                                }
                                Icon(tabIcon, contentDescription = title)
                            }
                        )
                    }
                }
                }
            }
        }
    ) { innerPadding ->
        val contentPadding = if (isFullScreen && selectedTab == 1) PaddingValues(0.dp) else innerPadding
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ReaderScreen(viewModel = viewModel)
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isFullScreen && !focusMode) {
                            // Collapsible import section: the user can fold it
                            // up (with a tap on the header) to give the video
                            // player and subtitle list more room to focus.
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isImportSectionExpanded = !isImportSectionExpanded
                                                // A manual toggle always overrides any scroll-driven fold.
                                                importSectionCollapsedByScroll = false
                                                sharedPrefs.edit().putBoolean("video_import_section_collapsed", !isImportSectionExpanded).apply()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(strings.videoImportSectionTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(text = if (isImportSectionExpanded) strings.collapseImportSection else strings.expandImportSection, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    androidx.compose.animation.AnimatedVisibility(visible = isImportSectionExpanded) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                                                    .fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                OutlinedButton(
                                    onClick = { videoLauncher.launch(arrayOf("video/*", "audio/*")) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(strings.videoAudioLabel, style = MaterialTheme.typography.labelLarge)
                                        if (videoFileName.isNotEmpty()) {
                                            Text(videoFileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { subtitleChooserTarget = 0 },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(strings.subEnLabel, style = MaterialTheme.typography.labelLarge)
                                        if (subEnFileName.isNotEmpty()) {
                                            Text(subEnFileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { subtitleChooserTarget = 1 },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(strings.subFaLabel, style = MaterialTheme.typography.labelLarge)
                                        if (subFaFileName.isNotEmpty()) {
                                            Text(subFaFileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { subtitleChooserTarget = 2 },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (jsonSubtitles != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(strings.subJsonLabel, style = MaterialTheme.typography.labelLarge, fontWeight = if (jsonSubtitles != null) FontWeight.Bold else FontWeight.Normal)
                                        if (jsonSubFileName.isNotEmpty()) {
                                            Text(jsonSubFileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                            }

                                            Text(
                                                text = strings.importSectionScrollHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                            )

                                            // "Remove Imported Subtitles" — clears English,
                                            // Persian AND JSON subtitle data in one tap.
                                            if (subEnFileName.isNotEmpty() || subFaFileName.isNotEmpty() || jsonSubtitles != null) {
                                                OutlinedButton(
                                                    onClick = { showRemoveSubsConfirm = true },
                                                    modifier = Modifier
                                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                                        .fillMaxWidth(),
                                                    shape = MaterialTheme.shapes.medium,
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(strings.removeImportedSubtitlesBtn, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                                        targetLang = aiPrefs.getString("target_lang", "فارسی") ?: "فارسی"
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
                                // Scrolling the subtitle list up folds the
                                // import section away; scrolling back to the
                                // very top brings it back.
                                onUserScrollCollapse = { shouldCollapse ->
                                    if (shouldCollapse) {
                                        if (isImportSectionExpanded) {
                                            isImportSectionExpanded = false
                                            importSectionCollapsedByScroll = true
                                            sharedPrefs.edit().putBoolean("video_import_section_collapsed", true).apply()
                                        }
                                    } else if (importSectionCollapsedByScroll) {
                                        isImportSectionExpanded = true
                                        importSectionCollapsedByScroll = false
                                        sharedPrefs.edit().putBoolean("video_import_section_collapsed", false).apply()
                                    }
                                }
                            )
                        }
                    }
                }
                2 -> {
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
                3 -> {
                    LeitnerScreen(viewModel = viewModel)
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

        // Add-subtitle chooser popup: lets the user either pick a subtitle
        // file from storage or paste a copied subtitle (file contents or a
        // copied subtitle file) straight from the clipboard. For the JSON
        // subtitle slot (target 2) the paste option opens a dedicated input
        // dialog instead of the clipboard.
        if (subtitleChooserTarget != null) {
            val target = subtitleChooserTarget!!
            val isEnglish = target == 0
            val isJson = target == 2
            val subtitleLabel = when {
                isJson -> strings.subJsonLabel
                isEnglish -> strings.subEnLabel
                else -> strings.subFaLabel
            }
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
                        Card(
                            onClick = {
                                subtitleChooserTarget = null
                                when {
                                    isJson -> jsonSubLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                    isEnglish -> subEnLauncher.launch(arrayOf("*/*"))
                                    else -> subFaLauncher.launch(arrayOf("*/*"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isJson) strings.selectJsonFileOption else strings.selectSubtitleFileOption,
                                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (isJson) strings.selectJsonFileDesc else strings.selectSubtitleFileDesc,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            onClick = {
                                subtitleChooserTarget = null
                                if (isJson) showJsonPasteDialog = true
                                else if (isEnglish) viewModel.loadSubEnFromClipboard()
                                else viewModel.loadSubFaFromClipboard()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isJson) strings.pasteJsonOption else strings.pasteFromClipboardOption,
                                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (isJson) strings.pasteJsonDesc else strings.pasteFromClipboardDesc,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { subtitleChooserTarget = null }) { Text(strings.cancel) }
                }
            )
        }

        // Paste-JSON import dialog (auto-detects the format live and offers
        // a one-tap sample JSON for testing the import).
        if (showJsonPasteDialog) {
            JsonSubtitlePasteDialog(
                strings = strings,
                onImport = { text -> viewModel.importJsonSubtitleText(text) },
                onDismiss = { showJsonPasteDialog = false }
            )
        }

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

        // Settings ▸ Theme section — main theme mode picker (existing
        // system) + the independent Beta theme layer toggle.
        if (showThemeSettings) {
            ThemeSettingsDialog(
                strings = strings,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeToggle,
                onDismiss = { showThemeSettings = false }
            )
        }

        // Settings ▸ Tutorial & AI Learning section — learning level,
        // dictionary-vs-JSON toggle, and the JSON prompt generator with the
        // three prompt modes (Translation Only / Translation + Learning /
        // Word Analysis).
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
