package com.kevin.hrtracker.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Eine Datenreihe für [TrendChart]. `null` = kein Wert an diesem Index (Linie wird unterbrochen).
 * [lines] verbindet benachbarte Werte, [dots] markiert jeden Wert einzeln (für lückenhafte Reihen).
 */
data class TrendSeries(
    val values: List<Float?>,
    val color: Color,
    val lines: Boolean = true,
    val dots: Boolean = false
)

/**
 * Minimaler Linien-Chart ohne Achsen: alle Reihen teilen x (Index) und y (gemeinsame Skala).
 * [band] zeichnet einen horizontalen Bereich (z. B. Normalbereich), [referenceY] eine Linie.
 */
@Composable
fun TrendChart(
    series: List<TrendSeries>,
    modifier: Modifier = Modifier,
    band: ClosedFloatingPointRange<Float>? = null,
    bandColor: Color = Color.White.copy(alpha = 0.08f),
    referenceY: Float? = null,
    referenceColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val all = series.flatMap { it.values.filterNotNull() } +
        listOfNotNull(band?.start, band?.endInclusive, referenceY)
    if (all.isEmpty()) return
    val rawMin = all.min()
    val rawMax = all.max()
    val pad = ((rawMax - rawMin) * 0.1f).takeIf { it > 0f } ?: (rawMax.coerceAtLeast(1f) * 0.1f)
    val yMin = rawMin - pad
    val yMax = rawMax + pad

    Canvas(modifier) {
        fun y(v: Float) = size.height * (1f - (v - yMin) / (yMax - yMin))
        fun x(i: Int, n: Int) = if (n <= 1) size.width / 2f else size.width * i / (n - 1)

        band?.let {
            val top = y(it.endInclusive)
            drawRect(bandColor, topLeft = Offset(0f, top), size = Size(size.width, y(it.start) - top))
        }
        referenceY?.let {
            drawLine(referenceColor, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.dp.toPx())
        }
        series.forEach { s ->
            val n = s.values.size
            if (s.lines) {
                val path = Path()
                var penDown = false
                s.values.forEachIndexed { i, v ->
                    if (v == null) { penDown = false; return@forEachIndexed }
                    if (penDown) path.lineTo(x(i, n), y(v)) else path.moveTo(x(i, n), y(v))
                    penDown = true
                }
                drawPath(path, s.color, style = Stroke(width = 2.dp.toPx()))
            }
            if (s.dots) {
                s.values.forEachIndexed { i, v ->
                    if (v != null) drawCircle(s.color, radius = 3.dp.toPx(), center = Offset(x(i, n), y(v)))
                }
            }
        }
    }
}
