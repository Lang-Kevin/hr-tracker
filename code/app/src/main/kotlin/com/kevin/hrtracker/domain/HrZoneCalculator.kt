package com.kevin.hrtracker.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.kevin.shared.domain.validateZoneTexts
import com.kevin.hrtracker.data.entity.HrSample

@Serializable
data class ZoneBounds(val zone: Int, val lo: Int, val hi: Int) {
    val label: String get() = "Z$zone"
}

object HrZoneCalculator {

    private const val MIN_BPM = 30
    private const val MAX_BPM = 220
    // ponytail: Lücke > MAX_SAMPLE_GAP_MS = BLE-Dropout, Dauer zählt nicht in Zeit-in-Zone.
    // Wert ggf. an Auto-Pause-Schwelle angleichen, falls die abweicht.
    private const val MAX_SAMPLE_GAP_MS = 5000L

    fun zonesToBoundaries(zones: List<ZoneBounds>): List<Int> =
        listOf(zones.first().lo) + zones.map { it.hi }   // Größe 6

    fun boundariesToZones(b: List<Int>): List<ZoneBounds> =
        (0 until 5).map { ZoneBounds(it + 1, b[it], b[it + 1]) }

    // ponytail: Grenze[index]=value auf [MIN_BPM+index, MAX_BPM-(lastIndex-index)] klemmen,
    // damit Kaskade garantiert in 30..220 und streng aufsteigend bleibt.
    fun adjustBoundary(boundaries: List<Int>, index: Int, value: Int): List<Int> {
        val b = boundaries.toIntArray()
        val lastIndex = b.size - 1
        val lo = MIN_BPM + index
        val hi = MAX_BPM - (lastIndex - index)
        b[index] = value.coerceIn(lo, hi)
        for (i in index - 1 downTo 0) if (b[i] >= b[i + 1]) b[i] = b[i + 1] - 1
        for (i in index + 1 until b.size) if (b[i] <= b[i - 1]) b[i] = b[i - 1] + 1
        return b.toList()
    }

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

    /**
     * Liefert die Zonen einer Session: bevorzugt den persistierten Snapshot
     * (zoneSnapshotJson, enthält ggf. Custom-Zonen), Fallback auf Neuberechnung
     * via calculateZones bei NULL oder Parse-Fehler.
     */
    fun resolveZones(zoneSnapshotJson: String?, maxHr: Int, restingHr: Int?): List<ZoneBounds> =
        zoneSnapshotJson
            ?.let { runCatching { Json.decodeFromString<List<ZoneBounds>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: calculateZones(maxHr, restingHr)

    fun zoneFor(bpm: Int, zones: List<ZoneBounds>): Int {
        for (z in zones) { if (bpm <= z.hi) return z.zone }
        return 5
    }

    /**
     * Erkennt Verbindungs-Lücken (BLE-Dropout) als Timestamp-Bereiche.
     * Ein Bereich entsteht zwischen zwei aufeinanderfolgenden Samples mit Δt > MAX_SAMPLE_GAP_MS.
     * @return Liste von LongRange(startMs..endMs), aufsteigend nach Zeit. Leer wenn keine Lücke.
     */
    fun detectGaps(samples: List<HrSample>): List<LongRange> {
        if (samples.size < 2) return emptyList()
        val sorted = samples.sortedBy { it.timestampMs }
        val gaps = mutableListOf<LongRange>()
        for (i in 0 until sorted.size - 1) {
            val cur = sorted[i].timestampMs
            val next = sorted[i + 1].timestampMs
            if (next - cur > MAX_SAMPLE_GAP_MS) {
                gaps += cur..next
            }
        }
        return gaps
    }

    /**
     * Aggregates time spent in each zone from a list of HR samples.
     * Calculates duration between consecutive samples and assigns to the zone of the first sample.
     * Duration for the last sample is 1 second.
     * Accumulates milliseconds per zone and divides by 1000 only once at the end,
     * to avoid systematic loss of sub-second remainders on BLE jitter.
     *
     * @param samples List of HR samples, must be sorted by timestamp
     * @param zones Zone bounds (typically from calculateZones)
     * @return Map of zone -> total duration in seconds
     */
    fun aggregateTimeInZone(samples: List<HrSample>, zones: List<ZoneBounds>): Map<Int, Long> {
        if (samples.isEmpty()) return emptyMap()

        val sorted = samples.sortedBy { it.timestampMs }
        val msPerZone = mutableMapOf<Int, Long>()
        for (i in 0 until sorted.size - 1) {
            val gapMs = sorted[i + 1].timestampMs - sorted[i].timestampMs
            if (gapMs > MAX_SAMPLE_GAP_MS) continue
            val zone = zoneFor(sorted[i].bpm, zones)
            msPerZone[zone] = (msPerZone[zone] ?: 0L) + gapMs
        }
        val lastZone = zoneFor(sorted.last().bpm, zones)
        msPerZone[lastZone] = (msPerZone[lastZone] ?: 0L) + 1000L
        return msPerZone.mapValues { it.value / 1000L }
    }
}
