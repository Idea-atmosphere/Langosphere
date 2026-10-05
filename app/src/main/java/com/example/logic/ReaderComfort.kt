package com.example.logic

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reading comfort for the book/PDF reader (the "Book" tab).
 *
 * Everything a long reading session needs lives here and is deliberately kept
 * INDEPENDENT of the app theme: the reader can stay on its warm day paper
 * while the rest of the app is dark, and the other way round
 * ([ReaderThemeMode]).
 *
 * Defaults are chosen for eye comfort rather than for looking punchy:
 *  - a warm sepia paper instead of pure white (less blue light, less glare),
 *  - dark-grey-on-cream / cream-on-near-black instead of #000 on #FFF,
 *  - a slightly larger font (1.1x) and generous 1.75 line spacing, which is
 *    what actually reduces fatigue over hours of reading,
 *  - a small 6% warm overlay that takes the edge off backlit screens.
 *
 * State is Compose-observable and persisted to SharedPreferences on every
 * change, so the reader looks the same the next time the app opens.
 */
enum class ReaderThemeMode(val key: String) {
    FOLLOW_APP("follow"),
    DAY("day"),
    NIGHT("night");

    companion object {
        fun from(key: String?): ReaderThemeMode = values().firstOrNull { it.key == key } ?: FOLLOW_APP
    }
}

/** Which typeface family the reading surface uses. APP = follow Settings ▸ Theme ▸ Font. */
enum class ReaderFontChoice(val key: String) {
    APP("app"),
    SERIF("serif"),
    SANS("sans"),
    MONO("mono");

    companion object {
        fun from(key: String?): ReaderFontChoice = values().firstOrNull { it.key == key } ?: APP
    }
}

/**
 * A paper: one background/text pair for the day side and one for the night
 * side, so switching day/night keeps the same character instead of jumping to
 * an unrelated color.
 */
data class ReaderPalette(
    val key: String,
    val nameEn: String,
    val nameFa: String,
    val dayBackground: Int,
    val dayText: Int,
    val nightBackground: Int,
    val nightText: Int,
)

object ReaderPalettes {

    /** The default. Warm cream paper, soft dark-brown ink: the lowest-glare pair here. */
    val SEPIA = ReaderPalette(
        key = "sepia",
        nameEn = "Sepia",
        nameFa = "سپیا",
        dayBackground = 0xFFF6ECD9.toInt(),
        dayText = 0xFF4A4034.toInt(),
        nightBackground = 0xFF1B1714.toInt(),
        nightText = 0xFFD9CDBA.toInt(),
    )

    val PAPER = ReaderPalette(
        key = "paper",
        nameEn = "Soft paper",
        nameFa = "کاغذ نرم",
        dayBackground = 0xFFFBF7F0.toInt(),
        dayText = 0xFF3A3A3A.toInt(),
        nightBackground = 0xFF16191C.toInt(),
        nightText = 0xFFC8CDD2.toInt(),
    )

    val WARM_GRAY = ReaderPalette(
        key = "warm_gray",
        nameEn = "Warm grey",
        nameFa = "خاکستری گرم",
        dayBackground = 0xFFEFEAE3.toInt(),
        dayText = 0xFF3D3A36.toInt(),
        nightBackground = 0xFF202326.toInt(),
        nightText = 0xFFC6C9CC.toInt(),
    )

    val MINT = ReaderPalette(
        key = "mint",
        nameEn = "Calm mint",
        nameFa = "سبز آرام",
        dayBackground = 0xFFE9F1E7.toInt(),
        dayText = 0xFF33403A.toInt(),
        nightBackground = 0xFF121A17.toInt(),
        nightText = 0xFFBFD2C6.toInt(),
    )

    val DUSK = ReaderPalette(
        key = "dusk",
        nameEn = "Cool dusk",
        nameFa = "آبی ملایم",
        dayBackground = 0xFFE8EEF4.toInt(),
        dayText = 0xFF2E3A45.toInt(),
        nightBackground = 0xFF101821.toInt(),
        nightText = 0xFFBCCBD8.toInt(),
    )

