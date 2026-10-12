package com.kevin.hrtracker.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.kevin.hrtracker.R
import com.kevin.hrtracker.data.entity.Milestone
import com.kevin.hrtracker.data.entity.isHrvMeasurement
import com.kevin.shared.format.DurationStyle
import com.kevin.shared.format.formatElapsed
import com.kevin.shared.ui.chart.ChartMarker
import com.kevin.shared.ui.chart.ChartReferenceLine
import com.kevin.shared.ui.chart.ChartViewport
import com.kevin.shared.ui.chart.ChartZone
import com.kevin.shared.ui.chart.LineChart
import com.kevin.shared.ui.chart.TimeInZoneSection
import com.kevin.shared.ui.chart.chartZoomPan
import com.kevin.shared.ui.chart.plotFraction
import com.kevin.hrtracker.domain.HrvQuality
import com.kevin.hrtracker.domain.HrvUnreliableReason
import com.kevin.shared.ui.chart.ChartToggleButton
import com.kevin.shared.ui.LabelPickerDialog
import androidx.compose.foundation.shape.CircleShape
import java.time.ZoneId
import com.kevin.hrtracker.domain.HrrRating
import com.kevin.shared.format.DateStyle
import com.kevin.shared.format.NO_VALUE
import com.kevin.shared.format.formatDate
import com.kevin.shared.format.formatDecimal
import com.kevin.shared.ui.chart.ChartDefaults
import com.kevin.shared.ui.components.ListCard
import com.kevin.shared.ui.components.Metric
import com.kevin.shared.ui.components.PrimaryButton
import com.kevin.shared.ui.components.SecondaryButton
import com.kevin.shared.ui.components.StatCell
import com.kevin.shared.ui.components.StatGrid
import com.kevin.shared.ui.components.TextActionButton
import com.kevin.shared.ui.theme.AppTheme

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
    val chartSeries by viewModel.chartSeries.collectAsStateWithLifecycle()
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
    var chartWidthPx by remember { mutableFloatStateOf(0f) }
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

    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
    ) {
        // Header: tappable label + date/duration meta, training-zone tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable { showEditDialog = true }) {
                Text(
                    text = session?.label?.uppercase() ?: stringResource(R.string.detail_label_fallback),
                    style = MaterialTheme.typography.headlineSmall
                )
                session?.let { s ->
                    Text(
                        text = stringResource(
                            R.string.detail_header_meta,
                            formatDate(s.startedAt, DateStyle.ShortDate, locale, zone),
                            activeSeconds?.let { formatElapsed(it, DurationStyle.Compact) } ?: NO_VALUE
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            dominantZone.let { zoneNo -> // ponytail: VM default is 3, never null; same value feeds LineChart
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.detail_training_zone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = AppTheme.pillShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, AppTheme.colors.zones[zoneNo - 1])
                    ) {
                        Text(
                            "Z$zoneNo",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.xs)
                        )
                    }
                }
            }
        }

        // Chart toolbar: visible range + reset zoom, scale toggle
        val totalSessionSeconds = session?.endedAt?.let { end ->
            ((end - (session?.startedAt ?: end)) / 1000L)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!viewport.isFull) {
                // Zeitbereich des sichtbaren Ausschnitts + Zurücksetzen
                if (totalSessionSeconds != null && totalSessionSeconds > 0) {
                    val fromSec = (viewport.start * totalSessionSeconds).toLong()
                    val toSec = (viewport.end * totalSessionSeconds).toLong()
                    Text(
                        text = "${formatElapsed(fromSec, DurationStyle.Compact)} – ${formatElapsed(toSec, DurationStyle.Compact)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewport = ChartViewport.Full }) {
                    Icon(
                        Icons.Default.ZoomOut,
                        contentDescription = stringResource(R.string.detail_zoom_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // BPM Zone Chart
        // Mindest-Sichtbreite: 20 s (Fallback 5 %)
        val totalSeconds = totalSessionSeconds?.toFloat() ?: 0f
        val minSpan = if (totalSeconds > 20f) 20f / totalSeconds else 0.05f
        var scrubX by remember { mutableStateOf<Float?>(null) }
        val haptic = LocalHapticFeedback.current
        val zoneLabelFmt = stringResource(R.string.live_chart_zone_label)
        val meanFmt = stringResource(R.string.detail_chart_mean)
        val scrubFmt = stringResource(R.string.live_chart_scrub_label)
        val milestoneFmt = stringResource(R.string.live_chart_milestone_label)
        LineChart(
            series = chartSeries,
            window = viewport.toWindow(totalSeconds),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .onSizeChanged { chartWidthPx = it.width.toFloat() }
                .chartZoomPan(
                    panEnabled = { !viewport.isFull },
                    onZoom = { factor, x ->
                        viewport = viewport.zoomBy(factor, plotFraction(x, 0f, chartWidthPx), minSpan)
                    },
                    onPan = { dxPx -> viewport = viewport.panBy(dxPx / chartWidthPx.coerceAtLeast(1f)) },
                    onDoubleTap = { x ->
                        viewport = if (!viewport.isFull) ChartViewport.Full
                        else viewport.zoomBy(3f, plotFraction(x, 0f, chartWidthPx), minSpan)
                    },
                    onScrub = { x ->
                        if (scrubX == null && x != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubX = x
                    }
                ),
            zones = zoneBounds.map { z ->
                ChartZone(z.zone, z.lo.toFloat(), z.hi.toFloat(), String.format(locale, zoneLabelFmt, z.zone, z.lo))
            },
            targetZone = dominantZone,
            goalLabel = stringResource(R.string.live_chart_target_badge),
            dynamicScale = dynamicScale,
            referenceLine = stats?.let {
                ChartReferenceLine(it.avgBpm.toFloat(), String.format(locale, meanFmt, it.avgBpm))
            },
            markers = chartMilestones.mapIndexed { i, m ->
                ChartMarker(m.atSeconds.toFloat(), String.format(locale, milestoneFmt, i + 1))
            },
            scrubFraction = scrubX?.let { plotFraction(it, 0f, chartWidthPx) },
            scrubLabel = { x, y ->
                String.format(locale, scrubFmt, y.roundToInt(), formatElapsed(x.toLong(), DurationStyle.Compact))
            }
        )

        // Meilensteine
        if (milestones.isNotEmpty()) {
            Text(
                text = stringResource(R.string.detail_milestones),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val markerColor = ChartDefaults.colors().marker
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                milestones.forEachIndexed { i, milestone ->
                    ListCard(
                        title = stringResource(
                            R.string.detail_milestone_title,
                            i + 1,
                            milestone.label.ifBlank { stringResource(R.string.detail_milestone_fallback) }
                        ),
                        subtitle = formatElapsed(milestone.atSeconds, DurationStyle.Compact),
                        onClick = {
                            editingMilestoneId = milestone.id
                            editingMilestoneLabel = milestone.label
                        },
                        leading = { Box(Modifier.size(8.dp).background(markerColor, CircleShape)) }
                    )
                }
            }
        }

        // Zeit in Zone
        TimeInZoneSection(
            durations = (1..5).map { timeInZone[it] ?: 0L },
            durationText = { formatElapsed(it, DurationStyle.Clock) }, // timeInZone is in seconds
            title = stringResource(R.string.common_time_in_zone_title),
            targetZone = dominantZone,
            targetSummary = stringResource(
                R.string.common_time_in_zone_percent_in_target, ((percentInTargetZone ?: 0f) * 100).toInt()
            )
        )

        // Stats grid 3x3
        StatGrid(
            cells = listOf(
                StatCell(stringResource(R.string.detail_stat_avg_bpm), stats?.avgBpm?.toString() ?: NO_VALUE, "bpm"),
                StatCell(stringResource(R.string.detail_stat_active), activeSeconds?.let { formatElapsed(it, DurationStyle.Hms) } ?: NO_VALUE),
                StatCell(stringResource(R.string.detail_stat_max_bpm), stats?.maxBpm?.toString() ?: NO_VALUE, "bpm"),
                StatCell(
                    "RMSSD",
                    hrv?.rmssd?.let { formatDecimal(it.toDouble(), 0, locale) } ?: NO_VALUE,
                    "ms",
                    valueColor = if (hrvReason != null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                ),
                StatCell("TRIMP", trimp?.toString() ?: NO_VALUE),
                StatCell(stringResource(R.string.detail_stat_min_bpm), stats?.minBpm?.toString() ?: NO_VALUE, "bpm"),
                StatCell(stringResource(R.string.detail_stat_calories), calories?.toString() ?: NO_VALUE, "kcal"),
                StatCell("HRMAX", session?.maxHrUsed?.toString() ?: NO_VALUE, "bpm"),
                StatCell(stringResource(R.string.detail_stat_resting_hr), session?.restingHr?.toString() ?: NO_VALUE, "bpm"),
            ),
            columns = 3
        )

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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (calories == null && session?.endedAt != null) {
            Text(
                if (bodyDataMissing) stringResource(R.string.detail_calories_body_data_missing)
                else stringResource(R.string.detail_calories_not_enough_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // RPE (session-RPE) + HRR60
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
        ) {
            if (session?.isHrvMeasurement == false) {
                val rpe = session?.rpe
                DetailCard(Modifier.weight(1f).fillMaxHeight()) {
                    Metric("RPE", rpe?.toString() ?: NO_VALUE)
                    Text(
                        if (rpe == null) stringResource(R.string.detail_rpe_rate)
                        else srpeLoad?.let { stringResource(R.string.detail_rpe_value_with_load, rpe, stringResource(rpeLabelRes(rpe)), it) }
                            ?: stringResource(R.string.detail_rpe_value, rpe, stringResource(rpeLabelRes(rpe))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextActionButton(stringResource(R.string.detail_rpe_rate_description), { showRpeDialog = true })
                }
            }
            DetailCard(Modifier.weight(1f).fillMaxHeight()) {
                val hrr = recovery
                Metric("HRR60", hrr?.hrr60?.toString() ?: NO_VALUE, unit = "bpm")
                if (hrr != null) {
                    val good = hrr.rating == HrrRating.GUT || hrr.rating == HrrRating.SEHR_GUT
                    val tagColor = if (good) AppTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = AppTheme.pillShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, tagColor)
                    ) {
                        Text(
                            stringResource(
                                when (hrr.rating) {
                                    HrrRating.NIEDRIG -> R.string.detail_hrr_rating_low
                                    HrrRating.NORMAL -> R.string.detail_hrr_rating_normal
                                    HrrRating.GUT -> R.string.detail_hrr_rating_good
                                    HrrRating.SEHR_GUT -> R.string.detail_hrr_rating_very_good
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = tagColor,
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.xs)
                        )
                    }
                    if (hrr.recoveredToTarget && hrr.secondsToTarget != null) {
                        Text(
                            stringResource(R.string.detail_hrr_recovered_after, hrr.secondsToTarget),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.detail_hrr_peak, hrr.peakBpm, hrr.hrAt60s),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.detail_hrr_not_enough_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Note
        val noteText = session?.note
        DetailCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.detail_note_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (noteText.isNullOrBlank()) stringResource(R.string.detail_note_add) else noteText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (noteText.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
            )
            TextActionButton(stringResource(R.string.detail_note_edit), { showNoteDialog = true })
        }

        // Actions
        val exportJsonChooser = stringResource(R.string.detail_export_json)
        val exportCsvChooser = stringResource(R.string.detail_export_csv_chooser)
        PrimaryButton(
            stringResource(R.string.detail_generate_report),
            { reportJson = viewModel.generateReport() },
            Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            SecondaryButton(
                stringResource(R.string.detail_export_json),
                {
                    scope.launch {
                        val intent = viewModel.export(context) ?: return@launch
                        context.startActivity(Intent.createChooser(intent, exportJsonChooser))
                    }
                },
                Modifier.weight(1f)
            )
            SecondaryButton(
                stringResource(R.string.detail_export_csv_button),
                {
                    scope.launch {
                        val intent = viewModel.exportCsv(context) ?: return@launch
                        context.startActivity(Intent.createChooser(intent, exportCsvChooser))
                    }
                },
                Modifier.weight(1f)
            )
        }
    }
}

/** Bordered surface card used by the RPE / HRR60 / note blocks. */
@Composable
private fun DetailCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
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
