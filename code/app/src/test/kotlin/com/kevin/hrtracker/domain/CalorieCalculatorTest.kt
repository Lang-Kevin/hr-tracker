package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieCalculatorTest {

    /** Ein Sample pro Sekunde, [minutes] Minuten lang → minutes*60 Intervalle à 1 s. */
    private fun steady(bpm: Int, minutes: Int, startMs: Long = 0L): List<HrSample> =
        (0..minutes * 60).map { i ->
            HrSample(sessionId = 1, timestampMs = startMs + i * 1_000L, bpm = bpm)
        }

    @Test
    fun `male active kcal = keytel minus schofield bmr`() {
        // Keytel: (-55.0969 + 0.6309*150 + 0.1988*80 + 0.2017*30) / 4.184 = 14.6972 kcal/min
        // Schofield 30–60 J: (11.472*80 + 873.1) / 1440 = 1.2437 kcal/min
        // (14.6972 - 1.2437) * 60 = 807.2
        val kcal = CalorieCalculator.estimateActiveKcal(
            steady(bpm = 150, minutes = 60), weightKg = 80, age = 30, sex = Sex.MALE
        )
        assertEquals(807, kcal)
    }

    @Test
    fun `female active kcal = keytel minus schofield bmr`() {
        // Keytel: (-20.4022 + 0.4472*140 - 0.1263*60 + 0.074*25) / 4.184 = 8.7184 kcal/min
        // Schofield 18–30 J: (14.818*60 + 486.6) / 1440 = 0.9553 kcal/min
        // (8.7184 - 0.9553) * 30 = 232.9
        val kcal = CalorieCalculator.estimateActiveKcal(
            steady(bpm = 140, minutes = 30), weightKg = 60, age = 25, sex = Sex.FEMALE
        )
        assertEquals(233, kcal)
    }

    @Test
    fun `pause gap is not counted`() {
        // 2 × 30 min mit 10 min Pause dazwischen = gleiche kcal wie 60 min am Stück
        val first = steady(bpm = 150, minutes = 30)
        val second = steady(bpm = 150, minutes = 30, startMs = first.last().timestampMs + 10 * 60_000L)
        val kcal = CalorieCalculator.estimateActiveKcal(
            first + second, weightKg = 80, age = 30, sex = Sex.MALE
        )
        assertEquals(807, kcal)
    }

    @Test
    fun `intervals are weighted by time and bpm`() {
        // 30 min @150 + 30 min @100: Summe beider Teile, kein Durchschnitts-BPM über Sample-Anzahl
        val hi = steady(bpm = 150, minutes = 30)
        val lo = steady(bpm = 100, minutes = 30, startMs = hi.last().timestampMs + 1_000L)
        val kcal = CalorieCalculator.estimateActiveKcal(
            hi + lo, weightKg = 80, age = 30, sex = Sex.MALE
        )!!
        val hiOnly = CalorieCalculator.estimateActiveKcal(hi, 80, 30, Sex.MALE)!!
        val loOnly = CalorieCalculator.estimateActiveKcal(lo, 80, 30, Sex.MALE)!!
        // +1 Intervall (1 s @150) am Übergang, Rundung ±1
        assertEquals((hiOnly + loOnly).toDouble(), kcal.toDouble(), 1.0)
    }

    @Test
    fun `resting heart rate yields zero active kcal`() {
        val kcal = CalorieCalculator.estimateActiveKcal(
            steady(bpm = 60, minutes = 60), weightKg = 70, age = 30, sex = Sex.MALE
        )
        assertEquals(0, kcal)
    }

    @Test
    fun `null when weight missing`() {
        assertNull(
            CalorieCalculator.estimateActiveKcal(steady(150, 60), weightKg = null, age = 30, sex = Sex.MALE)
        )
    }

    @Test
    fun `null when sex missing`() {
        assertNull(
            CalorieCalculator.estimateActiveKcal(steady(150, 60), weightKg = 80, age = 30, sex = null)
        )
    }

    @Test
    fun `null when fewer than two samples`() {
        assertNull(
            CalorieCalculator.estimateActiveKcal(steady(150, 0), weightKg = 80, age = 30, sex = Sex.MALE)
        )
    }
}