    /** High contrast / OLED: pure black at night saves power but is the harshest, so it is not the default. */
    val CONTRAST = ReaderPalette(
        key = "contrast",
        nameEn = "High contrast",
        nameFa = "کنتراست بالا",
        dayBackground = 0xFFFFFFFF.toInt(),
        dayText = 0xFF111111.toInt(),
        nightBackground = 0xFF000000.toInt(),
        nightText = 0xFFBFBFBF.toInt(),
    )

    val ALL = listOf(SEPIA, PAPER, WARM_GRAY, MINT, DUSK, CONTRAST)

    fun from(key: String?): ReaderPalette = ALL.firstOrNull { it.key == key } ?: SEPIA
}

object ReaderComfortState {

    private const val PREFS = "reader_comfort_prefs"

    /**
     * The key the web reading surface stores the same choice under
     * (`reader-weight-idx`). [LEGACY_TEXT_WEIGHT_IDX] is the name an earlier
     * build of this app wrote, and is read once so nobody loses their choice.
     */
    private const val KEY_TEXT_WEIGHT_IDX = "reader-weight-idx"
    private const val LEGACY_TEXT_WEIGHT_IDX = "noor-weight-idx"
    /** Persists the sentence card layout across documents, pages and launches. */
    private const val KEY_BUBBLE_MODE = "reader-bubble-mode"

    private var appContext: Context? = null
    private var loaded = false

    // ── The shipped defaults. Declared before the state they initialise, since
    // an object's properties are initialised in declaration order. ──

