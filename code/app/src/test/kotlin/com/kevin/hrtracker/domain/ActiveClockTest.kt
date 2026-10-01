package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveClockTest {

    @Test
    fun `no pauses - elapsed equals wall clock time`() {
        val clock = ActiveClock(startMs = 1000L)
        val elapsed = clock.activeElapsedMs(nowMs = 5000L)
        assertEquals(4000L, elapsed)
    }

    @Test
    fun `pause freezes elapsed time`() {
        val clock = ActiveClock(startMs = 1000L)
        clock.pause(nowMs = 3000L)
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 3000L))
        // Time passes but elapsed stays the same
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 5000L))
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 10000L))
    }

    @Test
    fun `resume resumes counting`() {
        val clock = ActiveClock(startMs = 1000L)
        clock.pause(nowMs = 3000L)  // paused after 2s
        clock.resume(nowMs = 5000L) // resume after 2s of pause
        // elapsed = 2s (before pause) + (7000 - 5000) = 4s
        assertEquals(4000L, clock.activeElapsedMs(nowMs = 7000L))
    }

    @Test
    fun `double pause is idempotent`() {
        val clock = ActiveClock(startMs = 1000L)
        clock.pause(nowMs = 3000L)
        clock.pause(nowMs = 4000L) // second pause while already paused
        // Should still reflect 2s elapsed
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 10000L))
    }

    @Test
    fun `double resume is idempotent`() {
        val clock = ActiveClock(startMs = 1000L)
        clock.pause(nowMs = 3000L)
        clock.resume(nowMs = 5000L)
        clock.resume(nowMs = 6000L) // second resume while already running
        // elapsed = 2s + (8000 - 5000) = 5s
        assertEquals(5000L, clock.activeElapsedMs(nowMs = 8000L))
    }

    @Test
    fun `pause during ongoing pause - only one pause accumulated`() {
        val clock = ActiveClock(startMs = 1000L)
        clock.pause(nowMs = 3000L)   // pause after 2s active
        clock.pause(nowMs = 5000L)   // pause again (while already paused)
        clock.resume(nowMs = 10000L) // resume after 7s total pause
        // elapsed = 2s + (11000 - 10000) = 3s
        assertEquals(3000L, clock.activeElapsedMs(nowMs = 11000L))
    }

    @Test
    fun `isPaused reflects pause state`() {
        val clock = ActiveClock(startMs = 1000L)
        assertFalse(clock.isPaused)
        clock.pause(nowMs = 3000L)
        assertTrue(clock.isPaused)
        clock.resume(nowMs = 5000L)
        assertFalse(clock.isPaused)
    }

    @Test
    fun `overlapping pauses handled correctly`() {
        // Simulate: pause for user, pause for auto (idempotent), resume user, pause auto, resume auto
        val clock = ActiveClock(startMs = 1000L)
        clock.pause(nowMs = 3000L)   // user pause
        clock.pause(nowMs = 3000L)   // auto pause (idempotent)
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 3000L))
        clock.resume(nowMs = 5000L)  // user resume (paused 2s)
        clock.pause(nowMs = 6000L)   // auto pause again
        clock.resume(nowMs = 8000L)  // auto resume (paused 2s)
        // Timeline: 1000-3000 (2s active) + pause 2s + 5000-6000 (1s active) + pause 2s = 3s active
        assertEquals(3000L, clock.activeElapsedMs(nowMs = 8000L))
    }

    @Test
    fun `elapsed never negative`() {
        val clock = ActiveClock(startMs = 5000L)
        // Even if we somehow query before start, should be 0
        assertEquals(0L, clock.activeElapsedMs(nowMs = 3000L))
        assertEquals(0L, clock.activeElapsedMs(nowMs = 5000L))
    }

    @Test
    fun `multiple pause-resume cycles`() {
        val clock = ActiveClock(startMs = 0L)
        // Cycle 1: 0-2s active
        clock.pause(nowMs = 2000L)
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 2000L))
        clock.resume(nowMs = 5000L) // 3s pause
        // Cycle 2: 5-8s active = 3s more active (total 5s)
        clock.pause(nowMs = 8000L)
        assertEquals(5000L, clock.activeElapsedMs(nowMs = 8000L))
        clock.resume(nowMs = 12000L) // 4s pause
        // Cycle 3: 12-13s active = 1s more active (total 6s)
        clock.pause(nowMs = 13000L)
        assertEquals(6000L, clock.activeElapsedMs(nowMs = 13000L))
        clock.resume(nowMs = 15000L) // 2s pause
        assertEquals(6000L, clock.activeElapsedMs(nowMs = 15000L))
    }

    @Test
    fun `pause, elapsed query, and resume all at same ms - no elapsed change`() {
        val clock = ActiveClock(startMs = 1000L)
        // Pause at 3000ms: 2s active
        clock.pause(nowMs = 3000L)
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 3000L))
        // Immediately resume at same 3000ms (0s pause duration)
        clock.resume(nowMs = 3000L)
        // Still 2s active (pause duration was 0)
        assertEquals(2000L, clock.activeElapsedMs(nowMs = 3000L))
        // And continues normally after
        assertEquals(3000L, clock.activeElapsedMs(nowMs = 4000L))
    }

    @Test
    fun `resume with nowMs before pauseStart - elapsed never negative`() {
        val clock = ActiveClock(startMs = 1000L)
        // Pause at 5000ms: 4s active
        clock.pause(nowMs = 5000L)
        assertEquals(4000L, clock.activeElapsedMs(nowMs = 5000L))
        // Resume at 3000ms (before pauseStart); elapsed should clamp to 0 and not go negative
        clock.resume(nowMs = 3000L)
        // The implementation subtracts (3000 - 5000) = -2000, then coerceAtLeast(0L) ensures it's 0
        // Total pause accumulated = max(0, 5000 - 3000) = 2000 OR 0 (depending on impl semantics)
        // Behavior: when resume is before pause, we treat it as a no-op, just clearing the pause flag
        // The resulting elapsed should never be negative
        val elapsed = clock.activeElapsedMs(nowMs = 6000L)
        assertTrue("Elapsed should never be negative", elapsed >= 0L)
    }
}
