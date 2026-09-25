package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ln

class ReadinessTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun m(back: Int, rmssd: Int, hr: Int? = 50, hour: Int = 7) = HrvMeasurement(
        date = today.minusDays(back.toLong()),
        timestampMs = (today.toEpochDay() - back) * 86_400_000L + hour * 3_600_000L,
        rmssd = rmssd,
        restingHr = hr
    )

    @Test
    fun `first measurement of a day wins`() {
        val s = Readiness.summarize(listOf(m(0, 80, hour = 18), m(0, 40, hour = 7)), today, chartDays = 1)
        assertEquals(ln(40.0), s.days.single().lnRmssd!!, 1e-9)
    }

    @Test
    fun `rolling average needs three measurements`() {
        val two = Readiness.summarize(listOf(m(0, 60), m(1, 60)), today)
        assertNull(two.rmssd7)
        assertNull(two.restingHr7)
        val three = Readiness.summarize(listOf(m(0, 60), m(1, 60), m(2, 60)), today)
        assertEquals(60, three.rmssd7)
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
        assertNull(Readiness.summarize(list, today).status)
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
}
