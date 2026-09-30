package com.kevin.hrtracker.ui.shared

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.hrtracker.ui.theme.LightPurple
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.ZoneColors
import com.kevin.shared.ui.chart.aggregateByChunks
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun BpmZoneChart(
    bpmHistory: List<Int>,
    currentBpm: Int?,
    zoneBounds: List<ZoneBounds>,
    targetZone: Int,
    modifier: Modifier = Modifier,
    dynamicScale: Boolean = false,
    reachedZones: Set<Int> = emptySet(),
    detailMilestones: List<Milestone> = emptyList(),
    totalSessionSeconds: Long? = null,
    gaps: List<Pair<Float, Float>> = emptyList(),
    meanBpm: Int? = null,
    viewport: ChartViewport = ChartViewport.Full,
    scrubX: () -> Float? = { null },
    sampleFractions: List<Float>? = null
) {
    val density = LocalDensity.current

    Canvas(modifier = modifier.clipToBounds()) {
        if (zoneBounds.isEmpty()) return@Canvas

        val scrubXValue = scrubX()
        val leftPaddingPx = with(density) { 54.dp.toPx() }
        val chartWidth = size.width - leftPaddingPx
        // Unten Platz für die Zeitachsen-Beschriftung reservieren.
        val axisPx = max(TIME_AXIS_HEIGHT_DP.dp.toPx(), 10.sp.toPx() * 1.4f)
        val plotHeight = (size.height - axisPx).coerceAtLeast(1f)
        val targetBound = zoneBounds.getOrNull(targetZone - 1)

        val fr = sampleFractions?.takeIf { it.size == bpmHistory.size }

        // Sichtbarer Ausschnitt der Historie (bei Zoom); Indizes beziehen sich auf bpmHistory.
        val visibleRange = if (fr != null) {
            val lo = fr.indexOfLast { it < viewport.start }.coerceAtLeast(0)
            val hi = fr.indexOfFirst { it > viewport.end }.let { if (it < 0) fr.lastIndex else it }
            lo..hi
        } else {
            viewport.visibleIndexRange(bpmHistory.size)
        }
        val visibleSlice = if (visibleRange.isEmpty()) emptyList() else bpmHistory.subList(visibleRange.first, visibleRange.last + 1)

        val bpmMin: Float
        val bpmMax: Float

        if (dynamicScale && visibleSlice.isNotEmpty()) {
            // DYNAMIC: measured min/max des sichtbaren Ausschnitts with 10% padding
            val actualMin = visibleSlice.minOrNull()?.toFloat() ?: 60f
            val actualMax = visibleSlice.maxOrNull()?.toFloat() ?: 180f
            val pad = (actualMax - actualMin).coerceAtLeast(1f) * 0.10f
            // Zielband/ZIEL-Badge rechnen mit zoneBounds — Skala muss die Zielzone einschließen,
            // sonst landet bpmToY() für das Band außerhalb [0, size.height].
            bpmMin = if (targetBound != null) min(actualMin - pad, targetBound.lo.toFloat()) else actualMin - pad
            bpmMax = if (targetBound != null) max(actualMax + pad, targetBound.hi.toFloat()) else actualMax + pad
        } else {
            // STATIC: keep current behavior
            bpmMin = (zoneBounds.minOf { it.lo } - 8).toFloat()
            bpmMax = (zoneBounds.maxOf { it.hi } + 8).toFloat()
        }

        val bpmRange = bpmMax - bpmMin

        fun bpmToY(bpm: Int): Float =
            plotHeight * (1f - (bpm - bpmMin).toFloat() / bpmRange)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
            color = android.graphics.Color.argb(160, 255, 255, 255)
        }

        val zonesToDraw = if (dynamicScale && reachedZones.isNotEmpty()) {
            zoneBounds.filter { it.zone in reachedZones }
        } else {
            zoneBounds
        }

        run {
            val totalSec = if (totalSessionSeconds != null && totalSessionSeconds > 0L) {
                totalSessionSeconds.toFloat()
            } else {
                (bpmHistory.size - 1).coerceAtLeast(0).toFloat()
            }
            drawTimeAxis(
                fromSec = viewport.start * totalSec,
                toSec = viewport.end * totalSec,
                leftPaddingPx = leftPaddingPx,
                plotHeight = plotHeight,
                labelPaint = labelPaint,
                density = density
            )
        }

        clipRect(left = 0f, top = 0f, right = size.width, bottom = plotHeight) {
            zonesToDraw.forEach { z ->
                val y = bpmToY(z.lo)
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(leftPaddingPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = with(density) { 1.dp.toPx() }
                )
                val yCentre = bpmToY((z.lo + z.hi) / 2)
                val labelBaselineY = yCentre + labelPaint.textSize / 3f
                if (labelBaselineY >= labelPaint.textSize && labelBaselineY <= plotHeight) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "Z${z.zone} ${z.lo}",
                        4f,
                        labelBaselineY,
                        labelPaint
                    )
                }
            }
            zonesToDraw.lastOrNull()?.let { z ->
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(leftPaddingPx, bpmToY(z.hi)),
                    end = Offset(size.width, bpmToY(z.hi)),
                    strokeWidth = with(density) { 1.dp.toPx() }
                )
            }

            targetBound?.let { zBound ->
                val yTop = bpmToY(zBound.hi)
                val yBottom = bpmToY(zBound.lo)
                drawRect(
                    color = ZoneColors[targetZone - 1].copy(alpha = 0.18f),
                    topLeft = Offset(leftPaddingPx, yTop),
                    size = Size(chartWidth, yBottom - yTop)
                )
            }

            targetBound?.let { zBound ->
                val yZiel = bpmToY(zBound.lo)
                val bw = with(density) { 34.dp.toPx() }
                val bh = with(density) { 15.dp.toPx() }
                val br = with(density) { 4.dp.toPx() }
                val bx = leftPaddingPx + with(density) { 6.dp.toPx() }
                val by = yZiel - bh - with(density) { 2.dp.toPx() }
                drawRoundRect(
                    color = PrimaryPurple.copy(alpha = 0.9f),
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(br)
                )
                val zielPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = with(density) { 9.sp.toPx() }
                    color = android.graphics.Color.WHITE
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "ZIEL",
                    bx + bw / 2,
                    by + bh / 2 + zielPaint.textSize / 3f,
                    zielPaint
                )
            }

            // Milestone vertical lines for detail view
            if (detailMilestones.isNotEmpty() && totalSessionSeconds != null && totalSessionSeconds > 0) {
                val milestonePaint = Paint().apply {
                    isAntiAlias = true
                    textSize = with(density) { 9.sp.toPx() }
                    color = android.graphics.Color.argb(200, 255, 200, 80)
                    textAlign = Paint.Align.CENTER
                }
                detailMilestones.forEach { milestone ->
                    val posRatio = milestone.atSeconds.toFloat() / totalSessionSeconds.toFloat()
                    val x = leftPaddingPx + viewport.mapX(posRatio) * chartWidth
                    if (x in leftPaddingPx..size.width) {
                        drawLine(
                            color = Color(0xFFFFC850).copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, plotHeight),
                            strokeWidth = with(density) { 1.5.dp.toPx() }
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            milestone.label.ifBlank { "M" },
                            x,
                            with(density) { 12.sp.toPx() },
                            milestonePaint
                        )
                    }
                }
            }

            // Gap mean-line: red dashed horizontal line over connection-loss gaps
            if (meanBpm != null && gaps.isNotEmpty()) {
                val gapLineY = bpmToY(meanBpm.coerceIn(bpmMin.toInt(), bpmMax.toInt()))
                val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                val gapLineColor = Color(0xFFE53935)
                val gapStrokeWidth = with(density) { 2.dp.toPx() }
                gaps.forEach { (startFraction, endFraction) ->
                    val xStart = leftPaddingPx + viewport.mapX(startFraction).coerceIn(0f, 1f) * chartWidth
                    val xEnd = leftPaddingPx + viewport.mapX(endFraction).coerceIn(0f, 1f) * chartWidth
                    if (xEnd > xStart) {
                        val gapPath = Path().apply {
                            moveTo(xStart, gapLineY)
                            lineTo(xEnd, gapLineY)
                        }
                        drawPath(
                            path = gapPath,
                            color = gapLineColor,
                            style = Stroke(
                                width = gapStrokeWidth,
                                pathEffect = dashPathEffect
                            )
                        )
                    }
                }
            }

            if (bpmHistory.size >= 2 && visibleSlice.size >= 2) {
                // ponytail: downsample via aggregateByChunks when slice size > 300 in dynamic mode
                val historyToUse = if (dynamicScale && visibleSlice.size > 300) {
                    val floatValues = visibleSlice.map { it.toFloat() }
                    val aggregated = aggregateByChunks(floatValues, maxPoints = 300)
                    aggregated.map { it.toInt() }
                } else {
                    visibleSlice
                }

                // Aggregierte Punkte linear auf die Indexspanne des Ausschnitts zurückrechnen,
                // damit x-Positionen (Anteil an der Gesamtdauer) auch nach dem Downsampling stimmen.
                val totalSteps = (bpmHistory.size - 1).toFloat()
                val firstIdx = visibleRange.first.toFloat()
                val idxSpan = (visibleRange.last - visibleRange.first).toFloat()
                val path = Path()
                historyToUse.forEachIndexed { index, bpm ->
                    val idxPos = if (historyToUse.size > 1) firstIdx + index.toFloat() / (historyToUse.size - 1) * idxSpan else firstIdx
                    val frac = if (fr != null && idxPos.toInt() in bpmHistory.indices) {
                        fr[idxPos.toInt()]
                    } else {
                        idxPos / totalSteps
                    }
                    val x = leftPaddingPx + viewport.mapX(frac) * chartWidth
                    val y = bpmToY(bpm.coerceIn(bpmMin.toInt(), bpmMax.toInt()))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                // Linie nicht über die Y-Achsen-Beschriftung zeichnen
                clipRect(left = leftPaddingPx) {
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(
                            width = with(density) { 2.dp.toPx() },
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                val lastBpm = bpmHistory.last()
                val lastFrac = fr?.last() ?: 1f
                val lastX = leftPaddingPx + viewport.mapX(lastFrac) * chartWidth
                val lastY = bpmToY(lastBpm.coerceIn(bpmMin.toInt(), bpmMax.toInt()))

                // Aktueller Punkt/Label nur, wenn das letzte Sample sichtbar ist
                if (viewport.end >= lastFrac - 1e-4f) {
                    drawCircle(
                        color = LightPurple,
                        radius = with(density) { 4.dp.toPx() },
                        center = Offset(lastX, lastY)
                    )

                    val bpmLabelPaint = Paint().apply {
                        isAntiAlias = true
                        textSize = with(density) { 11.sp.toPx() }
                        color = LightPurple.toArgb()
                        textAlign = Paint.Align.RIGHT
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "• ${currentBpm ?: lastBpm} bpm",
                        size.width - with(density) { 2.dp.toPx() },
                        lastY - with(density) { 8.dp.toPx() },
                        bpmLabelPaint
                    )
                }
            }

            // Scrubber (langes Drücken): exakter BPM-Wert und Zeit am Finger
            if (scrubXValue != null && scrubXValue >= leftPaddingPx && bpmHistory.isNotEmpty() && chartWidth > 0f) {
                val f = ((scrubXValue - leftPaddingPx) / chartWidth).coerceIn(0f, 1f)
                val target = viewport.start + f * viewport.span
                val idx = if (fr != null) {
                    val searchResult = fr.binarySearch(target)
                    val insertIdx = if (searchResult < 0) -searchResult - 1 else searchResult
                    val idx1 = insertIdx.coerceIn(0, fr.lastIndex)
                    val idx2 = (insertIdx - 1).coerceAtLeast(0)
                    if (kotlin.math.abs(fr[idx1] - target) <= kotlin.math.abs(fr[idx2] - target)) idx1 else idx2
                } else {
                    ((target) * (bpmHistory.size - 1)).roundToInt()
                        .coerceIn(0, bpmHistory.size - 1)
                }
                run {
                    val frac = if (fr != null) {
                        fr[idx]
                    } else {
                        val n = (bpmHistory.size - 1).coerceAtLeast(1).toFloat()
                        idx / n
                    }
                    val sx = leftPaddingPx + viewport.mapX(frac) * chartWidth
                    val totalSec = if (totalSessionSeconds != null && totalSessionSeconds > 0L) {
                        totalSessionSeconds.toFloat()
                    } else {
                        (bpmHistory.size - 1).coerceAtLeast(0).toFloat()
                    }
                    val bpm = bpmHistory[idx]
                    drawScrubber(
                        x = sx.coerceIn(leftPaddingPx, size.width),
                        y = bpmToY(bpm.coerceIn(bpmMin.toInt(), bpmMax.toInt())),
                        label = "$bpm bpm · ${formatTickLabel(frac * totalSec)}",
                        leftPaddingPx = leftPaddingPx,
                        plotHeight = plotHeight,
                        density = density
                    )
                }
            }
        }
    }
}

