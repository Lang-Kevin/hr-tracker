package com.kevin.hrtracker.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartTicksTest {

    @Test fun smallRange_usesFiveSecondSteps() {
        assertEquals(listOf(0f, 5f, 10f, 15f, 20f), timeTicks(0f, 20f))
    }

    @Test fun oneMinute_usesFifteenSecondSteps() {
        assertEquals(listOf(0f, 15f, 30f, 45f, 60f), timeTicks(0f, 60f))
    }

    @Test fun offsetRange_ticksAreMultiplesWithinRange() {
        val t = timeTicks(12f, 47f)
        assertEquals(listOf(20f, 30f, 40f), t)
    }

    @Test fun twoHourSession() {
        val t = timeTicks(0f, 7200f)
        assertTrue(t.size <= 5)
        assertEquals(0f, t.first(), 0f)
        assertEquals(7200f, t.last(), 0f)
        assertEquals(listOf(0f, 1800f, 3600f, 5400f, 7200f), t)
    }

    @Test fun emptyOrInvertedRange() {
        assertTrue(timeTicks(10f, 10f).isEmpty())
        assertTrue(timeTicks(20f, 10f).isEmpty())
        assertTrue(timeTicks(Float.NaN, 10f).isEmpty())
    }

    @Test fun tickCountNeverExceedsLimit() {
        for (max in 1..8) {
            for (to in listOf(1f, 7f, 33f, 90f, 500f, 1234f, 5000f, 20000f, 86400f)) {
                val t = timeTicks(0f, to, max)
                assertTrue("max=$max to=$to size=${t.size}", t.size <= max)
                t.forEach { assertTrue(it in 0f..to) }
            }
        }
    }

    @Test fun rangeWithoutTickIsEmpty() {
        assertTrue(timeTicks(1f, 4f).isEmpty())
    }

    @Test fun labels() {
        assertEquals("0:00", formatTickLabel(0f))
        assertEquals("0:30", formatTickLabel(30f))
        assertEquals("12:05", formatTickLabel(725f))
        assertEquals("59:59", formatTickLabel(3599f))
        assertEquals("1:00:00", formatTickLabel(3600f))
        assertEquals("2:00:00", formatTickLabel(7200f))
        assertEquals("1:02:03", formatTickLabel(3723f))
    }
}
