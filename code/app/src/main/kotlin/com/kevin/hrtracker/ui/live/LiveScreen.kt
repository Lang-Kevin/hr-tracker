package com.kevin.hrtracker.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.kevin.hrtracker.ui.currentLocale
import com.kevin.shared.ui.chart.chartZoomPan
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
import androidx.compose.ui.draw.scale
import com.kevin.shared.ui.chart.ChartToggleButton
import com.kevin.shared.ui.theme.AppTheme
import com.kevin.shared.ui.components.Metric
import androidx.compose.ui.unit.dp
import com.kevin.shared.ui.session.LeaveSessionDialog
import com.kevin.shared.ui.zone.TargetZoneDialog
import com.kevin.shared.ui.overlay.TutorialSpotlight
import kotlin.math.roundToInt
import com.kevin.shared.ui.overlay.TutorialStep
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.shared.ui.overlay.rememberTutorialAnchors
import com.kevin.shared.ui.overlay.tutorialAnchor

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
            zoneCount = 5
        )
    }

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("live").collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.md)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = (sessionLabel ?: stringResource(R.string.live_title_default)).uppercase(currentLocale()),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatElapsed(elapsed, DurationStyle.Hms),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.live_bpm_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.live_heartbeat_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(pulseScale.value)
                    )
                    Text(
                        text = currentBpm?.toString() ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.sm))

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
                    Surface(
                        onClick = { window = LiveWindow(window.windowSeconds) },
                        shape = AppTheme.pillShape,
                        color = AppTheme.colors.accentSoft
                    ) {
                        Text(
                            text = stringResource(R.string.live_tag_live),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.xs)
                        )
                    }
                }
                if (window.windowSeconds.roundToInt() != LiveWindow.DEFAULT) {
                    Text(
                        text = stringResource(
                            R.string.live_window,
                            formatElapsed(window.windowSeconds.toLong(), DurationStyle.Clock)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = AppTheme.spacing.sm)
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
        val locale = currentLocale()
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

        Spacer(Modifier.height(AppTheme.spacing.md))

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

        Spacer(Modifier.height(AppTheme.spacing.md))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Metric(stringResource(R.string.live_stat_avg_bpm), averageBpm?.toString() ?: "—", Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Metric(
                stringResource(R.string.live_stat_target_zone),
                stringResource(R.string.live_zone_value, targetZone),
                Modifier
                    .weight(1f)
                    .tutorialAnchor(tutorialAnchors, "live_zone_stat")
                    .clickable { showTargetZoneDialog = true }
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            if (hrvCountdown != null) {
                val cr = hrvCountdown ?: 0
                Metric(
                    stringResource(R.string.live_stat_remaining),
                    "%02d:%02d".format(cr / 60, cr % 60),
                    Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            } else {
                Metric(stringResource(R.string.live_stat_total_time), formatElapsed(elapsed, DurationStyle.Hms), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.lg))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val outline = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            IconButton(
                onClick = { viewModel.togglePause() },
                modifier = Modifier.size(48.dp).border(outline, CircleShape).tutorialAnchor(tutorialAnchors, "live_pause")
            ) {
                Icon(
                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) stringResource(R.string.live_resume_cd) else stringResource(R.string.live_pause_cd)
                )
            }
            IconButton(
                onClick = { viewModel.addMilestone() },
                modifier = Modifier.size(48.dp).border(outline, CircleShape).tutorialAnchor(tutorialAnchors, "live_milestone")
            ) {
                Icon(Icons.Default.Flag, contentDescription = stringResource(R.string.live_milestone_cd))
            }
            IconButton(
                onClick = { showLeaveDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), CircleShape)
                    .tutorialAnchor(tutorialAnchors, "live_stop")
            ) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.live_finish_cd), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

        if (connectionLost) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.xxl)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.live_connection_lost),
                    color = AppTheme.colors.warning,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.md)
                )
            }
        }

        TutorialSpotlight(
            steps = listOf(
                TutorialStep("live_chart", stringResource(R.string.live_tutorial_chart_title), stringResource(R.string.live_tutorial_chart_text)),
                TutorialStep("live_zone_stat", stringResource(R.string.live_tutorial_zone_title), stringResource(R.string.live_tutorial_zone_text)),
                TutorialStep("live_pause", stringResource(R.string.live_tutorial_pause_title), stringResource(R.string.live_tutorial_pause_text)),
                TutorialStep("live_milestone", stringResource(R.string.live_tutorial_milestone_title), stringResource(R.string.live_tutorial_milestone_text)),
                TutorialStep("live_stop", stringResource(R.string.live_tutorial_finish_title), stringResource(R.string.live_tutorial_finish_text))
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("live") },
            skipLabel = stringResource(R.string.common_tutorial_skip),
            nextLabel = { n, m -> stringResource(R.string.common_tutorial_next_progress, n, m) },
            doneLabel = stringResource(R.string.common_tutorial_done),
        )
    }
}