/**
 * Scrubber-Overlay: vertikale Linie über den Plotbereich, Punkt am Sample bei ([x], [y]) und
 * Tooltip mit [label] oben im Plot, horizontal in [leftPaddingPx]..Canvas-Breite geklemmt.
 */
internal fun DrawScope.drawScrubber(
    x: Float,
    y: Float,
    label: String,
    leftPaddingPx: Float,
    plotHeight: Float,
    density: Density
) {
    drawLine(
        color = LightPurple.copy(alpha = 0.7f),
        start = Offset(x, 0f),
        end = Offset(x, plotHeight),
        strokeWidth = with(density) { 1.dp.toPx() }
    )
    drawCircle(
        color = LightPurple,
        radius = with(density) { 4.dp.toPx() },
        center = Offset(x, y.coerceIn(0f, plotHeight))
    )
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = with(density) { 11.sp.toPx() }
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    val padH = with(density) { 8.dp.toPx() }
    val boxW = paint.measureText(label) + 2 * padH
    val boxH = paint.textSize + with(density) { 10.dp.toPx() }
    val top = with(density) { 4.dp.toPx() }
    val left = (x - boxW / 2f).coerceIn(leftPaddingPx, max(leftPaddingPx, size.width - boxW))
    drawRoundRect(
        color = Color(0xE6202030),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(with(density) { 6.dp.toPx() })
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        left + padH,
        top + boxH / 2f + paint.textSize / 3f,
        paint
    )
}

