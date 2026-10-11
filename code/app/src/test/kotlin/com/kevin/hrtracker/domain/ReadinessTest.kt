package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.toHrvMeasurements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ln

class ReadinessTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun m(back: Int, rmssd: Int, hr: Int? = 50, hour: Int = 7, posture: HrvPosture? = null, reliable: Boolean = true) = HrvMeasurement(
        date = today.minusDays(back.toLong()),
        timestampMs = (today.toEpochDay() - back) * 86_400_000L + hour * 3_600_000L,
        rmssd = rmssd,
        restingHr = hr,
        posture = posture,
        reliable = reliable
    )

    @Test
    fun `first measurement of a day wins`() {
        val s = Readiness.summarize(listOf(m(0, 80, hour = 18), m(0, 40, hour = 7)), today, chartDays = 1)
        assertEquals(ln(40.0), s.days.single().lnRmssd!!, 1e-9)
    }

    @Test
    fun `rolling average needs five readings`() {
        val four = Readiness.summarize((0 until 4).map { m(it, 60) }, today)
        assertNull(four.rmssd7)
        val five = Readiness.summarize((0 until 5).map { m(it, 60) }, today)
        assertEquals(60, five.rmssd7)
        assertEquals(5, five.measurementsLast7)
        val three = Readiness.summarize((0 until 3).map { m(it, 60) }, today)
        assertEquals(50, three.restingHr7)
        assertEquals(3, three.measurementsLast7)
    }

    @Test
    fun `status below normal after a bad week`() {
        // 30 Tage alternierend 60/70 ms, letzte 7 Tage 35 ms
        val list = (0 until 30).map { back ->
            m(back, if (back < 7) 35 else if (back % 2 == 0) 60 else 70)
        }
        val s = Readiness.summarize(list, today)
        assertEquals(ReadinessStatus.BELOW, s.status)
    }

    @Test
    fun `status normal on stable values`() {
        val list = (0 until 30).map { back -> m(back, if (back % 2 == 0) 60 else 70) }
        assertEquals(ReadinessStatus.NORMAL, Readiness.summarize(list, today).status)
    }

    @Test
    fun `no status without baseline`() {
        val list = (0 until 5).map { m(it, 60) }
        val s = Readiness.summarize(list, today)
        assertNull(s.status)
        assertEquals(ReadinessBlock.BASELINE_BUILDING, s.block)
        assertEquals(5, s.baselineCount)
    }

    @Test
    fun `compare windows`() {
        val points = listOf(
            today to 30, today.minusDays(3) to 20,          // aktuelles 7-Tage-Fenster
            today.minusDays(8) to 10, today.minusDays(20) to 99  // davor / außerhalb
        )
        val (cur, prev) = Readiness.compareWindows(points, today, windowDays = 7)
        assertEquals(25.0, cur!!, 1e-9)
        assertEquals(10.0, prev!!, 1e-9)
    }

    @Test
    fun `unreliable reading is ignored, first reliable of day counts`() {
        val s = Readiness.summarize(listOf(m(0, 20, hour = 6, reliable = false), m(0, 60, hour = 7)), today, chartDays = 1)
        assertEquals(ln(60.0), s.days.last().lnRmssd!!, 1e-9)
    }

    @Test
    fun `unreliable readings excluded from baseline`() {
        val list = (0 until 20).map { m(it, 60) } + (20 until 30).map { m(it, 20, reliable = false) }
        assertEquals(20, Readiness.summarize(list, today).baselineCount)
    }

    @Test
    fun `only latest posture is compared`() {
        val list = (1..30).map { m(it, 60, posture = HrvPosture.LYING) } + m(0, 60, posture = HrvPosture.SITTING)
        val s = Readiness.summarize(list, today)
        assertEquals(HrvPosture.SITTING, s.posture)
        assertEquals(1, s.baselineCount)
        assertEquals(ReadinessBlock.BASELINE_BUILDING, s.block)
    }

    @Test
    fun `posture group comes from latest reliable reading`() {
        val list = (1..30).map { m(it, 60, posture = HrvPosture.LYING) } + m(0, 60, posture = HrvPosture.SITTING, reliable = false)
        val s = Readiness.summarize(list, today)
        assertEquals(HrvPosture.LYING, s.posture)
        assertEquals(30, s.baselineCount)
        assertNotNull(s.status)
    }

    @Test
    fun `null quality columns map to unreliable`() {
        val sess = com.kevin.hrtracker.data.entity.Session(
            label = Readiness.HRV_LABEL, startedAt = 0L, endedAt = null, maxHrUsed = 190, restingHr = null, rmssd = 50
        )
        assertEquals(false, listOf(sess).toHrvMeasurements().single().reliable)
    }

    @Test
    fun `good quality columns map to reliable`() {
        val sess = com.kevin.hrtracker.data.entity.Session(
            label = Readiness.HRV_LABEL, startedAt = 0L, endedAt = null, maxHrUsed = 190, restingHr = null, rmssd = 50,
            activeMs = 300_259L, rmssdValidBeats = 268, rmssdArtefactPct = 0.0
        )
        assertEquals(true, listOf(sess).toHrvMeasurements().single().reliable)
    }

    @Test
    fun `legacy null posture is its own group`() {
        val list = (1..30).map { m(it, 60) } + m(0, 60, posture = HrvPosture.LYING)
        assertEquals(1, Readiness.summarize(list, today).baselineCount)
    }

    @Test
    fun `too few recent readings`() {
        val list = (7..26).map { m(it, 60) } + (0 until 4).map { m(it, 60) }
        val s = Readiness.summarize(list, today)
        assertEquals(ReadinessBlock.TOO_FEW_RECENT, s.block)
        assertEquals(4, s.measurementsLast7)
    }

    @Test
    fun `above band is ABOVE`() {
        val list = (0 until 30).map { back -> m(back, if (back < 7) 110 else if (back % 2 == 0) 60 else 70) }
        assertEquals(ReadinessStatus.ABOVE, Readiness.summarize(list, today).status)
    }

    @Test
    fun `unknown posture string parses to null`() {
        val sess = com.kevin.hrtracker.data.entity.Session(
            label = Readiness.HRV_LABEL, startedAt = 0L, endedAt = null, maxHrUsed = 190, restingHr = null,
            rmssd = 50, posture = "GONE"
        )
        assertNull(listOf(sess).toHrvMeasurements().single().posture)
    }
}
