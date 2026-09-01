package com.example.ui.theme

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

class AppStrings(lang: AppLanguage) {
    val isEn = lang == AppLanguage.EN
    private fun t(fa: String, en: String) = if (isEn) en else fa

    // ── App-wide / MainScreen ──
    val appTitle = t("لنگوسفر", "Langosphere")
    val tabReader = t("کتابخوان / متن", "Reader / Text")
    val tabVideo = t("پخش‌کننده ویدیو", "Video Player")
    val tabAgent = t("دستیار", "Assistant")
    val tabLeitner = t("جعبه لایتنر", "Leitner Box")
    val changeThemeCd = t("تغییر تم", "Change theme")
    val themeLightMenu = t("☀️ روشن", "☀️ Light")
    val themeDarkMenu = t("🌙 تاریک", "🌙 Dark")
    val themeSystemMenu = t("📱 سیستم", "📱 System")
    val appLanguageMenu = t("🌐 زبان برنامه", "🌐 App language")
    val appLanguageDialogTitle = t("زبان برنامه", "App language")
    val languageFaLabel = "فارسی"
    val languageEnLabel = "English"
    val unlimitedLabel = t("نامحدود", "Unlimited")
    fun maxWordsMenuLabel(count: Int) = t(
        "📝 تعداد کلمات: ${if (count == 0) "نامحدود" else "$count"}",
        "📝 Word count: ${if (count == 0) "Unlimited" else "$count"}"
    )
    val fileManagerMenu = t("📁 مدیریت فایل‌ها", "📁 File manager")
    val aboutMenu = t("ℹ️ درباره", "ℹ️ About")
    val videoAudioLabel = t("فیلم / آهنگ", "Video / Audio")
    val subEnLabel = t("زیرنویس انگلیسی", "English subtitle")
    val subFaLabel = t("زیرنویس فارسی", "Persian subtitle")

    // Add-subtitle chooser popup (file picker vs. clipboard paste)
    fun addSubtitleTitle(label: String) = t("افزودن $label", "Add $label")
    val chooseSubtitleSourceTitle = t("روش افزودن زیرنویس را انتخاب کنید", "Choose how to add the subtitle")
    val selectSubtitleFileOption = t("📂 انتخاب فایل زیرنویس", "📂 Select subtitle file")
    val selectSubtitleFileDesc = t("از حافظه دستگاه (SRT، VTT، LRC و...)", "From device storage (SRT, VTT, LRC...)")
    val pasteFromClipboardOption = t("📋 جای‌گذاری از کلیپ‌بورد", "📋 Paste from clipboard")
    val pasteFromClipboardDesc = t("اگر متن یا فایل زیرنویس را کپی کرده‌اید", "If you copied subtitle text or a subtitle file")
    val clipboardSubtitleDefaultNameEn = t("زیرنویس انگلیسی (از کلیپ‌بورد).srt", "English subtitle (clipboard).srt")
    val clipboardSubtitleDefaultNameFa = t("زیرنویس فارسی (از کلیپ‌بورد).srt", "Persian subtitle (clipboard).srt")
    val clipboardEmptyError = t("کلیپ‌بورد خالی است؛ ابتدا فایل یا متن زیرنویس را کپی کنید.", "Clipboard is empty; copy a subtitle file or text first.")
    val clipboardNoSubtitleError = t("محتوای کلیپ‌بورد زیرنویس معتبر (SRT/VTT/LRC) نیست.", "The clipboard content is not a valid subtitle (SRT/VTT/LRC).")
    fun subtitleLoadedFromClipboard(name: String) = t("✅ زیرنویس از کلیپ‌بورد بارگذاری شد: $name", "✅ Subtitle loaded from clipboard: $name")

    // Max words dialog
    val maxWordsDialogTitle = t("تعداد کلمات وارد شده", "Imported word count")
    val maxWordsUnlimitedAll = t("نامحدود (همه کلمات)", "Unlimited (all words)")
    fun maxWordsCountText(n: Int) = t("$n کلمه", "$n words")
    val rangeStart = t("۰", "0")
    val rangeMid = t("۵۰۰۰", "5000")
    val rangeEnd = t("۱۰۰۰۰", "10000")
    val maxWordsHint = t(
        "۰ = نامحدود. هنگام وارد کردن فایل دیکشنری اعمال می‌شود.",
        "0 = unlimited. Applied when importing a dictionary file."
    )
    val save = t("ذخیره", "Save")
    val cancel = t("لغو", "Cancel")
    val close = t("بستن", "Close")

    // File manager dialog
    val fileManagerTitle = t("📁 مدیریت فایل‌ها", "📁 File manager")
    val fileManagerSavedLabel = t("فایل‌های ذخیره شده در فضای برنامه:", "Files saved in app storage:")
    val exportAll = t("خروجی همه", "Export all")
    val noFilesSaved = t("هیچ فایلی ذخیره نشده.", "No files saved.")
    val exportCd = t("خروجی", "Export")
    val deleteCd = t("حذف", "Delete")

