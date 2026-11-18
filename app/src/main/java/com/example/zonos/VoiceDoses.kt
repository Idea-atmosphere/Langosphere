package com.example.zonos

import kotlin.math.abs

/**
 * Shared voice-design code for the in-app TTS (and the Studio-design
 * conversion in StudioDesign.kt) — pure Kotlin, no Android
 * dependencies, so plain JVM unit tests can cover it.
 *
 * Origin: ported from the ZonoLingua reader (v0.2.21); the Persian
 * labels of the control/accent tables are kept as documentation of
 * the Studio's UI ids — the app never shows them.
 *
 * Design -> request controls: the Studio's buildRequest() does NOT
 * send UI control ids. It runs toDoses() (studio/src/state/engine.ts):
 * each UI value v maps through the fixture's map — v > 0 uses
 * map['+'] x v, v < 0 uses map['-'] x |v| — into primitive direction
 * ids ("delivery.tempo", "identity.gender", ...). The web worker
 * (servers/web/src/request/steer.ts resolveControls) THROWS
 * "Unknown control '<id>'" for any other key, so a bare "pace" would
 * fail the whole generate. [controlDoses] mirrors toDoses exactly:
 * span_only controls (only `emphasis`) are dropped at request level,
 * values round to 4 decimals, zeros are dropped. All 24 remaining
 * controls are usable in speaker_mode "embedding" (every set in the
 * fixture lists 'embedding' in its modes), so no usability filter is
 * needed here. No accent pin (accents default 0) and no script
 * baseline extras.
 */

// ---- minimal JSON (no org.json on the JVM harness classpath) ----

/** Tiny JSON value model: just enough for voices.json. */
sealed class JVal {
    data class Obj(val map: Map<String, JVal>) : JVal()
    data class Arr(val items: List<JVal>) : JVal()
    data class Str(val s: String) : JVal()
    data class Num(val d: Double) : JVal()
    data class Bool(val b: Boolean) : JVal()
    object Null : JVal()
}

/** Minimal recursive-descent JSON parser (objects, arrays, strings with escapes, numbers, literals). */
object MiniJson {
    fun parse(text: String): JVal? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value() ?: return null
        p.skipWs()
        return if (p.atEnd()) v else null
    }

    private class Parser(val s: String) {
        var i = 0
        fun atEnd() = i >= s.length
        fun skipWs() {
            while (i < s.length && s[i] in " \t\r\n") i++
        }

        fun value(): JVal? {
            if (atEnd()) return null
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JVal.Str(str())
                't' -> if (s.startsWith("true", i)) {
                    i += 4; JVal.Bool(true)
                } else null
                'f' -> if (s.startsWith("false", i)) {
                    i += 5; JVal.Bool(false)
                } else null
                'n' -> if (s.startsWith("null", i)) {
                    i += 4; JVal.Null
                } else null
                else -> num()
            }
        }

        fun obj(): JVal? {
            i++ // {
            val m = LinkedHashMap<String, JVal>()
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return JVal.Obj(m)
            }
            while (true) {
                skipWs()
                if (atEnd() || s[i] != '"') return null
                val k = str()
                skipWs()
                if (atEnd() || s[i] != ':') return null
                i++
                skipWs()
                val v = value() ?: return null
                m[k] = v
                skipWs()
                if (atEnd()) return null
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return JVal.Obj(m)
                    }
                    else -> return null
                }
            }
        }

        fun arr(): JVal? {
            i++ // [
            val items = ArrayList<JVal>()
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return JVal.Arr(items)
            }
            while (true) {
                skipWs()
                val v = value() ?: return null
                items.add(v)
                skipWs()
                if (atEnd()) return null
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return JVal.Arr(items)
                    }
                    else -> return null
                }
            }
        }

        fun str(): String {
            // Assumes s[i] == '"'.
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val ch = s[i++]
                if (ch == '"') return sb.toString()
                if (ch == '\\' && i < s.length) {
                    when (val e = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 <= s.length) {
                                val hex = s.substring(i, i + 4)
                                val code = hex.toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                i += 4
                            }
                        }
                        else -> sb.append(e)
                    }
                } else sb.append(ch)
            }
            return sb.toString()
        }

        fun num(): JVal? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            if (start == i) return null
            val d = s.substring(start, i).toDoubleOrNull() ?: return null
            return JVal.Num(d)
        }
    }
}

