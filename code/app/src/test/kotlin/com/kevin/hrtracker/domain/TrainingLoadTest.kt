package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrainingLoadTest {

    private val today = LocalDate.of(2026, 9, 25)

    @Test
    fun `srpe is rpe times active minutes`() {
        assertEquals(420.0, TrainingLoad.srpe(rpe = 7, activeMs = 60 * 60_000L), 1e-9)
        assertEquals(600.0, TrainingLoad.srpe(rpe = 15, activeMs = 60 * 60_000L), 1e-9) // clamp auf 10
    }

    @Test
    fun `steady load gives acwr 1`() {
        // 4 Wochen lang jeden Tag 100 → akut 700, chronisch 2800/4 = 700
        val loads = (0 until 28).map { today.minusDays(it.toLong()) to 100.0 }
        val last = TrainingLoad.series(loads, firstDay = today.minusDays(27), today = today, days = 1).single()
        assertEquals(700.0, last.acute, 1e-9)
        assertEquals(700.0, last.chronic, 1e-9)
        assertEquals(1.0, last.acwr!!, 1e-9)
    }

    @Test
    fun `spike in last week raises acwr`() {
        // 3 Wochen je 100/Tag, letzte Woche 200/Tag → akut 1400, chronisch (2100 + 1400) / 4 = 875
        val loads = (0 until 28).map { back ->
            today.minusDays(back.toLong()) to if (back < 7) 200.0 else 100.0
        }
        val last = TrainingLoad.series(loads, today.minusDays(27), today, 1).single()
        assertEquals(1.6, last.acwr!!, 1e-9)
        assertEquals(AcwrZone.HIGH, TrainingLoad.zoneOf(last.acwr!!))
    }

    @Test
    fun `no acwr before 28 days of history`() {
        val loads = (0 until 10).map { today.minusDays(it.toLong()) to 100.0 }
        val last = TrainingLoad.series(loads, today.minusDays(9), today, 1).single()
        assertNull(last.acwr)
        assertEquals(700.0, last.acute, 1e-9)
    }

    @Test
    fun `multiple sessions per day are summed and series has one entry per day`() {
        val loads = listOf(today to 50.0, today to 70.0)
        val series = TrainingLoad.series(loads, today.minusDays(40), today, days = 14)
        assertEquals(14, series.size)
        assertEquals(today, series.last().date)
        assertEquals(120.0, series.last().acute, 1e-9)
        assertEquals(0.0, series.first().acute, 1e-9)
        assertNotNull(series.last().acwr)
    }

    @Test
    fun `acwr zones`() {
        assertEquals(AcwrZone.LOW, TrainingLoad.zoneOf(0.7))
        assertEquals(AcwrZone.OPTIMAL, TrainingLoad.zoneOf(1.0))
        assertEquals(AcwrZone.OPTIMAL, TrainingLoad.zoneOf(1.3))
        assertEquals(AcwrZone.CAUTION, TrainingLoad.zoneOf(1.4))
        assertEquals(AcwrZone.HIGH, TrainingLoad.zoneOf(1.51))
    }
}
