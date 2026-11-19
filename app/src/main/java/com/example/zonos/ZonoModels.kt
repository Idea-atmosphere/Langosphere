package com.example.zonos

/**
 * One bundled voice of the engine (assets/zonos/voices.json). [tag] is the
 * voice's accent or style from Zyphra's own labels (`Cillian en Irish M 1`
 * → `irish`; `Thaddeus en announcervoice M 1` → `announcer`), [gender] is
 * `m` / `f`; both may be empty — the non-English voices carry only their
 * language and gender. See [ZonoVoiceTags] for the labels shown to the learner.
 */
data class ZonoVoice(
    val id: String,
    val name: String,
    val language: String,
    val tag: String = "",
    val gender: String = "",
)

/**
 * The words shown next to a voice's name so the right one is easy to pick
 * — «Cillian · Irish · male» / «Cillian · ایرلندی · مرد». Accents and
 * styles come from the pack's voice labels; unknown tags are shown as-is.
 */
object ZonoVoiceTags {

    private val tags: Map<String, Pair<String, String>> = mapOf(
        // accents (English voices)
        "irish" to ("Irish" to "ایرلندی"),
        "british" to ("British" to "بریتانیایی"),
        "american" to ("American" to "آمریکایی"),
        "scottish" to ("Scottish" to "اسکاتلندی"),
        "australian" to ("Australian" to "استرالیایی"),
        "indian" to ("Indian" to "هندی"),
        // styles
        "energetic" to ("energetic" to "پرانرژی"),
        "witch" to ("witch" to "جادوگر"),
        "anime" to ("anime" to "انیمه"),
        "announcer" to ("announcer" to "گوینده"),
        "asmr" to ("ASMR" to "ASMR"),
    )

    /** The accent / style label of [tag] in the UI language, or null for an empty tag. */
    fun tagLabel(tag: String, isFa: Boolean): String? {
        val key = tag.trim().lowercase()
        if (key.isEmpty()) return null
        val pair = tags[key] ?: return tag.trim()
        return if (isFa) pair.second else pair.first
    }

    /** «male» / «female» (or «مرد» / «زن»), null when the pack gives no gender. */
    fun genderLabel(gender: String, isFa: Boolean): String? = when (gender.trim().lowercase()) {
        "m", "male" -> if (isFa) "مرد" else "male"
        "f", "female" -> if (isFa) "زن" else "female"
        else -> null
    }

    /** The caption under the voice's name: accent/style and gender joined with « · », or null. */
    fun caption(voice: ZonoVoice, isFa: Boolean): String? {
        val parts = listOfNotNull(tagLabel(voice.tag, isFa), genderLabel(voice.gender, isFa))
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }
}

/**
 * [IDLE]: the engine page is up and the model pack is present, but the
 * ~900 MB model is loaded only on the first playback (so opening the app
 * never pays for it). [LOADING] covers both the page load and that model
 * load; the model load reports [EngineState.loaded] / [EngineState.total].
 */
enum class EngineStatus { NOT_LOADED, LOADING, IDLE, READY, ERROR, MISSING, NO_MODEL }

/**
 * Observable state of the hidden engine WebView. [error] is a technical
 * detail (English, from the engine or the interceptor); the UI maps the
 * [status] to a localized line and shows the detail underneath.
 */
data class EngineState(
    val status: EngineStatus = EngineStatus.NOT_LOADED,
    val loaded: Long = 0,
    val total: Long = 0,
    val phase: String = "",
    val gpuSupported: Boolean? = null,
    val error: String? = null,
    val voicesCount: Int = 0,
    /** The engine page finished loading at least once in this process. */
    val pageLoaded: Boolean = false,
    /** The page's module evaluated: window.__zonoGenerate exists (probed after page load). */
    val functionsReady: Boolean = false,
    val webViewVersion: String? = null,
    val lastConsoleError: String? = null,
    val lastPageError: String? = null,
    /** Outcome of the last /models/ intercept (null = served fine). */
    val lastInterceptorError: String? = null,
    /** Index of the sentence whose audio is audibly playing right now (null = generating or idle). */
    val playingIndex: Int? = null
)

/** Default voice of the engine (always present in voices.json). */
const val DEFAULT_VOICE_ID = "default_940eaff39a929da0"

/**
 * Transport state of the take that is playing in the engine page (from
 * its <audio> element): position / duration in milliseconds, whether it is
 * paused, and the playback rate in effect. Null when nothing is loaded.
 */
data class Playback(
    val index: Int,
    val positionMs: Long,
    val durationMs: Long,
    val paused: Boolean,
    val rate: Float,
)
