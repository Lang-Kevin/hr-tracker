package com.kevin.hrtracker.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.kevin.hrtracker.R
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.data.entity.isHrvMeasurement
import com.kevin.hrtracker.ui.formatDuration
import com.kevin.hrtracker.ui.shared.BpmZoneChart
import com.kevin.hrtracker.ui.shared.ChartViewport
import com.kevin.hrtracker.ui.shared.chartZoomPan
import com.kevin.hrtracker.domain.HrvQuality
import com.kevin.hrtracker.domain.HrvUnreliableReason
import com.kevin.shared.ui.StatItem
import com.kevin.shared.ui.chart.ChartToggleButton
import com.kevin.hrtracker.ui.shared.ZeitInZoneSection
import com.kevin.shared.ui.theme.BackgroundDark
import com.kevin.shared.ui.theme.OnPrimary
import com.kevin.shared.ui.theme.PrimaryPurple
import com.kevin.shared.ui.session.durationString
import com.kevin.shared.ui.LabelPickerDialog
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun DetailScreen(
    viewModel: DetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val bpmHistory by viewModel.bpmHistory.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val zoneBounds by viewModel.zoneBounds.collectAsStateWithLifecycle()
    val timeInZone by viewModel.timeInZone.collectAsStateWithLifecycle()
    val dominantZone by viewModel.dominantZone.collectAsStateWithLifecycle()
    val percentInTargetZone by viewModel.percentInTargetZone.collectAsStateWithLifecycle()
    val hrv by viewModel.hrv.collectAsStateWithLifecycle()
    val hrvReason by viewModel.hrvReason.collectAsStateWithLifecycle()
    val trimp by viewModel.trimp.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val bodyDataMissing by viewModel.bodyDataMissing.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val trainingLabels by viewModel.trainingLabels.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    val chartDynamicScaleDefault by viewModel.chartDynamicScaleDefault.collectAsStateWithLifecycle()
    val gapFractions by viewModel.gapFractions.collectAsStateWithLifecycle()
    val activeSeconds by viewModel.activeSeconds.collectAsStateWithLifecycle()
    val srpeLoad by viewModel.srpeLoad.collectAsStateWithLifecycle()
    val sampleFractions by viewModel.sampleFractions.collectAsStateWithLifecycle()
    val chartMilestones by viewModel.chartMilestones.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var reportJson by remember { mutableStateOf<String?>(null) }
    var dynamicScaleOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val dynamicScale = dynamicScaleOverride ?: chartDynamicScaleDefault
    // Zoom/Pan-Ausschnitt des Diagramms (Anteile 0..1 der Sitzung)
    var viewport by rememberSaveable(
        stateSaver = listSaver<ChartViewport, Float>(
            save = { listOf(it.start, it.end) },
            restore = { ChartViewport(it[0], it[1]) }
        )
    ) { mutableStateOf(ChartViewport.Full) }
    var chartWidthPx by remember { mutableStateOf(0) }
    val chartLeftPadPx = with(LocalDensity.current) { 54.dp.toPx() }
    var editingMilestoneId by remember { mutableStateOf<Long?>(null) }
    var editingMilestoneLabel by remember { mutableStateOf("") }
    var showRpeDialog by remember { mutableStateOf(false) }
    var rpeAutoPrompted by rememberSaveable { mutableStateOf(false) }

    // Nach Session-Ende einmalig nach der Belastung fragen (nicht bei HRV-Messungen)
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        if (viewModel.askRpeOnOpen && !rpeAutoPrompted && s.rpe == null && !s.isHrvMeasurement) {
            showRpeDialog = true
        }
        rpeAutoPrompted = true
    }

    if (showRpeDialog) {
        RpeDialog(
            current = session?.rpe,
            onSave = { rpe ->
                viewModel.updateRpe(rpe)
                showRpeDialog = false
            },
            onDismiss = { showRpeDialog = false }
        )
    }

    // ponytail: compute reachedZones from existing timeInZone map (zones with duration > 0)
    val reachedZones = timeInZone.filter { it.value > 0 }.keys

    if (showEditDialog) {
        LabelPickerDialog(
            title = stringResource(R.string.detail_change_type_title),
            items = trainingLabels,
            initialSelection = session?.label ?: trainingLabels.firstOrNull()?.name,
            confirmText = stringResource(R.string.detail_save),
            onConfirm = { label ->
                viewModel.updateLabel(label)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
            dismissText = null,
            addFieldLabel = null,
            onAdd = null,
            onDelete = null
        )
    }

    if (showNoteDialog) {
        EditNoteDialog(
            current = session?.note ?: "",
            onSave = { note ->
                viewModel.updateNote(note)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false }
        )
    }

    if (reportJson != null) {
        val clipboard = LocalClipboardManager.current
        val reportCopiedText = stringResource(R.string.detail_report_copied)
        AlertDialog(
            onDismissRequest = { reportJson = null },
            title = { Text(stringResource(R.string.detail_report_title)) },
            text = {
                SelectionContainer {
                    Text(reportJson!!)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(reportJson!!))
                    Toast.makeText(context, reportCopiedText, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.detail_report_copy)) }
            },
            confirmButton = {
                TextButton(onClick = { reportJson = null }) { Text(stringResource(R.string.detail_report_close)) }
            }
        )
    }

    if (editingMilestoneId != null) {
        AlertDialog(
            onDismissRequest = { editingMilestoneId = null },
            title = { Text(stringResource(R.string.detail_milestone_name_title)) },
            text = {
                OutlinedTextField(
                    value = editingMilestoneLabel,
                    onValueChange = { editingMilestoneLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.detail_milestone_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateMilestoneLabel(editingMilestoneId!!, editingMilestoneLabel)
                        editingMilestoneId = null
                    },
                    enabled = editingMilestoneLabel.trim().isNotEmpty()
                ) { Text(stringResource(R.string.detail_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingMilestoneId = null }) { Text(stringResource(R.string.detail_cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header: tappable label + date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clickable { showEditDialog = true }) {
                Text(
                    text = session?.label?.uppercase() ?: stringResource(R.string.detail_label_fallback),
                    color = PrimaryPurple,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            session?.let { s ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        SimpleDateFormat("dd.MM.yy", LocalConfiguration.current.locales[0]).format(Date(s.startedAt)),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    s.endedAt?.let { end ->
                        Text(
                            durationString((end - s.startedAt) / 1000),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // BPM Zone Chart Header with Toggle
        val totalSessionSeconds = session?.endedAt?.let { end ->
            ((end - (session?.startedAt ?: end)) / 1000L)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.detail_training_zone),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!viewport.isFull) {
                    // Zeitbereich des sichtbaren Ausschnitts + Zurücksetzen
                    if (totalSessionSeconds != null && totalSessionSeconds > 0) {
                        val fromSec = (viewport.start * totalSessionSeconds).toLong()
                        val toSec = (viewport.end * totalSessionSeconds).toLong()
                        Text(
                            text = "${formatChartTime(fromSec)} – ${formatChartTime(toSec)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewport = ChartViewport.Full }) {
                        Icon(
                            Icons.Default.ZoomOut,
                            contentDescription = stringResource(R.string.detail_zoom_reset),
                            tint = PrimaryPurple
                        )
                    }
                }
                ChartToggleButton(
                    checked = dynamicScale,
                    onCheckedChange = { dynamicScaleOverride = it },
                    icon = if (dynamicScale) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap,
                    contentDescription = if (dynamicScale)
                        stringResource(R.string.detail_scale_dynamic) else
                        stringResource(R.string.detail_scale_static),
                    contentColor = PrimaryPurple
                )
            }
        }

        // BPM Zone Chart
        // Mindest-Sichtbreite: 20 s (Fallback 5 %)
        val minSpan = if (totalSessionSeconds != null && totalSessionSeconds > 20) 20f / totalSessionSeconds else 0.05f
        val drawWidthPx = (chartWidthPx - chartLeftPadPx).coerceAtLeast(1f)
        var scrubX by remember { mutableStateOf<Float?>(null) }
        val haptic = LocalHapticFeedback.current
        BpmZoneChart(
            bpmHistory = bpmHistory,
            currentBpm = bpmHistory.lastOrNull(),
            zoneBounds = zoneBounds,
            targetZone = dominantZone,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .onSizeChanged { chartWidthPx = it.width }
                .chartZoomPan(
                    panEnabled = { !viewport.isFull },
                    onZoom = { factor, anchorX ->
                        val anchor = ((anchorX - chartLeftPadPx) / drawWidthPx).coerceIn(0f, 1f)
                        viewport = viewport.zoomBy(factor, anchor, minSpan)
                    },
                    onPan = { dxPx -> viewport = viewport.panBy(dxPx / drawWidthPx) },
                    onDoubleTap = { x ->
                        viewport = if (!viewport.isFull) {
                            ChartViewport.Full
                        } else {
                            val anchor = ((x - chartLeftPadPx) / drawWidthPx).coerceIn(0f, 1f)
                            viewport.zoomBy(3f, anchor, minSpan)
                        }
                    },
                    onScrub = { x ->
                        if (scrubX == null && x != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubX = x
                    }
                ),
            dynamicScale = dynamicScale,
            reachedZones = reachedZones,
            detailMilestones = chartMilestones,
            totalSessionSeconds = totalSessionSeconds,
            gaps = gapFractions,
            meanBpm = stats?.avgBpm,
            viewport = viewport,
            scrubX = { scrubX },
            sampleFractions = sampleFractions
        )

        Spacer(Modifier.height(12.dp))

        // Meilensteine
        if (milestones.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_milestones),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    milestones.forEach { milestone ->
                        val timeStr = formatDuration(milestone.atSeconds)
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingMilestoneId = milestone.id
                                    editingMilestoneLabel = milestone.label
                                },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = milestone.label.ifBlank { stringResource(R.string.detail_milestone_fallback) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.detail_edit),
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Zeit in Zone
        ZeitInZoneSection(
            timeInZone = timeInZone,
            percentInTargetZone = percentInTargetZone,
            targetZone = dominantZone
        )

        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem(stringResource(R.string.detail_stat_avg_bpm), stats?.avgBpm?.toString() ?: "—", Modifier.weight(1f))
            StatItem(stringResource(R.string.detail_stat_active), activeSeconds?.let { durationString(it) } ?: "—", Modifier.weight(1f))
            StatItem(stringResource(R.string.detail_stat_max_bpm), stats?.maxBpm?.toString() ?: "—", Modifier.weight(1f), valueColor = PrimaryPurple)
        }

        Spacer(Modifier.height(8.dp))

        // Analytics Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem(
                "RMSSD",
                hrv?.rmssd?.let { "${it}ms" } ?: "—",
                Modifier.weight(1f),
                valueColor = if (hrvReason != null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
            )
            StatItem("TRIMP", trimp?.toString() ?: "—", Modifier.weight(1f), valueColor = PrimaryPurple)
            StatItem(stringResource(R.string.detail_stat_min_bpm), stats?.minBpm?.toString() ?: "—", Modifier.weight(1f))
        }

        val hrvResult = hrv
        if (session?.isHrvMeasurement == true && hrvResult != null) {
            Text(
                when (hrvReason) {
                    null -> stringResource(R.string.detail_hrv_quality, hrvResult.validBeats, hrvResult.artefactPct)
                    HrvUnreliableReason.TOO_SHORT -> stringResource(R.string.detail_hrv_unreliable_too_short)
                    HrvUnreliableReason.TOO_FEW_BEATS ->
                        stringResource(R.string.detail_hrv_unreliable_too_few_beats, hrvResult.validBeats, HrvQuality.MIN_VALID_BEATS)
                    HrvUnreliableReason.TOO_MANY_ARTEFACTS ->
                        stringResource(R.string.detail_hrv_unreliable_too_many_artefacts, hrvResult.artefactPct, HrvQuality.MAX_ARTEFACT_PCT.toInt())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Kalorien + Zonen-Basis
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatItem(
                stringResource(R.string.detail_stat_calories),
                calories?.let { "$it kcal" } ?: "—",
                Modifier.weight(1f),
                valueColor = PrimaryPurple
            )
            StatItem("HRMAX", session?.maxHrUsed?.toString() ?: "—", Modifier.weight(1f))
            StatItem(stringResource(R.string.detail_stat_resting_hr), session?.restingHr?.toString() ?: "—", Modifier.weight(1f))
        }

        if (calories == null && session?.endedAt != null) {
            Text(
                if (bodyDataMissing) stringResource(R.string.detail_calories_body_data_missing)
                else stringResource(R.string.detail_calories_not_enough_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Subjektive Belastung (Session-RPE)
        if (session?.isHrvMeasurement == false) {
            Spacer(Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showRpeDialog = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.detail_rpe_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val rpe = session?.rpe
                        Text(
                            if (rpe == null) stringResource(R.string.detail_rpe_rate)
                            else srpeLoad?.let { stringResource(R.string.detail_rpe_value_with_load, rpe, stringResource(rpeLabelRes(rpe)), it) }
                                ?: stringResource(R.string.detail_rpe_value, rpe, stringResource(rpeLabelRes(rpe))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rpe == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        )
                    }
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.detail_rpe_rate_description),
                        tint = PrimaryPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Heart Rate Recovery Card
        recovery?.let { hrr ->
            val ratingColor = when (hrr.rating) {
                com.kevin.hrtracker.domain.HrrRating.NIEDRIG -> MaterialTheme.colorScheme.errorContainer
                com.kevin.hrtracker.domain.HrrRating.NORMAL -> MaterialTheme.colorScheme.tertiary
                com.kevin.hrtracker.domain.HrrRating.GUT -> MaterialTheme.colorScheme.primary
                com.kevin.hrtracker.domain.HrrRating.SEHR_GUT -> MaterialTheme.colorScheme.primary
            }
            val ratingText = stringResource(
                when (hrr.rating) {
                    com.kevin.hrtracker.domain.HrrRating.NIEDRIG -> R.string.detail_hrr_rating_low
                    com.kevin.hrtracker.domain.HrrRating.NORMAL -> R.string.detail_hrr_rating_normal
                    com.kevin.hrtracker.domain.HrrRating.GUT -> R.string.detail_hrr_rating_good
                    com.kevin.hrtracker.domain.HrrRating.SEHR_GUT -> R.string.detail_hrr_rating_very_good
                }
            )
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.detail_hrr_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${hrr.hrr60} bpm",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Surface(
                            color = ratingColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                ratingText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (hrr.recoveredToTarget && hrr.secondsToTarget != null) {
                        Text(
                            stringResource(R.string.detail_hrr_recovered_after, hrr.secondsToTarget),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        stringResource(R.string.detail_hrr_peak, hrr.peakBpm, hrr.hrAt60s),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } ?: OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.detail_hrr_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("—", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.detail_hrr_not_enough_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Note field
        val noteText = session?.note
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { showNoteDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.detail_note_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (noteText.isNullOrBlank()) stringResource(R.string.detail_note_add) else noteText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (noteText.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.detail_note_edit),
                    tint = PrimaryPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        val exportJsonChooser = stringResource(R.string.detail_export_json)
        val exportCsvChooser = stringResource(R.string.detail_export_csv_chooser)
        Button(
            onClick = {
                scope.launch {
                    val intent = viewModel.export(context) ?: return@launch
                    context.startActivity(Intent.createChooser(intent, exportJsonChooser))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryPurple,
                contentColor = OnPrimary
            )
        ) { Text(stringResource(R.string.detail_export_json)) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val intent = viewModel.exportCsv(context) ?: return@launch
                    context.startActivity(Intent.createChooser(intent, exportCsvChooser))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, PrimaryPurple),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
        ) { Text(stringResource(R.string.detail_export_csv_button)) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                val json = viewModel.generateReport()
                reportJson = json
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, PrimaryPurple),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
        ) { Text(stringResource(R.string.detail_generate_report)) }
    }
}

@Composable
private fun EditNoteDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.detail_note_placeholder)) },
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.detail_save)) }
        }
    )
}

/** Zeitangabe für den Diagramm-Ausschnitt: mm:ss, ab einer Stunde h:mm:ss. */
private fun formatChartTime(totalSeconds: Long): String =
    if (totalSeconds >= 3600) formatDuration(totalSeconds)
    else "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** Borg CR-10 Kurzlabels. */
@StringRes
private fun rpeLabelRes(rpe: Int): Int = when (rpe) {
    0 -> R.string.detail_rpe_label_0
    1 -> R.string.detail_rpe_label_1
    2 -> R.string.detail_rpe_label_2
    3 -> R.string.detail_rpe_label_3
    4 -> R.string.detail_rpe_label_4
    5, 6 -> R.string.detail_rpe_label_5_6
    7, 8, 9 -> R.string.detail_rpe_label_7_9
    else -> R.string.detail_rpe_label_10
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpeDialog(
    current: Int?,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_rpe_dialog_title)) },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..10).forEach { value ->
                        FilterChip(
                            selected = selected == value,
                            onClick = { selected = value },
                            label = { Text("$value") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(selected?.let { rpeLabelRes(it) } ?: R.string.detail_rpe_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }, enabled = selected != null) { Text(stringResource(R.string.detail_save)) }
        },
        dismissButton = {
            TextButton(onClick = if (current != null) ({ onSave(null) }) else onDismiss) {
                Text(stringResource(if (current != null) R.string.detail_rpe_remove else R.string.detail_rpe_later))
            }
        }
    )
}
