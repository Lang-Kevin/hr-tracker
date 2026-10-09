package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevin.hrtracker.R
import com.kevin.hrtracker.domain.AcwrZone
import com.kevin.hrtracker.domain.LoadMetric
import com.kevin.hrtracker.domain.HrvPosture
import com.kevin.hrtracker.domain.Readiness
import com.kevin.hrtracker.domain.ReadinessBlock
import com.kevin.hrtracker.domain.ReadinessStatus
import com.kevin.hrtracker.domain.ReadinessSummary
import com.kevin.hrtracker.domain.TrainingLoad
import com.kevin.hrtracker.ui.shared.TrendChart
import com.kevin.hrtracker.ui.shared.TrendSeries
import com.kevin.shared.ui.theme.ConnectedGreen
import com.kevin.shared.ui.theme.ErrorRed
import com.kevin.shared.ui.theme.PrimaryPurple
import com.kevin.shared.ui.theme.SecondaryBlue
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

private val CautionOrange = Color(0xFFFFB74D)

private fun Double.fmt(decimals: Int) = String.format(Locale.getDefault(), "%.${decimals}f", this)

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Legend(vararg entries: Pair<String, Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { (label, color) -> Text("● $label", style = MaterialTheme.typography.labelSmall, color = color) }
    }
}

// --- Trainingslast ---

@Composable
fun LoadCard(
    state: HistoryViewModel.LoadState,
    metric: LoadMetric,
    onMetricChange: (LoadMetric) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.history_load_title)) }
                FilterChip(
                    selected = metric == LoadMetric.TRIMP,
                    onClick = { onMetricChange(LoadMetric.TRIMP) },
                    label = { Text("TRIMP") } // i18n-ignore: unit/acronym, same in every language
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = metric == LoadMetric.SRPE,
                    onClick = { onMetricChange(LoadMetric.SRPE) },
                    label = { Text("sRPE") } // i18n-ignore: unit/acronym, same in every language
                )
            }

            val today = state.days.lastOrNull()
            val acwr = today?.acwr
            val zone = acwr?.let { TrainingLoad.zoneOf(it) }
            val zoneColor = when (zone) {
                AcwrZone.LOW -> SecondaryBlue
                AcwrZone.OPTIMAL -> ConnectedGreen
                AcwrZone.CAUTION -> CautionOrange
                AcwrZone.HIGH -> ErrorRed
                null -> Color.White
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.history_load_acute), today?.acute?.roundToInt()?.toString() ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric(stringResource(R.string.history_load_chronic), today?.chronic?.roundToInt()?.toString() ?: "—", Modifier.weight(1f), SecondaryBlue)
                Metric("ACWR", acwr?.fmt(2) ?: "—", Modifier.weight(0.7f), zoneColor)
            }
            Hint(
                stringResource(
                    when (zone) {
                        AcwrZone.LOW -> R.string.history_load_zone_low
                        AcwrZone.OPTIMAL -> R.string.history_load_zone_optimal
                        AcwrZone.CAUTION -> R.string.history_load_zone_caution
                        AcwrZone.HIGH -> R.string.history_load_zone_high
                        null -> R.string.history_load_zone_none
                    }
                ),
                zoneColor.takeIf { zone != null } ?: MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.days.isNotEmpty()) {
                TrendChart(
                    series = listOf(
                        TrendSeries(state.days.map { it.chronic.toFloat() }, SecondaryBlue),
                        TrendSeries(state.days.map { it.acute.toFloat() }, PrimaryPurple)
                    ),
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
                Legend(
                    stringResource(R.string.history_load_legend_acute) to PrimaryPurple,
                    stringResource(R.string.history_load_legend_chronic) to SecondaryBlue
                )
                Hint(pluralStringResource(R.plurals.history_load_chart_hint, state.days.size, state.days.size))
            }
            if (metric == LoadMetric.SRPE) {
                Hint(
                    if (state.unratedLast28 > 0)
                        pluralStringResource(R.plurals.history_load_unrated, state.unratedLast28, state.unratedLast28)
                    else stringResource(R.string.history_load_srpe_hint)
                )
            }
        }
    }
}

// --- Form ---