    /**
     * The three reading weights, in the order the header's button rotates
     * through them: 400 (regular), 500 (medium), 700 (bold). The numbers are
     * the same ones the web reader exposes as `--weight`, so the control and
     * its persisted choice behave identically in both readers.
     */
    val TEXT_WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)

    /** Numeric form of [TEXT_WEIGHTS], for labels and for the stored value. */
    val TEXT_WEIGHT_VALUES = listOf(400, 500, 700)

    /** Index into [TEXT_WEIGHTS] the reader starts on. */
    const val DEFAULT_TEXT_WEIGHT_IDX = 0

    const val DEFAULT_FONT_SCALE = 1.1f
    const val DEFAULT_LINE_HEIGHT = 1.75f
    const val DEFAULT_LETTER_SPACING = 0.1f
    const val DEFAULT_PARAGRAPH_SPACING = 10f
    const val DEFAULT_MARGIN = 20f
    const val DEFAULT_WARMTH = 0.06f
    val DEFAULT_HIGHLIGHT = 0xFFF2C744.toInt()

    /** The highlighter pen colors, kept light so dark ink stays readable on top of them. */
    val HIGHLIGHT_COLORS = listOf(
        0xFFF2C744.toInt(), // amber
        0xFF8FD694.toInt(), // green
        0xFF8FC7E8.toInt(), // blue
        0xFFE8A9C8.toInt(), // pink
        0xFFCDB4F0.toInt(), // lavender
        0xFFF09C7A.toInt(), // coral
    )

    // ── Backing state. Private and underscore-named so the generated accessors
    // cannot collide on the JVM with the public setX(...) mutators below
    // (a `var x` + `fun setX(...)` pair compiles to the same setX signature). ──

    // Typography
    private var _fontScale by mutableStateOf(DEFAULT_FONT_SCALE)
    private var _lineHeight by mutableStateOf(DEFAULT_LINE_HEIGHT)
    private var _letterSpacing by mutableStateOf(DEFAULT_LETTER_SPACING)
    private var _paragraphSpacing by mutableStateOf(DEFAULT_PARAGRAPH_SPACING)
    private var _horizontalMargin by mutableStateOf(DEFAULT_MARGIN)
    private var _justify by mutableStateOf(false)

    // Index into [TEXT_WEIGHTS]. The comfort sheet's older boolean switch is a
    // view of this single value rather than a second, competing setting.
    private var _textWeightIdx by mutableStateOf(DEFAULT_TEXT_WEIGHT_IDX)
    private var _fontChoice by mutableStateOf(ReaderFontChoice.APP)

    // Colors
    private var _themeMode by mutableStateOf(ReaderThemeMode.FOLLOW_APP)
    private var _paletteKey by mutableStateOf(ReaderPalettes.SEPIA.key)
    private var _customDayBackground by mutableStateOf<Int?>(null)
    private var _customDayText by mutableStateOf<Int?>(null)
    private var _customNightBackground by mutableStateOf<Int?>(null)
    private var _customNightText by mutableStateOf<Int?>(null)
    private var _warmth by mutableStateOf(DEFAULT_WARMTH)
    private var _dimming by mutableStateOf(0f)

    // Behaviour
    private var _keepScreenOn by mutableStateOf(true)
    private var _tapToHighlight by mutableStateOf(false)
    private var _highlightColor by mutableStateOf(DEFAULT_HIGHLIGHT)
    private var _showPdfImages by mutableStateOf(true)
    private var _showChapterProgress by mutableStateOf(true)
    // The bubble layout is enabled by default so imported sentences remain
    // visibly paired on first use. It is shared by the merged reader and the
    // translation-only list, rather than being tied to a particular document.
    private var _bubbleMode by mutableStateOf(true)

    // ── Typography ──
    val fontScale: Float get() = _fontScale
    val lineHeight: Float get() = _lineHeight
    val letterSpacing: Float get() = _letterSpacing
    val paragraphSpacing: Float get() = _paragraphSpacing
    val horizontalMargin: Float get() = _horizontalMargin
    val justify: Boolean get() = _justify
    val fontChoice: ReaderFontChoice get() = _fontChoice

    /** Index into [TEXT_WEIGHTS]: 0 = 400, 1 = 500, 2 = 700. */
    val textWeightIdx: Int get() = _textWeightIdx

    /** The reading weight itself, clamped so a bad stored index can never crash a layout. */
    val textWeight: FontWeight get() = TEXT_WEIGHTS[_textWeightIdx.coerceIn(0, TEXT_WEIGHTS.lastIndex)]

    /**
     * The comfort sheet's "heavier weight" switch, kept as a VIEW of the one
     * weight setting: on is the bold stop (700), off is the regular one (400).
     * Two independent weight states would fight each other - flipping the
     * switch would look like it did nothing whenever the header button had
     * already been used.
     */
    val boldText: Boolean get() = _textWeightIdx == TEXT_WEIGHTS.lastIndex

    // ── Colors ──
    val themeMode: ReaderThemeMode get() = _themeMode
    val paletteKey: String get() = _paletteKey
    val customDayBackground: Int? get() = _customDayBackground
    val customDayText: Int? get() = _customDayText
    val customNightBackground: Int? get() = _customNightBackground
    val customNightText: Int? get() = _customNightText

    /** Warm (amber) wash over the page, 0f..0.35f. A small amount cuts blue light. */
    val warmth: Float get() = _warmth

    /** Extra software dimming, 0f..0.6f, for reading in the dark below the system's minimum brightness. */
    val dimming: Float get() = _dimming

    // ── Behaviour ──
    val keepScreenOn: Boolean get() = _keepScreenOn
    val tapToHighlight: Boolean get() = _tapToHighlight
    val highlightColor: Int get() = _highlightColor
    val showPdfImages: Boolean get() = _showPdfImages
    val showChapterProgress: Boolean get() = _showChapterProgress
    /** Whether each English sentence and its translation share one card. */
    val bubbleMode: Boolean get() = _bubbleMode

    fun load(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _fontScale = p.getFloat("fontScale", DEFAULT_FONT_SCALE)
        _lineHeight = p.getFloat("lineHeight", DEFAULT_LINE_HEIGHT)
        _letterSpacing = p.getFloat("letterSpacing", DEFAULT_LETTER_SPACING)
        _paragraphSpacing = p.getFloat("paragraphSpacing", DEFAULT_PARAGRAPH_SPACING)
        _horizontalMargin = p.getFloat("horizontalMargin", DEFAULT_MARGIN)
        _justify = p.getBoolean("justify", false)
        // Three generations of the same setting, newest first:
        //  1. `reader-weight-idx` - the web reading surface's key, current here;
        //  2. `noor-weight-idx` - this app's earlier key for the identical value;
        //  3. the old `boldText` boolean, in use before the three-stop control.
        // Reading them in that order means an upgrade never silently moves the
        // reader's text weight back to 400.
        val legacyBold = p.getBoolean("boldText", false)
        val fallback = if (legacyBold) TEXT_WEIGHTS.lastIndex else DEFAULT_TEXT_WEIGHT_IDX
        _textWeightIdx = p
            .getInt(KEY_TEXT_WEIGHT_IDX, p.getInt(LEGACY_TEXT_WEIGHT_IDX, fallback))
            .coerceIn(0, TEXT_WEIGHTS.lastIndex)
        _fontChoice = ReaderFontChoice.from(p.getString("fontChoice", null))
        _themeMode = ReaderThemeMode.from(p.getString("themeMode", null))
        _paletteKey = p.getString("paletteKey", ReaderPalettes.SEPIA.key) ?: ReaderPalettes.SEPIA.key
        _customDayBackground = p.readNullableInt("customDayBackground")
        _customDayText = p.readNullableInt("customDayText")
        _customNightBackground = p.readNullableInt("customNightBackground")
        _customNightText = p.readNullableInt("customNightText")
        _warmth = p.getFloat("warmth", DEFAULT_WARMTH)
        _dimming = p.getFloat("dimming", 0f)
        _keepScreenOn = p.getBoolean("keepScreenOn", true)
        _tapToHighlight = p.getBoolean("tapToHighlight", false)
        _highlightColor = p.getInt("highlightColor", DEFAULT_HIGHLIGHT)
        _showPdfImages = p.getBoolean("showPdfImages", true)
        _showChapterProgress = p.getBoolean("showChapterProgress", true)
        _bubbleMode = p.getBoolean(KEY_BUBBLE_MODE, true)
        loaded = true
    }

    // ── Mutators. Every one persists, so there is no "save" button anywhere. ──

    fun setFontScale(value: Float) { _fontScale = value.coerceIn(0.7f, 2.6f); persist() }
    fun stepFontScale(delta: Float) = setFontScale(_fontScale + delta)
    fun setLineHeight(value: Float) { _lineHeight = value.coerceIn(1.0f, 2.8f); persist() }
    fun setLetterSpacing(value: Float) { _letterSpacing = value.coerceIn(-0.4f, 2.0f); persist() }
    fun setParagraphSpacing(value: Float) { _paragraphSpacing = value.coerceIn(0f, 32f); persist() }
    fun setHorizontalMargin(value: Float) { _horizontalMargin = value.coerceIn(0f, 56f); persist() }
    fun setJustify(value: Boolean) { _justify = value; persist() }
    fun setBoldText(value: Boolean) { setTextWeightIdx(if (value) TEXT_WEIGHTS.lastIndex else 0) }

    fun setTextWeightIdx(index: Int) {
        _textWeightIdx = index.coerceIn(0, TEXT_WEIGHTS.lastIndex)
        persist()
    }

    /** Steps to the next weight and wraps around: the header button's only job. */
    fun cycleTextWeight() = setTextWeightIdx((_textWeightIdx + 1) % TEXT_WEIGHTS.size)

    /**
     * The weight a reading surface should paint with: the chosen stop, or the
     * style's own weight while the reader is on 400 - so a skin that ships a
     * heavier body style is not flattened to Normal.
     */
    fun resolveTextWeight(base: FontWeight): FontWeight =
        if (_textWeightIdx == DEFAULT_TEXT_WEIGHT_IDX) base else textWeight

    fun setFontChoice(value: ReaderFontChoice) { _fontChoice = value; persist() }
    fun setThemeMode(value: ReaderThemeMode) { _themeMode = value; persist() }
    fun setPalette(key: String) {
        _paletteKey = ReaderPalettes.from(key).key
        // Picking a ready-made paper clears the hand-mixed colors, otherwise the
        // preset would look like it did nothing.
        _customDayBackground = null
        _customDayText = null
        _customNightBackground = null
        _customNightText = null
        persist()
    }
    fun setCustomBackground(night: Boolean, argb: Int?) {
        if (night) _customNightBackground = argb else _customDayBackground = argb
        persist()
    }
    fun setCustomText(night: Boolean, argb: Int?) {
        if (night) _customNightText = argb else _customDayText = argb
        persist()
    }
    fun setWarmth(value: Float) { _warmth = value.coerceIn(0f, 0.35f); persist() }
    fun setDimming(value: Float) { _dimming = value.coerceIn(0f, 0.6f); persist() }
    fun setKeepScreenOn(value: Boolean) { _keepScreenOn = value; persist() }
    fun setTapToHighlight(value: Boolean) { _tapToHighlight = value; persist() }
    fun setHighlightColor(argb: Int) { _highlightColor = argb; persist() }
    fun setShowPdfImages(value: Boolean) { _showPdfImages = value; persist() }
    fun setShowChapterProgress(value: Boolean) { _showChapterProgress = value; persist() }

    /** Changes the sentence layout without touching TTS, selection or study state. */
    fun setBubbleMode(value: Boolean) { _bubbleMode = value; persist() }
    fun toggleBubbleMode() { setBubbleMode(!_bubbleMode) }

    /** Back to the eye-comfort colors only; typography is left alone. */
    fun resetColors() {
        _paletteKey = ReaderPalettes.SEPIA.key
        _customDayBackground = null
        _customDayText = null
        _customNightBackground = null
        _customNightText = null
        _warmth = DEFAULT_WARMTH
        _dimming = 0f
        _themeMode = ReaderThemeMode.FOLLOW_APP
        persist()
    }

    /** Back to the shipped eye-comfort defaults, colors and typography together. */
    fun resetAll() {
        _fontScale = DEFAULT_FONT_SCALE
        _lineHeight = DEFAULT_LINE_HEIGHT
        _letterSpacing = DEFAULT_LETTER_SPACING
        _paragraphSpacing = DEFAULT_PARAGRAPH_SPACING
        _horizontalMargin = DEFAULT_MARGIN
        _justify = false
        _textWeightIdx = DEFAULT_TEXT_WEIGHT_IDX
        _fontChoice = ReaderFontChoice.APP
        _highlightColor = DEFAULT_HIGHLIGHT
        _tapToHighlight = false
        _showPdfImages = true
        _showChapterProgress = true
        _keepScreenOn = true
        _bubbleMode = true
        resetColors()
    }

    /** True when any setting differs from the shipped defaults (drives the Reset button's state). */
    fun isDefault(): Boolean =
        _fontScale == DEFAULT_FONT_SCALE &&
            _lineHeight == DEFAULT_LINE_HEIGHT &&
            _letterSpacing == DEFAULT_LETTER_SPACING &&
            _paragraphSpacing == DEFAULT_PARAGRAPH_SPACING &&
            _horizontalMargin == DEFAULT_MARGIN &&
            !_justify &&
            _textWeightIdx == DEFAULT_TEXT_WEIGHT_IDX &&
            _bubbleMode &&
            _fontChoice == ReaderFontChoice.APP &&
            _themeMode == ReaderThemeMode.FOLLOW_APP &&
            _paletteKey == ReaderPalettes.SEPIA.key &&
            _customDayBackground == null &&
            _customDayText == null &&
            _customNightBackground == null &&
            _customNightText == null &&
            _warmth == DEFAULT_WARMTH &&
            _dimming == 0f

    /** The page color for the given side, hand-mixed value first, then the paper. */
    fun backgroundArgb(night: Boolean): Int {
        val palette = ReaderPalettes.from(_paletteKey)
        return if (night) {
            _customNightBackground ?: palette.nightBackground
        } else {
            _customDayBackground ?: palette.dayBackground
        }
    }

    /** The ink color for the given side, hand-mixed value first, then the paper. */
    fun textArgb(night: Boolean): Int {
        val palette = ReaderPalettes.from(_paletteKey)
        return if (night) {
            _customNightText ?: palette.nightText
        } else {
            _customDayText ?: palette.dayText
        }
    }

    /** Whether the reading surface should use its night side, given the app's own dark mode. */
    fun isNight(appIsDark: Boolean): Boolean = when (_themeMode) {
        ReaderThemeMode.DAY -> false
        ReaderThemeMode.NIGHT -> true
        ReaderThemeMode.FOLLOW_APP -> appIsDark
    }

    private fun persist() {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putFloat("fontScale", _fontScale)
            putFloat("lineHeight", _lineHeight)
            putFloat("letterSpacing", _letterSpacing)
            putFloat("paragraphSpacing", _paragraphSpacing)
            putFloat("horizontalMargin", _horizontalMargin)
            putBoolean("justify", _justify)
            putInt(KEY_TEXT_WEIGHT_IDX, _textWeightIdx)
            // Legacy mirror: an older build reading this file still gets the
            // same weight it would have stored itself.
            putBoolean("boldText", _textWeightIdx == TEXT_WEIGHTS.lastIndex)
            putString("fontChoice", _fontChoice.key)
            putString("themeMode", _themeMode.key)
            putString("paletteKey", _paletteKey)
            writeNullableInt("customDayBackground", _customDayBackground)
            writeNullableInt("customDayText", _customDayText)
            writeNullableInt("customNightBackground", _customNightBackground)
            writeNullableInt("customNightText", _customNightText)
            putFloat("warmth", _warmth)
            putFloat("dimming", _dimming)
            putBoolean("keepScreenOn", _keepScreenOn)
            putBoolean("tapToHighlight", _tapToHighlight)
            putInt("highlightColor", _highlightColor)
            putBoolean("showPdfImages", _showPdfImages)
            putBoolean("showChapterProgress", _showChapterProgress)
            putBoolean(KEY_BUBBLE_MODE, _bubbleMode)
            apply()
        }
    }
}

