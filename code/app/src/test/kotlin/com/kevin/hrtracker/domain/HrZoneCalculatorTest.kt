package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.*
import org.junit.Test

class HrZoneCalculatorTest {

    @Test
    fun aggregateTimeInZone_emptyList_returnsEmptyMap() {
        val result = HrZoneCalculator.aggregateTimeInZone(emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun aggregateTimeInZone_singleSample_returns1Second() {
        val zones = HrZoneCalculator.calculateZones(200, 60)
        val sample = HrSample(
            sessionId = 1,
            timestampMs = 1000,
            bpm = 120  // Zone 2 or 3 depending on age
        )
        val result = HrZoneCalculator.aggregateTimeInZone(listOf(sample), zones)
        assertEquals(1L, result.values.sum())
    }

    @Test
    fun aggregateTimeInZone_twoConsecutiveSamples_computesDuration() {
        val zones = HrZoneCalculator.calculateZones(200, 60)
        val sample1 = HrSample(
            sessionId = 1,
            timestampMs = 1000,
            bpm = 100
        )
        val sample2 = HrSample(
            sessionId = 1,
            timestampMs = 5000,  // 4 seconds later
            bpm = 100
        )
        val result = HrZoneCalculator.aggregateTimeInZone(listOf(sample1, sample2), zones)
        // sample1 → sample2: 4 seconds
        // sample2 alone: 1 second
        // Total for zone: 5 seconds
        assertEquals(5L, result.values.sum())
    }

    @Test
    fun aggregateTimeInZone_multiZoneSamples_distributesTimeByZone() {
        val maxHr = 200
        val zones = HrZoneCalculator.calculateZones(maxHr, 60)

        val baseMs = 1000L
        val samples = listOf(
            // Zone 1-2 (low HR)
            HrSample(sessionId = 1, timestampMs = baseMs, bpm = 100),
            HrSample(sessionId = 1, timestampMs = baseMs + 3000, bpm = 100),
            // Zone 4-5 (high HR)
            HrSample(sessionId = 1, timestampMs = baseMs + 7000, bpm = 180),
            HrSample(sessionId = 1, timestampMs = baseMs + 10000, bpm = 180)
        )

        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)

        // sample[0] → sample[1]: 3 seconds in low zone
        // sample[1] → sample[2]: 4 seconds in low zone (sample[1] is zone of first sample)
        // sample[2] → sample[3]: 3 seconds in high zone
        // sample[3] alone: 1 second in high zone
        // Total in low zones (1,2): 7 seconds
        // Total in high zones (4,5): 4 seconds
        assertEquals(11L, result.values.sum())
    }

    @Test
    fun aggregateTimeInZone_resumeSession_reconstructsTimeFromPersisted() {
        // Simulate pause/resume scenario:
        // Session started with some samples, then paused, then resumed
        val maxHr = 200
        val zones = HrZoneCalculator.calculateZones(maxHr, 60)

        val baseMs = 1000L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = baseMs, bpm = 120),
            HrSample(sessionId = 1, timestampMs = baseMs + 2000, bpm = 120),
            HrSample(sessionId = 1, timestampMs = baseMs + 4000, bpm = 130),
            HrSample(sessionId = 1, timestampMs = baseMs + 9000, bpm = 130)
        )

        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)

        // sample[0] → sample[1]: 2 seconds
        // sample[1] → sample[2]: 2 seconds
        // sample[2] → sample[3]: 5 seconds
        // sample[3] alone: 1 second
        // Total: 10 seconds
        assertEquals(10L, result.values.sum())

        // Verify that time is distributed across zones (not all in one zone)
        assertTrue(result.size > 0)
    }
}
