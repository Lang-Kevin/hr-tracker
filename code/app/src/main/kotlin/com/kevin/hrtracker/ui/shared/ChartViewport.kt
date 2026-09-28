package com.kevin.hrtracker.ui.shared

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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
        if (factor <= 0f || factor.isNaN()) return this
        val a = anchor.coerceIn(0f, 1f)
        val newSpan = (span / factor).coerceIn(minSpan.coerceIn(TINY, 1f), 1f)
        val focus = start + a * span
        val newStart = (focus - a * newSpan).coerceIn(0f, 1f - newSpan)
        return ChartViewport(newStart, newStart + newSpan)
    }

    /**
     * Verschiebt um [deltaFraction] der sichtbaren Breite. Positiv = Inhalt nach rechts,
     * d.h. frühere Daten werden sichtbar (wie Wischen nach rechts). Spanne bleibt erhalten.
     */
    fun panBy(deltaFraction: Float): ChartViewport {
        val s = span
        val newStart = (start - deltaFraction * s).coerceIn(0f, 1f - s)
        return ChartViewport(newStart, newStart + s)
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
 * Live-Fenster in Sekunden/Samples (1 Sample ≈ 1 s): Fensterbreite und Abstand des rechten
 * Randes zum neuesten Sample ([offsetFromEnd] = 0 -> folgt live).
 */
data class LiveWindow(val windowSeconds: Int = DEFAULT, val offsetFromEnd: Int = 0) {

    val isFollowing: Boolean get() = offsetFromEnd == 0

    /** Sichtbare Sample-Indizes bei [total] Samples; leer wenn total <= 0. */
    fun visibleRange(total: Int): IntRange {
        if (total <= 0) return IntRange.EMPTY
        val window = min(windowSeconds, total)
        val end = (total - 1 - offsetFromEnd).coerceIn(0, total - 1)
        val start = max(0, end - window + 1)
        return start..end
    }

    /**
     * Zoomt um [factor] (>1 = hinein) mit [anchor] (0..1) in der sichtbaren Breite; das Sample
     * unter dem Anker bleibt möglichst fest. Fenster in [MIN, max(MIN, total)].
     */
    fun zoomBy(factor: Float, anchor: Float, total: Int): LiveWindow {
        if (factor <= 0f || factor.isNaN()) return this
        val a = anchor.coerceIn(0f, 1f)
        val newWindow = (windowSeconds / factor).roundToInt().coerceIn(MIN, max(MIN, total))
        val oldWindow = min(windowSeconds, max(total, 1))
        val oldStart = total - offsetFromEnd - oldWindow
        val focus = oldStart + a * oldWindow
        val newStart = focus - a * newWindow
        val newEnd = newStart + newWindow - 1
        val newOffset = (total - 1 - newEnd).roundToInt()
            .coerceIn(0, max(0, total - newWindow))
        return LiveWindow(newWindow, newOffset)
    }

    /** Verschiebt um [deltaSeconds]; positiv = weiter in die Vergangenheit. */
    fun panBy(deltaSeconds: Float, total: Int): LiveWindow {
        val maxOffset = max(0, total - min(windowSeconds, total))
        val newOffset = (offsetFromEnd + deltaSeconds).roundToInt().coerceIn(0, maxOffset)
        return copy(offsetFromEnd = newOffset)
    }

    /** Neues Sample eingetroffen: Ansicht bleibt beim selben Zeitpunkt, wenn zurückgescrollt. */
    fun onSampleAdded(): LiveWindow = if (isFollowing) this else copy(offsetFromEnd = offsetFromEnd + 1)

    companion object {
        const val DEFAULT = 120
        const val MIN = 30
    }
}
