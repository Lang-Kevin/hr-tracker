package com.kevin.hrtracker.domain

import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Eine Ruhe-HRV-Messung (Session mit Label [Readiness.HRV_LABEL]). */
data class HrvMeasurement(val date: LocalDate, val timestampMs: Long, val rmssd: Int, val restingHr: Int?)

enum class ReadinessStatus { BELOW, NORMAL, ABOVE }

data class ReadinessDay(
    val date: LocalDate,
    /** ln(RMSSD) der Tagesmessung, null an Tagen ohne Messung. */
    val lnRmssd: Double?,
    /** Ø ln(RMSSD) der letzten 7 Tage (nur Tage mit Messung). */
    val rolling7: Double?
)

data class ReadinessSummary(
    val days: List<ReadinessDay>,
    /** Normalbereich = Baseline-Mittel ± 0.5 SD (Plews et al.) über [Readiness.BASELINE_DAYS]. */
    val normalLow: Double?,
    val normalHigh: Double?,
    val status: ReadinessStatus?,
    /** Aktueller 7-Tage-Schnitt als RMSSD in ms (e^rolling7). */
    val rmssd7: Int?,
    /** Ø Ruhepuls der Messungen der letzten 7 Tage. */
    val restingHr7: Int?,
    val measurementsLast7: Int
)

/**
 * Morgendliche Bereitschaft aus kurzen Ruhe-HRV-Messungen. Pro Tag zählt die erste Messung
 * (Standardprotokoll: direkt nach dem Aufwachen). Auswertung auf ln(RMSSD), weil RMSSD
 * rechtsschief verteilt ist.
 */
object Readiness {

    const val HRV_LABEL = "HRV RMSSD"
    const val BASELINE_DAYS = 60
    const val MIN_ROLLING = 3
    const val MIN_BASELINE = 7

    fun summarize(measurements: List<HrvMeasurement>, today: LocalDate, chartDays: Int = 30): ReadinessSummary {
        val perDay = measurements
            .filter { it.rmssd > 0 }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.minBy { it.timestampMs } }

        fun window(end: LocalDate, n: Int) =
            (0 until n).mapNotNull { perDay[end.minusDays(it.toLong())] }

        fun rolling(end: LocalDate): Double? {
            val w = window(end, 7)
            return if (w.size < MIN_ROLLING) null else w.map { ln(it.rmssd.toDouble()) }.average()
        }

        val days = (chartDays - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            ReadinessDay(d, perDay[d]?.let { ln(it.rmssd.toDouble()) }, rolling(d))
        }

        val baseline = window(today, BASELINE_DAYS).map { ln(it.rmssd.toDouble()) }
        val (low, high) = if (baseline.size >= MIN_BASELINE) {
            val mean = baseline.average()
            val sd = sqrt(baseline.sumOf { (it - mean) * (it - mean) } / (baseline.size - 1))
            (mean - 0.5 * sd) to (mean + 0.5 * sd)
        } else null to null

        val current = rolling(today)
        val status = if (current == null || low == null || high == null) null else when {
            current < low -> ReadinessStatus.BELOW
            current > high -> ReadinessStatus.ABOVE
            else -> ReadinessStatus.NORMAL
        }

        val last7 = window(today, 7)
        val hrs = last7.mapNotNull { it.restingHr }
        return ReadinessSummary(
            days = days,
            normalLow = low,
            normalHigh = high,
            status = status,
            rmssd7 = current?.let { exp(it).roundToInt() },
            restingHr7 = if (hrs.size < MIN_ROLLING) null else hrs.average().roundToInt(),
            measurementsLast7 = last7.size
        )
    }

    /**
     * Vergleicht den Ø der letzten [windowDays] Tage mit dem Fenster davor.
     * @return (aktuell, vorher), jeweils null ohne Werte.
     */
    fun compareWindows(points: List<Pair<LocalDate, Int>>, today: LocalDate, windowDays: Int): Pair<Double?, Double?> {
        val currentStart = today.minusDays((windowDays - 1).toLong())
        val previousStart = currentStart.minusDays(windowDays.toLong())
        val current = points.filter { !it.first.isBefore(currentStart) && !it.first.isAfter(today) }.map { it.second }
        val previous = points.filter { !it.first.isBefore(previousStart) && it.first.isBefore(currentStart) }.map { it.second }
        return current.takeIf { it.isNotEmpty() }?.average() to previous.takeIf { it.isNotEmpty() }?.average()
    }
}
