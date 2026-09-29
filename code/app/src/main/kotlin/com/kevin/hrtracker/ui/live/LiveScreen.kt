package com.kevin.hrtracker.ui.live

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kevin.hrtracker.ui.shared.LiveWindow
import com.kevin.hrtracker.ui.shared.TIME_AXIS_HEIGHT_DP
import com.kevin.hrtracker.ui.shared.drawTimeAxis
import com.kevin.hrtracker.ui.shared.chartZoomPan
import com.kevin.hrtracker.ui.shared.drawScrubber
import com.kevin.hrtracker.ui.shared.formatTickLabel
import com.kevin.hrtracker.ui.shared.nearestIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.scale
import com.kevin.shared.ui.chart.ChartToggleButton
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.shared.ui.StatItem
import com.kevin.hrtracker.ui.shared.ZeitInZoneSection
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.hrtracker.ui.theme.BackgroundDark
import com.kevin.hrtracker.ui.theme.LightPurple
import com.kevin.hrtracker.ui.theme.OnPrimary
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.TertiaryPink
import com.kevin.hrtracker.ui.theme.ZoneColors
import com.kevin.shared.ui.session.LeaveSessionDialog
import com.kevin.shared.ui.zone.TargetZoneDialog
import com.kevin.hrtracker.ui.tutorial.TutorialOverlay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.roundToInt
import com.kevin.hrtracker.ui.tutorial.TutorialStep
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.hrtracker.ui.tutorial.rememberTutorialAnchors
import com.kevin.hrtracker.ui.tutorial.tutorialAnchor

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
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val reachedZones by viewModel.reachedZones.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val connectionLost by viewModel.connectionLost.collectAsStateWithLifecycle()
    val milestoneSampleIdx by viewModel.milestoneSampleIndices.collectAsStateWithLifecycle()
    val hrvCountdown by viewModel.hrvCountdown.collectAsStateWithLifecycle()
    val chartDynamicScaleDefault by viewModel.chartDynamicScaleDefault.collectAsStateWithLifecycle()

    val leftPadPx = with(LocalDensity.current) { 54.dp.toPx() } // = linker Rand in LiveBpmZoneChart
    // Live-Viewport (Scroll/Zoom). Folgt neuen Samples, solange window.anchorEnd == null; der
    // absolute Anker bleibt beim Anhängen neuer Samples stehen (kein Effect nötig).
    // Hinweis: Erreicht der Verlauf die Kappe (6 h), verschiebt sich die Liste; ein
    // zurückgescrolltes Fenster driftet dann mit (akzeptiert).
    var window by remember { mutableStateOf(LiveWindow()) }
    var scrubX by remember { mutableStateOf<Float?>(null) }
    val haptic = LocalHapticFeedback.current
    var chartWidthPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(bpmHistory.isEmpty()) {
        if (bpmHistory.isEmpty()) window = LiveWindow() // Verlauf zurückgesetzt (neue Session)
    }
    val visibleRange = window.visibleRange(bpmHistory.size)

    LaunchedEffect(hrvCountdown) {
        if (hrvCountdown == 0) onStopSession()
    }

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(lastRrMs) {
        if (lastRrMs != null) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(1.4f, animationSpec = tween(80))
            pulseScale.animateTo(1f, animationSpec = tween(200))
        }
    }

    var showLeaveDialog by remember { mutableStateOf(false) }
    var showTargetZoneDialog by remember { mutableStateOf(false) }
    // Lokaler Toggle überschreibt nur die laufende Session, sonst gilt der Settings-Default
    var dynamicScaleOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val dynamicScale = dynamicScaleOverride ?: chartDynamicScaleDefault

    BackHandler(enabled = activeSessionId != null) { showLeaveDialog = true }

    if (showLeaveDialog) {
        LeaveSessionDialog(
            onSave = { showLeaveDialog = false; onStopSession() },
            onDiscard = { showLeaveDialog = false; onAbortSession() },
            onDismiss = { showLeaveDialog = false }
        )
    }

    if (showTargetZoneDialog) {
        TargetZoneDialog(
            targetZone = targetZone,
            onSelect = { viewModel.setTargetZone(it); showTargetZoneDialog = false },
            onDismiss = { showTargetZoneDialog = false },
            zoneCount = 5,
            zoneColors = ZoneColors
        )
    }

    val mm = elapsed / 60
    val ss = elapsed % 60

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("live").collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
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
                        contentDescription = "Herzschlag",
                        tint = TertiaryPink,
                        modifier = Modifier
                            .size(20.dp)
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

        // Chart header with scale toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!window.isFollowing) {
                    AssistChip(
                        onClick = { window = window.copy(anchorEnd = null) },
                        label = { Text("LIVE") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                if (window.windowSeconds.roundToInt() != LiveWindow.DEFAULT) {
                    val ws = min(window.windowSeconds.roundToInt(), bpmHistory.size.coerceAtLeast(1))
                    Text(
                        text = "%d:%02d".format(ws / 60, ws % 60),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            ChartToggleButton(
                checked = dynamicScale,
                onCheckedChange = { dynamicScaleOverride = it },
                icon = if (dynamicScale) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap,
                contentDescription = if (dynamicScale)
                    "Dynamische Skalierung" else
                    "Statische Skalierung"
            )
        }

        // BPM Zone Chart
        LiveBpmZoneChart(
            bpmHistory = bpmHistory,
            currentBpm = currentBpm,
            zoneBounds = zoneBounds,
            targetZone = targetZone,
            dynamicScale = dynamicScale,
            reachedZones = reachedZones,
            milestoneSampleIndices = milestoneSampleIdx,
            visibleRange = visibleRange,
            following = window.isFollowing,
            scrubX = scrubX,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .tutorialAnchor(tutorialAnchors, "live_chart")
                .onSizeChanged { chartWidthPx = it.width }
                .chartZoomPan(
                    panEnabled = { true },
                    onZoom = { factor, x ->
                        val leftPad = leftPadPx
                        val anchor = ((x - leftPad) / (chartWidthPx - leftPad).coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        window = window.zoomBy(factor, anchor, bpmHistory.size)
                    },
                    onPan = { dx ->
                        val total = bpmHistory.size
                        val visibleSeconds = window.visibleRange(total).count()
                        val drawWidth = (chartWidthPx - leftPadPx).coerceAtLeast(1f)
                        // dx > 0 (Finger nach rechts) -> ältere Daten -> positive Verschiebung
                        window = window.panBy(dx * visibleSeconds / drawWidth, total)
                    },
                    onDoubleTap = { window = LiveWindow() },
                    onScrub = { x ->
                        if (scrubX == null && x != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubX = x
                    }
                )
        )

        Spacer(Modifier.height(12.dp))

        // Zeit in Zone
        ZeitInZoneSection(
            timeInZone = timeInZone,
            percentInTargetZone = percentInTargetZone,
            targetZone = targetZone
        )

        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem("BPM Ø", averageBpm?.toString() ?: "—", Modifier.weight(1f))
            StatItem(
                "ZIEL-ZONE",
                "Zone $targetZone",
                Modifier.weight(1f).tutorialAnchor(tutorialAnchors, "live_zone_stat"),
                valueColor = PrimaryPurple,
                onClick = { showTargetZoneDialog = true }
            )
            if (hrvCountdown != null) {
                val cr = hrvCountdown ?: 0
                StatItem(
                    "VERBLEIBEND",
                    "%02d:%02d".format(cr / 60, cr % 60),
                    Modifier.weight(1f),
                    valueColor = TertiaryPink
                )
            } else {
                StatItem("GESAMTZEIT", formatDuration(elapsed), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalIconButton(
                onClick = { viewModel.togglePause() },
                modifier = Modifier.size(56.dp).tutorialAnchor(tutorialAnchors, "live_pause")
            ) {
                Icon(
                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Fortsetzen" else "Pause"
                )
            }
            FilledTonalIconButton(
                onClick = { viewModel.addMilestone() },
                modifier = Modifier.size(56.dp).tutorialAnchor(tutorialAnchors, "live_milestone")
            ) {
                Icon(Icons.Default.Flag, contentDescription = "Meilenstein setzen")
            }
            FilledIconButton(
                onClick = { showLeaveDialog = true },
                modifier = Modifier.size(56.dp).tutorialAnchor(tutorialAnchors, "live_stop"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Abschließen")
            }
        }
    }

        if (connectionLost) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Verbindung verloren — Messung pausiert",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        TutorialOverlay(
            steps = listOf(
                TutorialStep("live_chart", "BPM-Verlauf", "Hier siehst du deinen Herzfrequenz-Verlauf in Echtzeit, eingefärbt nach Zone. Wische zum Zurückscrollen, ziehe mit zwei Fingern zum Skalieren, Doppeltipp setzt zurück. Lange drücken zeigt den genauen Wert."),
                TutorialStep("live_zone_stat", "Zielzone", "Tippe hier, um deine Zielzone für dieses Training zu ändern."),
                TutorialStep("live_pause", "Pause", "Pausiere die Aufzeichnung, ohne das Training zu beenden."),
                TutorialStep("live_milestone", "Meilenstein", "Setzt eine Markierung im Chart — z. B. für Intervallwechsel oder besondere Momente."),
                TutorialStep("live_stop", "Abschließen", "Beendet das Training und speichert die aufgezeichneten Daten.")
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("live") }
        )
    }
}

/**
 * Live-Chart für die Aufzeichnung. Bewusst NICHT [com.kevin.hrtracker.ui.shared.BpmZoneChart]:
 * 1. Positionierung: hier Index ins sichtbare Fenster ([visibleRange], per `LiveWindow`-Viewport
 *    scroll-/zoombar, Standard 120 s; [bpmHistory] ist die gesamte Session, Kappe 6 h im
 *    [LiveViewModel]), dort anteilig zur Gesamt-Sessiondauer.
 * 2. `Milestone` braucht `label` + `sessionId`; live existieren nur Sekunden-Timestamps,
 *    persistiert wird erst am Session-Ende (LiveViewModel.kt:189-191).
 * 3. Die öffentliche Variante filtert bei `dynamicScale` auf `reachedZones` — live sollen alle
 *    Zonenlinien stehenbleiben, auch die noch nicht erreichten.
 *
 * ponytail: kein Adapter, keine Umwandlung — dieser Fork bleibt bestehen.
 */
@Composable
private fun LiveBpmZoneChart(
    bpmHistory: List<Int>,
    currentBpm: Int?,
    zoneBounds: List<ZoneBounds>,
    targetZone: Int,
    dynamicScale: Boolean = false,
    reachedZones: Set<Int> = emptySet(),
    milestoneSampleIndices: List<Int> = emptyList(),
    visibleRange: IntRange = bpmHistory.indices,
    following: Boolean = true,
    scrubX: Float? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val visible: List<Int> = remember(bpmHistory, visibleRange) {
        if (visibleRange.isEmpty() || visibleRange.first < 0 || visibleRange.last >= bpmHistory.size) {
            emptyList()
        } else {
            bpmHistory.subList(visibleRange.first, visibleRange.last + 1)
        }
    }

    Canvas(modifier = modifier.clipToBounds()) {
        if (zoneBounds.isEmpty()) return@Canvas

        val leftPaddingPx = with(density) { 54.dp.toPx() }
        val chartWidth = size.width - leftPaddingPx
        // Unten Platz für die Zeitachsen-Beschriftung reservieren.
        val axisPx = with(density) { TIME_AXIS_HEIGHT_DP.dp.toPx() }
        val plotHeight = (size.height - axisPx).coerceAtLeast(1f)
        val targetBound = zoneBounds.getOrNull(targetZone - 1)

        val bpmMin: Float
        val bpmMax: Float

        if (dynamicScale && visible.isNotEmpty()) {
            var lo = visible[0]
            var hi = visible[0]
            for (i in 1 until visible.size) {
                val v = visible[i]
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            val actualMin = lo.toFloat()
            val actualMax = hi.toFloat()
            val pad = (actualMax - actualMin).coerceAtLeast(1f) * 0.10f
            // Zielband/ZIEL-Badge rechnen mit zoneBounds — Skala muss die Zielzone einschließen,
            // sonst landet bpmToY() für das Band außerhalb [0, size.height].
            bpmMin = if (targetBound != null) min(actualMin - pad, targetBound.lo.toFloat()) else actualMin - pad
            bpmMax = if (targetBound != null) max(actualMax + pad, targetBound.hi.toFloat()) else actualMax + pad
        } else {
            bpmMin = (zoneBounds.minOf { it.lo } - 8).toFloat()
            bpmMax = (zoneBounds.maxOf { it.hi } + 8).toFloat()
        }

        val bpmRange = bpmMax - bpmMin

        fun bpmToY(bpm: Float): Float =
            plotHeight * (1f - (bpm - bpmMin) / bpmRange)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10.sp.toPx() }
            color = android.graphics.Color.argb(160, 255, 255, 255)
        }

        // Zeitachse: 1 Sample ≈ 1 s seit Sessionstart
        if (visible.isNotEmpty()) {
            drawTimeAxis(
                fromSec = visibleRange.first.toFloat(),
                toSec = visibleRange.last.toFloat(),
                leftPaddingPx = leftPaddingPx,
                plotHeight = plotHeight,
                labelPaint = labelPaint,
                density = density
            )
        }

        // Zone separator lines and Y-axis labels
        zoneBounds.forEach { z ->
            val y = bpmToY(z.lo.toFloat())
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(leftPaddingPx, y),
                end = Offset(size.width, y),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
            val yCentre = bpmToY(((z.lo + z.hi) / 2).toFloat())
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
        // Top boundary line for highest zone
        zoneBounds.lastOrNull()?.let { z ->
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(leftPaddingPx, bpmToY(z.hi.toFloat())),
                end = Offset(size.width, bpmToY(z.hi.toFloat())),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
        }

        // Target zone highlight band
        targetBound?.let { zBound ->
            val yTop = bpmToY(zBound.hi.toFloat())
            val yBottom = bpmToY(zBound.lo.toFloat())
            drawRect(
                color = ZoneColors[targetZone - 1].copy(alpha = 0.18f),
                topLeft = Offset(leftPaddingPx, yTop),
                size = Size(chartWidth, yBottom - yTop)
            )
        }

        // ZIEL badge at target zone lower boundary
        targetBound?.let { zBound ->
            val yZiel = bpmToY(zBound.lo.toFloat())
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

        // Milestone vertical lines — anchored to their sample index, scroll with the data
        if (visible.size >= 2 && milestoneSampleIndices.isNotEmpty()) {
            val milestonePaint = Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 9.sp.toPx() }
                color = android.graphics.Color.argb(200, 255, 200, 80)
                textAlign = Paint.Align.CENTER
            }
            milestoneSampleIndices.forEachIndexed { idx, absIndex ->
                if (absIndex in visibleRange) {
                    val x = leftPaddingPx +
                        ((absIndex - visibleRange.first).toFloat() / (visible.size - 1)) * chartWidth
                    drawLine(
                        color = Color(0xFFFFC850).copy(alpha = 0.6f),
                        start = Offset(x, 0f),
                        end = Offset(x, plotHeight),
                        strokeWidth = with(density) { 1.5.dp.toPx() }
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "M${idx + 1}",
                        x,
                        with(density) { 12.sp.toPx() },
                        milestonePaint
                    )
                }
            }
        }

        // BPM history line
        if (visible.size >= 2) {
            // Dezimierung mit an absolute Indizes gebundenen Chunks (Grenzen wandern nicht jede
            // Sekunde): Mittelwert je Chunk am Chunk-Mittelpunkt, zuletzt das rohe letzte Sample.
            val first = visibleRange.first
            val last = visibleRange.last
            val span = (last - first).coerceAtLeast(1).toFloat()
            val chunk = ceil(visible.size / 600.0).toInt().coerceAtLeast(1)
            val path = Path()
            var started = false
            fun addPoint(absIdx: Float, bpm: Float) {
                val x = leftPaddingPx + (absIdx - first) / span * chartWidth
                val y = bpmToY(bpm.coerceIn(bpmMin, bpmMax))
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            if (chunk == 1) {
                for (i in visible.indices) addPoint((first + i).toFloat(), visible[i].toFloat())
            } else {
                for (k in (first / chunk)..(last / chunk)) {
                    val gs = max(k * chunk, first)
                    val ge = min((k + 1) * chunk - 1, last)
                    if (gs == last) break // letztes Sample wird unten roh gezeichnet
                    var sum = 0L
                    for (i in gs..ge) sum += visible[i - first]
                    addPoint((gs + ge) / 2f, sum.toFloat() / (ge - gs + 1))
                }
                addPoint(last.toFloat(), visible[visible.size - 1].toFloat())
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

            // Scrubber (langes Drücken): exakter BPM-Wert und Zeit am Finger
            if (scrubX != null && scrubX >= leftPaddingPx && chartWidth > 0f) {
                val idx = nearestIndex((scrubX - leftPaddingPx) / chartWidth, visibleRange)
                if (idx != null) {
                    drawScrubber(
                        x = leftPaddingPx + (idx - first).toFloat() / span * chartWidth,
                        y = bpmToY(bpmHistory[idx].toFloat().coerceIn(bpmMin, bpmMax)),
                        label = "${bpmHistory[idx]} bpm · ${formatTickLabel(idx.toFloat())}",
                        leftPaddingPx = leftPaddingPx,
                        plotHeight = plotHeight,
                        density = density
                    )
                }
            }

            // Current BPM dot and label at last point (nur wenn live folgend)
            if (!following) return@Canvas
            val lastBpm = bpmHistory.last()
            val lastX = leftPaddingPx + chartWidth
            val lastY = bpmToY(lastBpm.toFloat().coerceIn(bpmMin, bpmMax))

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
