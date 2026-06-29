package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.*
import org.junit.Test

class HrRecoveryTest {

    /**
     * Helper to build HR samples with 1-second intervals.
     * @param baseTimestampMs starting timestamp
     * @param bpmCurve list of BPM values, one per second
     * @param sessionId session identifier
     */
    private fun buildSamples(
        baseTimestampMs: Long = 0,
        bpmCurve: List<Int>,
        sessionId: Long = 1
    ): List<HrSample> {
        return bpmCurve.mapIndexed { idx, bpm ->
            HrSample(
                sessionId = sessionId,
                timestampMs = baseTimestampMs + (idx * 1000L),
                bpm = bpm
            )
        }
    }

    @Test
    fun computeHrRecovery_emptyList_returnsNull() {
        val result = HrRecovery.computeHrRecovery(emptyList(), 200)
        assertNull(result)
    }

    @Test
    fun computeHrRecovery_singleSample_returnsNull() {
        val samples = buildSamples(bpmCurve = listOf(150))
        val result = HrRecovery.computeHrRecovery(samples, 200)
        assertNull(result)
    }

    @Test
    fun computeHrRecovery_invalidMaxHr_returnsNull() {
        val samples = buildSamples(bpmCurve = listOf(100, 150, 120))
        val result = HrRecovery.computeHrRecovery(samples, 0)
        assertNull(result)
    }

    @Test
    fun computeHrRecovery_neverReachesHighThreshold_returnsNull() {
        val maxHr = 200
        val highThreshold = 0.70 * maxHr  // 140
        // All samples below 140
        val samples = buildSamples(bpmCurve = listOf(100, 120, 130, 135, 120, 100))
        val result = HrRecovery.computeHrRecovery(samples, maxHr)
        assertNull(result)
    }

    @Test
    fun computeHrRecovery_peakTooLateNotEnoughFollowUp_returnsNull() {
        val maxHr = 200
        // Peak at last sample, not enough samples after
        val samples = buildSamples(bpmCurve = listOf(100, 120, 150, 160))
        val result = HrRecovery.computeHrRecovery(samples, maxHr)
        assertNull(result)
    }

    @Test
    fun computeHrRecovery_normalCase_peak170_at60s_150_hrr20_ratingGUT() {
        val maxHr = 200
        // Peak at index 30 (30 seconds), HR 170
        // At 30+60=90 seconds, HR 150 -> hrr60 = 20
        // This should be GUT (18..29)
        val bpmCurve = mutableListOf<Int>()
        // Warm-up: 0-29s gradually to 170
        for (i in 0..29) bpmCurve.add(120 + (i * 50 / 30))  // ~120 to ~170
        bpmCurve[29] = 170  // ensure peak
        // Cool-down: 30-100s gradually from 170 to 120
        for (i in 30..99) bpmCurve.add(170 - ((i - 29) * 50 / 70))  // 170 down to ~120
        bpmCurve[89] = 150  // At 60s after peak (index 89), ensure HR is 150

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertEquals(170, result!!.peakBpm)
        assertEquals(150, result.hrAt60s)
        assertEquals(20, result.hrr60)
        assertEquals(HrrRating.GUT, result.rating)
    }

    @Test
    fun computeHrRecovery_recoveredToTarget() {
        val maxHr = 200
        val recoveryTarget = 0.60 * maxHr  // 120
        // Peak at index 30 (30s), HR 180
        val bpmCurve = mutableListOf<Int>()
        // Warm-up
        for (i in 0..29) bpmCurve.add(100 + (i * 80 / 30))  // ~100 to ~180
        bpmCurve[29] = 180
        // Cool-down: index 30-99
        for (i in 30..99) bpmCurve.add(180 - ((i - 29) * 60 / 70))  // 180 down to ~120
        bpmCurve[89] = 155  // At 60s: 155
        // Add more samples for recovery
        for (i in 100..105) bpmCurve.add(110)  // At 70-75s: 110 (below recoveryTarget)

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertTrue(result!!.recoveredToTarget)
        assertNotNull(result.secondsToTarget)
        // secondsToTarget should be around 70s (when first sample <= 120)
        assertTrue(result.secondsToTarget!! > 60 && result.secondsToTarget!! < 80)
    }

