package com.example.zonos

/**
 * What the UI shows while the ~900 MB model is being loaded into the
 * engine on the first playback of a session.
 *
 * @property fraction 0..1 of the bytes loaded, or null before the first
 *   progress report.
 * @property elapsedMs time since the load started.
 * @property etaMs estimated time left, or null when nothing can be said yet.
 * @property etaFromHistory true when [etaMs] comes from the previous load's
 *   duration (no byte progress yet) rather than from the measured rate.
 * @property finalizing all bytes are in but the engine is not READY yet
 *   (shader compilation / GPU upload): the bar is full, the ETA is unknown.
 */
data class LoadProgress(
    val fraction: Float?,
    val loaded: Long,
    val total: Long,
    val phase: String,
    val elapsedMs: Long,
    val etaMs: Long?,
    val etaFromHistory: Boolean,
    val finalizing: Boolean,
)

/**
 * Turns the engine's byte progress into a percentage and a time estimate.
 *
 * Rate-based ETA: the average rate since the first progress sample (the
 * disk read is steady, so the average beats a noisy instantaneous rate).
 * Before any bytes are counted, the previous load's total duration (if
 * known) is used, so even the first seconds show "about 1 min 40 s".
 * Pure Kotlin: the clock is passed in, which keeps it unit-testable.
 */
class LoadEstimator(private val expectedTotalMs: Long? = null) {
    private var startMs = -1L
    private var firstSampleMs = -1L
    private var firstLoaded = 0L
    private var lastLoaded = 0L
    private var lastTotal = 0L
    private var lastPhase = ""
    private var lastSampleMs = -1L

    /** True once [start] (or a sample) happened and [reset] did not follow. */
    val running: Boolean get() = startMs >= 0

    /** Mark the moment the load was requested (the first sample may come later). */
    fun start(nowMs: Long) {
        if (startMs < 0) startMs = nowMs
    }

    /** One progress report from the engine. */
    fun sample(loaded: Long, total: Long, phase: String, nowMs: Long) {
        if (startMs < 0) startMs = nowMs
        if (firstSampleMs < 0 || loaded < lastLoaded) {
            // First report, or a restart (a new phase counting from zero).
            firstSampleMs = nowMs
            firstLoaded = loaded
        }
        lastLoaded = loaded
        lastTotal = total
        lastPhase = phase
        lastSampleMs = nowMs
    }

    /** How long the whole load took, for the next session's estimate. */
    fun elapsed(nowMs: Long): Long = if (startMs < 0) 0L else nowMs - startMs

    fun reset() {
        startMs = -1L
        firstSampleMs = -1L
        firstLoaded = 0L
        lastLoaded = 0L
        lastTotal = 0L
        lastPhase = ""
        lastSampleMs = -1L
    }

    fun snapshot(nowMs: Long): LoadProgress? {
        if (startMs < 0) return null
        val elapsed = nowMs - startMs
        val fraction = if (lastTotal > 0) (lastLoaded.toDouble() / lastTotal).coerceIn(0.0, 1.0).toFloat() else null
        val complete = lastTotal > 0 && lastLoaded >= lastTotal
        var eta: Long? = null
        var fromHistory = false
        if (!complete && lastTotal > 0 && lastLoaded > firstLoaded && lastSampleMs - firstSampleMs >= MIN_RATE_WINDOW_MS) {
            val rate = (lastLoaded - firstLoaded).toDouble() / (lastSampleMs - firstSampleMs) // bytes per ms
            if (rate > 0) {
                val remainingBytes = lastTotal - lastLoaded
                // Measured from the last sample, minus the time already gone since.
                eta = ((remainingBytes / rate) - (nowMs - lastSampleMs)).toLong().coerceAtLeast(0L)
            }
        }
        if (eta == null && !complete && expectedTotalMs != null && expectedTotalMs > 0) {
            eta = (expectedTotalMs - elapsed).coerceAtLeast(0L)
            fromHistory = true
        }
        return LoadProgress(
            fraction = fraction,
            loaded = lastLoaded,
            total = lastTotal,
            phase = lastPhase,
            elapsedMs = elapsed,
            etaMs = eta,
            etaFromHistory = fromHistory,
            finalizing = complete,
        )
    }

    companion object {
        /** A rate needs a little history before it means anything. */
        const val MIN_RATE_WINDOW_MS = 1500L
    }
}

/** "1:40" / "0:07" style duration for the loading banner. */
fun formatEta(ms: Long): String {
    val totalSec = (ms + 500) / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:" + sec.toString().padStart(2, '0')
}
