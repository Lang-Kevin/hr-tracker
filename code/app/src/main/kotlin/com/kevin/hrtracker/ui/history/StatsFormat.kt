package com.kevin.hrtracker.ui.history

import com.kevin.shared.format.formatDecimal
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** ISO-8601 week number (Monday start, week-based year). Used for "W42" / "KW 42" labels. */
internal fun isoWeek(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/** ACWR per day for the Stats trend (D8a); null (< 28 days history) becomes a NaN gap. */
internal fun acwrSeries(acwr: List<Double?>): List<Float> = acwr.map { it?.toFloat() ?: Float.NaN }

/**
 * Form hero line (D8b): baseline RMSSD in ms = exp(midpoint of the ln-RMSSD normal band) and the
 * relative deviation of [rmssd7] from it. Null when any input is missing.
 */
internal fun baselineDelta(rmssd7: Double?, normalLowLn: Double?, normalHighLn: Double?): Pair<Double, Double>? {
    if (rmssd7 == null || normalLowLn == null || normalHighLn == null) return null
    val baseline = exp((normalLowLn + normalHighLn) / 2.0)
    if (baseline <= 0.0) return null
    return baseline to (rmssd7 / baseline - 1.0)
}

/** Resting HR card "−2 vs. 7 days" (D8d): current minus 7-day mean, rounded; null hides the line. */
internal fun restingDelta(current: Int?, avg7: Double?): Int? =
    if (current == null || avg7 == null) null else (current - avg7).roundToInt()

/** "+9%" / "−4%". Lib `formatPercent` clamps to 0..1, so it cannot show negative deltas. */
internal fun signedPercent(fraction: Double, locale: Locale): String {
    val pct = formatDecimal(abs(fraction) * 100.0, 0, locale)
    return when {
        pct == "0" -> "0%"
        fraction < 0 -> "−$pct%"
        else -> "+$pct%"
    }
}

internal fun signedInt(v: Int): String = when {
    v < 0 -> "−${-v}"
    v > 0 -> "+$v"
    else -> "0"
}
