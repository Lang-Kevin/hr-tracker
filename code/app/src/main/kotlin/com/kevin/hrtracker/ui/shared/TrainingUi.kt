package com.kevin.hrtracker.ui.shared

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.hrtracker.ui.theme.LightPurple
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.SurfaceDark
import com.kevin.hrtracker.ui.theme.ZoneColors

@Composable
fun BpmZoneChart(
    bpmHistory: List<Int>,
    currentBpm: Int?,
    zoneBounds: List<ZoneBounds>,
    targetZone: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        if (zoneBounds.isEmpty()) return@Canvas

        val leftPaddingPx = with(density) { 54.dp.toPx() }
        val chartWidth = size.width - leftPaddingPx

        val bpmMin = zoneBounds.minOf { it.lo } - 8
        val bpmMax = zoneBounds.maxOf { it.hi } + 8
        val bpmRange = (bpmMax - bpmMin).toFloat()

        fun bpmToY(bpm: Int): Float =
            size.height * (1f - (bpm - bpmMin).toFloat() / bpmRange)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
            color = android.graphics.Color.argb(160, 255, 255, 255)
        }

        zoneBounds.forEach { z ->
            val y = bpmToY(z.lo)
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(leftPaddingPx, y),
                end = Offset(size.width, y),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
            val yCentre = bpmToY((z.lo + z.hi) / 2)
            drawContext.canvas.nativeCanvas.drawText(
                "Z${z.zone} ${z.lo}",
                4f,
                yCentre + labelPaint.textSize / 3f,
                labelPaint
            )
        }
        zoneBounds.lastOrNull()?.let { z ->
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(leftPaddingPx, bpmToY(z.hi)),
                end = Offset(size.width, bpmToY(z.hi)),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
        }

        zoneBounds.getOrNull(targetZone - 1)?.let { zBound ->
            val yTop = bpmToY(zBound.hi)
            val yBottom = bpmToY(zBound.lo)
            drawRect(
                color = ZoneColors[targetZone - 1].copy(alpha = 0.18f),
                topLeft = Offset(leftPaddingPx, yTop),
                size = Size(chartWidth, yBottom - yTop)
            )
        }

        zoneBounds.getOrNull(targetZone - 1)?.let { zBound ->
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

        if (bpmHistory.size >= 2) {
            val path = Path()
            bpmHistory.forEachIndexed { index, bpm ->
                val x = leftPaddingPx + (index.toFloat() / (bpmHistory.size - 1)) * chartWidth
                val y = bpmToY(bpm.coerceIn(bpmMin, bpmMax))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = with(density) { 2.dp.toPx() },
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            val lastBpm = bpmHistory.last()
            val lastX = leftPaddingPx + chartWidth
            val lastY = bpmToY(lastBpm.coerceIn(bpmMin, bpmMax))

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
}

@Composable
fun ZeitInZoneSection(
    timeInZone: Map<Int, Long>,
    percentInTargetZone: Float?,
    targetZone: Int,
    onZoneClick: (Int) -> Unit = {}
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
                        .clickable { onZoneClick(z) }
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

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
