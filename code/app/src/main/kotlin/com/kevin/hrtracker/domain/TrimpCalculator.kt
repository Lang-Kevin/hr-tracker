package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import kotlin.math.exp

/**
 * TRIMP sample-weise integriert: jedes Intervall zwischen zwei Samples zählt mit der BPM
 * des ersten Samples. Intervalle > MAX_SAMPLE_GAP_MS (Pause, BLE-Dropout) zählen nicht,
 * damit Pausenzeit nicht mit Trainingspuls hochgerechnet wird.
 *
 * Mit Ruhepuls: Banister, Karvonen-Ratio `Δt[min] × r × e^(1.92 r)`.
 * Ohne Ruhepuls: Fallback `Δt[min] × %HRmax × 100`.
 */
object TrimpCalculator {

    fun compute(samples: List<HrSample>, maxHr: Int, restingHr: Int?): Int? {
        if (samples.size < 2) return null
        val sorted = samples.sortedBy { it.timestampMs }
        val hrr = restingHr?.let { (maxHr - it).toDouble().coerceAtLeast(1.0) }

        val trimp = sorted.zipWithNext().sumOf { (a, b) ->
            val dtMs = b.timestampMs - a.timestampMs
            if (dtMs <= 0L || dtMs > HrZoneCalculator.MAX_SAMPLE_GAP_MS || a.bpm <= 0) 0.0
            else {
                val minutes = dtMs / 60_000.0
                if (restingHr != null && hrr != null) {
                    val r = ((a.bpm - restingHr) / hrr).coerceIn(0.0, 1.0)
                    minutes * r * exp(1.92 * r)
                } else {
                    val r = (a.bpm.toDouble() / maxHr).coerceIn(0.0, 1.0)
                    minutes * r * 100
                }
            }
        }
        return trimp.toInt()
    }
}