    // ── ReaderScreen ──
    val importingDbTitle = t("در حال ایجاد دیتابیس دیکشنری...", "Building dictionary database...")
    fun importingWordsCount(n: Int) = t("$n واژه ثبت شد\nلطفا صبور باشید...", "$n words registered\nPlease wait...")
    val errorTitle = t("خطا", "Error")
    val ok = t("باشه", "OK")
    val textColorCd = t("رنگ متن", "Text color")
    val exitFullscreenCd = t("خروج از تمام صفحه", "Exit fullscreen")
    val fullscreenCd = t("تمام صفحه", "Fullscreen")
    val dictLoadedActive = t("دیکشنری آفلاین فعال", "Offline dictionary active")
    val dictEmpty = t("پایگاه‌داده خالی است", "Database is empty")
    val dictHint = t(
        "فایل‌های MDX، MDD، دیتابیس SQLite (.db) یا متنی را اضافه کرده و همزمان استفاده کنید.",
        "Add MDX, MDD, SQLite (.db), or plain text files and use them together."
    )
    val addDictionary = t("افزودن دیکشنری", "Add dictionary")
    val clearAll = t("پاک کردن همه", "Clear all")
    fun importedFilesCount(n: Int) = t("فایل‌های وارد شده ($n)", "Imported files ($n)")
    val selectTextPdf = t("انتخاب متن / PDF", "Select text / PDF")
    val prevPage = t("◀ قبلی", "◀ Previous")
    val nextPage = t("بعدی ▶", "Next ▶")
    fun pageOfCount(n: Int) = t("از $n صفحه", "of $n pages")
    val emptyReaderHint = t("برای شروع مطالعه، یک سند را بارگذاری کنید.", "Load a document to start reading.")
    val colorDialogSubtitle = t(
        "یکی از رنگ‌های زیر را انتخاب کنید یا رنگ دلخواه خود را بسازید:",
        "Choose one of the colors below, or mix your own:"
    )
    val customColorLabel = t("رنگ دلخواه", "Custom color")
    val redLabel = t("قرمز", "Red")
    val greenLabel = t("سبز", "Green")
    val blueLabel = t("آبی", "Blue")
    val applyCustomColor = t("اعمال رنگ دلخواه", "Apply custom color")
    val presetDefault = t("پیش‌فرض", "Default")
    val presetBlack = t("سیاه", "Black")
    val presetWhite = t("سفید", "White")
    val presetGreen = t("سبز", "Green")
    val presetRed = t("قرمز", "Red")
    val presetCyan = t("فیروزه‌ای", "Cyan")
    val presetIndigo = t("بنفش", "Indigo")
    val presetAmber = t("کهربایی", "Amber")

    // ── LeitnerScreen ──
    val leitnerTitle = t("📦 جعبه لایتنر", "📦 Leitner Box")
    val exportAnki = t("خروجی Anki (txt)", "Export to Anki (txt)")
    fun leitnerSummary(all: Int, due: Int) = t(
        "در مجموع $all کارت، $due کارت آمادهٔ مرور امروز.",
        "$all cards total, $due due for review today."
    )
    fun reviewTodayChip(n: Int) = t("مرور امروز ($n)", "Review today ($n)")
    fun allCardsChip(n: Int) = t("همه کارت‌ها ($n)", "All cards ($n)")
    val leitnerEmptyAddHint = t(
        "هنوز کلمه‌ای به جعبه لایتنر اضافه نکرده‌اید. روی هر کلمه در دیکشنری ضربه بزنید و «افزودن به جعبه لایتنر» را انتخاب کنید.",
        "You haven't added any words to the Leitner box yet. Tap a word in the dictionary and choose \"Add to Leitner box\"."
    )
    val leitnerEmptyDoneToday = t("کارتی برای مرور امروز باقی نمانده. 🎉", "No cards left to review today. 🎉")
    val leitnerEmptyNoCards = t("هنوز کلمه‌ای به جعبه لایتنر اضافه نکرده‌اید.", "You haven't added any words to the Leitner box yet.")
    fun boxOfFive(level: Int) = t("جعبه $level از 5", "Box $level of 5")
    fun boxLabel(level: Int) = t("جعبه $level", "Box $level")
    val showMeaning = t("نمایش معنی", "Show meaning")
    val didntKnow = t("بلد نبودم", "Didn't know")
    val knewIt = t("بلد بودم", "Knew it")