private fun android.content.SharedPreferences.readNullableInt(key: String): Int? =
    if (contains(key)) getInt(key, 0) else null

private fun android.content.SharedPreferences.Editor.writeNullableInt(key: String, value: Int?) {
    if (value == null) remove(key) else putInt(key, value)
}

/** One highlighted word/phrase in one document, optionally with a user note. */
data class ReaderHighlight(
    val text: String,
    val colorArgb: Int,
    val location: String,
    val createdAt: Long,
    val note: String = "",
)

/** One free-form note attached to a selected phrase (may exist without a highlight). */
data class ReaderNote(
    val text: String,
    val note: String,
    val location: String,
    val createdAt: Long,
)

/**
 * Highlights, stored per document (the document key is its file name plus
 * size, so re-opening the same book restores its marks). Tap-to-highlight in
 * the reader writes here, and the comfort sheet lists/removes/exports them.
 */
object ReaderHighlightState {

    private const val PREFS = "reader_highlights_prefs"
    private var appContext: Context? = null

    var docKey by mutableStateOf(""); private set
    var items by mutableStateOf<List<ReaderHighlight>>(emptyList()); private set

    fun open(context: Context, key: String) {
        appContext = context.applicationContext
        if (docKey == key) return
        docKey = key
        items = if (key.isEmpty()) emptyList() else read(context, key)
    }

