package com.kevin.hrtracker.ui.live

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.kevin.hrtracker.R
import com.kevin.shared.ui.chart.ChartMarker
import com.kevin.shared.ui.chart.ChartZone
import com.kevin.shared.ui.chart.LineChart
import com.kevin.shared.ui.chart.LineSeries
import com.kevin.shared.ui.chart.LiveWindow
import com.kevin.shared.ui.chart.TimeInZoneSection
import com.kevin.shared.ui.chart.plotFraction
import com.kevin.shared.format.DurationStyle
import com.kevin.shared.format.formatElapsed
import java.util.Locale
import com.kevin.shared.ui.chart.chartZoomPan
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.kevin.shared.ui.StatItem
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.shared.ui.theme.BackgroundDark
import com.kevin.shared.ui.theme.OnPrimary
import com.kevin.shared.ui.theme.PrimaryPurple
import com.kevin.shared.ui.theme.TertiaryPink
import com.kevin.shared.ui.theme.ZoneColors
import com.kevin.shared.ui.session.LeaveSessionDialog
import com.kevin.shared.ui.zone.TargetZoneDialog
import com.kevin.hrtracker.ui.tutorial.TutorialOverlay
import kotlin.math.min
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
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val connectionLost by viewModel.connectionLost.collectAsStateWithLifecycle()
    val milestoneSampleIdx by viewModel.milestoneSampleIndices.collectAsStateWithLifecycle()
    val hrvCountdown by viewModel.hrvCountdown.collectAsStateWithLifecycle()
    val chartDynamicScaleDefault by viewModel.chartDynamicScaleDefault.collectAsStateWithLifecycle()

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
                text = sessionLabel?.uppercase() ?: stringResource(R.string.live_title_default),
                color = PrimaryPurple,
                style = MaterialTheme.typography.titleLarge
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.live_bpm_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.live_heartbeat_cd),
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
                        label = { Text(stringResource(R.string.live_chip_live)) },
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
                    stringResource(R.string.live_scale_dynamic_cd) else
                    stringResource(R.string.live_scale_static_cd)
            )
        }

        // BPM Zone Chart
        val total = bpmHistory.size
        val locale = Locale.getDefault()
        val zoneLabelFmt = stringResource(R.string.live_chart_zone_label)
        val bpmFmt = stringResource(R.string.live_chart_current_bpm)
        val scrubFmt = stringResource(R.string.live_chart_scrub_label)
        val milestoneFmt = stringResource(R.string.live_chart_milestone_label)
        LineChart(
            series = LineSeries(bpmHistory.map { it.toFloat() }),
            window = window.toWindow(total),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .tutorialAnchor(tutorialAnchors, "live_chart")
                .onSizeChanged { chartWidthPx = it.width }
                .chartZoomPan(
                    panEnabled = { true },
                    onZoom = { factor, x ->
                        window = window.zoomBy(factor, plotFraction(x, 0f, chartWidthPx.toFloat()), total)
                    },
                    onPan = { dx ->
                        val visibleSeconds = window.visibleRange(total).count()
                        // dx > 0 (Finger nach rechts) -> ältere Daten -> positive Verschiebung
                        window = window.panBy(dx * visibleSeconds / chartWidthPx.coerceAtLeast(1), total)
                    },
                    onDoubleTap = { window = LiveWindow() },
                    onScrub = { x ->
                        if (scrubX == null && x != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubX = x
                    }
                ),
            zones = zoneBounds.map { z ->
                ChartZone(z.zone, z.lo.toFloat(), z.hi.toFloat(), String.format(locale, zoneLabelFmt, z.zone, z.lo))
            },
            targetZone = targetZone,
            goalLabel = stringResource(R.string.live_chart_target_badge),
            dynamicScale = dynamicScale,
            markers = milestoneSampleIdx.mapIndexed { i, idx ->
                ChartMarker(idx.toFloat(), String.format(locale, milestoneFmt, i + 1))
            },
            lastPoint = window.isFollowing,
            lastPointLabel = { y -> String.format(locale, bpmFmt, currentBpm ?: y.roundToInt()) },
            scrubFraction = scrubX?.let { plotFraction(it, 0f, chartWidthPx.toFloat()) },
            scrubLabel = { x, y ->
                String.format(locale, scrubFmt, y.roundToInt(), formatElapsed(x.toLong(), DurationStyle.Clock))
            }
        )

        Spacer(Modifier.height(12.dp))

        // Zeit in Zone
        TimeInZoneSection(
            durations = (1..5).map { timeInZone[it] ?: 0L },
            durationText = { formatElapsed(it, DurationStyle.Clock) }, // 1 Sample = 1 s
            title = stringResource(R.string.common_time_in_zone_title),
            targetZone = targetZone,
            targetSummary = stringResource(
                R.string.common_time_in_zone_percent_in_target, ((percentInTargetZone ?: 0f) * 100).toInt()
            )
        )

        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem(stringResource(R.string.live_stat_avg_bpm), averageBpm?.toString() ?: "—", Modifier.weight(1f))
            StatItem(
                stringResource(R.string.live_stat_target_zone),
                stringResource(R.string.live_zone_value, targetZone),
                Modifier.weight(1f).tutorialAnchor(tutorialAnchors, "live_zone_stat"),
                valueColor = PrimaryPurple,
                onClick = { showTargetZoneDialog = true }
            )
            if (hrvCountdown != null) {
                val cr = hrvCountdown ?: 0
                StatItem(
                    stringResource(R.string.live_stat_remaining),
                    "%02d:%02d".format(cr / 60, cr % 60),
                    Modifier.weight(1f),
                    valueColor = TertiaryPink
                )
            } else {
                StatItem(stringResource(R.string.live_stat_total_time), formatDuration(elapsed), Modifier.weight(1f))
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
                    contentDescription = if (isPaused) stringResource(R.string.live_resume_cd) else stringResource(R.string.live_pause_cd)
                )
            }
            FilledTonalIconButton(
                onClick = { viewModel.addMilestone() },
                modifier = Modifier.size(56.dp).tutorialAnchor(tutorialAnchors, "live_milestone")
            ) {
                Icon(Icons.Default.Flag, contentDescription = stringResource(R.string.live_milestone_cd))
            }
            FilledIconButton(
                onClick = { showLeaveDialog = true },
                modifier = Modifier.size(56.dp).tutorialAnchor(tutorialAnchors, "live_stop"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.live_finish_cd))
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
                    text = stringResource(R.string.live_connection_lost),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        TutorialOverlay(
            steps = listOf(
                TutorialStep("live_chart", stringResource(R.string.live_tutorial_chart_title), stringResource(R.string.live_tutorial_chart_text)),
                TutorialStep("live_zone_stat", stringResource(R.string.live_tutorial_zone_title), stringResource(R.string.live_tutorial_zone_text)),
                TutorialStep("live_pause", stringResource(R.string.live_tutorial_pause_title), stringResource(R.string.live_tutorial_pause_text)),
                TutorialStep("live_milestone", stringResource(R.string.live_tutorial_milestone_title), stringResource(R.string.live_tutorial_milestone_text)),
                TutorialStep("live_stop", stringResource(R.string.live_tutorial_finish_title), stringResource(R.string.live_tutorial_finish_text))
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("live") }
        )
    }
}
