package com.example.logic

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.mutableStateOf
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
 */
object TtsSpeaker {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Boolean>? = null
    private val speakingState = mutableStateOf(false)

    val isSpeaking: Boolean
        get() = speakingState.value

    fun ensureInit(context: Context) {
        if (engine != null) return
        val app = context.applicationContext
        try {
            engine = TextToSpeech(app) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    try {
                        engine?.setLanguage(Locale.US)
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                speakingState.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                speakingState.value = false
                            }

                            override fun onError(utteranceId: String?) {
                                speakingState.value = false
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
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(context: Context, text: String, slow: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureInit(context)
        if (!ready) {
            pending = clean to slow
            return
        }
        speakNow(clean, slow)
    }

    private fun speakNow(text: String, slow: Boolean) {
        try {
            engine?.setSpeechRate(if (slow) 0.6f else 0.95f)
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "langosphere")
        } catch (e: Exception) {
            e.printStackTrace()
            speakingState.value = false
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
        pending = null
        speakingState.value = false
    }
}
