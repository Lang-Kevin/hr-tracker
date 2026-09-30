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

    /**
     * Maps an active time offset to the corresponding wall-clock timestamp.
     * Walks through sorted samples, summing interval durations (only valid intervals 1..MAX_GAP_MS count),
     * and returns the timestamp at which the sum reaches >= [activeMs].
     * Returns the last sample's timestamp if [activeMs] was never reached, or null if the list is empty.
     */
    fun activeToWallMs(samples: List<HrSample>, activeMs: Long): Long? {
        if (samples.isEmpty()) return null
        if (activeMs <= 0L) return samples.first().timestampMs
        val sorted = samples.sortedBy { it.timestampMs }
        var accum = 0L
        for (i in 0 until sorted.size - 1) {
            val dt = sorted[i + 1].timestampMs - sorted[i].timestampMs
            if (dt in 1..MAX_GAP_MS) {
                accum += dt
                if (accum >= activeMs) return sorted[i + 1].timestampMs
            }
        }
        return sorted.last().timestampMs
    }
}