    @Test
    fun computeHrRecovery_neverRecoveredToTarget() {
        val maxHr = 200
        // Peak at index 30 (30s), HR 180
        val bpmCurve = mutableListOf<Int>()
        // Warm-up
        for (i in 0..29) bpmCurve.add(100 + (i * 80 / 30))
        bpmCurve[29] = 180
        // Cool-down: never goes below 120 (recoveryTarget)
        for (i in 30..99) bpmCurve.add(180 - ((i - 29) * 40 / 70))  // 180 down to ~140
        bpmCurve[89] = 155  // At 60s: 155

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertFalse(result!!.recoveredToTarget)
        assertNull(result.secondsToTarget)
    }

    @Test
    fun computeHrRecovery_ratingNIEDRIG() {
        val maxHr = 200
        // Peak at index 30, HR 160
        // At 60s: HR 152 -> hrr60 = 8 (NIEDRIG < 12)
        val bpmCurve = mutableListOf<Int>()
        for (i in 0..29) bpmCurve.add(120 + (i * 40 / 30))
        bpmCurve[29] = 160
        for (i in 30..99) bpmCurve.add(160 - ((i - 29) * 8 / 70))  // slight drop only
        bpmCurve[89] = 152  // 60s after peak

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertEquals(8, result!!.hrr60)
        assertEquals(HrrRating.NIEDRIG, result.rating)
    }

    @Test
    fun computeHrRecovery_ratingNORMAL() {
        val maxHr = 200
        // Peak at index 30, HR 160
        // At 60s: HR 145 -> hrr60 = 15 (NORMAL 12..17)
        val bpmCurve = mutableListOf<Int>()
        for (i in 0..29) bpmCurve.add(120 + (i * 40 / 30))
        bpmCurve[29] = 160
        for (i in 30..99) bpmCurve.add(160 - ((i - 29) * 15 / 70))
        bpmCurve[89] = 145

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertEquals(15, result!!.hrr60)
        assertEquals(HrrRating.NORMAL, result.rating)
    }

    @Test
    fun computeHrRecovery_ratingSEHR_GUT() {
        val maxHr = 200
        // Peak at index 30, HR 170
        // At 60s: HR 135 -> hrr60 = 35 (SEHR_GUT >= 30)
        val bpmCurve = mutableListOf<Int>()
        for (i in 0..29) bpmCurve.add(110 + (i * 60 / 30))
        bpmCurve[29] = 170
        for (i in 30..99) bpmCurve.add(170 - ((i - 29) * 50 / 70))  // aggressive drop
        bpmCurve[89] = 135

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertEquals(35, result!!.hrr60)
        assertEquals(HrrRating.SEHR_GUT, result.rating)
    }

    @Test
    fun computeHrRecovery_hrAt60OutOfTolerance_returnsNull() {
        val maxHr = 200
        // Peak at 30s (30000ms), HR 170
        // Looking for sample at [90000, 95000]ms (+60±5s after peak)
        // Manually create samples with a gap in the critical window
        val samples = mutableListOf<HrSample>()
        // Warm-up: 0-29s
        for (i in 0..29) {
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = 120 + (i * 50 / 30)))
        }
        // Peak at 30s
        samples.add(HrSample(sessionId = 1, timestampMs = 30000, bpm = 170))
        // Cool-down: 31-89s
        for (i in 31..89) {
            val bpm = 170 - ((i - 30) * 60 / 60)
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = bpm.coerceAtLeast(120)))
        }
        // Gap: no samples from 90-95s
        // Resume at 96s
        for (i in 96..110) {
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = 110))
        }

        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        // No sample in [90000-95000ms] window, should return null
        assertNull(result)
    }
}
