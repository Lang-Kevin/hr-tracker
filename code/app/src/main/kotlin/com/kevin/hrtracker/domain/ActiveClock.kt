package com.kevin.hrtracker.domain

/**
 * Tracks elapsed active time across pause/resume cycles.
 * Persists across ViewModel recreation.
 *
 * @param startMs wall-clock time when the session started (not the "active" time)
 */
class ActiveClock(private val startMs: Long) {
    private var pauseStartMs: Long? = null
    private var totalPausedMs: Long = 0L

    /** True iff currently paused. */
    val isPaused: Boolean
        get() = synchronized(this) { pauseStartMs != null }

    /**
     * Pause the clock at [nowMs]. Idempotent: calling pause while already paused has no effect.
     */
    @Synchronized
    fun pause(nowMs: Long) {
        if (pauseStartMs == null) {
            pauseStartMs = nowMs
        }
    }

    /**
     * Resume the clock at [nowMs]. Idempotent: calling resume while running has no effect.
     */
    @Synchronized
    fun resume(nowMs: Long) {
        pauseStartMs?.let { pauseStart ->
            totalPausedMs += nowMs - pauseStart
            pauseStartMs = null
        }
    }

    /**
     * Compute the active (non-paused) elapsed time in milliseconds at the given wall-clock time [nowMs].
     * Never returns a negative value.
     *
     * The elapsed time excludes the current ongoing pause (if paused).
     */
    @Synchronized
    fun activeElapsedMs(nowMs: Long): Long {
        val wallClockElapsed = (nowMs - startMs).coerceAtLeast(0L)
        val p = pauseStartMs
        val totalPause = if (p != null) {
            // Currently paused: add the ongoing pause to total
            totalPausedMs + (nowMs - p)
        } else {
            totalPausedMs
        }
        return (wallClockElapsed - totalPause).coerceAtLeast(0L)
    }
}
