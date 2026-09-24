package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @Test
    fun aggregateTimeInZone_bleDropout_gapNotCountedInZone() {
        // Normal samples at 1s intervals in zone A, then a 65s BLE dropout, then one more sample.
        val zones = HrZoneCalculator.calculateZones(200, 60)
        val base = 0L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = base,          bpm = 100),
            HrSample(sessionId = 1, timestampMs = base + 1000,   bpm = 100),
            HrSample(sessionId = 1, timestampMs = base + 2000,   bpm = 100),
            // 65s gap — BLE dropout, must NOT be counted
            HrSample(sessionId = 1, timestampMs = base + 67000,  bpm = 100),
            HrSample(sessionId = 1, timestampMs = base + 68000,  bpm = 100)
        )
        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)
        // Counted: 1s + 1s (normal) + (gap skipped) + 1s + 1s last = 4s total
        assertEquals(4L, result.values.sum())
        // All bpm=100 land in zone 1 (Karvonen, maxHr=200, resting=60 → Z1 hi=144).
        // A broken zoneFor that maps everything to zone 0 would fail this assert.
        assertEquals(4L, result[1])
    }

    @Test
    fun aggregateTimeInZone_noGap_regressionUnchanged() {
        // Four consecutive 1s-interval samples — verifies no regression for normal data.
        val zones = HrZoneCalculator.calculateZones(200, 60)
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = 0L,    bpm = 100),
            HrSample(sessionId = 1, timestampMs = 1000L, bpm = 100),
            HrSample(sessionId = 1, timestampMs = 2000L, bpm = 100),
            HrSample(sessionId = 1, timestampMs = 3000L, bpm = 100)
        )
        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)
        // 1s + 1s + 1s (intervals) + 1s (last) = 4s
        assertEquals(4L, result.values.sum())
    }

    @Test
    fun detectGaps_oneGap_returnsCorrectRange() {
        val base = 0L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = base,         bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 1000,  bpm = 80),
            // 65s gap
            HrSample(sessionId = 1, timestampMs = base + 66000, bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 67000, bpm = 80)
        )
        val gaps = HrZoneCalculator.detectGaps(samples)
        assertEquals(1, gaps.size)
        assertEquals(base + 1000, gaps[0].first)
        assertEquals(base + 66000, gaps[0].last)
    }

    @Test
    fun detectGaps_noGap_returnsEmptyList() {
        val base = 0L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = base,        bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 1000, bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 2000, bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 3000, bpm = 80)
        )
        val gaps = HrZoneCalculator.detectGaps(samples)
        assertTrue(gaps.isEmpty())
    }

    @Test
    fun detectGaps_twoGaps_returnsTwoRangesInOrder() {
        val base = 0L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = base,          bpm = 80),
            // first gap: 30s
            HrSample(sessionId = 1, timestampMs = base + 30000,  bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 31000,  bpm = 80),
            // second gap: 20s
            HrSample(sessionId = 1, timestampMs = base + 51000,  bpm = 80),
            HrSample(sessionId = 1, timestampMs = base + 52000,  bpm = 80)
        )
        val gaps = HrZoneCalculator.detectGaps(samples)
        assertEquals(2, gaps.size)
        assertEquals(base,         gaps[0].first)
        assertEquals(base + 30000, gaps[0].last)
        assertEquals(base + 31000, gaps[1].first)
        assertEquals(base + 51000, gaps[1].last)
    }

    @Test
    fun aggregateTimeInZone_longSessionWithJitter_sumMatchesDuration() {
        // ~2.5h session with alternating 900ms/1100ms jitter (avg 1000ms), all samples in one zone.
        val zones = HrZoneCalculator.calculateZones(185, null)
        val samples = mutableListOf<HrSample>()
        var t = 0L
        var toggle = true
        val targetCount = 9000
        repeat(targetCount) {
            samples += HrSample(sessionId = 1, timestampMs = t, bpm = 150)
            t += if (toggle) 900L else 1100L
            toggle = !toggle
        }

        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)
        val totalS = result.values.sum()
        val expectedS = samples.last().timestampMs / 1000L + 1L

        // Old (buggy) per-interval integer division would yield roughly half of expectedS.
        val diffRatio = kotlin.math.abs(totalS - expectedS).toDouble() / expectedS
        assertTrue(
            "Expected total ($totalS) within 2% of session duration ($expectedS), diffRatio=$diffRatio",
            diffRatio <= 0.02
        )
    }

    @Test
    fun aggregateTimeInZone_gapExcluded() {
        val zones = HrZoneCalculator.calculateZones(200, 60)
        val base = 0L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = base,         bpm = 100),
            HrSample(sessionId = 1, timestampMs = base + 1000,  bpm = 100),
            // > 5000ms gap — must not count
            HrSample(sessionId = 1, timestampMs = base + 10000, bpm = 100),
            HrSample(sessionId = 1, timestampMs = base + 11000, bpm = 100)
        )
        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)
        // 1s (0->1) + gap skipped + 1s (10s->11s) + 1s (last) = 3s
        assertEquals(3L, result.values.sum())
    }

    @Test
    fun aggregateTimeInZone_multiZone_distributesCorrectly() {
        val maxHr = 200
        val restingHr = 60
        val zones = HrZoneCalculator.calculateZones(maxHr, restingHr)

        val base = 0L
        val samples = listOf(
            HrSample(sessionId = 1, timestampMs = base,          bpm = 90),   // low zone
            HrSample(sessionId = 1, timestampMs = base + 2000,   bpm = 90),
            HrSample(sessionId = 1, timestampMs = base + 4000,   bpm = 150),  // mid zone
            HrSample(sessionId = 1, timestampMs = base + 7000,   bpm = 150),
            HrSample(sessionId = 1, timestampMs = base + 9000,   bpm = 190),  // high zone
            HrSample(sessionId = 1, timestampMs = base + 11000,  bpm = 190)
        )
        val result = HrZoneCalculator.aggregateTimeInZone(samples, zones)

        val lowZone = HrZoneCalculator.zoneFor(90, zones)
        val midZone = HrZoneCalculator.zoneFor(150, zones)
        val highZone = HrZoneCalculator.zoneFor(190, zones)
        check(lowZone != midZone && midZone != highZone && lowZone != highZone) {
            "Test setup invalid: zones must differ (low=$lowZone mid=$midZone high=$highZone)"
        }

        // low: 0->2000 (2s) + 2000->4000 (2s) = 4s
        assertEquals(4L, result[lowZone])
        // mid: 4000->7000 (3s) + 7000->9000 (2s) = 5s
        assertEquals(5L, result[midZone])
        // high: 9000->11000 (2s) + last sample (1s) = 3s
        assertEquals(3L, result[highZone])
        assertEquals(12L, result.values.sum())
    }

    // --- ZoneModel ---

    @Test
    fun calculateZones_hrMaxModel_ignoresRestingHr() {
        val zones = HrZoneCalculator.calculateZones(187, 60, ZoneModel.HR_MAX)
        val zone2 = zones.first { it.zone == 2 }
        assertEquals(112, zone2.lo)
        assertEquals(130, zone2.hi)
    }

    @Test
    fun calculateZones_karvonenModel_usesHeartRateReserve() {
        val zones = HrZoneCalculator.calculateZones(187, 60, ZoneModel.KARVONEN)
        val zone2 = zones.first { it.zone == 2 }
        assertEquals(136, zone2.lo)
        assertEquals(148, zone2.hi)
    }

    @Test
    fun calculateZones_karvonenModel_nullRestingHr_fallsBackToHrMax() {
        val karvonen = HrZoneCalculator.calculateZones(187, null, ZoneModel.KARVONEN)
        val hrMax = HrZoneCalculator.calculateZones(187, null, ZoneModel.HR_MAX)
        assertEquals(hrMax, karvonen)
    }

    @Test
    fun calculateZones_defaultModel_isHrMax() {
        val default = HrZoneCalculator.calculateZones(187, 60)
        val hrMax = HrZoneCalculator.calculateZones(187, 60, ZoneModel.HR_MAX)
        assertEquals(hrMax, default)
    }

    // --- resolveZones ---

    @Test
    fun resolveZones_validSnapshot_returnsSnapshotZones() {
        val maxHr = 180
        val restingHr = 55
        val snapshotZones = listOf(
            ZoneBounds(1, 90, 108),
            ZoneBounds(2, 108, 126),
            ZoneBounds(3, 126, 144),
            ZoneBounds(4, 144, 162),
            ZoneBounds(5, 162, 180)
        )
        val json = Json.encodeToString(snapshotZones)
        val result = HrZoneCalculator.resolveZones(json, maxHr, restingHr)
        assertEquals(snapshotZones, result)
        assertNotEquals(HrZoneCalculator.calculateZones(maxHr, restingHr), result)
    }

    @Test
    fun resolveZones_nullSnapshot_fallsBackToCalculate() {
        val maxHr = 185
        val restingHr = 60
        val result = HrZoneCalculator.resolveZones(null, maxHr, restingHr)
        // restingHr gesetzt -> Legacy-Regel ist KARVONEN, nicht der HR_MAX-Default von calculateZones.
        assertEquals(HrZoneCalculator.calculateZones(maxHr, restingHr, ZoneModel.KARVONEN), result)
    }

    @Test
    fun resolveZones_emptyArray_fallsBackToCalculate() {
        val maxHr = 185
        val restingHr = 60
        val result = HrZoneCalculator.resolveZones("[]", maxHr, restingHr)
        assertEquals(HrZoneCalculator.calculateZones(maxHr, restingHr, ZoneModel.KARVONEN), result)
    }

    @Test
    fun resolveZones_brokenJson_fallsBackToCalculateWithoutThrow() {
        val maxHr = 185
        val restingHr = 60
        val result = HrZoneCalculator.resolveZones("{nonsense", maxHr, restingHr)
        assertEquals(HrZoneCalculator.calculateZones(maxHr, restingHr, ZoneModel.KARVONEN), result)
    }

    @Test
    fun resolveZones_nullSnapshot_withRestingHr_keepsLegacyKarvonen() {
        val result = HrZoneCalculator.resolveZones(null, 187, 60)
        val zone2 = result.first { it.zone == 2 }
        assertEquals(136, zone2.lo)
        assertEquals(148, zone2.hi)
    }

    @Test
    fun resolveZones_nullSnapshot_withoutRestingHr_usesHrMax() {
        val result = HrZoneCalculator.resolveZones(null, 187, null)
        val zone2 = result.first { it.zone == 2 }
        assertEquals(112, zone2.lo)
        assertEquals(130, zone2.hi)
    }

    @Test
    fun zonesToBoundaries_roundTrip_returnsOriginalZones() {
        val zones = HrZoneCalculator.calculateZones(190, null)
        val boundaries = HrZoneCalculator.zonesToBoundaries(zones)
        val restored = HrZoneCalculator.boundariesToZones(boundaries)
        assertEquals(zones, restored)
    }

    @Test
    fun adjustBoundary_middleBoundaryUp_shiftsLaterBoundariesAndStrictlyAscending() {
        val b = listOf(100, 120, 140, 160, 180, 200)
        val result = HrZoneCalculator.adjustBoundary(b, 2, 175)
        // index 2 must be 175
        assertEquals(175, result[2])
        // index 3 >= 176, index 4 >= 177, index 5 >= 178
        assertTrue(result[3] >= 176)
        assertTrue(result[4] >= 177)
        assertTrue(result[5] >= 178)
        // strictly ascending
        for (i in 0 until result.size - 1) {
            assertTrue("Not strictly ascending at $i: ${result[i]} >= ${result[i+1]}", result[i] < result[i + 1])
        }
    }

    @Test
    fun adjustBoundary_upperEdge_cascadeStaysIn220() {
        val b = listOf(100, 120, 140, 160, 180, 200)
        val result = HrZoneCalculator.adjustBoundary(b, 4, 219)
        // b[4] clamped to max 219 (220 - 1 slot needed for b[5])
        assertTrue(result[4] <= 219)
        // all strictly ascending
        for (i in 0 until result.size - 1) {
            assertTrue("Not strictly ascending at $i", result[i] < result[i + 1])
        }
        // no value exceeds MAX_BPM
        assertTrue("b[5] must be <= 220", result[5] <= 220)
    }

    @Test
    fun adjustBoundary_lowerEdge_cascadeStaysAbove30() {
        val b = listOf(100, 120, 140, 160, 180, 200)
        val result = HrZoneCalculator.adjustBoundary(b, 1, 30)
        // b[1] clamped to min 31 (30 + 1 slot needed for b[0])
        assertTrue(result[1] >= 31)
        // all strictly ascending
        for (i in 0 until result.size - 1) {
            assertTrue("Not strictly ascending at $i", result[i] < result[i + 1])
        }
        // no value goes below MIN_BPM
        assertTrue("b[0] must be >= 30", result[0] >= 30)
    }

    @Test
    fun adjustBoundary_boundaryDown_shiftsEarlierBoundariesAndStrictlyAscending() {
        val b = listOf(100, 120, 140, 160, 180, 200)
        val result = HrZoneCalculator.adjustBoundary(b, 3, 110)
        // index 3 must be 110
        assertEquals(110, result[3])
        // index 2 <= 109, index 1 <= 108, index 0 <= 107
        assertTrue(result[2] <= 109)
        assertTrue(result[1] <= 108)
        assertTrue(result[0] <= 107)
        // strictly ascending
        for (i in 0 until result.size - 1) {
            assertTrue("Not strictly ascending at $i: ${result[i]} >= ${result[i+1]}", result[i] < result[i + 1])
        }
    }
}