    // ── AgentScreen ──
    val agentNewChatCd = t("چت جدید", "New chat")
    val agentHistoryCd = t("تاریخچه چت", "Chat history")
    val agentMemoryCd = t("حافظه AI", "AI memory")
    val agentSettingsCd = t("تنظیمات", "Settings")
    val agentNoFileLoaded = t("فایلی بارگذاری نشده", "No file loaded")
    val agentMemoryActive = t("🧠 حافظه فعال", "🧠 Memory active")
    val apiKeyLabel = "API Key"
    val baseUrlLabel = "Base URL"
    val modelLabel = "Model"
    val targetLangLabel = t("زبان مقصد", "Target language")
    val targetLangPlaceholder = t("فارسی", "Persian")
    val bubbleColorTitle = t("رنگ حباب پیام‌ها", "Message bubble color")
    val bubbleColorHint = t(
        "رنگ پیام‌هایی که شما ارسال می‌کنید و پیام‌هایی که از Agent دریافت می‌کنید را جدا از هم انتخاب کنید",
        "Choose separate colors for the messages you send and the ones you receive from the Agent"
    )
    val sentMessagesLabel = t("پیام‌های شما (ارسالی)", "Your messages (sent)")
    val receivedMessagesLabel = t("پیام‌های دستیار (دریافتی)", "Assistant messages (received)")
    val defaultCd = t("پیش‌فرض", "Default")
    val learnFromSubtitlesBtn = t("🧠 یادگیری از زیرنویس", "🧠 Learn from subtitles")
    val learnFromDictionaryBtn = t("📖 یادگیری از دیکشنری", "📖 Learn from dictionary")
    val stopCd = t("توقف", "Stop")
    val agentThinking = t("در حال فکر کردن...", "Thinking...")
    val askAgentPlaceholder = t("از Agent بپرسید...", "Ask the Agent...")
    val sendCd = t("ارسال", "Send")
    val chatHistoryTitle = t("تاریخچه چت‌ها", "Chat history")
    val noChatsSaved = t("هیچ چتی ذخیره نشده.", "No chats saved.")
    val openCd = t("باز کردن", "Open")
    val memoryTabPrompts = t("پرامپت‌ها", "Prompts")
    val memoryTabCorrections = t("اصلاحات", "Corrections")
    val memoryTabSkills = t("مهارت‌ها", "Skills")
    val memoryTabExportImport = t("خروجی/ورودی", "Export/Import")
    val promptTranslate = t("ترجمه", "Translate")
    val promptChat = t("چت", "Chat")
    val promptAgent = "Agent"
    val promptTranslateEmpty = t("ترجمه خالی‌ها", "Translate blanks")
    val customBadge = t("سفارشی ✓", "Custom ✓")
    val editBtn = t("ویرایش", "Edit")
    val resetBtn = t("بازنشانی", "Reset")
    fun editingPromptTitle(name: String) = t("ویرایش پرامپت: $name", "Editing prompt: $name")
    val promptTextPlaceholder = t("متن پرامپت...", "Prompt text...")
    val noCorrectionsSaved = t("هیچ اصلاحی ذخیره نشده. وقتی خروجی AI رو تصحیح کنی، خودکار ذخیره می‌شه.", "No corrections saved yet. They're saved automatically whenever you correct an AI output.")
    fun correctionsSavedCount(n: Int) = t("$n اصلاح ذخیره شده:", "$n corrections saved:")
    fun sourceLabel(text: String) = t("منبع: $text", "Source: $text")
    val addManualCorrection = t("+ افزودن اصلاح دستی", "+ Add manual correction")
    val addCorrectionTitle = t("افزودن اصلاح", "Add correction")
    val sourceTextLabel = t("متن منبع", "Source text")
    val wrongTranslationLabel = t("ترجمه اشتباه", "Wrong translation")
    val correctTranslationLabel = t("ترجمه درست", "Correct translation")
    val noSkillsSaved = t("هیچ مهارت/یادداشتی ذخیره نشده.\nبرای افزودن، در چت بنویس:\n«یادداشت: ...» یا «قانون: ...»", "No skills/notes saved yet.\nTo add one, write in chat:\n\"note: ...\" or \"rule: ...\"")
    fun skillsSavedCount(n: Int) = t("$n مهارت/یادداشت:", "$n skills/notes:")
    val addSkillNote = t("+ افزودن مهارت/یادداشت", "+ Add skill/note")
    val addSkillTitle = t("افزودن مهارت/یادداشت", "Add skill/note")
    val textLabel = t("متن", "Text")
    val categoryUserNote = t("یادداشت", "Note")
    val categoryTranslationRule = t("قانون ترجمه", "Translation rule")
    val categorySkill = t("مهارت", "Skill")
    val categoryDictionaryTip = t("نکته دیکشنری", "Dictionary tip")
    val exportImportTitle = t("خروجی/ورودی حافظه AI", "AI memory export/import")
    val exportImportDesc = t("تمام داده‌های حافظه (پرامپت‌ها، اصلاحات، مهارت‌ها) به‌صورت یه فایل JSON.", "All memory data (prompts, corrections, skills) as a single JSON file.")
    val exportToDownloadsBtn = t("📥 خروجی به Downloads", "📥 Export to Downloads")
    val importFromFileLabel = t("ورودی از فایل:", "Import from file:")
    val selectFileImportBtn = t("📤 انتخاب فایل و وارد کردن", "📤 Select file and import")
    val clearAllMemoryBtn = t("🗑 پاک کردن کل حافظه", "🗑 Clear all memory")

