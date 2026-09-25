package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMetricsTest {

    @Test
    fun `metrics ignore pause`() {
        // 30 min @150, 10 min Pause, 30 min @150
        val first = (0..1800).map { HrSample(sessionId = 1, timestampMs = it * 1_000L, bpm = 150) }
        val offset = first.last().timestampMs + 10 * 60_000L
        val second = (0..1800).map { HrSample(sessionId = 1, timestampMs = offset + it * 1_000L, bpm = 150) }
        val m = SessionMetrics.compute(first + second, maxHr = 190, restingHr = 60)
        assertEquals(60 * 60_000L, m.activeMs)
        assertEquals(150, m.avgBpm)
        assertEquals(156, m.trimp)
    }
}
