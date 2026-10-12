package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.BorderStroke
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
import com.kevin.shared.format.NO_VALUE
import com.kevin.shared.format.formatDecimal
import com.kevin.shared.format.formatMeasure
import com.kevin.shared.ui.chart.ChartDefaults
import com.kevin.shared.ui.chart.TrendChart
import com.kevin.shared.ui.chart.TrendSeries
import com.kevin.shared.ui.components.Metric
import com.kevin.shared.ui.components.SecondaryButton
import com.kevin.shared.ui.components.SegmentedControl
import com.kevin.shared.ui.theme.AppTheme
import com.kevin.hrtracker.ui.currentLocale
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

private fun Double.fmt(decimals: Int) = String.format(Locale.getDefault(), "%.${decimals}f", this)

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

// --- Trainingslast ---

@Composable
fun LoadCard(
    state: HistoryViewModel.LoadState,
    metric: LoadMetric,
    onMetricChange: (LoadMetric) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            val locale = currentLocale()
            Text(
                stringResource(R.string.history_load_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SegmentedControl(
                listOf("TRIMP", "sRPE"), // i18n-ignore: unit/acronym, same in every language
                selectedIndex = metric.ordinal,
                onSelect = { onMetricChange(LoadMetric.entries[it]) }
            )

            val today = state.days.lastOrNull()
            val acwr = today?.acwr
            val zone = acwr?.let { TrainingLoad.zoneOf(it) }
            val zoneColor = when (zone) {
                AcwrZone.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                AcwrZone.OPTIMAL -> AppTheme.colors.success
                AcwrZone.CAUTION -> AppTheme.colors.warning
                AcwrZone.HIGH -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val divider = MaterialTheme.colorScheme.outline
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Metric(
                    stringResource(R.string.history_load_acute),
                    today?.let { formatDecimal(it.acute, 0, locale) } ?: NO_VALUE,
                    Modifier.weight(1f)
                )
                VerticalDivider(color = divider)
                Metric(
                    stringResource(R.string.history_load_chronic),
                    today?.let { formatDecimal(it.chronic, 0, locale) } ?: NO_VALUE,
                    Modifier.weight(1f)
                )
                VerticalDivider(color = divider)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Metric(
                        "ACWR", // i18n-ignore: acronym, same in every language
                        acwr?.let { formatDecimal(it, 2, locale, minDecimals = 2) } ?: NO_VALUE
                    )
                    if (zone != null) {
                        Surface(
                            shape = AppTheme.pillShape,
                            border = BorderStroke(1.dp, zoneColor),
                            color = Color.Transparent
                        ) {
                            Text(
                                stringResource(
                                    when (zone) {
                                        AcwrZone.LOW -> R.string.stats_acwr_low
                                        AcwrZone.OPTIMAL -> R.string.stats_acwr_optimal
                                        AcwrZone.CAUTION -> R.string.stats_acwr_caution
                                        AcwrZone.HIGH -> R.string.stats_acwr_high
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = zoneColor,
                                modifier = Modifier.padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs)
                            )
                        }
                    }
                }
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
                zoneColor
            )

            if (state.days.isNotEmpty()) {
                // Band = AcwrZone.OPTIMAL range, see TrainingLoad.zoneOf (0.8..1.3).
                TrendChart(
                    series = listOf(TrendSeries(acwrSeries(state.days.map { it.acwr }), highlightLast = true)),
                    band = 0.8f..1.3f,
                    bandLabel = "${formatDecimal(0.8, 1, locale)} – ${formatDecimal(1.3, 1, locale)}",
                    xLabels = state.days.map { stringResource(R.string.stats_week_short, isoWeek(it.date)) },
                    maxXLabels = 4,
                    contentDescription = stringResource(R.string.stats_acwr_cd),
                    modifier = Modifier.fillMaxWidth().height(110.dp)
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
    onApplyRestingHr: () -> Unit,
    onMeasureHrv: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
        item { ReadinessCard(readiness) }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
                RestingHrCard(readiness, currentRestingHr, autoRestingHr, onAutoRestingHrChange, onApplyRestingHr, Modifier.weight(1f).fillMaxHeight())
                HrrCard(hrrTrend, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item { HrrTrendCard(hrrTrend) }
        item { SecondaryButton(stringResource(R.string.scan_measure_hrv), onMeasureHrv, Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(AppTheme.spacing.lg)) }
    }
}

@Composable
private fun ReadinessCard(readiness: ReadinessSummary?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(AppTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            val locale = currentLocale()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_readiness_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                val ready = readiness?.status?.let { it != ReadinessStatus.BELOW }
                if (ready != null) {
                    Surface(
                        shape = AppTheme.pillShape,
                        color = if (ready) AppTheme.colors.accentSoft else Color.Transparent,
                        border = if (ready) null else BorderStroke(1.dp, AppTheme.colors.warning)
                    ) {
                        Text(
                            stringResource(if (ready) R.string.form_ready else R.string.form_recover),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ready) MaterialTheme.colorScheme.primary else AppTheme.colors.warning,
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs)
                        )
                    }
                }
            }
            if (readiness == null) {
                Hint(stringResource(R.string.history_readiness_empty))
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                Text(
                    readiness.rmssd7?.let { formatDecimal(it.toDouble(), 0, locale) } ?: NO_VALUE,
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    "ms RMSSD", // i18n-ignore: unit/acronym, same in every language
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppTheme.spacing.sm)
                )
            }
            baselineDelta(readiness.rmssd7?.toDouble(), readiness.normalLow, readiness.normalHigh)?.let { (b, d) ->
                Hint(stringResource(R.string.form_baseline, formatMeasure(b, "ms", 0, locale), signedPercent(d, locale)))
            }
            if (readiness.status == null) {
                val msg = when (readiness.block) {
                    ReadinessBlock.TOO_FEW_RECENT -> stringResource(
                        R.string.history_readiness_too_few_recent, readiness.measurementsLast7, Readiness.MIN_ROLLING
                    )
                    else -> stringResource(
                        R.string.history_readiness_baseline_building, readiness.baselineCount, Readiness.MIN_BASELINE
                    )
                }
                Hint(msg)
            }
            when (readiness.posture) {
                HrvPosture.LYING -> Hint(stringResource(R.string.history_readiness_posture_lying))
                HrvPosture.SITTING -> Hint(stringResource(R.string.history_readiness_posture_sitting))
                HrvPosture.STANDING -> Hint(stringResource(R.string.history_readiness_posture_standing))
                null -> Unit
            }
            TrendChart(
                series = listOf(
                    TrendSeries(readiness.days.map { it.lnRmssd?.toFloat() ?: Float.NaN }, color = ChartDefaults.colors().secondaryLine, lines = false, dots = true),
                    TrendSeries(readiness.days.map { it.rolling7?.toFloat() ?: Float.NaN }, highlightLast = true)
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
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(AppTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            val measured = readiness?.restingHr7
            Metric(
                stringResource(R.string.history_resting_hr_title),
                currentRestingHr?.toString() ?: NO_VALUE,
                unit = "bpm" // i18n-ignore: unit, same in every language
            )
            restingDelta(currentRestingHr, measured?.toDouble())?.let {
                Hint(stringResource(R.string.form_resting_delta, signedInt(it)))
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
private fun HrrCard(trend: HistoryViewModel.HrrTrend, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(AppTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            Metric(
                "HRR60", // i18n-ignore: acronym, same in every language
                trend.points.lastOrNull()?.let { formatDecimal(it.second.toDouble(), 0, Locale.getDefault()) } ?: NO_VALUE,
                unit = "bpm" // i18n-ignore: unit, same in every language
            )
            Hint(stringResource(R.string.form_hrr_last))
        }
    }
}

@Composable
private fun HrrTrendCard(trend: HistoryViewModel.HrrTrend) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            CardTitle(stringResource(R.string.history_hrr_title))
            if (trend.points.isEmpty()) {
                Hint(stringResource(R.string.history_hrr_empty))
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.history_hrr_avg_last_4w), trend.avgLast4Weeks?.let { "${it.fmt(1)} bpm" } ?: "—", Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
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
                        delta >= 1.0 -> AppTheme.colors.success
                        delta <= -1.0 -> AppTheme.colors.warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            TrendChart(
                series = listOf(TrendSeries(trend.points.map { it.second.toFloat() }, dots = true, highlightLast = true)),
                referenceY = cur?.toFloat(),
                modifier = Modifier.fillMaxWidth().height(90.dp)
            )
            Hint(stringResource(R.string.history_hrr_chart_hint))
        }
    }
}