    // ── TranslationScreen ──
    val subEnChip = t("زیرنویس انگلیسی", "English subtitle")
    val subFaChip = t("زیرنویس فارسی", "Persian subtitle")
    fun linesPerRequestLabel(isDefault: Boolean, n: Int) = t(
        if (isDefault) "خط در هر درخواست: پیش‌فرض (همه)" else "خط در هر درخواست: $n",
        if (isDefault) "Lines per request: default (all)" else "Lines per request: $n"
    )
    val defaultChip = t("پیش‌فرض", "Default")
    val translateBtn = t("ترجمه", "Translate")
    val allBtn = t("همه", "All")
    val apiKeySetError = t("API Key را تنظیم کنید", "Please set the API Key")
    val noLinesLeftError = t("خطی برای ترجمه باقی نمانده", "No lines left to translate")
    fun translatingLinesProgress(start: Int, end: Int, total: Int) = t(
        "ترجمه خطوط $start تا $end از $total...",
        "Translating lines $start to $end of $total..."
    )
    fun translatingAllProgress(total: Int) = t("ترجمه همه $total خط...", "Translating all $total lines...")
    fun errorWithMessage(msg: String?) = t("خطا: $msg", "Error: $msg")
    val prevLineCd = t("خط قبلی", "Previous line")
    val nextLineCd = t("خط بعدی", "Next line")
    fun batchInfo(current: Int, total: Int, start: Int, end: Int) = t(
        "دسته $current از $total (خطوط $start تا $end)",
        "Batch $current of $total (lines $start to $end)"
    )
    val nextBatchBtn = t("۱۰۰ خط بعدی", "Next 100 lines")
    val allDoneLabel = t("✅ تمام", "✅ Done")
    val noSubtitleLoadedTitle = t("زیرنویسی بارگذاری نشده", "No subtitle loaded")
    val noSubtitleLoadedHint = t("از تب پخش‌کننده ویدیو زیرنویس اضافه کنید", "Add a subtitle from the Video Player tab")
    fun moreLinesLabel(n: Int) = t("... و $n خط دیگر", "... and $n more lines")
    val askAboutSubtitlePlaceholder = t("پرسش درباره زیرنویس...", "Ask about the subtitle...")
    val translationSettingsTitle = t("تنظیمات ترجمه هوشمند", "Smart translation settings")
    val langFa = t("فارسی", "Persian")
    val langAr = t("عربی", "Arabic")
    val langTr = t("ترکی", "Turkish")
    val langFr = t("فرانسه", "French")
    val langDe = t("آلمانی", "German")
    val langEs = t("اسپانیایی", "Spanish")
    val langJa = t("ژاپنی", "Japanese")
    val langKo = t("کره‌ای", "Korean")
    val translationProcessing = t("در حال پردازش...", "Processing...")

