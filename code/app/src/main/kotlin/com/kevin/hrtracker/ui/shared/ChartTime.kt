package com.kevin.hrtracker.ui.shared

import kotlin.math.ceil

/*
 * Zeitbasierte X-Positionen: Samples werden über ihren Zeitstempel (Anteil an der Sessiondauer bzw.
 * Sekunden seit Start) statt über ihren Index positioniert. Die Arrays sind aufsteigend sortiert.
 * Reines Kotlin (keine Android/Compose-Abhängigkeiten) -> auf der JVM testbar.
 */

/** Erster Index mit fractions[i] >= [value] (size, wenn keiner). */
private fun lowerBound(size: Int, value: Float, at: (Int) -> Float): Int {
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (at(mid) < value) lo = mid + 1 else hi = mid
    }
    return lo
}

private fun visibleRangeBy(size: Int, from: Float, to: Float, at: (Int) -> Float): IntRange {
    if (size <= 0 || from.isNaN() || to.isNaN()) return IntRange.EMPTY
    // erster Index >= from, letzter Index <= to (= lowerBound(to) - 1, bzw. inkl. Gleichheit)
    val firstIn = lowerBound(size, from, at)
    var lastIn = lowerBound(size, to, at) // erster Index >= to
    if (lastIn < size && at(lastIn) == to) lastIn++ // Gleichheit gehört dazu
    lastIn -= 1
    val lo = (firstIn - 1).coerceIn(0, size - 1)
    val hi = (lastIn + 1).coerceIn(0, size - 1)
    return if (lo <= hi) lo..hi else IntRange.EMPTY
}

private fun nearestBy(size: Int, target: Float, at: (Int) -> Float): Int? {
    if (size <= 0 || target.isNaN()) return null
    val i = lowerBound(size, target, at)
    if (i <= 0) return 0
    if (i >= size) return size - 1
    // Bei Gleichstand das frühere Sample.
    return if (target - at(i - 1) <= at(i) - target) i - 1 else i
}

/**
 * Indizes der Samples, deren Anteil in [start], [end] liegt, plus je ein Nachbar links/rechts,
 * damit die Linie bis zum Rand reicht. Leer bei leerem Array.
 */
fun visibleRangeByFractions(fractions: FloatArray, start: Float, end: Float): IntRange =
    visibleRangeBy(fractions.size, start, end) { fractions[it] }

/** Index des Samples mit dem Anteil, der [f] am nächsten liegt (Gleichstand: früher); null bei leer/NaN. */
fun nearestIndexByFraction(fractions: FloatArray, f: Float): Int? =
    nearestBy(fractions.size, f) { fractions[it] }

/** Wie [visibleRangeByFractions], aber über Sekunden seit Start (Live-Chart). */
fun visibleRangeBySeconds(seconds: List<Float>, fromSec: Float, toSec: Float): IntRange =
    visibleRangeBy(seconds.size, fromSec, toSec) { seconds[it] }

/** Wie [nearestIndexByFraction], aber über Sekunden seit Start (Live-Chart). */
fun nearestIndexBySeconds(seconds: List<Float>, sec: Float): Int? =
    nearestBy(seconds.size, sec) { seconds[it] }

/**
 * Länge der Sekunden-Domäne des Live-Charts: ceil(letzte Sekunde) + 1 (>= 1 bei vorhandenen
 * Samples), 0 ohne Samples. Entspricht der bisherigen "Sample-Anzahl" bei 1 Sample/s.
 */
fun liveTotalSeconds(seconds: List<Float>): Int {
    if (seconds.isEmpty()) return 0
    val last = seconds.last()
    if (last.isNaN() || last < 0f) return 1
    return ceil(last).toInt() + 1
}

/**
 * Mittelt [values] und [positions] (gleiche Länge) in Chunks gleicher Größe auf höchstens
 * [maxPoints] Punkte; der letzte Chunk kann kürzer sein. Ergibt (Positionen, Werte).
 */
fun downsampleMeans(positions: FloatArray, values: FloatArray, maxPoints: Int): Pair<FloatArray, FloatArray> {
    val n = minOf(positions.size, values.size)
    if (n <= maxPoints || maxPoints < 1) return positions.copyOf(n) to values.copyOf(n)
    val chunk = ceil(n / maxPoints.toDouble()).toInt()
    val count = (n + chunk - 1) / chunk
    val p = FloatArray(count)
    val v = FloatArray(count)
    for (k in 0 until count) {
        val a = k * chunk
        val b = minOf(a + chunk, n)
        var sp = 0.0
        var sv = 0.0
        for (i in a until b) { sp += positions[i]; sv += values[i] }
        p[k] = (sp / (b - a)).toFloat()
        v[k] = (sv / (b - a)).toFloat()
    }
    return p to v
}
