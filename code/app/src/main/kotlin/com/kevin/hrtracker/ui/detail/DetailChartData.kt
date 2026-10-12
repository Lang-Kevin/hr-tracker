package com.kevin.hrtracker.ui.detail

import com.kevin.shared.ui.chart.LineSeries

/**
 * Detail chart data: x = wall-clock seconds ([fractions] × [totalSeconds]), y = BPM. A NaN point
 * is inserted between two samples that bound a connection gap, so the line breaks there (D4).
 */
internal fun detailChartSeries(
    bpm: List<Int>,
    fractions: List<Float>,
    gaps: List<ClosedFloatingPointRange<Float>>,
    totalSeconds: Float,
): LineSeries {
    val n = minOf(bpm.size, fractions.size)
    val xs = ArrayList<Float>(n + gaps.size)
    val ys = ArrayList<Float>(n + gaps.size)
    for (i in 0 until n) {
        xs += fractions[i] * totalSeconds
        ys += bpm[i].toFloat()
        if (i < n - 1) {
            val a = fractions[i]
            val b = fractions[i + 1]
            if (gaps.any { it.start < b && it.endInclusive > a }) {
                xs += (a + b) / 2f * totalSeconds
                ys += Float.NaN
            }
        }
    }
    return LineSeries(ys, xs)
}