    // ── VideoPlayerScreen ──
    val resetPositionsTitle = t("بازنشانی موقعیت دکمه‌ها", "Reset button positions")
    val resumePlayBtn = t("ادامه پلی", "Resume playback")
    val autoStopPrevSubtitle = t("توقف خودکار — زیرنویس قبلی", "Auto-stop — previous subtitle")
    val autoStopCurrentSubtitle = t("توقف خودکار — زیرنویس فعلی", "Auto-stop — current subtitle")
    val resumeCd = t("ادامه", "Resume")
    val playerSettingsCd = t("تنظیمات پخش‌کننده", "Player settings")
    // Focus mode (video tab): hides the top bar/tabs, the import section and
    // the time-sync cards so only the video + subtitle list remain.
    val focusModeCd = t("حالت تمرکز", "Focus mode")
    // Smart-pause gear panel (persisted options)
    val smartPauseSettingsCd = t("تنظیمات توقف هوشمند", "Smart pause settings")
    val smartPausePanelTitle = t("⚙️ تنظیمات توقف هوشمند", "⚙️ Smart pause settings")
    val pauseDimTitle = t("لایه تیره هنگام توقف", "Dim overlay when paused")
    val pauseDimDesc = t(
        "هنگام توقف، لایه سیاه روی فیلم نمایش داده شود",
        "Show the dark layer over the video while it is paused"
    )
    val pauseHideUiTitle = t("مخفی کردن زیرنویس و دکمه‌ها", "Hide subtitles & buttons")
    val pauseHideUiDesc = t(
        "هنگام توقف، زیرنویس و دکمه‌های توقف هوشمند مخفی شوند تا فریم فیلم واضح دیده شود (چرخ‌دنده باقی می‌ماند)",
        "While paused, hide the subtitle text and smart-pause buttons so the video frame can be inspected clearly (the gear stays)"
    )
    val pauseRequireContinueTitle = t("ادامه فقط با دکمه «ادامه پلی»", "Resume only via the Continue button")
    val pauseRequireContinueDesc = t(
        "لمس هر جای صفحه، پخش را از سر نگیرد؛ برای ادامه حتماً باید دکمه «ادامه پلی» زده شود",
        "Tapping anywhere does not resume playback; the Continue button must be pressed to resume"
    )
    // Audio track selection (dual-language videos — works in normal and
    // smart-pause modes)
    val audioTrackTitle = t("🎧 انتخاب صوت فیلم", "🎧 Audio track")
    val audioTrackDesc = t(
        "برای فیلم‌های چندزبانه (مثلاً دوبله + زبان اصلی) صوت دلخواه را انتخاب کنید",
        "For multi-language videos (e.g. dubbed + original) choose the audio track you want"
    )
    val audioTrackUnavailable = t("این ویدیو هیچ آهنگ صوتی قابل انتخابی ندارد.", "This video has no selectable audio tracks.")
    fun audioTrackFallbackName(n: Int) = t("آهنگ $n", "Track $n")
    val audioPlayingHint = t("🎵 در حال پخش است... ضربه روی کلمات برای ترجمه", "🎵 Now playing... tap words to translate")
    val playerSettingsTitle = t("تنظیمات پخش‌کننده و زیرنویس", "Player and subtitle settings")
    val design1Title = t("طراحی ۱ (ظاهر مدرن و جلوه نیمه‌شفاف)", "Design 1 (modern look with glass effect)")
    val design1Desc = t("فعال کردن پوسته شیک، آمیخته با سایه‌های نرم و جلوه‌های زیبای ریتمیک (Blur)", "Enable the sleek skin with soft shadows and a smooth blur effect")
    val appAccentColorTitle = t("رنگ اصلی برنامه", "App accent color")
    val appAccentColorDesc = t("رنگ دکمه‌ها، آیکون‌ها و جلوه‌های اصلی برنامه را به سلیقهٔ خودتان تغییر دهید", "Customize the color of buttons, icons, and the app's main accents")
    val showSubtitlesTitle = t("نمایش زیرنویس‌ها", "Show subtitles")
    val showSubtitlesDesc = t("نمایش یا عدم نمایش متن‌های زیرنویس روی ویدیو", "Show or hide subtitle text over the video")
    val smartPauseTitle = t("توقف هوشمند کلمات", "Smart word pause")
    val smartPauseDesc = t("توقف فیلم با زدن روی کلمه بدون سیاه شدن آزاردهنده صفحه", "Pause the video by tapping a word, without an annoying blackout")
    val doubleTapSkipTitle = t("زمان پرش دوبار لمس ویدیو", "Double-tap skip duration")
    fun secondsLabel(n: Int) = t("$n ثانیه", "$n seconds")
    val subtitleFontSizeTitle = t("اندازه فونت زیرنویس", "Subtitle font size")
    val subtitlePositionTitle = t("موقعیّت قرارگیری (ارتفاع از پایین)", "Position (height from bottom)")
    val subtitleColorTitle = t("رنگ زیرنویس‌ها", "Subtitle colors")
    val subEnParenLabel = t("زیرنویس انگلیسی (EN)", "English subtitle (EN)")
    val subFaParenLabel = t("زیرنویس فارسی (FA)", "Persian subtitle (FA)")
    val subtitleFontTitle = t("فونت زیرنویس‌ها", "Subtitle fonts")
    val subtitleFontDesc = t("در صورتی که گزینه‌های پیش‌فرض برای فارسی مناسب نبود، فونت دلخواه (.ttf/.otf) وارد کنید", "If the built-in options don't suit Persian well, import a custom font (.ttf/.otf)")
    val fontEnLabel = t("فونت انگلیسی (EN)", "English font (EN)")
    val fontFaLabel = t("فونت فارسی (FA)", "Persian font (FA)")
    val fontDefault = t("پیش‌فرض", "Default")
    val fontCustomLabel = t("دلخواه", "Custom")
    val importCustomFontBtn = t("📂 وارد کردن فونت دلخواه", "📂 Import custom font")
    val removeCustomFontBtn = t("حذف فونت دلخواه", "Remove custom font")
    val syncTitle = t("⏱️ هماهنگ‌سازی زمان زیرنویس‌ها (جلو/عقب بردن)", "⏱️ Subtitle time sync (shift forward/back)")
    fun syncCurrentOffset(label: String, offset: String) = t("$label — تغییر فعلی: $offset ثانیه", "$label — current shift: $offset sec")
    fun shiftValueLabel(offset: String) = t("تغییر: $offset ثانیه", "Shift: $offset sec")
    val langCodeEn = t("انگلیسی (EN)", "English (EN)")
    val langCodeFa = t("فارسی (FA)", "Persian (FA)")
    // JSON subtitle time sync (same shift feature for the JSON learning file)
    val jsonSyncRowTitle = t("⏱️ هماهنگ‌سازی زمان JSON (جلو/عقب)", "⏱️ JSON time sync (shift forward/back)")
    val jsonSyncNoTimings = t(
        "این فایل JSON زمان‌بندی ندارد؛ همگام‌سازی فقط بر اساس متن انجام می‌شود و جلو/عقب بردن زمان ممکن نیست.",
        "This JSON has no timing data; sync is text-based only, so time shifting is unavailable."
    )
    val jsonResetBtn = t("بازنشانی تغییر زمان", "Reset time shift")
    val exactTimeLabel = t("زمان دقیق (ثانیه)", "Exact time (seconds)")
    val applyBtn = t("اعمال", "Apply")
    val syncHint = t("راهنما: عدد مثبت یعنی زیرنویس جلوتر نمایش داده شود (تأخیر آن جبران شود) و عدد منفی یعنی عقب‌تر.", "Tip: a positive number shows the subtitle earlier (compensating for delay); a negative number shows it later.")
    val saveSrtBtn = t("💾 ذخیره فایل SRT", "💾 Save SRT file")
    val noFaSubtitleToSave = t("زیرنویس فارسی برای ذخیره وجود ندارد", "No Persian subtitle to save")
    val confirmReturnBtn = t("تایید و بازگشت", "Confirm and return")
    val allSubtitlesListTitle = t("لیست تمام زیرنویس‌ها (برای پرش کلیک کنید)", "All subtitles (tap to jump)")
    val loadSubtitleHint = t("پس از بارگذاری فایل زیرنویس، لیست خطوط اینجا نمایش داده می‌شود.", "Once a subtitle file is loaded, the line list will appear here.")
    val syncSettingsRowTitle = t("تنظیم و هماهنگ‌سازی زمان زیرنویس‌ها (Sync)", "Adjust and sync subtitle timing")
    val collapseSync = t("بستن تنظیمات ▲", "Collapse settings ▲")
    val expandSync = t("کلیک برای تنظیم زمان ▼", "Tap to adjust timing ▼")
    val playingLabel = t("در حال پخش", "Playing")
    val playFromStartBtn = t("پخش از ابتدا", "Play from start")
    val playAutoStopBtn = t("پخش و توقف خودکار", "Play with auto-stop")
    val translatingLabel = t("در حال ترجمه...", "Translating...")
    val aiTranslateBtn = t("🤖 ترجمه AI", "🤖 AI translate")

