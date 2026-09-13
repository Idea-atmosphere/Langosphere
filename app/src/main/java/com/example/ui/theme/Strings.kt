package com.example.ui.theme

import android.content.Context
import com.example.R
import com.example.logic.AiMemoryManager
import com.example.logic.AiPromptTemplates.PromptMode
import com.example.logic.AiService
import com.example.logic.SqliteDictParser

/**
 * App-wide UI language support. [AppLanguage] is persisted and exposed by
 * AppViewModel (see AppViewModel.appLanguage / setAppLanguage); each screen
 * builds an [AppStrings] instance from the current language to look up its
 * user-facing text.
 *
 * Coverage: MainScreen, ReaderScreen, LeitnerScreen, AgentScreen,
 * TranslationScreen, VideoPlayerScreen, DictionaryBottomSheet, and the
 * donate popup / About dialog. A handful of raw exception messages
 * surfaced verbatim from Kotlin/Android APIs (e.g. `e.message`) are not
 * covered since they are not localizable app strings.
 */
enum class AppLanguage(val code: String) {
    FA("fa"),
    EN("en");

    companion object {
        fun fromCode(code: String?): AppLanguage = if (code == "en") EN else FA
    }
}

class AppStrings(
    lang: AppLanguage,
    context: Context,
) {
    val isEn = lang == AppLanguage.EN

    // Locale-wrapped resources backing every string below: values-fa/ when the
    // in-app language is Persian, values/ otherwise. The Activity is never
    // recreated — switching language just recomposes with a new AppStrings.
    // applicationContext is used so a remembered AppStrings never leaks an Activity.
    private val res = context.applicationContext.localizedFor(lang).resources

    // ── The configurable learning language pair ──
    // Every label below that used to hardcode "English" (the language being
    // learned) or "Persian" (the language everything is explained in) now reads
    // the pair typed in Settings ▸ Tutorial & AI Learning, so a German → Persian
    // or Persian → English learner never sees a lying label.
    //
    // The typed text is used EXACTLY as written — nothing renames "آلمانی" to
    // "German"; how a language is spelled is the learner's own choice, so both
    // UI languages show the same name: the one that was typed.
    //
    // These are getters, not vals, on purpose: AppStrings instances are cached
    // with remember(appLanguage, context) all over the UI, and a getter re-reads the
    // observable LanguagePairState on every access — so labels recompose the
    // moment the pair changes, without touching a single call site.
    private val srcLang get() = LanguagePairState.source
    private val tgtLang get() = LanguagePairState.target

    // ── App-wide / MainScreen ──
    val appTitle = res.getString(R.string.app_title)
    val tabReader = res.getString(R.string.tab_reader)
    val tabVideo = res.getString(R.string.tab_video)
    val tabAgent = res.getString(R.string.tab_agent)
    val tabLeitner = res.getString(R.string.tab_leitner)
    val changeThemeCd = res.getString(R.string.change_theme_cd)
    val themeLightMenu = res.getString(R.string.theme_light_menu)
    val themeDarkMenu = res.getString(R.string.theme_dark_menu)
    val themeSystemMenu = res.getString(R.string.theme_system_menu)
    val appLanguageMenu = res.getString(R.string.app_language_menu)
    val appLanguageDialogTitle = res.getString(R.string.app_language_dialog_title)
    val languageFaLabel = "فارسی"
    val languageEnLabel = "English"
    val unlimitedLabel = res.getString(R.string.unlimited_label)
    fun maxWordsMenuLabel(count: Int) = if (count == 0)
        res.getString(R.string.max_words_menu_unlimited)
    else
        res.getString(R.string.max_words_menu_count, count)
    val fileManagerMenu = res.getString(R.string.file_manager_menu)
    val aboutMenu = res.getString(R.string.about_menu)
    val videoAudioLabel = res.getString(R.string.video_audio_label)
    val subEnLabel get() = res.getString(R.string.sub_en_label, srcLang)
    val subFaLabel get() = res.getString(R.string.sub_fa_label, tgtLang)

    // Add-subtitle chooser popup (file picker vs. clipboard paste)
    fun addSubtitleTitle(label: String) = res.getString(R.string.add_subtitle_title, label)
    val chooseSubtitleSourceTitle = res.getString(R.string.choose_subtitle_source_title)
    val selectSubtitleFileOption = res.getString(R.string.select_subtitle_file_option)
    val selectSubtitleFileDesc = res.getString(R.string.select_subtitle_file_desc)
    val pasteFromClipboardOption = res.getString(R.string.paste_from_clipboard_option)
    val pasteFromClipboardDesc = res.getString(R.string.paste_from_clipboard_desc)
    val clipboardSubtitleDefaultNameEn get() = res.getString(R.string.clipboard_subtitle_default_name_en, srcLang)
    val clipboardSubtitleDefaultNameFa get() = res.getString(R.string.clipboard_subtitle_default_name_fa, tgtLang)
    val clipboardEmptyError = res.getString(R.string.clipboard_empty_error)
    val clipboardNoSubtitleError = res.getString(R.string.clipboard_no_subtitle_error)
    fun subtitleLoadedFromClipboard(name: String) = res.getString(R.string.subtitle_loaded_from_clipboard, name)

    // Max words dialog
    val maxWordsDialogTitle = res.getString(R.string.max_words_dialog_title)
    val maxWordsUnlimitedAll = res.getString(R.string.max_words_unlimited_all)
    fun maxWordsCountText(n: Int) = res.getString(R.string.max_words_count_text, n)
    val rangeStart = res.getString(R.string.range_start)
    val rangeMid = res.getString(R.string.range_mid)
    val rangeEnd = res.getString(R.string.range_end)
    val maxWordsHint = res.getString(R.string.max_words_hint)
    val save = res.getString(R.string.save)
    val cancel = res.getString(R.string.cancel)
    val close = res.getString(R.string.close)

    // ── Sora, the ANIME skin's mascot ──
    // Her rotating greetings. Only ever shown while the toon design is
    // active (and, outside the assistant, only while "Show Sora" is on).
    val soraName = res.getString(R.string.sora_name)
    val soraTagline = res.getString(R.string.sora_tagline)
    val soraGreetHello = res.getString(R.string.sora_greet_hello)
    val soraGreetTapWords = res.getString(R.string.sora_greet_tap_words)
    fun soraGreetLeitner(count: Int) = res.getString(R.string.sora_greet_leitner, count)
    val soraGreetEpisode = res.getString(R.string.sora_greet_episode)
    val soraOpenPdfHint = res.getString(R.string.sora_open_pdf_hint)

    // File manager dialog
    val fileManagerTitle = res.getString(R.string.file_manager_title)
    val fileManagerSavedLabel = res.getString(R.string.file_manager_saved_label)
    val exportAll = res.getString(R.string.export_all)
    val noFilesSaved = res.getString(R.string.no_files_saved)
    val exportCd = res.getString(R.string.export_cd)
    val deleteCd = res.getString(R.string.delete_cd)

    // ── ReaderScreen ──
    val importingDbTitle = res.getString(R.string.importing_db_title)
    fun importingWordsCount(n: Int) = res.getString(R.string.importing_words_count, n)
    val errorTitle = res.getString(R.string.error_title)
    val ok = res.getString(R.string.ok)
    val textColorCd = res.getString(R.string.text_color_cd)
    val exitFullscreenCd = res.getString(R.string.exit_fullscreen_cd)
    val fullscreenCd = res.getString(R.string.fullscreen_cd)
    val dictLoadedActive = res.getString(R.string.dict_loaded_active)
    val dictEmpty = res.getString(R.string.dict_empty)
    val dictHint = res.getString(R.string.dict_hint)
    val addDictionary = res.getString(R.string.add_dictionary)
    val clearAll = res.getString(R.string.clear_all)
    fun importedFilesCount(n: Int) = res.getString(R.string.imported_files_count, n)
    val selectTextPdf = res.getString(R.string.select_text_pdf)
    val prevPage = res.getString(R.string.prev_page)
    val nextPage = res.getString(R.string.next_page)
    fun pageOfCount(n: Int) = res.getString(R.string.page_of_count, n)
    val emptyReaderHint = res.getString(R.string.empty_reader_hint)
    val colorDialogSubtitle = res.getString(R.string.color_dialog_subtitle)
    val customColorLabel = res.getString(R.string.custom_color_label)
    val redLabel = res.getString(R.string.red_label)
    val greenLabel = res.getString(R.string.green_label)
    val blueLabel = res.getString(R.string.blue_label)
    val applyCustomColor = res.getString(R.string.apply_custom_color)
    val presetDefault = res.getString(R.string.preset_default)
    val presetBlack = res.getString(R.string.preset_black)
    val presetWhite = res.getString(R.string.preset_white)
    val presetGreen = res.getString(R.string.preset_green)
    val presetRed = res.getString(R.string.preset_red)
    val presetCyan = res.getString(R.string.preset_cyan)
    val presetIndigo = res.getString(R.string.preset_indigo)
    val presetAmber = res.getString(R.string.preset_amber)

    // ── LeitnerScreen ──
    // The two tools this tab offers, as the picker at its top. (The Leitner
    // header card no longer repeats the title — the picker already names it.)
    val toolLeitnerBox = res.getString(R.string.tool_leitner_box)
    val toolJsonQuiz = res.getString(R.string.tool_json_quiz)
    val exportAnki = res.getString(R.string.export_anki)
    fun leitnerSummary(all: Int, due: Int) = res.getString(R.string.leitner_summary, all, due)
    fun reviewTodayChip(n: Int) = res.getString(R.string.review_today_chip, n)
    fun allCardsChip(n: Int) = res.getString(R.string.all_cards_chip, n)
    val leitnerEmptyAddHint = res.getString(R.string.leitner_empty_add_hint)
    val leitnerEmptyDoneToday = res.getString(R.string.leitner_empty_done_today)
    val leitnerEmptyNoCards = res.getString(R.string.leitner_empty_no_cards)
    fun boxOfFive(level: Int) = res.getString(R.string.box_of_five, level)
    fun boxLabel(level: Int) = res.getString(R.string.box_label, level)
    val showMeaning = res.getString(R.string.show_meaning)
    val didntKnow = res.getString(R.string.didnt_know)
    val knewIt = res.getString(R.string.knew_it)

    // ── AgentScreen ──
    val agentNewChatCd = res.getString(R.string.agent_new_chat_cd)
    val agentHistoryCd = res.getString(R.string.agent_history_cd)
    val agentMemoryCd = res.getString(R.string.agent_memory_cd)
    val agentSettingsCd = res.getString(R.string.agent_settings_cd)
    val agentNoFileLoaded = res.getString(R.string.agent_no_file_loaded)
    val agentMemoryActive = res.getString(R.string.agent_memory_active)
    val apiKeyLabel = "API Key"
    val baseUrlLabel = "Base URL"
    val modelLabel = "Model"
    val targetLangLabel = res.getString(R.string.target_lang_label)
    // The AI assistant's target-language field: its placeholder is the target
    // language of the configured pair, typed exactly as the learner wrote it.
    val targetLangPlaceholder get() = tgtLang
    val bubbleColorTitle = res.getString(R.string.bubble_color_title)
    val bubbleColorHint = res.getString(R.string.bubble_color_hint)
    val sentMessagesLabel = res.getString(R.string.sent_messages_label)
    val receivedMessagesLabel = res.getString(R.string.received_messages_label)
    val defaultCd = res.getString(R.string.default_cd)
    val learnFromSubtitlesBtn = res.getString(R.string.learn_from_subtitles_btn)
    val learnFromDictionaryBtn = res.getString(R.string.learn_from_dictionary_btn)
    val stopCd = res.getString(R.string.stop_cd)
    val agentThinking = res.getString(R.string.agent_thinking)
    val askAgentPlaceholder = res.getString(R.string.ask_agent_placeholder)
    val sendCd = res.getString(R.string.send_cd)
    val chatHistoryTitle = res.getString(R.string.chat_history_title)
    val noChatsSaved = res.getString(R.string.no_chats_saved)
    val openCd = res.getString(R.string.open_cd)
    val memoryTabPrompts = res.getString(R.string.memory_tab_prompts)
    val memoryTabCorrections = res.getString(R.string.memory_tab_corrections)
    val memoryTabSkills = res.getString(R.string.memory_tab_skills)
    val memoryTabExportImport = res.getString(R.string.memory_tab_export_import)
    val promptTranslate = res.getString(R.string.prompt_translate)
    val promptChat = res.getString(R.string.prompt_chat)
    val promptAgent = "Agent"
    val promptTranslateEmpty = res.getString(R.string.prompt_translate_empty)
    val customBadge = res.getString(R.string.custom_badge)
    val editBtn = res.getString(R.string.edit_btn)
    val resetBtn = res.getString(R.string.reset_btn)
    fun editingPromptTitle(name: String) = res.getString(R.string.editing_prompt_title, name)
    val promptTextPlaceholder = res.getString(R.string.prompt_text_placeholder)
    val noCorrectionsSaved = res.getString(R.string.no_corrections_saved)
    fun correctionsSavedCount(n: Int) = res.getString(R.string.corrections_saved_count, n)
    fun sourceLabel(text: String) = res.getString(R.string.source_label, text)
    val addManualCorrection = res.getString(R.string.add_manual_correction)
    val addCorrectionTitle = res.getString(R.string.add_correction_title)
    val sourceTextLabel = res.getString(R.string.source_text_label)
    val wrongTranslationLabel = res.getString(R.string.wrong_translation_label)
    val correctTranslationLabel = res.getString(R.string.correct_translation_label)
    val noSkillsSaved = res.getString(R.string.no_skills_saved)
    fun skillsSavedCount(n: Int) = res.getString(R.string.skills_saved_count, n)
    val addSkillNote = res.getString(R.string.add_skill_note)
    val addSkillTitle = res.getString(R.string.add_skill_title)
    val textLabel = res.getString(R.string.text_label)
    val categoryUserNote = res.getString(R.string.category_user_note)
    val categoryTranslationRule = res.getString(R.string.category_translation_rule)
    val categorySkill = res.getString(R.string.category_skill)
    val categoryDictionaryTip = res.getString(R.string.category_dictionary_tip)
    val exportImportTitle = res.getString(R.string.export_import_title)
    val exportImportDesc = res.getString(R.string.export_import_desc)
    val exportToDownloadsBtn = res.getString(R.string.export_to_downloads_btn)
    val importFromFileLabel = res.getString(R.string.import_from_file_label)
    val selectFileImportBtn = res.getString(R.string.select_file_import_btn)
    val clearAllMemoryBtn = res.getString(R.string.clear_all_memory_btn)

    // ── TranslationScreen ──
    val subEnChip get() = res.getString(R.string.sub_en_chip, srcLang)
    val subFaChip get() = res.getString(R.string.sub_fa_chip, tgtLang)
    fun linesPerRequestLabel(isDefault: Boolean, n: Int) = if (isDefault)
        res.getString(R.string.lines_per_request_default)
    else
        res.getString(R.string.lines_per_request_count, n)
    val defaultChip = res.getString(R.string.default_chip)
    val translateBtn = res.getString(R.string.translate_btn)
    val allBtn = res.getString(R.string.all_btn)
    val apiKeySetError = res.getString(R.string.api_key_set_error)
    val noLinesLeftError = res.getString(R.string.no_lines_left_error)
    fun translatingLinesProgress(start: Int, end: Int, total: Int) = res.getString(R.string.translating_lines_progress, start, end, total)
    fun translatingAllProgress(total: Int) = res.getString(R.string.translating_all_progress, total)
    fun errorWithMessage(msg: String?) = res.getString(R.string.error_with_message, msg)
    val prevLineCd = res.getString(R.string.prev_line_cd)
    val nextLineCd = res.getString(R.string.next_line_cd)
    fun batchInfo(current: Int, total: Int, start: Int, end: Int) = res.getString(R.string.batch_info, current, total, start, end)
    val nextBatchBtn = res.getString(R.string.next_batch_btn)
    val allDoneLabel = res.getString(R.string.all_done_label)
    val noSubtitleLoadedTitle = res.getString(R.string.no_subtitle_loaded_title)
    val noSubtitleLoadedHint = res.getString(R.string.no_subtitle_loaded_hint)
    fun moreLinesLabel(n: Int) = res.getString(R.string.more_lines_label, n)
    val askAboutSubtitlePlaceholder = res.getString(R.string.ask_about_subtitle_placeholder)
    val translationSettingsTitle = res.getString(R.string.translation_settings_title)
    // The smart-translation sheet types its target language by hand instead of
    // picking it from a quick list, so the eight language chips and the names
    // behind them are gone: whatever is typed is what the prompt says.
    val translateTargetLangHint = res.getString(R.string.translate_target_lang_hint)
    val translationProcessing = res.getString(R.string.translation_processing)

    // ── VideoPlayerScreen ──
    val resetPositionsTitle = res.getString(R.string.reset_positions_title)
    val resumePlayBtn = res.getString(R.string.resume_play_btn)
    val autoStopPrevSubtitle = res.getString(R.string.auto_stop_prev_subtitle)
    val autoStopCurrentSubtitle = res.getString(R.string.auto_stop_current_subtitle)
    val resumeCd = res.getString(R.string.resume_cd)
    val playerSettingsCd = res.getString(R.string.player_settings_cd)
    // Focus mode (video tab): hides the top bar/tabs, the import section and
    // the time-sync cards so only the video + subtitle list remain.
    val focusModeCd = res.getString(R.string.focus_mode_cd)
    // Smart-pause gear panel (persisted options)
    val smartPauseSettingsCd = res.getString(R.string.smart_pause_settings_cd)
    val smartPausePanelTitle = res.getString(R.string.smart_pause_panel_title)
    val pauseDimTitle = res.getString(R.string.pause_dim_title)
    val pauseDimDesc = res.getString(R.string.pause_dim_desc)
    val pauseHideUiTitle = res.getString(R.string.pause_hide_ui_title)
    val pauseHideUiDesc = res.getString(R.string.pause_hide_ui_desc)
    val pauseRequireContinueTitle = res.getString(R.string.pause_require_continue_title)
    val pauseRequireContinueDesc = res.getString(R.string.pause_require_continue_desc)
    // Audio track selection (dual-language videos — works in normal and
    // smart-pause modes)
    val audioTrackTitle = res.getString(R.string.audio_track_title)
    val audioTrackDesc = res.getString(R.string.audio_track_desc)
    val audioTrackUnavailable = res.getString(R.string.audio_track_unavailable)
    fun audioTrackFallbackName(n: Int) = res.getString(R.string.audio_track_fallback_name, n)
    val audioPlayingHint = res.getString(R.string.audio_playing_hint)
    val playerSettingsTitle = res.getString(R.string.player_settings_title)
    val showSubtitlesTitle = res.getString(R.string.show_subtitles_title)
    val showSubtitlesDesc = res.getString(R.string.show_subtitles_desc)
    val smartPauseTitle = res.getString(R.string.smart_pause_title)
    val smartPauseDesc = res.getString(R.string.smart_pause_desc)
    val doubleTapSkipTitle = res.getString(R.string.double_tap_skip_title)
    fun secondsLabel(n: Int) = res.getString(R.string.seconds_label, n)
    val subtitleFontSizeTitle = res.getString(R.string.subtitle_font_size_title)
    val subtitlePositionTitle = res.getString(R.string.subtitle_position_title)
    val subtitleColorTitle = res.getString(R.string.subtitle_color_title)
    val subEnParenLabel get() = res.getString(R.string.sub_en_paren_label, srcLang)
    val subFaParenLabel get() = res.getString(R.string.sub_fa_paren_label, tgtLang)
    val subtitleFontDesc get() = res.getString(R.string.subtitle_font_desc, tgtLang)
    val fontEnLabel get() = res.getString(R.string.font_en_label, srcLang)
    val fontFaLabel get() = res.getString(R.string.font_fa_label, tgtLang)
    val fontDefault = res.getString(R.string.font_default)
    val fontCustomLabel = res.getString(R.string.font_custom_label)
    val importCustomFontBtn = res.getString(R.string.import_custom_font_btn)
    val removeCustomFontBtn = res.getString(R.string.remove_custom_font_btn)
    val syncTitle = res.getString(R.string.sync_title)
    fun syncCurrentOffset(label: String, offset: String) = res.getString(R.string.sync_current_offset, label, offset)
    fun shiftValueLabel(offset: String) = res.getString(R.string.shift_value_label, offset)
    val langCodeEn get() = res.getString(R.string.lang_code_en, srcLang)
    val langCodeFa get() = res.getString(R.string.lang_code_fa, tgtLang)
    // JSON subtitle time sync (same shift feature for the JSON learning file)
    val jsonSyncRowTitle = res.getString(R.string.json_sync_row_title)
    val jsonSyncNoTimings = res.getString(R.string.json_sync_no_timings)
    val jsonResetBtn = res.getString(R.string.json_reset_btn)
    val exactTimeLabel = res.getString(R.string.exact_time_label)
    val applyBtn = res.getString(R.string.apply_btn)
    val syncHint = res.getString(R.string.sync_hint)
    val saveSrtBtn = res.getString(R.string.save_srt_btn)
    val noFaSubtitleToSave get() = res.getString(R.string.no_fa_subtitle_to_save, tgtLang)
    val confirmReturnBtn = res.getString(R.string.confirm_return_btn)
    val allSubtitlesListTitle = res.getString(R.string.all_subtitles_list_title)
    val loadSubtitleHint = res.getString(R.string.load_subtitle_hint)
    val syncSettingsRowTitle = res.getString(R.string.sync_settings_row_title)
    val collapseSync = res.getString(R.string.collapse_sync)
    val expandSync = res.getString(R.string.expand_sync)
    val playingLabel = res.getString(R.string.playing_label)
    val playFromStartBtn = res.getString(R.string.play_from_start_btn)
    val playAutoStopBtn = res.getString(R.string.play_auto_stop_btn)
    val translatingLabel = res.getString(R.string.translating_label)
    val aiTranslateBtn = res.getString(R.string.ai_translate_btn)

    // ── DictionaryBottomSheet ──
    val allFilterChip = res.getString(R.string.all_filter_chip)
    val addedToLeitnerLabel = res.getString(R.string.added_to_leitner_label)
    val addToLeitnerBtn = res.getString(R.string.add_to_leitner_btn)
    val searchWordPlaceholder = res.getString(R.string.search_word_placeholder)
    val wordLabel = res.getString(R.string.word_label)
    val searchCd = res.getString(R.string.search_cd)
    val subtitleAndTranslationLabel = res.getString(R.string.subtitle_and_translation_label)
    fun matchedTranslationLabel(text: String) = res.getString(R.string.matched_translation_label, text)
    val autoDetectWarning = res.getString(R.string.auto_detect_warning)
    val noResultsFound = res.getString(R.string.no_results_found)

    // ── Theme settings section ──
    val themeSectionMenu = res.getString(R.string.theme_section_menu)
    val themeSectionTitle = res.getString(R.string.theme_section_title)
    val themeModeTitle = res.getString(R.string.theme_mode_title)
    val themeModeDesc = res.getString(R.string.theme_mode_desc)

    // ── Theme settings: the section hub (design / colors / font buttons) ──
    val themeSectionsTitle = res.getString(R.string.theme_sections_title)
    val themeSectionsDesc = res.getString(R.string.theme_sections_desc)
    /** Row/dialog title of the app-design section (moved behind a button). */
    val themeDesignTitle = res.getString(R.string.theme_design_title)
    /** Trailing label of the design row: the design that is active now. */
    fun themeActiveValue(value: String) = res.getString(R.string.theme_active_value, value)
    /** Back button of the theme sub-sections (returns to the Theme dialog). */
    val back = res.getString(R.string.back)
    val backToThemeBtn = res.getString(R.string.back_to_theme_btn)

    // ── Theme settings: app colors (palettes + custom hex) ──
    val themeColorsTitle = res.getString(R.string.theme_colors_title)
    val themeColorsDesc = res.getString(R.string.theme_colors_desc)
    val palettePresetsTitle = res.getString(R.string.palette_presets_title)
    val customPaletteTitle = res.getString(R.string.custom_palette_title)
    val customPaletteDesc = res.getString(R.string.custom_palette_desc)
    val paletteRolePrimary = res.getString(R.string.palette_role_primary)
    val paletteRoleSecondary = res.getString(R.string.palette_role_secondary)
    val paletteRoleTertiary = res.getString(R.string.palette_role_tertiary)
    val hexInputLabel = res.getString(R.string.hex_input_label)
    val hexInvalidError = res.getString(R.string.hex_invalid_error)

    // ── Theme settings: fonts (per scope) ──
    val themeFontTitle = res.getString(R.string.theme_font_title)
    val themeFontDesc = res.getString(R.string.theme_font_desc)
    val fontScopeApp = res.getString(R.string.font_scope_app)
    val fontScopeSubtitles = res.getString(R.string.font_scope_subtitles)
    val fontScopeReader = res.getString(R.string.font_scope_reader)
    val fontScopeLeitner = res.getString(R.string.font_scope_leitner)

    // ── Video import section (collapsible) ──
    val videoImportSectionTitle = res.getString(R.string.video_import_section_title)
    val collapseImportSection = res.getString(R.string.collapse_import_section)
    val expandImportSection = res.getString(R.string.expand_import_section)
    val importSectionScrollHint = res.getString(R.string.import_section_scroll_hint)

    // ── Tutorial & AI Learning section ──
    val tutorialMenu = res.getString(R.string.tutorial_menu)
    val tutorialTitle = res.getString(R.string.tutorial_title)
    val tutorialLearningLevelTitle = res.getString(R.string.tutorial_learning_level_title)
    val tutorialLearningLevelDesc = res.getString(R.string.tutorial_learning_level_desc)
    val dictionaryJsonToggleTitle = res.getString(R.string.dictionary_json_toggle_title)
    val dictionaryJsonToggleDescOn = res.getString(R.string.dictionary_json_toggle_desc_on)
    val dictionaryJsonToggleDescOff = res.getString(R.string.dictionary_json_toggle_desc_off)
    val promptGeneratorTitle = res.getString(R.string.prompt_generator_title)
    val promptGeneratorDesc = res.getString(R.string.prompt_generator_desc)
    val promptLevelLabel = res.getString(R.string.prompt_level_label)
    fun promptSourcePill(name: String) = res.getString(R.string.prompt_source_pill, name)
    fun promptTargetPill(name: String) = res.getString(R.string.prompt_target_pill, name)
    val promptModeTitle = res.getString(R.string.prompt_mode_title)
    val modeTranslationOnlyTitle = res.getString(R.string.mode_translation_only_title)
    val modeTranslationOnlyDesc = res.getString(R.string.mode_translation_only_desc)
    val modeTranslationLearningTitle = res.getString(R.string.mode_translation_learning_title)
    val modeTranslationLearningDesc = res.getString(R.string.mode_translation_learning_desc)
    val modeWordAnalysisTitle = res.getString(R.string.mode_word_analysis_title)
    val modeWordAnalysisDesc = res.getString(R.string.mode_word_analysis_desc)
    val promptPreviewTitle = res.getString(R.string.prompt_preview_title)
    val copyPromptBtn = res.getString(R.string.copy_prompt_btn)
    val promptCopiedToast = res.getString(R.string.prompt_copied_toast)
    fun levelName(code: String): String = when (code.trim().uppercase()) {
        "A1" -> res.getString(R.string.level_a1)
        "A2" -> res.getString(R.string.level_a2)
        "B1" -> res.getString(R.string.level_b1)
        "B2" -> res.getString(R.string.level_b2)
        "C1" -> res.getString(R.string.level_c1)
        "C2" -> res.getString(R.string.level_c2)
        else -> code
    }
    fun partOfSpeechName(pos: String?): String = when (pos?.trim()?.lowercase()) {
        "noun" -> res.getString(R.string.pos_noun)
        "verb" -> res.getString(R.string.pos_verb)
        "adjective" -> res.getString(R.string.pos_adjective)
        "adverb" -> res.getString(R.string.pos_adverb)
        "pronoun" -> res.getString(R.string.pos_pronoun)
        "preposition" -> res.getString(R.string.pos_preposition)
        "conjunction" -> res.getString(R.string.pos_conjunction)
        "interjection" -> res.getString(R.string.pos_interjection)
        "phrase" -> res.getString(R.string.pos_phrase)
        "idiom" -> res.getString(R.string.pos_idiom)
        "phrasal verb" -> res.getString(R.string.pos_phrasal_verb)
        else -> pos ?: ""
    }

    // ── JSON subtitle import ──
    val subJsonLabel = res.getString(R.string.sub_json_label)
    fun addJsonSubtitleTitle(label: String) = res.getString(R.string.add_json_subtitle_title, label)
    val selectJsonFileOption = res.getString(R.string.select_json_file_option)
    val selectJsonFileDesc = res.getString(R.string.select_json_file_desc)
    val pasteJsonOption = res.getString(R.string.paste_json_option)
    val pasteJsonDesc = res.getString(R.string.paste_json_desc)
    val jsonPasteDialogTitle = res.getString(R.string.json_paste_dialog_title)
    val jsonPastePlaceholder = res.getString(R.string.json_paste_placeholder)
    val jsonImportBtn = res.getString(R.string.json_import_btn)
    val jsonLoadSampleBtn = res.getString(R.string.json_load_sample_btn)
    val jsonDetectedLabel = res.getString(R.string.json_detected_label)
    val jsonNotSubtitleJson = res.getString(R.string.json_not_subtitle_json)
    fun jsonParseError(msg: String?) = res.getString(R.string.json_parse_error, msg)
    val jsonEmptyFileError = res.getString(R.string.json_empty_file_error)
    fun jsonImportedSuccess(name: String, count: Int) = res.getString(R.string.json_imported_success, name, count)
    val jsonDefaultName = res.getString(R.string.json_default_name)
    val jsonActiveBadge = res.getString(R.string.json_active_badge)
    val removeImportedSubtitlesBtn = res.getString(R.string.remove_imported_subtitles_btn)
    val removeSubsConfirmTitle = res.getString(R.string.remove_subs_confirm_title)
    val removeSubsConfirmDesc = res.getString(R.string.remove_subs_confirm_desc)
    val subtitleRemovedAll = res.getString(R.string.subtitle_removed_all)

    // ── JSON chunks: AI answers ("CHUNK 1-50") merged into one file ──
    val jsonChunksTitle = res.getString(R.string.json_chunks_title)
    fun jsonChunksSummary(chunks: Int, lines: Int) = res.getString(R.string.json_chunks_summary, chunks, lines)
    val jsonChunkMergedBadge = res.getString(R.string.json_chunk_merged_badge)
    val jsonChunksHint = res.getString(R.string.json_chunks_hint)
    fun jsonChunkImported(chunk: String, name: String, count: Int) = res.getString(R.string.json_chunk_imported, chunk, name, count)
    fun jsonChunkMerged(chunk: String, chunks: Int, total: Int) = res.getString(R.string.json_chunk_merged, chunk, chunks, total)
    fun jsonChunkReimported(chunk: String, total: Int) = res.getString(R.string.json_chunk_reimported, chunk, total)
    fun jsonChunkRemoved(chunk: String, remaining: Int) = res.getString(R.string.json_chunk_removed, chunk, remaining)
    fun jsonChunkRemovedLast(chunk: String) = res.getString(R.string.json_chunk_removed_last, chunk)
    fun jsonChunkLines(count: Int) = res.getString(R.string.json_chunk_lines, count)
    val jsonChunkDetectedLabel = res.getString(R.string.json_chunk_detected_label)
    val jsonChunkAutoStripHint = res.getString(R.string.json_chunk_auto_strip_hint)
    val jsonChunkMergeHint = res.getString(R.string.json_chunk_merge_hint)
    val jsonChunkReplaceHint = res.getString(R.string.json_chunk_replace_hint)
    val jsonRemoveChunkCd = res.getString(R.string.json_remove_chunk_cd)
    val jsonRemoveOnlyJsonBtn = res.getString(R.string.json_remove_only_json_btn)
    val jsonRemovedAll = res.getString(R.string.json_removed_all)
    val jsonExportBtn = res.getString(R.string.json_export_btn)
    fun jsonExportSaved(path: String, lines: Int, chunks: Int) = if (chunks > 1)
        res.getString(R.string.json_export_saved_merged, path, lines, chunks)
    else
        res.getString(R.string.json_export_saved, path, lines)
    val noJsonToExport = res.getString(R.string.no_json_to_export)

    // ── Settings ▸ Tutorial & AI Learning: the source → target language pair ──
    val sourceLanguageLabel = res.getString(R.string.source_language_label)
    val targetLanguageLabel = res.getString(R.string.target_language_label)
    val sourceLanguageHint = res.getString(R.string.source_language_hint)
    val targetLanguageHint = res.getString(R.string.target_language_hint)
    // The circled "!" beside each field: one tap explains what the field is for.
    val languageHelpCd = res.getString(R.string.language_help_cd)
    val sourceLanguageHelp = res.getString(R.string.source_language_help)
    val targetLanguageHelp = res.getString(R.string.target_language_help)
    val languagePairPreviewTitle = res.getString(R.string.language_pair_preview_title)
    fun languagePairArrow(source: String, target: String) = "$source ⟶ $target"
    fun languagePairSubtitlePreview(source: String, target: String) = res.getString(R.string.language_pair_subtitle_preview, source, target)
    fun languagePairPromptPreview(source: String, target: String) = res.getString(R.string.language_pair_prompt_preview, source, target)
    fun languagePairQuizPreview(source: String, target: String) = res.getString(R.string.language_pair_quiz_preview, source, target)
    val languageSameWarning = res.getString(R.string.language_same_warning)

    // ── Leitner tab ▸ beta: quiz built from an imported JSON package ──
    val jsonQuizBetaBadge = "BETA"
    val jsonQuizCardTitle = res.getString(R.string.json_quiz_card_title)
    val jsonQuizCardDesc = res.getString(R.string.json_quiz_card_desc)
    val jsonQuizOpenBtn = res.getString(R.string.json_quiz_open_btn)
    val jsonQuizDialogTitle = res.getString(R.string.json_quiz_dialog_title)
    val jsonQuizSourceTitle = res.getString(R.string.json_quiz_source_title)
    val jsonQuizPickFileBtn = res.getString(R.string.json_quiz_pick_file_btn)
    val jsonQuizPasteBtn = res.getString(R.string.json_quiz_paste_btn)
    val jsonQuizPasteHint = res.getString(R.string.json_quiz_paste_hint)
    val jsonQuizPasteFieldLabel = res.getString(R.string.json_quiz_paste_field_label)
    val jsonQuizImportPasteBtn = res.getString(R.string.json_quiz_import_paste_btn)
    fun jsonQuizLoadedInfo(name: String, lines: Int, chunks: Int) = if (chunks > 1)
        res.getString(R.string.json_quiz_loaded_info_merged, name, lines, chunks)
    else
        res.getString(R.string.json_quiz_loaded_info, name, lines)
    val jsonQuizNoSource = res.getString(R.string.json_quiz_no_source)
    val jsonQuizCountTitle = res.getString(R.string.json_quiz_count_title)
    fun jsonQuizCountChip(n: Int) = res.getString(R.string.json_quiz_count_chip, n)
    val jsonQuizStartBtn = res.getString(R.string.json_quiz_start_btn)
    val jsonQuizEmpty = res.getString(R.string.json_quiz_empty)
    fun jsonQuizProgress(current: Int, total: Int) = res.getString(R.string.json_quiz_progress, current, total)
    fun jsonQuizScoreLive(correct: Int, answered: Int) = res.getString(R.string.json_quiz_score_live, correct, answered)
    val jsonQuizTypeWord = res.getString(R.string.json_quiz_type_word)
    val jsonQuizTypeReverse = res.getString(R.string.json_quiz_type_reverse)
    val jsonQuizTypeSentence = res.getString(R.string.json_quiz_type_sentence)
    val jsonQuizTypeGrammar = res.getString(R.string.json_quiz_type_grammar)
    val jsonQuizContextLabel = res.getString(R.string.json_quiz_context_label)
    val jsonQuizCorrect = res.getString(R.string.json_quiz_correct)
    fun jsonQuizWrong(answer: String) = res.getString(R.string.json_quiz_wrong, answer)
    val jsonQuizSelectHint = res.getString(R.string.json_quiz_select_hint)
    val jsonQuizNextBtn = res.getString(R.string.json_quiz_next_btn)
    val jsonQuizFinishTitle = res.getString(R.string.json_quiz_finish_title)
    fun jsonQuizResult(correct: Int, total: Int, percent: Int) = res.getString(R.string.json_quiz_result, correct, total, percent)
    val jsonQuizWrongListTitle = res.getString(R.string.json_quiz_wrong_list_title)
    val jsonQuizAddToLeitnerBtn = res.getString(R.string.json_quiz_add_to_leitner_btn)
    val jsonQuizAddedToLeitner = res.getString(R.string.json_quiz_added_to_leitner)
    val jsonQuizRetryBtn = res.getString(R.string.json_quiz_retry_btn)
    val jsonQuizNewQuestionsBtn = res.getString(R.string.json_quiz_new_questions_btn)
    val jsonQuizBackToSourceBtn = res.getString(R.string.json_quiz_back_to_source_btn)

    // ── Subtitle learning sheet (sentence lesson / word analysis) ──
    val lessonSheetTitle = res.getString(R.string.lesson_sheet_title)
    val wordLessonSheetTitle = res.getString(R.string.word_lesson_sheet_title)
    val lessonTranslationLabel = res.getString(R.string.lesson_translation_label)
    val lessonGrammarLabel = res.getString(R.string.lesson_grammar_label)
    val lessonExplanationLabel = res.getString(R.string.lesson_explanation_label)
    val lessonStructureLabel = res.getString(R.string.lesson_structure_label)
    val lessonVocabLabel = res.getString(R.string.lesson_vocab_label)
    val lessonNotesLabel = res.getString(R.string.lesson_notes_label)
    val lessonPronunciationLabel = res.getString(R.string.lesson_pronunciation_label)
    val lessonDifficultyLabel = res.getString(R.string.lesson_difficulty_label)
    val lessonLevelLabel = res.getString(R.string.lesson_level_label)
    val lessonSentenceLabel = res.getString(R.string.lesson_sentence_label)
    val meaningInContextLabel = res.getString(R.string.meaning_in_context_label)
    val examplesLabel = res.getString(R.string.examples_label)
    val extraExplanationLabel = res.getString(R.string.extra_explanation_label)
    val lessonSentenceLevelNote = res.getString(R.string.lesson_sentence_level_note)
    val noJsonLessonFallback = res.getString(R.string.no_json_lesson_fallback)
    val noJsonWordData = res.getString(R.string.no_json_word_data)
    val dictionaryDataLabel = res.getString(R.string.dictionary_data_label)
    val jsonLearningDataLabel = res.getString(R.string.json_learning_data_label)
    val tapWordHint = res.getString(R.string.tap_word_hint)
    val closeSheetBtn = res.getString(R.string.close_sheet_btn)

    // ── AppViewModel status/toast messages ──
    val invalidIndexError = res.getString(R.string.invalid_index_error)
    val translationEmptyError = res.getString(R.string.translation_empty_error)
    val subEnNotLoaded get() = res.getString(R.string.sub_en_not_loaded, srcLang)
    val subFaNotLoadedBoth get() = res.getString(R.string.sub_fa_not_loaded_both, tgtLang)
    val noMatchedPairsFound = res.getString(R.string.no_matched_pairs_found)
    fun learnedPairsCount(n: Int) = res.getString(R.string.learned_pairs_count, n)
    val dictNotLoadedForLearning = res.getString(R.string.dict_not_loaded_for_learning)
    fun learnedWordsCount(count: Int, total: Int) = res.getString(R.string.learned_words_count, count, total)
    val fileNotFoundError = res.getString(R.string.file_not_found_error)
    fun savedAtPath(path: String) = res.getString(R.string.saved_at_path, path)
    val noFilesToExport = res.getString(R.string.no_files_to_export)
    fun exportedFilesSummary(ok: Int, total: Int, names: String) = res.getString(R.string.exported_files_summary, ok, total, names)
    val noFaSubtitleExists get() = res.getString(R.string.no_fa_subtitle_exists, tgtLang)
    val srtCreateError = res.getString(R.string.srt_create_error)
    val downloadsCreateError = res.getString(R.string.downloads_create_error)
    val fileWriteError = res.getString(R.string.file_write_error)
    val noWordMeaningFound = res.getString(R.string.no_word_meaning_found)
    val noMeaningToSave = res.getString(R.string.no_meaning_to_save)
    fun wordAddedToLeitner(word: String) = res.getString(R.string.word_added_to_leitner, word)
    fun wordUpdatedInLeitner(word: String) = res.getString(R.string.word_updated_in_leitner, word)
    val leitnerBoxEmpty = res.getString(R.string.leitner_box_empty)
    fun ankiExportSaved(path: String) = res.getString(R.string.anki_export_saved, path)

    // ── Donate popup / About dialog ──
    val socialTitle = res.getString(R.string.social_title)

	val socialDescription = res.getString(R.string.social_description)
	
	val telegramLabel = "Telegram"
	val telegramTopicLabel = "Telegram Topic"
	val githubLabel = "GitHub"
    val donateInfoBannerText = res.getString(R.string.donate_info_banner_text)
    val donateLinkInvalidError = res.getString(R.string.donate_link_invalid_error)
    fun addressCopiedToast(title: String) = res.getString(R.string.address_copied_toast, title)
    val openLinkCd = res.getString(R.string.open_link_cd)
    val copyAddressCd = res.getString(R.string.copy_address_cd)
    val donateCloseBtn = res.getString(R.string.donate_close_btn)
    val donateDontShowAgainBtn = res.getString(R.string.donate_dont_show_again_btn)
    val aboutDialogTitle = res.getString(R.string.about_dialog_title)
    fun aboutVersionLabel(version: String) = res.getString(R.string.about_version_label, version)
    val bitcoinTitle = res.getString(R.string.bitcoin_title)
    val tetherTitle = res.getString(R.string.tether_title)
    val tonTitle = res.getString(R.string.ton_title)

    // ── Phase 2: study ──
    val studyPlaybackSpeed = res.getString(R.string.study_playback_speed)
    val studyLoopThisLine = res.getString(R.string.study_loop_this_line)
    val studyPronunciation = res.getString(R.string.study_pronunciation)
    val studySpeakLine = res.getString(R.string.study_speak_line)
    val studySlowBtn = res.getString(R.string.study_slow_btn)
    val studyListenMode = res.getString(R.string.study_listen_mode)
    val studyListenDesc = res.getString(R.string.study_listen_desc)
    val studyListenOn = res.getString(R.string.study_listen_on)
    val studyListenOff = res.getString(R.string.study_listen_off)
    val studyCoverage = res.getString(R.string.study_coverage)
    val studyCoverageKnownDesc = res.getString(R.string.study_coverage_known_desc)
    fun studyMarkedKnown(n: Int) = res.getString(R.string.study_marked_known, n)
    val studyTopUnknownHint = res.getString(R.string.study_top_unknown_hint)

    // ── Phase 2: design ──
    val designSectionTitle = res.getString(R.string.design_section_title)
    val designSectionDesc = res.getString(R.string.design_section_desc)
    val designLangosphere = res.getString(R.string.design_langosphere)
    val designMaterial3 = res.getString(R.string.design_material3)
    val designMaterialYou = res.getString(R.string.design_material_you)
    val designNeobrutalism = res.getString(R.string.design_neobrutalism)
    val designAnime = res.getString(R.string.design_anime)
    val designAnimeMascot = res.getString(R.string.design_anime_mascot)
    val designAnimeMascotDesc = res.getString(R.string.design_anime_mascot_desc)
    val designApplyNote = res.getString(R.string.design_apply_note)
    val designSelectedLabel = res.getString(R.string.design_selected_label)

    // ── Phase 2: tutorial ──
    val howToUseTitle = res.getString(R.string.how_to_use_title)
    val hideBtn = res.getString(R.string.hide_btn)
    val showBtn = res.getString(R.string.show_btn)
    val promptModesSubtitle = res.getString(R.string.prompt_modes_subtitle)
    val importableJsonPill = res.getString(R.string.importable_json_pill)
    val forChatUsePill = res.getString(R.string.for_chat_use_pill)
    val cuesPerRequestLabel = res.getString(R.string.cues_per_request_label)
    val chunkSizeHint = res.getString(R.string.chunk_size_hint)
    fun chunkSizeLabel(size: Int) = res.getString(R.string.chunk_size_label, size)

    // ── Phase 2: agent ──
    val promptJsonLesson = res.getString(R.string.prompt_json_lesson)
    val promptEmptyLines = res.getString(R.string.prompt_empty_lines)
    val promptSyncTimings = res.getString(R.string.prompt_sync_timings)
    val promptSync = res.getString(R.string.prompt_sync)
    val promptDescTranslate = res.getString(R.string.prompt_desc_translate)
    val promptDescJsonLesson = res.getString(R.string.prompt_desc_json_lesson)
    val promptDescChat = res.getString(R.string.prompt_desc_chat)
    val promptDescAgent = res.getString(R.string.prompt_desc_agent)
    val promptDescEmpty = res.getString(R.string.prompt_desc_empty)
    val promptDescSyncTimings = res.getString(R.string.prompt_desc_sync_timings)
    val promptDescSync = res.getString(R.string.prompt_desc_sync)
    val tapToInsertVariable = res.getString(R.string.tap_to_insert_variable)
    fun langBecomesName(name: String) = res.getString(R.string.lang_becomes_name, name)
    fun testOnLines(n: Int) = res.getString(R.string.test_on_lines, n)
    val noSubtitleSamplesNote = res.getString(R.string.no_subtitle_samples_note)
    fun clearGlossary(n: Int) = res.getString(R.string.clear_glossary, n)

    // ── Phase 2: video ──
    val revealLine = res.getString(R.string.reveal_line)
    val studyToolsCd = res.getString(R.string.study_tools_cd)
    val moreControlsCd = res.getString(R.string.more_controls_cd)
    val abRepeatCd = res.getString(R.string.ab_repeat_cd)
    val clearAbLoopCd = res.getString(R.string.clear_ab_loop_cd)
    fun knownPercent(p: Int) = res.getString(R.string.known_percent, p)
    val listenPill = res.getString(R.string.listen_pill)
    val negativeMinusReplacement = res.getString(R.string.negative_minus_replacement)

    // ── Phase 2: sheet ──
    val speakCd = res.getString(R.string.speak_cd)
    val slowlyBtn = res.getString(R.string.slowly_btn)
    val knownMarked = res.getString(R.string.known_marked)
    val iKnowThis = res.getString(R.string.i_know_this)

    // ── Phase 2: leitner ──
    val swipeHintCards = res.getString(R.string.swipe_hint_cards)
    fun cardPositionLabel(position: Int, total: Int) = res.getString(R.string.card_position_label, position, total)

    // ── Phase 2: main ──
    val appGuideMenu = res.getString(R.string.app_guide_menu)

    // ── Phase 2: prompt_modes ──
    fun promptModeName(mode: PromptMode): String = when (mode) {
        PromptMode.TRANSLATION_ONLY -> res.getString(R.string.prompt_mode_title_translation_only)
        PromptMode.TRANSLATION_LEARNING -> res.getString(R.string.prompt_mode_title_translation_learning)
        PromptMode.VOCAB_PRONUNCIATION -> res.getString(R.string.prompt_mode_title_vocab_pronunciation)
        PromptMode.GRAMMAR_COACH -> res.getString(R.string.prompt_mode_title_grammar_coach)
        PromptMode.LEITNER_CARDS -> res.getString(R.string.prompt_mode_title_leitner_cards)
        PromptMode.WORD_ANALYSIS -> res.getString(R.string.prompt_mode_title_word_analysis)
    }
    fun promptModeDesc(mode: PromptMode): String = when (mode) {
        PromptMode.TRANSLATION_ONLY -> res.getString(R.string.prompt_mode_desc_translation_only)
        PromptMode.TRANSLATION_LEARNING -> res.getString(R.string.prompt_mode_desc_translation_learning)
        PromptMode.VOCAB_PRONUNCIATION -> res.getString(R.string.prompt_mode_desc_vocab_pronunciation)
        PromptMode.GRAMMAR_COACH -> res.getString(R.string.prompt_mode_desc_grammar_coach)
        PromptMode.LEITNER_CARDS -> res.getString(R.string.prompt_mode_desc_leitner_cards)
        PromptMode.WORD_ANALYSIS -> res.getString(R.string.prompt_mode_desc_word_analysis)
    }
    val usageSteps: List<String> = res.getStringArray(R.array.prompt_usage_steps).toList()

    // ── Phase 2: guide ──
    val guideTitle = res.getString(R.string.guide_title)
    val guideSubtitle = res.getString(R.string.guide_subtitle)
    val guideQuickStartTitle = res.getString(R.string.guide_quick_start_title)
    val guideQuickStartSummary = res.getString(R.string.guide_quick_start_summary)
    val guideQuickStartPoints: List<String> = res.getStringArray(R.array.guide_quick_start_points).toList()
    val guideReaderTabTitle = res.getString(R.string.guide_reader_tab_title)
    val guideReaderTabSummary = res.getString(R.string.guide_reader_tab_summary)
    val guideReaderTabPoints: List<String> = res.getStringArray(R.array.guide_reader_tab_points).toList()
    val guideVideoTabTitle = res.getString(R.string.guide_video_tab_title)
    val guideVideoTabSummary = res.getString(R.string.guide_video_tab_summary)
    val guideVideoTabPoints: List<String> = res.getStringArray(R.array.guide_video_tab_points).toList()
    val guideJsonPackageTitle = res.getString(R.string.guide_json_package_title)
    val guideJsonPackageSummary = res.getString(R.string.guide_json_package_summary)
    val guideJsonPackagePoints: List<String> = res.getStringArray(R.array.guide_json_package_points).toList()
    val guidePopupsTitle = res.getString(R.string.guide_popups_title)
    val guidePopupsSummary = res.getString(R.string.guide_popups_summary)
    val guidePopupsPoints: List<String> = res.getStringArray(R.array.guide_popups_points).toList()
    val guideAiTabTitle = res.getString(R.string.guide_ai_tab_title)
    val guideAiTabSummary = res.getString(R.string.guide_ai_tab_summary)
    val guideAiTabPoints: List<String> = res.getStringArray(R.array.guide_ai_tab_points).toList()
    val guideToolsTabTitle = res.getString(R.string.guide_tools_tab_title)
    val guideToolsTabSummary = res.getString(R.string.guide_tools_tab_summary)
    val guideToolsTabPoints: List<String> = res.getStringArray(R.array.guide_tools_tab_points).toList()
    val guideSettingsMenuTitle = res.getString(R.string.guide_settings_menu_title)
    val guideSettingsMenuSummary = res.getString(R.string.guide_settings_menu_summary)
    val guideSettingsMenuPoints: List<String> = res.getStringArray(R.array.guide_settings_menu_points).toList()
    val guideTipsTitle = res.getString(R.string.guide_tips_title)
    val guideTipsSummary = res.getString(R.string.guide_tips_summary)
    val guideTipsPoints: List<String> = res.getStringArray(R.array.guide_tips_points).toList()

    // ── Phase 2: ai_service ──
    val aiNothingToTranslate = res.getString(R.string.ai_nothing_to_translate)
    val aiNoTestLines = res.getString(R.string.ai_no_test_lines)
    fun aiServerError(code: Int, detail: String) = res.getString(R.string.ai_server_error, code, detail)
    val aiEmptyResponse = res.getString(R.string.ai_empty_response)
    val aiNoteSaved = res.getString(R.string.ai_note_saved)
    val sqliteNoWordTable = res.getString(R.string.sqlite_no_word_table)

    // ── Phase 2: chat ──
    val chatNewTitle = res.getString(R.string.chat_new_title)
    val chatUntitled = res.getString(R.string.chat_untitled)

    // ── Phase 2: crash ──
    val crashTitle = res.getString(R.string.crash_title)
    val crashDesc = res.getString(R.string.crash_desc)
    val crashCopy = res.getString(R.string.crash_copy)
    val crashShare = res.getString(R.string.crash_share)
    val crashContinue = res.getString(R.string.crash_continue)

    // ── Phase 2 (hand-written): palettes, AI/logic errors, memory summary ──
    fun paletteName(preset: ThemePalette) = res.getString(preset.nameRes)

    /** Unprefixed detail for a logic-layer failure: typed AI/DB errors map to strings. */
    fun apiErrorDetail(e: Throwable?): String? = when (e) {
        is AiService.NothingToTranslateException -> aiNothingToTranslate
        is AiService.NoSampleLinesException -> aiNoTestLines
        is AiService.NonRetryableApiException -> aiServerError(e.code, e.detail)
        is AiService.ApiServerException -> aiServerError(e.code, e.detail)
        is AiService.EmptyServerResponseException -> aiEmptyResponse
        is SqliteDictParser.NoWordTableException -> sqliteNoWordTable
        else -> e?.message
    }

    /** Error line for a logic-layer failure; unknown throwables surface verbatim. */
    fun apiErrorMessage(e: Throwable?): String = errorWithMessage(apiErrorDetail(e))

    fun memorySummary(c: AiMemoryManager.MemoryCounts) = res.getString(
        R.string.memory_summary, c.prompts, c.corrections, c.rules,
        c.glossary, c.dictNotes, c.learnedTranslations
    )
}
