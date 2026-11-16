package com.example.mdict.audio

import android.media.MediaPlayer
import java.io.File

/**
 * Plays a pronunciation resource referenced from a dictionary entry
 * (`sound://word.spx`, `<a href="word.mp3">`, …):
 *
 *  - Speex (`.spx`, the format most MDict sound packs use) through the
 *    pure-Java decoder + AudioTrack, because Android's MediaPlayer cannot
 *    decode Speex at all;
 *  - everything else (mp3/wav/ogg/…) through MediaPlayer from a temp file in
 *    [cacheDir].
 *
 * Call [play] off the main thread (it decodes and prepares synchronously).
 */
class MdictSoundPlayer(private val cacheDir: File) {

    enum class Result { PLAYING, NOT_FOUND, FAILED }

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Resolves [path] through [resource] and plays it. Like BlueDict, a link
     * with no extension is retried as `path.spx`; the bytes are also sniffed,
     * because an MDD key may carry the extension even when the link does not.
     */
    fun play(path: String, resource: (String) -> ByteArray?): Result {
        val direct = resource(path)
        val resolved: Pair<String, ByteArray>? = when {
            direct != null -> path to direct
            !path.substringAfterLast('/').contains('.') -> {
                val spxPath = "$path.spx"
                resource(spxPath)?.let { spxPath to it }
            }
            else -> null
        }
        if (resolved == null) return Result.NOT_FOUND
        val (effectivePath, bytes) = resolved
        val lower = effectivePath.lowercase()
        if (lower.endsWith(".spx") || SpeexDecoder.isSpeex(bytes)) {
            releaseMediaPlayer()
            return if (SpeexPlayer.play(bytes)) Result.PLAYING else Result.FAILED
        }
        SpeexPlayer.stop()
        // Keep the real extension so MediaPlayer sniffs the right format.
        val ext = when {
            lower.endsWith(".wav") -> ".wav"
            lower.endsWith(".ogg") || lower.endsWith(".oga") -> ".ogg"
            lower.endsWith(".flac") -> ".flac"
            lower.endsWith(".m4a") -> ".m4a"
            lower.endsWith(".aac") -> ".aac"
            lower.endsWith(".opus") -> ".opus"
            lower.endsWith(".mid") || lower.endsWith(".midi") -> ".mid"
            lower.endsWith(".amr") -> ".amr"
            else -> ".mp3"
        }
        val tmp = File(cacheDir, "mdict_sound$ext")
        val player = try {
            tmp.writeBytes(bytes)
            MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                prepare()
                setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            return Result.FAILED
        }
        releaseMediaPlayer()
        mediaPlayer = player
        return try {
            player.start()
            Result.PLAYING
        } catch (e: Exception) {
            releaseMediaPlayer()
            Result.FAILED
        }
    }

    fun stop() {
        SpeexPlayer.stop()
        releaseMediaPlayer()
    }

    fun release() = stop()

    private fun releaseMediaPlayer() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        try {
            mp.release()
        } catch (e: Exception) {
        }
    }
}
