package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import kotlin.math.roundToInt

/**
 * Gemeinsame Lücken-Regel für alle sample-basierten Kennzahlen.
 * Ein Intervall zwischen zwei aufeinanderfolgenden Samples zählt nur, wenn 0 < Δt ≤ MAX_GAP_MS.
 * Größere Lücken sind Pausen (Sample-Job gestoppt) oder BLE-Dropouts.
 */
object SampleIntervals {

    // Wert ggf. an Auto-Pause-Schwelle angleichen, falls die abweicht.
    const val MAX_GAP_MS = 5000L

    /** Gültige Intervalle als (erstes Sample, Δt in ms), zeitlich sortiert. */
    fun active(samples: List<HrSample>): List<Pair<HrSample, Long>> =
        samples.sortedBy { it.timestampMs }
            .zipWithNext { a, b -> a to (b.timestampMs - a.timestampMs) }
            .filter { (_, dtMs) -> dtMs in 1..MAX_GAP_MS }

    /** Aktive Trainingszeit: Summe der gültigen Intervalle. */
    fun activeMs(samples: List<HrSample>): Long = active(samples).sumOf { it.second }

    /** Zeitgewichteter Ø-Puls über die gültigen Intervalle, null ohne gültiges Intervall. */
    fun avgBpm(samples: List<HrSample>): Int? {
        val intervals = active(samples)
        val totalMs = intervals.sumOf { it.second }
        if (totalMs <= 0L) return null
        return (intervals.sumOf { (s, dt) -> s.bpm.toDouble() * dt } / totalMs).roundToInt()
    }
}