    fun isHighlighted(text: String): Boolean =
        items.any { it.text.equals(text.trim(), ignoreCase = true) }

    fun colorOf(text: String): Int? =
        items.firstOrNull { it.text.equals(text.trim(), ignoreCase = true) }?.colorArgb

    /** Adds the phrase with the chosen color, or updates its color if already highlighted. */
    fun addHighlight(context: Context, text: String, colorArgb: Int, location: String) {
        val clean = text.trim()
        if (clean.isEmpty() || docKey.isEmpty()) return
        val existing = items.firstOrNull { it.text.equals(clean, ignoreCase = true) }
        items = if (existing != null) {
            items - existing + existing.copy(colorArgb = colorArgb)
        } else {
            items + ReaderHighlight(clean, colorArgb, location, System.currentTimeMillis())
        }
        write(context)
    }

    /** Attaches or updates a note for the given phrase (creates highlight if needed). */
    fun addNote(context: Context, text: String, note: String, location: String) {
        val clean = text.trim()
        if (clean.isEmpty() || docKey.isEmpty()) return
        val trimmedNote = note.trim()
        val existing = items.firstOrNull { it.text.equals(clean, ignoreCase = true) }
        items = if (existing != null) {
            items - existing + existing.copy(note = trimmedNote)
        } else {
            items + ReaderHighlight(
                clean,
                ReaderComfortState.DEFAULT_HIGHLIGHT,
                location,
                System.currentTimeMillis(),
                note = trimmedNote
            )
        }
        write(context)
    }

