package com.example.logic

/**
 * HTTP identity the player uses when it fetches online video chunks.
 *
 * googlevideo.com — and several Piped/Invidious proxies in front of it —
 * answer HTTP 403 to requests that look like a bot: ExoPlayer's default
 * `ExoPlayerLib/…` user agent and no Referer is exactly that. The player's
 * HTTP data source therefore presents itself as a regular desktop Chrome
 * tab that is playing a video on youtube.com.
 *
 * API calls (search, channel lists, `/streams`, `/api/v1/videos`) keep the
 * app's honest `Langosphere/…` user agent (see OnlineVideoRepository); this
 * identity is only for the media bytes themselves.
 */
object OnlinePlaybackHeaders {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 15_000

    /** Sent with every media request (User-Agent is set separately on the factory). */
    val requestProperties: Map<String, String> = mapOf(
        "Referer" to "https://www.youtube.com/",
        "Origin" to "https://www.youtube.com",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9"
    )
}
