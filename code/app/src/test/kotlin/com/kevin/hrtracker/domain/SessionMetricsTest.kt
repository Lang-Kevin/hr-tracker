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

    @Test
    fun `hrv session discards stabilisation minute`() {
        val samples = (0 until 300).map { t ->
            val rr = if (t % 2 == 0) 1000 else if (t < 60) 1100 else 1020
            HrSample(sessionId = 1, timestampMs = t * 1_000L, bpm = 60, rrIntervalsMs = rr.toString())
        }
        val hrv = SessionMetrics.compute(samples, maxHr = 190, restingHr = 60, isHrvMeasurement = true)
        assertEquals(20, hrv.rmssd)
        assertEquals(240, hrv.rmssdValidBeats)
        assertEquals(0.0, hrv.rmssdArtefactPct!!, 1e-9)
        assertEquals(48, SessionMetrics.compute(samples, maxHr = 190, restingHr = 60).rmssd)
    }
}