    // ── DictionaryBottomSheet ──
    val allFilterChip = t("همه", "All")
    val addedToLeitnerLabel = t("در جعبه لایتنر ذخیره شد ✓", "Saved to Leitner box ✓")
    val addToLeitnerBtn = t("افزودن به جعبه لایتنر", "Add to Leitner box")
    val searchWordPlaceholder = t("جستجوی کلمه...", "Search a word...")
    val wordLabel = t("کلمه", "Word")
    val searchCd = t("جستجو", "Search")
    val subtitleAndTranslationLabel = t("متن زیرنویس و ترجمه", "Subtitle text and translation")
    fun matchedTranslationLabel(text: String) = t("ترجمه منطبق: $text", "Matched translation: $text")
    val autoDetectWarning = t("⚠️ تشخیص خودکار — ممکن است اشتباه باشد", "⚠️ Auto-detected — may be inaccurate")
    val noResultsFound = t("واژه‌ای یافت نشد. پسوندها (s, ed, ing) را حذف و مجددا جستجو کنید.", "No word found. Try removing suffixes (s, ed, ing) and search again.")

    // ── Theme settings section ──
    val themeSectionMenu = t("🎨 تم", "🎨 Theme")
    val themeSectionTitle = t("🎨 تم", "🎨 Theme")
    val themeModeTitle = t("حالت تم اصلی", "Main theme mode")
    val themeModeDesc = t(
        "روشن، تاریک یا پیروی از سیستم. رنگ‌ها بر اساس Material You (رنگ‌های پویای والپیپر در اندروید ۱۲ و بالاتر) تنظیم می‌شوند.",
        "Light, dark, or follow the system. Colors follow Material You (dynamic wallpaper colors on Android 12+)."
    )

    // ── Video import section (collapsible) ──
    val videoImportSectionTitle = t("🎬 فیلم و زیرنویس", "🎬 Video & subtitles")
    val collapseImportSection = t("جمع کردن ▲", "Collapse ▲")
    val expandImportSection = t("باز کردن ▼", "Expand ▼")
    val importSectionScrollHint = t(
        "💡 با اسکرول لیست زیرنویس به سمت بالا، این بخش خودکار جمع می‌شود و با برگشت به ابتدای لیست، دوباره باز می‌شود.",
        "💡 Scroll the subtitle list up to auto-fold this section; scroll back to the top of the list to reopen it."
    )

    // ── Tutorial & AI Learning section ──
    val tutorialMenu = t("🧠 آموزش و یادگیری AI", "🧠 Tutorial & AI Learning")
    val tutorialTitle = t("🧠 آموزش و یادگیری AI", "🧠 Tutorial & AI Learning")
    val tutorialLearningLevelTitle = t("سطح یادگیری شما", "Your learning level")
    val tutorialLearningLevelDesc = t(
        "توضیحات آموزشی، مثال‌ها و پرامپت‌ها بر اساس این سطح تنظیم می‌شوند.",
        "Learning explanations, examples, and prompts are tuned to this level."
    )
    val dictionaryJsonToggleTitle = t("استفاده از دیکشنری هنگام وجود داده یادگیری JSON", "Use Dictionary When JSON Learning Data Exists")
    val dictionaryJsonToggleDescOn = t(
        "فعال: دیکشنری عادی حتی وقتی فایل JSON وجود دارد باز می‌شود.",
        "Enabled: the normal dictionary opens even when a JSON file exists."
    )
    val dictionaryJsonToggleDescOff = t(
        "غیرفعال: با کلیک روی کلمات دیکشنری باز نمی‌شود؛ به‌جای آن توضیح یادگیری JSON نمایش داده می‌شود.",
        "Disabled: the dictionary does not open on word tap; the JSON learning explanation is shown instead."
    )
    val promptGeneratorTitle = t("سازنده پرامپت یادگیری JSON", "JSON Learning Prompt Generator")
    val promptGeneratorDesc = t(
        "پرامپت آماده برای مدل‌های AI؛ خروجی، فایل JSON یادگیری سازگار با این برنامه است و مستقیم قابل وارد کردن می‌باشد.",
        "Ready-to-copy AI prompts. The output is a JSON learning file compatible with this app and directly importable."
    )
    val promptLevelLabel = t("سطح پرامپت:", "Prompt level:")
    val promptModeTitle = t("حالت پرامپت", "Prompt mode")
    val modeTranslationOnlyTitle = t("فقط ترجمه", "Translation Only")
    val modeTranslationOnlyDesc = t("فقط جمله اصلی و ترجمه", "Only the original sentence and its translation")
    val modeTranslationLearningTitle = t("ترجمه + یادگیری", "Translation + Learning")
    val modeTranslationLearningDesc = t(
        "ترجمه + توضیح گرامر، واژگان، ساختار جمله و نکات آموزشی متناسب با سطح",
        "Translation + grammar, vocabulary, sentence-structure explanations, and level-appropriate teaching notes"
    )
    val modeWordAnalysisTitle = t("تحلیل کلمه", "Word Analysis")
    val modeWordAnalysisDesc = t(
        "ترجمه، نوع کلمه، معنی در جمله، مثال‌ها و توضیح مناسب سطح — برای وقتی روی یک کلمه کلیک می‌کنید",
        "Translation, word role, meaning in the sentence, examples, and level-appropriate explanation — for when you tap a word"
    )
    val promptPreviewTitle = t("متن پرامپت (آماده کپی)", "Prompt text (ready to copy)")
    val copyPromptBtn = t("📋 کپی پرامپت", "📋 Copy prompt")
    val promptCopiedToast = t("✅ پرامپت کپی شد", "✅ Prompt copied")
    fun levelName(code: String): String = when (code.trim().uppercase()) {
        "A1" -> t("A1 — مبتدی", "A1 — Beginner")
        "A2" -> t("A2 — مقدماتی", "A2 — Elementary")
        "B1" -> t("B1 — متوسط", "B1 — Intermediate")
        "B2" -> t("B2 — فراتر از متوسط", "B2 — Upper Intermediate")
        "C1" -> t("C1 — پیشرفته", "C1 — Advanced")
        "C2" -> t("C2 — همانند زبان مادری", "C2 — Native-like")
        else -> code
    }
    fun partOfSpeechName(pos: String?): String = when (pos?.trim()?.lowercase()) {
        "noun" -> t("اسم", "noun")
        "verb" -> t("فعل", "verb")
        "adjective" -> t("صفت", "adjective")
        "adverb" -> t("قید", "adverb")
        "pronoun" -> t("ضمیر", "pronoun")
        "preposition" -> t("حرف اضافه", "preposition")
        "conjunction" -> t("حرف ربط", "conjunction")
        "interjection" -> t("شبه‌جمله", "interjection")
        "phrase" -> t("عبارت", "phrase")
        "idiom" -> t("اصطلاح", "idiom")
        "phrasal verb" -> t("فعل عبارتی", "phrasal verb")
        else -> pos ?: ""
    }

