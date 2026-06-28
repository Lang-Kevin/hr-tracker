package com.kevin.hrtracker.domain

import kotlinx.serialization.Serializable
import com.kevin.shared.domain.validateZoneTexts
import com.kevin.hrtracker.data.entity.HrSample

@Serializable
data class ZoneBounds(val zone: Int, val lo: Int, val hi: Int) {
    val label: String get() = "Z$zone"
}

object HrZoneCalculator {

    private val zonePercentages = listOf(
        1 to (0.50 to 0.60),
        2 to (0.60 to 0.70),
        3 to (0.70 to 0.80),
        4 to (0.80 to 0.90),
        5 to (0.90 to 1.00)
    )

    fun tanakaMaxHr(age: Int): Int = (208 - 0.7 * age).toInt()

    // Karvonen if restingHr is provided, %HRmax otherwise
    fun calculateZones(maxHr: Int, restingHr: Int?): List<ZoneBounds> =
        zonePercentages.map { (zone, pcts) ->
            val (lo, hi) = if (restingHr != null) {
                val hrr = maxHr - restingHr
                Pair(
                    (hrr * pcts.first + restingHr).toInt(),
                    (hrr * pcts.second + restingHr).toInt()
                )
            } else {
                Pair(
                    (maxHr * pcts.first).toInt(),
                    (maxHr * pcts.second).toInt()
                )
            }
            ZoneBounds(zone, lo, hi)
        }

    fun zoneFor(bpm: Int, zones: List<ZoneBounds>): Int {
        for (z in zones) { if (bpm <= z.hi) return z.zone }
        return 5
    }

    /**
     * Aggregates time spent in each zone from a list of HR samples.
     * Calculates duration between consecutive samples and assigns to the zone of the first sample.
     * Duration for the last sample is 1 second.
     *
     * @param samples List of HR samples, must be sorted by timestamp
     * @param zones Zone bounds (typically from calculateZones)
     * @return Map of zone -> total duration in seconds
     */
    fun aggregateTimeInZone(samples: List<HrSample>, zones: List<ZoneBounds>): Map<Int, Long> {
        if (samples.isEmpty()) return emptyMap()

        val sorted = samples.sortedBy { it.timestampMs }
        val result = mutableMapOf<Int, Long>()
        for (i in 0 until sorted.size - 1) {
            val zone = zoneFor(sorted[i].bpm, zones)
            val durS = (sorted[i + 1].timestampMs - sorted[i].timestampMs) / 1000L
            result[zone] = (result[zone] ?: 0L) + durS.coerceAtLeast(0L)
        }
        val lastZone = zoneFor(sorted.last().bpm, zones)
        result[lastZone] = (result[lastZone] ?: 0L) + 1L
        return result
    }
}
