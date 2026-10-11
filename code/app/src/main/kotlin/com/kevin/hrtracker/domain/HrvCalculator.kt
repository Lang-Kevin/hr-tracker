package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class HrvResult(
    val rmssd: Int,            // roundToInt(), nicht truncaten
    val validBeats: Int,       // Schläge ohne Artefakt-Flag im ausgewerteten Teil
    val artefactPct: Double,   // geflaggte / alle Schläge × 100
    val analysedSeconds: Int   // HrvQuality.analysedSeconds(activeMs, effektiver Discard)
)

object HrvCalculator {

    private val VALID_RR_MS = 300..2000
    private const val MEDIAN_HALF_WINDOW = 5          // ±5 Nachbarn = 11er-Fenster
    // ponytail: fester relativer Schwellwert (30 % des lokalen Medians). Ceiling: verpasst kleine
    // Ektopien bei hoher HRV, überflaggt stark unregelmäßige Rhythmen. Upgrade: Kubios-artiger
    // adaptiver Schwellwert (zeitvariabel, Quartilsabstand-basiert), falls nötig.
    private const val ARTEFACT_REL_THRESHOLD = 0.30

    private class Beat(val rr: Int, val breakBefore: Boolean)

    /**
     * RMSSD aus den RR-Intervallen. Aufeinanderfolgende Samples sind aufeinanderfolgende
     * Schläge; über eine Sample-Lücke > MAX_GAP_MS (Pause, Dropout) hinweg wird nicht
     * differenziert. Artefakt: RR außerhalb 300–2000 ms ODER Abweichung > 30 % vom Median
     * des 11er-Fensters (Rohwerte). Jede Differenz, die einen geflaggten Schlag berührt,
     * entfällt (keine Interpolation). Ergebnis wird gerundet. [discardFirstMs] verwirft die
     * erste aktive Zeit (Einschwingphase), nur wenn die Aufnahme länger ist.
     */
    fun analyze(samples: List<HrSample>, discardFirstMs: Long = 0L): HrvResult? {
        val sorted = samples.sortedBy { it.timestampMs }
        if (sorted.isEmpty()) return null
        val activeMs = SampleIntervals.activeMs(sorted)
        // ponytail: kurze Messungen werden komplett ausgewertet und sind immer TOO_SHORT.
        val discard = if (discardFirstMs > 0 && activeMs > discardFirstMs) discardFirstMs else 0L
        val start = if (discard > 0) SampleIntervals.activeToWallMs(sorted, discard)!! else sorted.first().timestampMs

        val beats = ArrayList<Beat>()
        var prevTs: Long? = null
        var pendingBreak = false
        for (sample in sorted) {
            if (sample.timestampMs < start) continue
            if (prevTs != null && sample.timestampMs - prevTs > SampleIntervals.MAX_GAP_MS) pendingBreak = true
            prevTs = sample.timestampMs
            val rrs = sample.rrIntervalsMs?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: continue
            rrs.forEachIndexed { j, rr -> beats.add(Beat(rr, pendingBreak && j == 0)); pendingBreak = false }
        }
        val n = beats.size
        val flagged = flagArtefacts(beats.map { it.rr })
        var sumSq = 0.0
        var cnt = 0
        for (i in 1 until n) {
            if (beats[i].breakBefore || flagged[i] || flagged[i - 1]) continue
            val d = (beats[i].rr - beats[i - 1].rr).toDouble()
            sumSq += d * d
            cnt++
        }
        if (cnt == 0) return null
        val flaggedCount = flagged.count { it }
        return HrvResult(
            rmssd = sqrt(sumSq / cnt).roundToInt(),
            validBeats = n - flaggedCount,
            artefactPct = flaggedCount * 100.0 / n,
            analysedSeconds = HrvQuality.analysedSeconds(activeMs, discard)
        )
    }

    fun rmssd(samples: List<HrSample>): Int? = analyze(samples)?.rmssd

    internal fun flagArtefacts(rr: List<Int>): BooleanArray {
        val n = rr.size
        return BooleanArray(n) { i ->
            val w = rr.subList(max(0, i - MEDIAN_HALF_WINDOW), min(n, i + MEDIAN_HALF_WINDOW + 1)).sorted()
            val med = if (w.size % 2 == 1) w[w.size / 2].toDouble() else (w[w.size / 2 - 1] + w[w.size / 2]) / 2.0
            rr[i] !in VALID_RR_MS || abs(rr[i] - med) > ARTEFACT_REL_THRESHOLD * med
        }
    }
}