/** Höhe des Bandes für die Zeitachsen-Beschriftung unter dem Diagramm. */
internal const val TIME_AXIS_HEIGHT_DP = 14

/**
 * Zeichnet X-Achsen-Ticks: schwache vertikale Gitterlinien über den Plotbereich und
 * zentrierte "m:ss"-Beschriftungen im unteren Band. Fenster: [fromSec]..[toSec] über
 * die Breite rechts von [leftPaddingPx].
 */
internal fun DrawScope.drawTimeAxis(
    fromSec: Float,
    toSec: Float,
    leftPaddingPx: Float,
    plotHeight: Float,
    labelPaint: Paint,
    density: Density
) {
    if (toSec <= fromSec) return
    val chartWidth = size.width - leftPaddingPx
    if (chartWidth <= 0f) return
    val paint = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER }
    val baselineY = plotHeight + (size.height - plotHeight) / 2f + paint.textSize / 3f
    val gridStroke = with(density) { 1.dp.toPx() }
    timeTicks(fromSec, toSec).forEach { sec ->
        val x = leftPaddingPx + (sec - fromSec) / (toSec - fromSec) * chartWidth
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(x, 0f),
            end = Offset(x, plotHeight),
            strokeWidth = gridStroke
        )
        val label = formatTickLabel(sec)
        val half = paint.measureText(label) / 2f
        if (x - half >= 4f && x + half <= size.width) {
            drawContext.canvas.nativeCanvas.drawText(label, x, baselineY, paint)
        }
    }
}

@Composable
fun ZeitInZoneSection(
    timeInZone: Map<Int, Long>,
    percentInTargetZone: Float?,
    targetZone: Int
) {
    val pct = percentInTargetZone ?: 0f
    val total = timeInZone.values.sum().coerceAtLeast(1L)
    val hasData = timeInZone.values.any { it > 0L }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "ZEIT IN ZONE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(pct * 100).toInt()}% in Ziel-Zone",
                color = PrimaryPurple,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            if (hasData) {
                (1..5).forEach { z ->
                    val w = (timeInZone[z] ?: 0L).toFloat() / total
                    if (w > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(w)
                                .fillMaxHeight()
                                .background(ZoneColors[z - 1])
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (1..5).forEach { z ->
                val secs = timeInZone[z] ?: 0L
                val isTarget = z == targetZone
                val label = "Zone $z: ${formatDuration(secs)}${if (isTarget) ", Zielzone" else ""}"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .semantics { contentDescription = label }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(ZoneColors[z - 1], CircleShape)
                        )
                        Text(
                            "Z$z",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                            color = if (isTarget) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatDuration(secs),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                        color = if (isTarget) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