    /** Adds the word when it is not marked yet, removes it when it is. Kept for legacy tap path. */
    fun toggle(context: Context, text: String, colorArgb: Int, location: String) {
        val clean = text.trim()
        if (clean.isEmpty() || docKey.isEmpty()) return
        val existing = items.firstOrNull { it.text.equals(clean, ignoreCase = true) }
        items = if (existing != null) {
            items - existing
        } else {
            items + ReaderHighlight(clean, colorArgb, location, System.currentTimeMillis())
        }
        write(context)
    }

    fun remove(context: Context, highlight: ReaderHighlight) {
        items = items - highlight
        write(context)
    }

    fun clear(context: Context) {
        items = emptyList()
        write(context)
    }

    /** phrase -> color map used to paint the reading surface (case-insensitive). */
    fun colorMap(): Map<String, Int> =
        items.associate { it.text.lowercase() to it.colorArgb }

    /** Plain-text export, one highlight per line, for the clipboard. */
    fun asPlainText(): String =
        items.joinToString("\n") { highlight ->
            if (highlight.location.isBlank()) highlight.text else "${highlight.text}  —  ${highlight.location}"
        }

    private fun read(context: Context, key: String): List<ReaderHighlight> = try {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val text = obj.optString("text").orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            ReaderHighlight(
                text = text,
                colorArgb = obj.optInt("color", ReaderComfortState.DEFAULT_HIGHLIGHT),
                location = obj.optString("location").orEmpty(),
                createdAt = obj.optLong("createdAt", 0L),
                note = obj.optString("note", ""),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(context: Context) {
        val key = docKey
        if (key.isEmpty()) return
        val array = JSONArray()
        items.forEach { highlight ->
            array.put(
                JSONObject()
                    .put("text", highlight.text)
                    .put("color", highlight.colorArgb)
                    .put("location", highlight.location)
                    .put("createdAt", highlight.createdAt)
                    .put("note", highlight.note)
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .apply()
    }
}

/**
 * Standalone notes attached to selected phrases (without necessarily highlighting).
 * Stored per-document alongside highlights, shares the same prefs file for simplicity.
 */
object ReaderNoteState {
    private const val PREFS = "reader_notes_prefs"
    private var appContext: Context? = null

    var docKey by mutableStateOf(""); private set
    var items by mutableStateOf<List<ReaderNote>>(emptyList()); private set

    fun open(context: Context, key: String) {
        appContext = context.applicationContext
        if (docKey == key) return
        docKey = key
        items = if (key.isEmpty()) emptyList() else read(context, key)
    }

    fun add(context: Context, text: String, note: String, location: String) {
        val clean = text.trim()
        val trimmedNote = note.trim()
        if (clean.isEmpty() || trimmedNote.isEmpty() || docKey.isEmpty()) return
        val existing = items.firstOrNull { it.text.equals(clean, ignoreCase = true) }
        items = if (existing != null) {
            items - existing + existing.copy(note = trimmedNote)
        } else {
            items + ReaderNote(clean, trimmedNote, location, System.currentTimeMillis())
        }
        write(context)
    }

    fun remove(context: Context, note: ReaderNote) {
        items = items - note
        write(context)
    }

    fun clear(context: Context) {
        items = emptyList()
        write(context)
    }

    private fun read(context: Context, key: String): List<ReaderNote> = try {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val text = obj.optString("text").orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            ReaderNote(
                text = text,
                note = obj.optString("note").orEmpty(),
                location = obj.optString("location").orEmpty(),
                createdAt = obj.optLong("createdAt", 0L),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(context: Context) {
        val key = docKey
        if (key.isEmpty()) return
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject()
                    .put("text", entry.text)
                    .put("note", entry.note)
                    .put("location", entry.location)
                    .put("createdAt", entry.createdAt)
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .apply()
    }
}