@Composable
fun FormTab(
    readiness: ReadinessSummary?,
    hrrTrend: HistoryViewModel.HrrTrend,
    currentRestingHr: Int?,
    autoRestingHr: Boolean,
    onAutoRestingHrChange: (Boolean) -> Unit,
    onApplyRestingHr: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ReadinessCard(readiness) }
        item { RestingHrCard(readiness, currentRestingHr, autoRestingHr, onAutoRestingHrChange, onApplyRestingHr) }
        item { HrrTrendCard(hrrTrend) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ReadinessCard(readiness: ReadinessSummary?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(stringResource(R.string.history_readiness_title))
            if (readiness == null) {
                Hint(stringResource(R.string.history_readiness_empty))
                return@Column
            }
            val (statusText, statusColor) = when (readiness.status) {
                ReadinessStatus.BELOW -> stringResource(R.string.history_readiness_below) to CautionOrange
                ReadinessStatus.NORMAL -> stringResource(R.string.history_readiness_normal) to ConnectedGreen
                ReadinessStatus.ABOVE -> stringResource(R.string.history_readiness_above) to SecondaryBlue
                null -> when (readiness.block) {
                    ReadinessBlock.TOO_FEW_RECENT -> stringResource(
                        R.string.history_readiness_too_few_recent, readiness.measurementsLast7, Readiness.MIN_ROLLING
                    )
                    else -> stringResource(
                        R.string.history_readiness_baseline_building, readiness.baselineCount, Readiness.MIN_BASELINE
                    )
                } to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.history_readiness_rmssd_7d), readiness.rmssd7?.let { "$it ms" } ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric(stringResource(R.string.history_readiness_readings_7d), readiness.measurementsLast7.toString(), Modifier.weight(1f))
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            when (readiness.posture) {
                HrvPosture.LYING -> Hint(stringResource(R.string.history_readiness_posture_lying))
                HrvPosture.SITTING -> Hint(stringResource(R.string.history_readiness_posture_sitting))
                HrvPosture.STANDING -> Hint(stringResource(R.string.history_readiness_posture_standing))
                null -> Unit
            }
            TrendChart(
                series = listOf(
                    TrendSeries(readiness.days.map { it.lnRmssd?.toFloat() }, PrimaryPurple.copy(alpha = 0.6f), lines = false, dots = true),
                    TrendSeries(readiness.days.map { it.rolling7?.toFloat() }, PrimaryPurple)
                ),
                band = if (readiness.normalLow != null && readiness.normalHigh != null)
                    readiness.normalLow.toFloat()..readiness.normalHigh.toFloat() else null,
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )
            val legend = readiness.normalLow?.let {
                stringResource(R.string.history_readiness_legend_range, exp(it).roundToInt(), exp(readiness.normalHigh!!).roundToInt())
            } ?: stringResource(R.string.history_readiness_legend)
            Hint(legend + " " + pluralStringResource(R.plurals.history_readiness_last_days, readiness.days.size, readiness.days.size))
            Hint(stringResource(R.string.history_readiness_disclaimer))
        }
    }
}

@Composable
private fun RestingHrCard(
    readiness: ReadinessSummary?,
    currentRestingHr: Int?,
    autoRestingHr: Boolean,
    onAutoRestingHrChange: (Boolean) -> Unit,
    onApply: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(stringResource(R.string.history_resting_hr_title))
            val measured = readiness?.restingHr7
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.history_resting_hr_measured), measured?.let { "$it bpm" } ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric(stringResource(R.string.history_resting_hr_configured), currentRestingHr?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_resting_hr_auto), style = MaterialTheme.typography.bodyMedium)
                    Hint(stringResource(R.string.history_resting_hr_auto_hint))
                }
                Switch(checked = autoRestingHr, onCheckedChange = onAutoRestingHrChange)
            }
            if (!autoRestingHr && measured != null && measured != currentRestingHr) {
                OutlinedButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_resting_hr_apply, measured))
                }
            }
        }
    }
}

@Composable
private fun HrrTrendCard(trend: HistoryViewModel.HrrTrend) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(stringResource(R.string.history_hrr_title))
            if (trend.points.isEmpty()) {
                Hint(stringResource(R.string.history_hrr_empty))
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.history_hrr_avg_last_4w), trend.avgLast4Weeks?.let { "${it.fmt(1)} bpm" } ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric(stringResource(R.string.history_hrr_avg_prev_4w), trend.avgPrev4Weeks?.let { "${it.fmt(1)} bpm" } ?: "—", Modifier.weight(1f))
            }
            val cur = trend.avgLast4Weeks
            val prev = trend.avgPrev4Weeks
            if (cur != null && prev != null) {
                val delta = cur - prev
                Hint(
                    when {
                        delta >= 1.0 -> stringResource(R.string.history_hrr_faster, delta.fmt(1))
                        delta <= -1.0 -> stringResource(R.string.history_hrr_slower, (-delta).fmt(1))
                        else -> stringResource(R.string.history_hrr_stable)
                    },
                    when {
                        delta >= 1.0 -> ConnectedGreen
                        delta <= -1.0 -> CautionOrange
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            TrendChart(
                series = listOf(TrendSeries(trend.points.map { it.second.toFloat() }, PrimaryPurple, dots = true)),
                referenceY = cur?.toFloat(),
                modifier = Modifier.fillMaxWidth().height(90.dp)
            )
            Hint(stringResource(R.string.history_hrr_chart_hint))
        }
    }
}
