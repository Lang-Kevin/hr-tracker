package com.kevin.hrtracker.ui.shared

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Sichtbarer X-Bereich eines Diagramms als Anteil (0..1) der gesamten Datenbreite.
 * Reines Kotlin (keine Android/Compose-Abhängigkeiten) -> auf der JVM testbar.
 */
data class ChartViewport(val start: Float = 0f, val end: Float = 1f) {

    val span: Float get() = end - start

    /** true, wenn der komplette Datenbereich sichtbar ist. */
    val isFull: Boolean get() = start <= EPS && end >= 1f - EPS

    /**
     * Zoomt um [factor] (>1 = hinein). [anchor] (0..1) ist die Position innerhalb der
     * sichtbaren Breite; der Datenpunkt darunter bleibt dort (außer bei Begrenzung an 0/1).
     */
    fun zoomBy(factor: Float, anchor: Float, minSpan: Float): ChartViewport {
        if (factor <= 0f || factor.isNaN() || factor.isInfinite()) return this
        val a = anchor.coerceIn(0f, 1f)
        val newSpan = (span / factor).coerceIn(minSpan.coerceIn(TINY, 1f), 1f)
        val focus = start + a * span
        val newStart = (focus - a * newSpan).coerceIn(0f, 1f - newSpan)
        return snapped(newStart, newSpan)
    }

    /**
     * Verschiebt um [deltaFraction] der sichtbaren Breite. Positiv = Inhalt nach rechts,
     * d.h. frühere Daten werden sichtbar (wie Wischen nach rechts). Spanne bleibt erhalten.
     */
    fun panBy(deltaFraction: Float): ChartViewport {
        val s = span
        val newStart = (start - deltaFraction * s).coerceIn(0f, 1f - s)
        return snapped(newStart, s)
    }

    /** Rastet an exakten Rändern ein, damit Float-Rauschen kein "fast voll" erzeugt. */
    private fun snapped(start: Float, span: Float): ChartViewport {
        var s = start
        var e = start + span
        val atEnd = e > 1f - EPS
        if (atEnd) { e = 1f; s = 1f - span }
        if (s < EPS) { s = 0f; if (!atEnd) e = span }
        return ChartViewport(s, e)
    }

    /** Position von [fraction] (Datenbereich) im sichtbaren Fenster; kann außerhalb 0..1 liegen. */
    fun mapX(fraction: Float): Float = (fraction - start) / span

    /**
     * Indizes der Samples, deren Anteil i/(size-1) in [start, end] liegt, plus je ein Nachbar
     * links/rechts, damit die Linie bis zum Rand reicht.
     */
    fun visibleIndexRange(size: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        if (size == 1) return 0..0
        val n = (size - 1).toFloat()
        val first = ceil(start * n - 1e-4f).toInt() - 1
        val last = floor(end * n + 1e-4f).toInt() + 1
        val lo = first.coerceIn(0, size - 1)
        val hi = last.coerceIn(0, size - 1)
        return if (lo <= hi) lo..hi else IntRange.EMPTY
    }

    companion object {
        val Full = ChartViewport()
        private const val EPS = 1e-4f
        private const val TINY = 1e-4f
    }
}

/**
 * Index des Samples unter [fraction] (0..1 über die sichtbare Plotbreite), linear auf
 * range.first..range.last abgebildet, gerundet und geklemmt; null bei leerem Bereich.
 */
fun nearestIndex(fraction: Float, range: IntRange): Int? {
    if (range.isEmpty()) return null
    if (fraction.isNaN()) return null
    val f = fraction.coerceIn(0f, 1f)
    val idx = range.first + (f * (range.last - range.first)).roundToInt()
    return idx.coerceIn(range.first, range.last)
}
