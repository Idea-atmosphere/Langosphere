package com.example.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.AppStrings
import java.util.Locale

/**
 * Pronunciation playback.
 *
 * The app taught vocabulary with IPA strings only, which is useless to most
 * learners — hearing the word is the whole point. This wraps the platform
 * text-to-speech engine so any screen can speak a word or a sentence with
 * one call, including a slow mode for difficult words.
 *
 * The engine is created lazily and shared, because building one per screen
 * is slow and each instance holds a service connection.
 *
 * When the phone has no usable engine (no Google Text-to-speech, or no
 * English voice data — common on phones without Google services) the tap
 * used to do nothing at all. Now it says so once, and points at the
 * on-device Zonos voice button that sits next to every speaker.
 */
object TtsSpeaker {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var languageMissing = false
    private var pending: Pair<String, Boolean>? = null
    private var appContext: Context? = null
    private var lastWarnMs = 0L
    private val speakingState = mutableStateOf(false)

    val isSpeaking: Boolean
        get() = speakingState.value

    fun ensureInit(context: Context) {
        if (engine != null) return
        val app = context.applicationContext
        appContext = app
        try {
            engine = TextToSpeech(app) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    try {
                        val lang = engine?.setLanguage(Locale.US)
                        languageMissing = lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                speakingState.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                speakingState.value = false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                speakingState.value = false
                                warnUnavailable()
                            }
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    // A tap that arrived before the engine was ready is not
                    // dropped; it plays as soon as initialisation finishes.
                    val queued = pending
                    pending = null
                    if (queued != null) speakNow(queued.first, queued.second)
                } else {
                    // No engine on this phone: say so, and let the next tap
                    // try again (the learner may install one meanwhile).
                    pending = null
                    try { engine?.shutdown() } catch (_: Exception) { }
                    engine = null
                    warnUnavailable()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            engine = null
            warnUnavailable()
        }
    }

    fun speak(context: Context, text: String, slow: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureInit(context)
        if (engine == null) return // init failed synchronously; already warned
        if (!ready) {
            pending = clean to slow
            return
        }
        speakNow(clean, slow)
    }

    private fun speakNow(text: String, slow: Boolean) {
        try {
            engine?.setSpeechRate(if (slow) 0.6f else 0.95f)
            val result = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "langosphere")
            if (result != TextToSpeech.SUCCESS || languageMissing) warnUnavailable()
        } catch (e: Exception) {
            e.printStackTrace()
            speakingState.value = false
            warnUnavailable()
        }
    }

    /** One toast per few seconds, in the app's UI language. */
    private fun warnUnavailable() {
        val app = appContext ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastWarnMs < 5000L) return
        lastWarnMs = now
        val code = try {
            app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("app_language", null)
        } catch (_: Exception) {
            null
        }
        val message = try {
            AppStrings(AppLanguage.fromCode(code), app).ttsSystemUnavailable
        } catch (_: Exception) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            try { Toast.makeText(app, message, Toast.LENGTH_LONG).show() } catch (_: Exception) { }
        }
    }

    fun stop() {
        try {
            engine?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speakingState.value = false
    }

    fun shutdown() {
        try {
            engine?.stop()
            engine?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        engine = null
        ready = false
        languageMissing = false
        pending = null
        speakingState.value = false
    }
}
