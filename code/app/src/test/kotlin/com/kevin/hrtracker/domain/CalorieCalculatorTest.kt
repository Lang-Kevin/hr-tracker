package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun `keytel male reference value`() {
        // HR 150, 80 kg, 30 J, 60 min
        // (-55.0969 + 0.6309*150 + 0.1988*80 + 0.2017*30) / 4.184 = 14.6972 kcal/min
        val kcal = CalorieCalculator.estimateKcal(
            avgBpm = 150, durationMs = 60 * 60_000L,
            weightKg = 80, age = 30, sex = Sex.MALE
        )
        assertEquals(882, kcal)
    }

    @Test
    fun `keytel female reference value`() {
        // HR 140, 60 kg, 25 J, 30 min
        // (-20.4022 + 0.4472*140 - 0.1263*60 + 0.074*25) / 4.184 = 8.7184 kcal/min
        val kcal = CalorieCalculator.estimateKcal(
            avgBpm = 140, durationMs = 30 * 60_000L,
            weightKg = 60, age = 25, sex = Sex.FEMALE
        )
        assertEquals(262, kcal)
    }

    @Test
    fun `negative result is clamped to zero`() {
        val kcal = CalorieCalculator.estimateKcal(
            avgBpm = 50, durationMs = 60 * 60_000L,
            weightKg = 70, age = 30, sex = Sex.MALE
        )
        assertEquals(0, kcal)
    }

    @Test
    fun `null when weight missing`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 150, durationMs = 60 * 60_000L,
                weightKg = null, age = 30, sex = Sex.MALE
            )
        )
    }

    @Test
    fun `null when sex missing`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 150, durationMs = 60 * 60_000L,
                weightKg = 80, age = 30, sex = null
            )
        )
    }

    @Test
    fun `null when duration not positive`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 150, durationMs = 0L,
                weightKg = 80, age = 30, sex = Sex.MALE
            )
        )
    }

    @Test
    fun `null when avgBpm not positive`() {
        assertNull(
            CalorieCalculator.estimateKcal(
                avgBpm = 0, durationMs = 60 * 60_000L,
                weightKg = 80, age = 30, sex = Sex.MALE
            )
        )
    }
}