    // ── JSON subtitle import ──
    val subJsonLabel = t("زیرنویس JSON", "JSON subtitle")
    fun addJsonSubtitleTitle(label: String) = t("افزودن $label", "Add $label")
    val selectJsonFileOption = t("📂 انتخاب فایل JSON", "📂 Select JSON file")
    val selectJsonFileDesc = t("فایل JSON یادگیری زیرنویس (خروجی AI) از حافظه دستگاه", "A subtitle-learning JSON file (AI output) from device storage")
    val pasteJsonOption = t("📋 جای‌گذاری محتوای JSON", "📋 Paste JSON content")
    val pasteJsonDesc = t("متن JSON را مستقیم وارد کنید؛ برنامه خودکار آن را تشخیص می‌دهد", "Enter JSON text directly; the app auto-detects it")
    val jsonPasteDialogTitle = t("وارد کردن JSON زیرنویس", "Import subtitle JSON")
    val jsonPastePlaceholder = t("محتوای JSON را اینجا جای‌گذاری کنید...", "Paste the JSON content here...")
    val jsonImportBtn = t("وارد کردن JSON", "Import JSON")
    val jsonLoadSampleBtn = t("نمونه JSON", "Sample JSON")
    val jsonDetectedLabel = t("✅ فرمت JSON یادگیری زیرنویس تشخیص داده شد", "✅ Subtitle-learning JSON format detected")
    val jsonNotSubtitleJson = t(
        "محتوا JSON یادگیری زیرنویس معتبر نیست؛ به کلیدهای subtitles / english / translation نیاز دارد.",
        "Not a valid subtitle-learning JSON; it needs the subtitles / english / translation keys."
    )
    fun jsonParseError(msg: String?) = t("خطای JSON: $msg", "JSON error: $msg")
    val jsonEmptyFileError = t("فایل JSON خالی است", "The JSON file is empty")
    fun jsonImportedSuccess(name: String, count: Int) = t(
        "✅ JSON وارد شد: $name ($count زیرنویس)",
        "✅ JSON imported: $name ($count subtitles)"
    )
    val jsonDefaultName = t("زیرنویس یادگیری (JSON).json", "Learning subtitle (JSON).json")
    val jsonActiveBadge = t("JSON فعال — اولویت بالا", "JSON active — highest priority")
    val removeImportedSubtitlesBtn = t("🗑 حذف زیرنویس‌های وارد شده", "🗑 Remove imported subtitles")
    val removeSubsConfirmTitle = t("حذف همه زیرنویس‌های وارد شده؟", "Remove all imported subtitles?")
    val removeSubsConfirmDesc = t(
        "زیرنویس انگلیسی، زیرنویس فارسی و زیرنویس JSON حذف می‌شوند.",
        "English subtitles, Persian subtitles, and JSON subtitles will be removed."
    )
    val subtitleRemovedAll = t("همه زیرنویس‌های وارد شده حذف شدند", "All imported subtitles were removed")