// ---- design controls (fixture: studio/src/fixtures/controls.json, ui.controls) ----


/** One design slider: id, Persian label, group, fixture range, span-only flag, and the direction maps. */
data class ControlDef(
    val id: String,
    val labelFa: String,
    val group: String,
    val min: Float,
    val max: Float,
    val spanOnly: Boolean,
    /** Direction -> weight when the value is positive. */
    val plus: Map<String, Float>,
    /** Direction -> weight when the value is negative (applied x |value|). */
    val minus: Map<String, Float>
)

/** The 25 controls, generated from controls.json (see build script note in the harness). */
val VOICE_CONTROLS = listOf(
    ControlDef(
        id = "gender", labelFa = "جنسیت", group = "who",
        min = -3.0f, max = 3.0f, spanOnly = false,
        plus = mapOf("identity.gender" to 1.0f),
        minus = mapOf("identity.gender" to -1.0f)
    ),
    ControlDef(
        id = "age", labelFa = "سن", group = "who",
        min = -3.0f, max = 3.0f, spanOnly = false,
        plus = mapOf("identity.age" to 1.0f),
        minus = mapOf("identity.age" to -1.0f)
    ),
    ControlDef(
        id = "pitch", labelFa = "زیروبمی صدا", group = "who",
        min = -3.0f, max = 3.0f, spanOnly = false,
        plus = mapOf("pitch.pitch" to 1.0f),
        minus = mapOf("pitch.pitch" to -1.0f)
    ),
    ControlDef(
        id = "range", labelFa = "گسترهٔ آهنگ", group = "who",
        min = -3.0f, max = 3.0f, spanOnly = false,
        plus = mapOf("pitch.pitch_range" to 1.0f),
        minus = mapOf("pitch.pitch_range" to -1.0f)
    ),
    ControlDef(
        id = "deep", labelFa = "عمیق", group = "texture",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("texture.deep" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "bright", labelFa = "شفاف", group = "texture",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("texture.shrill" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "husky", labelFa = "خش‌دار", group = "texture",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("texture.husky" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "raspy", labelFa = "گرفته", group = "texture",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("texture.raspy" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "soft", labelFa = "نرم", group = "texture",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("texture.soft" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "authority", labelFa = "مقتدر", group = "texture",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("texture.authoritative" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "room", labelFa = "طنین اتاق", group = "room",
        min = -4.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("room.clean" to 1.0f),
        minus = mapOf("room.clean" to -1.0f)
    ),
    ControlDef(
        id = "projection", labelFa = "بلندی صدا", group = "delivery",
        min = -3.0f, max = 4.0f, spanOnly = false,
        plus = mapOf("delivery.loud" to 1.0f),
        minus = mapOf("delivery.whisper" to 1.0f)
    ),
    ControlDef(
        id = "pace", labelFa = "سرعت", group = "delivery",
        min = -3.0f, max = 3.0f, spanOnly = false,
        plus = mapOf("delivery.tempo" to 1.0f),
        minus = mapOf("delivery.tempo" to -1.0f)
    ),
    ControlDef(
        id = "chatty", labelFa = "محاوره‌ای", group = "delivery",
        min = -5.0f, max = 5.0f, spanOnly = false,
        plus = mapOf("delivery.conversational" to 1.0f),
        minus = mapOf("delivery.conversational" to -1.0f)
    ),
    ControlDef(
        id = "hesitant", labelFa = "تردید", group = "delivery",
        min = -8.0f, max = 20.0f, spanOnly = false,
        plus = mapOf("disfluency.disfluent" to 1.0f),
        minus = mapOf("disfluency.disfluent" to -1.0f)
    ),
    ControlDef(
        id = "lively", labelFa = "سرزندگی", group = "delivery",
        min = -16.0f, max = 16.0f, spanOnly = false,
        plus = mapOf("expressive.expressive" to 1.0f),
        minus = mapOf("expressive.expressive" to -1.0f)
    ),
    ControlDef(
        id = "emphasis", labelFa = "تأکید", group = "delivery",
        min = 0.0f, max = 6.0f, spanOnly = true,
        plus = mapOf("emphasis_nv_nodur.emphasis" to 4.0f, "mood.arousal" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "valence", labelFa = "مثبت‌بودن حس", group = "mood",
        min = -6.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("mood.valence" to 1.0f),
        minus = mapOf("mood.valence" to -1.0f)
    ),
    ControlDef(
        id = "arousal", labelFa = "برانگیختگی", group = "mood",
        min = -6.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("mood.arousal" to 1.0f),
        minus = mapOf("mood.arousal" to -1.0f)
    ),
    ControlDef(
        id = "angry", labelFa = "عصبانی", group = "mood",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("emotions.angry" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "happy", labelFa = "شاد", group = "mood",
        min = 0.0f, max = 6.0f, spanOnly = false,
        plus = mapOf("emotions.happy" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "sad", labelFa = "غمگین", group = "mood",
        min = 0.0f, max = 8.0f, spanOnly = false,
        plus = mapOf("emotions.sad" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "calm", labelFa = "آرام", group = "mood",
        min = 0.0f, max = 8.0f, spanOnly = false,
        plus = mapOf("emotions.serene" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "scared", labelFa = "ترسیده", group = "mood",
        min = 0.0f, max = 14.0f, spanOnly = false,
        plus = mapOf("emotions.scared" to 1.0f),
        minus = emptyMap()
    ),
    ControlDef(
        id = "confused", labelFa = "گیج", group = "mood",
        min = 0.0f, max = 4.0f, spanOnly = false,
        plus = mapOf("emotions.confused" to 1.0f),
        minus = emptyMap()
    )
)

/**
 * UI control values (+ accent mix) -> request-level `controls` for POST /v2/generate.
 * Exact mirror of the Studio's toDoses() (studio/src/state/engine.ts):
 * skip zero values and span-only controls, apply the side map with the
 * magnitude, then the accent loop (each picked accent adds its direction
 * map x strength), round each dose to 4 decimals, drop zeros. All accent
 * sets allow speaker_mode "embedding" (the app's only mode), so no
 * mode filter is needed here.
 */
fun controlDoses(values: Map<String, Float>, accents: Map<String, Float> = emptyMap()): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    for (def in VOICE_CONTROLS) {
        val v = values[def.id] ?: 0f
        if (v == 0f || def.spanOnly) continue
        val side = if (v > 0f) def.plus else def.minus
        val mag = abs(v).toDouble()
        for ((dir, w) in side) {
            out[dir] = (out[dir] ?: 0.0) + w.toDouble() * mag
        }
    }
    for (a in VOICE_ACCENTS) {
        val s = accents[a.id] ?: 0f
        if (s == 0f) continue
        for ((dir, w) in a.map) {
            out[dir] = (out[dir] ?: 0.0) + w.toDouble() * s.toDouble()
        }
    }
    val it = out.entries.iterator()
    while (it.hasNext()) {
        val e = it.next()
        val r = Math.round(e.value * 1e4) / 1e4
        if (r == 0.0) it.remove() else e.setValue(r)
    }
    return out
}

// ---- accents (fixture ui.accents: 15) ----


/** One accent: id, Persian label, tab (english/world), direction map. */
data class AccentDef(
    val id: String,
    val labelFa: String,
    val tab: String,
    val map: Map<String, Float>
)

val VOICE_ACCENTS = listOf(
    AccentDef("american", "آمریکای شمالی", "english", mapOf("accents_en.american" to 1f)),
    AccentDef("british", "انگلستان", "english", mapOf("accents_en.british" to 1f)),
    AccentDef("scottish", "اسکاتلند", "english", mapOf("accents_en.scottish" to 1f)),
    AccentDef("irish", "ایرلند", "english", mapOf("accents_en.irish" to 1f)),
    AccentDef("welsh", "ولز", "english", mapOf("accents_en.welsh" to 1f)),
    AccentDef("australian", "استرالیا", "english", mapOf("accents_en.australian" to 1f)),
    AccentDef("indian", "هند (انگلیسی)", "english", mapOf("accents_en.indian" to 1f)),
    AccentDef("hin", "هندی", "world", mapOf("accents_l1.hin" to 1f)),
    AccentDef("spa", "اسپانیایی", "world", mapOf("accents_l1.spa" to 1f)),
    AccentDef("fra", "فرانسوی", "world", mapOf("accents_l1.fra" to 1f)),
    AccentDef("por", "پرتغالی", "world", mapOf("accents_l1.por" to 1f)),
    AccentDef("deu", "آلمانی", "world", mapOf("accents_l1.deu" to 1f)),
    AccentDef("vie", "ویتنامی", "world", mapOf("accents_l1.vie" to 1f)),
    AccentDef("pol", "لهستانی", "world", mapOf("accents_l1.pol" to 1f)),
    AccentDef(
        "mandarin", "ماندارین", "world",
        mapOf("mandarin.cmn" to 1f, "mandarin_hidden.cmn" to (1f / 3f))
    )
)

// ---- reader speed slider ----


/** Default pace timing table (Zyphra controls.json timing["delivery.tempo"]): (dose, duration factor). */
val DEFAULT_PACE_TIMING_TABLE = listOf(
    -4f to 2.881f, -2f to 2.212f, -1f to 1.451f, 0f to 1.0f,
    1f to 0.779f, 2f to 0.63f, 4f to 0.549f
)

/**
 * Map the 0.5x..2.0x speed slider to a pace dose: find the dose whose
 * duration factor is 1/speed by linear interpolation on the timing
 * table, clamped to the control's safe range. (Same algorithm as the
 * AppViewModel original; moved here unchanged for testability.)
 */
fun paceDoseForSpeed(
    speed: Float,
    table: List<Pair<Float, Float>> = DEFAULT_PACE_TIMING_TABLE,
    min: Float = -2f,
    max: Float = 2f
): Float {
    val target = 1f / speed.coerceIn(0.5f, 2.0f)
    var dose = when {
        target >= table.first().second -> table.first().first
        target <= table.last().second -> table.last().first
        else -> {
            var d = 0f
            for (i in 0 until table.size - 1) {
                val (x0, y0) = table[i]
                val (x1, y1) = table[i + 1]
                if (target <= y0 && target >= y1) {
                    d = if (y0 == y1) x0 else x0 + (target - y0) / (y1 - y0) * (x1 - x0)
                    break
                }
            }
            d
        }
    }
    return dose.coerceIn(min, max)
}

/**
 * The exact opts JSON the reader sends for one sentence: language +
 * the pace dose under the RUNTIME control id "delivery.tempo".
 * At speed 1.0x the dose is 0, so no "controls" key is sent at all
 * (the worker silently drops dose 0 anyway). A bare "pace" key never
 * appears — that is the v0.2.15 bug this fixes.
 *
 * v0.2.19 SPEAKER MODE RULE: "speaker_mode" is ALWAYS sent. The
 * server defaults to the checkpoint's FIRST mode when the field is
 * omitted (generate.ts planGenerate resolve_speaker_mode: `let mode =
 * modes[0]`; speakers.ts checkpointModes() returns
 * ['reference', 'embedding']), so omitting it silently selected
 * 'reference' — and any embedding-moving control (texture.*, identity.*,
 * pitch.*, room.clean, accents — the raw-space sets, see
 * EMBEDDING_MOVING_DIRECTIONS in StudioDesign.kt) then 400s with
 * "need speaker_mode=embedding" (log #12). The Studio always sends
 * d.mode (requests.ts:132), so the app must too. Mode comes from the
 * effective design (a Studio bench/persona mode); with no design
 * it is "embedding".
 */
fun readerOptsJson(
    speed: Float,
    table: List<Pair<Float, Float>> = DEFAULT_PACE_TIMING_TABLE,
    min: Float = -2f,
    max: Float = 2f,
    language: String = "en_us"
): String = readerOptsJsonWithDesign(speed, table, min, max, null, language = language)

// ---- the reader speaks with the voice's Studio design ----

/**
 * PACE RULE (v0.2.17 — defined here, in code, not just in prose):
 * the reader's speed slider OWNS "delivery.tempo". A saved design's
 * pace value is dropped before the merge; the speed dose (from the
 * slider, via the timing table) is the only delivery.tempo that ever
 * goes out. Every other Studio control applies as-is. The Studio LACI
 * applies only when the engine serves alignment heads for the reader's
 * language (en_us); the Studio decoder applies only when the FULL
 * DECODER pack is installed. Language always stays en_us.
 * SPEAKER MODE (v0.2.19): "speaker_mode" is always sent — the
 * design's mode when one applies (a Studio design in 'reference' mode
 * sends 'reference' with its controls already filtered to the
 * reference-usable set by toVoiceDesign), else "embedding". With
 * design == null this is byte-identical to readerOptsJson().
 */
fun readerOptsJsonWithDesign(
    speed: Float,
    table: List<Pair<Float, Float>> = DEFAULT_PACE_TIMING_TABLE,
    min: Float = -2f,
    max: Float = 2f,
    design: VoiceDesign? = null,
    laciLanguages: List<String> = emptyList(),
    fullMrAvailable: Boolean = false,
    /** Engine language id of the text being spoken (see [ZonoLanguages]); was fixed to en_us in the origin. */
    language: String = "en_us"
): String {
    val speedDose = paceDoseForSpeed(speed, table, min, max)
    val controls = LinkedHashMap<String, Double>()
    if (design != null) {
        for ((k, v) in controlDoses(design.controls, design.accents)) {
            // The saved pace never survives: the slider owns this key.
            if (k != "delivery.tempo") controls[k] = v
        }
    }
    // Same 4-decimal rounding as controlDoses (keeps the no-design
    // output identical to readerOptsJson).
    val roundedSpeed = Math.round(speedDose.toDouble() * 1e4) / 1e4
    if (roundedSpeed != 0.0) controls["delivery.tempo"] = roundedSpeed
    val sb = StringBuilder("{\"language\":\"").append(jsonEscape(language)).append('"')
    if (controls.isNotEmpty()) {
        sb.append(",\"controls\":{")
        sb.append(controls.entries.joinToString(",") { (k, v) -> "\"$k\":$v" })
        sb.append('}')
    }
    if (design != null && design.decoder == "full_mr" && fullMrAvailable) {
        sb.append(",\"decoder\":\"full_mr\"")
    }
    if (design != null && design.laci != "off" && laciLanguages.contains(language)) {
        sb.append(",\"laci\":true")
        if (design.laci == "whole") sb.append(",\"stream_decode\":\"whole\"")
    }
    // v0.2.19: always explicit — see the SPEAKER MODE RULE above.
    sb.append(",\"speaker_mode\":\"${design?.mode ?: "embedding"}\"")
    sb.append('}')
    return sb.toString()
}

/** Minimal JSON string escaping for the hand-built opts document. */
internal fun jsonEscape(raw: String): String {
    val sb = StringBuilder(raw.length + 8)
    for (c in raw) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * One voice's design: the 25 control values + accent mix +
 * LACI mode + decoder choice. Language is deliberately NOT stored
 * (the reader always uses en_us).
 * `mode` (v0.2.19): the speaker mode this design's controls were made
 * for — 'embedding' or 'reference'. A Studio design converted by
 * toVoiceDesign carries the Studio design's mode, so the reader sends
 * the matching speaker_mode.
 */
data class VoiceDesign(
    val controls: Map<String, Float> = emptyMap(),
    val accents: Map<String, Float> = emptyMap(),
    val laci: String = "off",
    val decoder: String = "keep2_plain",
    val mode: String = "embedding"
)
