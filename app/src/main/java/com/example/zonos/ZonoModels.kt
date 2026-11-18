package com.example.zonos

/** One bundled voice of the engine (assets/zonos/voices.json). */
data class ZonoVoice(val id: String, val name: String, val language: String)

enum class EngineStatus { NOT_LOADED, LOADING, READY, ERROR, MISSING, NO_MODEL }

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
    val webViewVersion: String? = null,
    val lastConsoleError: String? = null,
    val lastPageError: String? = null,
    /** Outcome of the last /models/ intercept (null = served fine). */
    val lastInterceptorError: String? = null
)

/** Default voice of the engine (always present in voices.json). */
const val DEFAULT_VOICE_ID = "default_940eaff39a929da0"
