package com.kevin.hrtracker.ui.live

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.draw.scale
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.ui.theme.BackgroundDark
import com.kevin.hrtracker.ui.theme.LightPurple
import com.kevin.hrtracker.ui.theme.OnPrimary
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.SurfaceDark
import com.kevin.hrtracker.ui.theme.TertiaryPink
import com.kevin.hrtracker.ui.theme.ZoneColors

@Composable
fun LiveScreen(
    onStopSession: () -> Unit,
    onAbortSession: () -> Unit = onStopSession,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val currentBpm by viewModel.currentBpm.collectAsStateWithLifecycle()
    val averageBpm by viewModel.averageBpm.collectAsStateWithLifecycle()
    val bpmHistory by viewModel.bpmHistory.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val targetZone by viewModel.targetZone.collectAsStateWithLifecycle()
    val timeInZone by viewModel.timeInZone.collectAsStateWithLifecycle()
    val percentInTargetZone by viewModel.percentInTargetZone.collectAsStateWithLifecycle()
    val zoneBounds by viewModel.zoneBounds.collectAsStateWithLifecycle()
    val sessionLabel by viewModel.sessionLabel.collectAsStateWithLifecycle()
    val lastRrMs by viewModel.lastRrMs.collectAsStateWithLifecycle()

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(lastRrMs) {
        if (lastRrMs != null) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(1.4f, animationSpec = tween(80))
            pulseScale.animateTo(1f, animationSpec = tween(200))
        }
    }

    var showAbortDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }

    if (showAbortDialog) {
        ConfirmDialog(
            title = "Training abbrechen?",
            text = "Die aufgezeichneten Daten werden verworfen und nicht gespeichert.",
            confirmLabel = "Abbrechen",
            onConfirm = { showAbortDialog = false; onAbortSession() },
            onDismiss = { showAbortDialog = false }
        )
    }

    if (showStopDialog) {
        ConfirmDialog(
            title = "Training abschließen?",
            text = "Das Training wird beendet und die Daten werden gespeichert.",
            confirmLabel = "Speichern",
            onConfirm = { showStopDialog = false; onStopSession() },
            onDismiss = { showStopDialog = false }
        )
    }

    val mm = elapsed / 60
    val ss = elapsed % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sessionLabel?.uppercase() ?: "LIVE-TRAINING",
                color = PrimaryPurple,
                style = MaterialTheme.typography.titleLarge
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = TertiaryPink,
                        modifier = Modifier
                            .size(14.dp)
                            .scale(pulseScale.value)
                    )
                    Text(
                        text = currentBpm?.toString() ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // BPM Zone Chart
        BpmZoneChart(
            bpmHistory = bpmHistory,
            currentBpm = currentBpm,
            zoneBounds = zoneBounds,
            targetZone = targetZone,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(Modifier.height(12.dp))

        // Zeit in Zone
        ZeitInZoneSection(
            timeInZone = timeInZone,
            percentInTargetZone = percentInTargetZone,
            targetZone = targetZone,
            onZoneClick = { viewModel.setTargetZone(it) }
        )

        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem("BPM Ø", averageBpm?.toString() ?: "—", Modifier.weight(1f))
            StatItem("ZIEL-ZONE", "Zone $targetZone", Modifier.weight(1f), valueColor = PrimaryPurple)
            StatItem("GESAMTZEIT", "%02d:%02d".format(mm, ss), Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showAbortDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error
                )
            ) { Text("Abbrechen") }
            Button(
                onClick = { showStopDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryPurple,
                    contentColor = OnPrimary
                )
            ) { Text("Abschließen") }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Weiter messen") }
        }
    )
}

@Composable
private fun BpmZoneChart(
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

        // Zone separator lines and Y-axis labels
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
        // Top boundary line for highest zone
        zoneBounds.lastOrNull()?.let { z ->
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(leftPaddingPx, bpmToY(z.hi)),
                end = Offset(size.width, bpmToY(z.hi)),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
        }

        // Target zone highlight band
        zoneBounds.getOrNull(targetZone - 1)?.let { zBound ->
            val yTop = bpmToY(zBound.hi)
            val yBottom = bpmToY(zBound.lo)
            drawRect(
                color = ZoneColors[targetZone - 1].copy(alpha = 0.18f),
                topLeft = Offset(leftPaddingPx, yTop),
                size = Size(chartWidth, yBottom - yTop)
            )
        }

        // ZIEL badge at target zone lower boundary
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

        // BPM history line
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

            // Current BPM dot and label at last point
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
private fun ZeitInZoneSection(
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(pct * 100).toInt()}% in Ziel-Zone",
                color = PrimaryPurple,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Proportional zone distribution bar
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

        // Zone time columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (1..5).forEach { z ->
                val secs = timeInZone[z] ?: 0L
                val isTarget = z == targetZone
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onZoneClick(z) }
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
                        "%02d:%02d".format(secs / 60, secs % 60),
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
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
