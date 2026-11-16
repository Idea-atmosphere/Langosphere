package com.example.mdict.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays Speex (.spx) audio the way BlueDict does: decode to PCM with the
 * pure-Java Speex decoder, then play the PCM with AudioTrack. Android's
 * MediaPlayer cannot decode Speex at all.
 */
object SpeexPlayer {

    @Volatile
    private var current: AudioTrack? = null

    /** Decodes Speex bytes and plays them. False when the bytes are not playable Speex. */
    fun play(spx: ByteArray): Boolean {
        stop()
        val pcm = SpeexDecoder.decode(spx) ?: return false
        val channelConfig = if (pcm.channels == 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }
        val minBuf = AudioTrack.getMinBufferSize(
            pcm.sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, pcm.data.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            return false
        }
        return try {
            track.write(pcm.data, 0, pcm.data.size)
            track.play()
            current = track
            // Release the track once playback finishes.
            val frames = pcm.data.size / (2 * pcm.channels)
            val millis = frames * 1000L / pcm.sampleRate + 200
            Thread {
                try {
                    Thread.sleep(millis)
                } catch (e: InterruptedException) {
                }
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                }
                if (current == track) current = null
            }.start()
            true
        } catch (e: Exception) {
            try {
                track.release()
            } catch (ignored: Exception) {
            }
            false
        }
    }

    fun stop() {
        current?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
            }
        }
        current = null
    }
}