    // ── Subtitle learning sheet (sentence lesson / word analysis) ──
    val lessonSheetTitle = t("🎓 درس زیرنویس", "🎓 Subtitle lesson")
    val wordLessonSheetTitle = t("📖 یادگیری کلمه", "📖 Word learning")
    val lessonTranslationLabel = t("ترجمه", "Translation")
    val lessonGrammarLabel = t("گرامر", "Grammar")
    val lessonExplanationLabel = t("توضیح", "Explanation")
    val lessonStructureLabel = t("ساختار جمله", "Sentence structure")
    val lessonVocabLabel = t("واژگان", "Vocabulary")
    val lessonNotesLabel = t("یادداشت‌های یادگیری", "Learning notes")
    val lessonPronunciationLabel = t("تلفظ", "Pronunciation")
    val lessonDifficultyLabel = t("سختی", "Difficulty")
    val lessonLevelLabel = t("سطح", "Level")
    val lessonSentenceLabel = t("جمله", "Sentence")
    val meaningInContextLabel = t("معنی در این جمله", "Meaning in this sentence")
    val examplesLabel = t("مثال‌ها", "Examples")
    val extraExplanationLabel = t("توضیح بیشتر", "Additional explanation")
    val lessonSentenceLevelNote = t("توضیح بر اساس سطح یادگیری شما", "Explanation based on your learning level")
    val noJsonLessonFallback = t(
        "برای این جمله داده یادگیری JSON وجود ندارد؛ ترجمه و واژگان از منابع دیگر نمایش داده می‌شوند.",
        "No JSON learning data exists for this sentence; translation and vocabulary are shown from other sources."
    )
    val noJsonWordData = t(
        "برای این کلمه داده یادگیری JSON وجود ندارد.",
        "There is no JSON learning data for this word."
    )
    val dictionaryDataLabel = t("داده دیکشنری", "Dictionary data")
    val jsonLearningDataLabel = t("داده یادگیری JSON", "JSON learning data")
    val tapWordHint = t("برای دیدن درس هر کلمه، روی آن کلیک کنید", "Tap any word to see its lesson")
    val closeSheetBtn = t("بستن", "Close")

    // ── AppViewModel status/toast messages ──
    val invalidIndexError = t("ایندکس نامعتبر", "Invalid index")
    val translationEmptyError = t("ترجمه خالی بود", "Translation was empty")
    val subEnNotLoaded = t("زیرنویس انگلیسی بارگذاری نشده", "English subtitle not loaded")
    val subFaNotLoadedBoth = t("زیرنویس فارسی بارگذاری نشده. هر دو فایل را وارد کنید.", "Persian subtitle not loaded. Please import both files.")
    val noMatchedPairsFound = t("هیچ جفت هماهنگ پیدا نشد.", "No matching pairs found.")
    fun learnedPairsCount(n: Int) = t("یادگیری: $n جفت ذخیره شد", "Learned: $n pairs saved")
    val dictNotLoadedForLearning = t("دیکشنری بارگذاری نشده.", "Dictionary not loaded.")
    fun learnedWordsCount(count: Int, total: Int) = t("یادگیری از دیکشنری: $count کلمه ذخیره شد (از $total کلمه)", "Learned from dictionary: $count words saved (of $total)")
    val fileNotFoundError = t("فایل پیدا نشد", "File not found")
    fun savedAtPath(path: String) = t("✅ ذخیره شد: $path", "✅ Saved: $path")
    val noFilesToExport = t("هیچ فایلی برای خروجی وجود ندارد", "No files to export")
    fun exportedFilesSummary(ok: Int, total: Int, names: String) = t("✅ $ok از $total فایل در Downloads ذخیره شد:\n$names", "✅ $ok of $total files saved to Downloads:\n$names")
    val noFaSubtitleExists = t("زیرنویس فارسی وجود ندارد", "No Persian subtitle exists")
    val srtCreateError = t("خطا در ایجاد فایل SRT", "Error creating SRT file")
    val downloadsCreateError = t("خطا در ایجاد فایل در Downloads", "Error creating file in Downloads")
    val fileWriteError = t("خطا در نوشتن فایل", "Error writing file")
    val noWordMeaningFound = t("برای این کلمه معنایی یافت نشد", "No meaning found for this word")
    val noMeaningToSave = t("معنایی برای ذخیره پیدا نشد", "No meaning found to save")
    fun wordAddedToLeitner(word: String) = t("«$word» به جعبه لایتنر اضافه شد", "\"$word\" added to the Leitner box")
    fun wordUpdatedInLeitner(word: String) = t("معنای «$word» در جعبه لایتنر به‌روزرسانی شد", "The meaning of \"$word\" was updated in the Leitner box")
    val leitnerBoxEmpty = t("جعبه لایتنر خالی است", "The Leitner box is empty")
    fun ankiExportSaved(path: String) = t("✅ خروجی Anki ذخیره شد: $path", "✅ Anki export saved: $path")

    // ── Donate popup / About dialog ──
    val socialTitle = t("ارتباط با ما", "Connect with us")

	val socialDescription = t(
	    "برای خبرها، پیشنهادها و پشتیبانی ما را دنبال کنید.",
	    "Follow us for news, suggestions, and support."
	)
	
	val telegramLabel = "Telegram"
	val telegramTopicLabel = "Telegram Topic"
	val githubLabel = "GitHub"
    val donateInfoBannerText = t(
        "این برنامه کاملاً رایگانه، ولی اگر خواستید می‌توانید از پروژه حمایت کنید",
        "This app is completely free, but if you'd like, you can donate to support the project"
    )
    val donateLinkInvalidError = t("لینک معتبر نیست", "The link isn't valid")
    fun addressCopiedToast(title: String) = t("آدرس $title کپی شد", "$title address copied")
    val openLinkCd = t("باز کردن لینک", "Open link")
    val copyAddressCd = t("کپی آدرس", "Copy address")
    val donateCloseBtn = t("بستن", "Close")
    val donateDontShowAgainBtn = t("دیگر نمایش نده", "Don't show again")
    val aboutDialogTitle = t("درباره", "About")
    fun aboutVersionLabel(version: String) = t("نسخه $version", "Version $version")
    val bitcoinTitle = t("بیت‌کوین (Bitcoin)", "Bitcoin")
    val tetherTitle = t("تتر (USDT)", "Tether (USDT)")
    val tonTitle = t("تون (TON)", "TON")
}
