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
        val bpmCurve = mutableListOf<Int>()
        // 0-80s: stay low, no high threshold reached
        for (i in 0..80) bpmCurve.add(120)
        // 81-119s: rapid rise to peak
        for (i in 81..119) bpmCurve.add(120 + ((i - 80) * 50 / 39))
        // 120s: peak at 170
        bpmCurve.add(170)  // Index 120
        // 121-180s: cool-down from 170 to 150
        for (i in 121..180) bpmCurve.add(170 - ((i - 120) * 20 / 60))
        bpmCurve[180] = 150  // Ensure at 60s: 150
        // 181-250s: flatten — very slow decline (candidates @ 181-190 have minimal drop)
        for (i in 181..250) bpmCurve.add((150 - (i - 180) / 14).toInt().coerceAtLeast(100))

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
        val bpmCurve = mutableListOf<Int>()
        // 0-80s: stay low
        for (i in 0..80) bpmCurve.add(100)
        // 81-119s: rapid rise to just below peak
        for (i in 81..119) bpmCurve.add(100 + ((i - 80) * 79 / 39))
        // 120s: peak at 180
        bpmCurve.add(180)  // Index 120
        // 121-250s: steady 1 bpm/s decline. Reaches target 120 at 180s (60s after peak),
        // monotonic so the 120s peak is the unique max-drop candidate (drop=60).
        for (i in 121..250) bpmCurve.add((180 - (i - 120)).coerceAtLeast(90))

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertTrue(result!!.recoveredToTarget)
        assertNotNull(result.secondsToTarget)
        // secondsToTarget should be around 70-100s
        assertTrue(result.secondsToTarget!! >= 60 && result.secondsToTarget!! <= 110)
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
        val bpmCurve = mutableListOf<Int>()
        // 0-80s: stay low
        for (i in 0..80) bpmCurve.add(110)
        // 81-119s: rapid rise
        for (i in 81..119) bpmCurve.add(110 + ((i - 80) * 60 / 39))
        // 120s: peak at 170
        bpmCurve.add(170)  // Index 120
        // 121-180s: aggressive drop from 170 to 135
        for (i in 121..180) bpmCurve.add(170 - ((i - 120) * 35 / 60))
        bpmCurve[180] = 135  // Ensure at 60s: 135
        // 181-250s: flatten — very slow decline (candidates @ 181-190 have minimal drop)
        for (i in 181..250) bpmCurve.add((135 - (i - 180) / 10).toInt().coerceAtLeast(0))

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        assertEquals(35, result!!.hrr60)
        assertEquals(HrrRating.SEHR_GUT, result.rating)
    }

    @Test
    fun computeHrRecovery_hrAt60OutOfTolerance_returnsNull() {
        val maxHr = 200
        val samples = mutableListOf<HrSample>()
        // Stay low: 0-80s
        for (i in 0..80) {
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = 120))
        }
        // Moderate rise: 81-119s (keep below 140 to exclude pre-peak candidates)
        for (i in 81..119) {
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = 120 + ((i - 80) * 18 / 39)))
        }
        // Peak at 120s
        samples.add(HrSample(sessionId = 1, timestampMs = 120000, bpm = 170))
        // Cool-down: 121-179s
        for (i in 121..179) {
            val bpm = 170 - ((i - 120) * 60 / 60)
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = bpm.coerceAtLeast(120)))
        }
        // Gap: no samples from 180-215s (covers all candidate 60±5s windows)
        // Resume at 216s
        for (i in 216..250) {
            samples.add(HrSample(sessionId = 1, timestampMs = (i * 1000).toLong(), bpm = 110))
        }

        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        // No sample in any valid candidate window, should return null
        assertNull(result)
    }

    @Test
    fun computeHrRecovery_globalMaxMidTraining_selectsRealRecoveryAtEnd() {
        // Scenario: Dauertraining mit globalem Max @ 120s, dann wieder hoch.
        // Echte Erholung am Ende @ 600s.
        // Der Algorithmus sollte den Peak mit dem grössten drop wählen, nicht das globale BPM-Max.
        val maxHr = 200
        val highThreshold = 0.70 * maxHr  // 140

        val bpmCurve = mutableListOf<Int>()

        // 0-120s: Aufwärmen, globales Max @ 120s
        for (i in 0..120) {
            bpmCurve.add(100 + (i * 80 / 120))  // 100 to 180
        }

        // 121-180s: Hochbelastung (z.B. Sprintphase), wieder hoch
        for (i in 121..180) {
            bpmCurve.add(160)
        }

        // 181-599s: langsame Reduktion
        for (i in 181..599) {
            bpmCurve.add(160 - ((i - 180) * 60 / 420).coerceAtLeast(0))  // 160 down towards 100
        }

        // 600s: Start echte Erholung @ 170 BPM
        bpmCurve.add(170)  // Index 600

        // 601-659s: Abfall
        for (i in 601..659) {
            bpmCurve.add(170 - ((i - 600) * 30 / 60))  // 170 down to 140
        }

        // 660-665s: 140 BPM (hrAt60Sample für 600s Peak)
        for (i in 660..665) {
            bpmCurve.add(140)
        }

        val samples = buildSamples(bpmCurve = bpmCurve)
        val result = HrRecovery.computeHrRecovery(samples, maxHr)

        assertNotNull(result)
        // Der Peak sollte @ 600s sein (170), nicht @ 120s (180, globales Max)
        // drop @ 120s: 180 - 160 (hrAt60 @ 180s) = 20
        // drop @ 600s: 170 - 140 (hrAt60 @ 660s) = 30
        // 30 > 20, also wähle Peak @ 600s
        assertEquals(170, result!!.peakBpm)
        assertEquals(140, result.hrAt60s)
        assertEquals(30, result.hrr60)  // 170 - 140 = 30
        assertEquals(HrrRating.SEHR_GUT, result.rating)
    }
}
