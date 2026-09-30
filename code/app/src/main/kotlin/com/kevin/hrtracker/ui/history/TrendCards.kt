package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevin.hrtracker.domain.AcwrZone
import com.kevin.hrtracker.domain.LoadMetric
import com.kevin.hrtracker.domain.ReadinessStatus
import com.kevin.hrtracker.domain.ReadinessSummary
import com.kevin.hrtracker.domain.TrainingLoad
import com.kevin.hrtracker.ui.shared.TrendChart
import com.kevin.hrtracker.ui.shared.TrendSeries
import com.kevin.hrtracker.ui.theme.ConnectedGreen
import com.kevin.hrtracker.ui.theme.ErrorRed
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.SecondaryBlue
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

private val CautionOrange = Color(0xFFFFB74D)

private fun Double.fmt(decimals: Int) = String.format(Locale.GERMANY, "%.${decimals}f", this)

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
                Box(Modifier.weight(1f)) { CardTitle("Trainingslast") }
                FilterChip(
                    selected = metric == LoadMetric.TRIMP,
                    onClick = { onMetricChange(LoadMetric.TRIMP) },
                    label = { Text("TRIMP") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = metric == LoadMetric.SRPE,
                    onClick = { onMetricChange(LoadMetric.SRPE) },
                    label = { Text("sRPE") }
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
                Metric("AKUT (7 T)", today?.acute?.roundToInt()?.toString() ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric("CHRONISCH (Ø 4 W)", today?.chronic?.roundToInt()?.toString() ?: "—", Modifier.weight(1f), SecondaryBlue)
                Metric("ACWR", acwr?.fmt(2) ?: "—", Modifier.weight(0.7f), zoneColor)
            }
            Hint(
                when (zone) {
                    AcwrZone.LOW -> "Unter 0,8: Belastung liegt unter deinem Niveau – Steigerung möglich."
                    AcwrZone.OPTIMAL -> "0,8–1,3: Belastung passt zu deinem Trainingsniveau."
                    AcwrZone.CAUTION -> "1,3–1,5: Deutlicher Anstieg – nicht weiter steigern."
                    AcwrZone.HIGH -> "Über 1,5: Belastungsspitze – erhöhtes Verletzungsrisiko."
                    null -> "ACWR erscheint nach 4 Wochen mit Trainingsdaten."
                },
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
                Legend("Akut" to PrimaryPurple, "Chronisch" to SecondaryBlue)
                Hint("Letzte ${state.days.size} Tage, Last pro Woche.")
            }
            if (metric == LoadMetric.SRPE) {
                Hint(
                    if (state.unratedLast28 > 0)
                        "${state.unratedLast28} Training(s) der letzten 4 Wochen ohne RPE – im Detail-Screen bewerten, sonst fehlt deren Last."
                    else "sRPE = Belastung (0–10) × aktive Minuten."
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
            CardTitle("Bereitschaft (Ruhe-HRV)")
            if (readiness == null) {
                Hint(
                    "Noch keine HRV-Messungen. Miss morgens direkt nach dem Aufwachen, liegend, " +
                        "1–5 min über „HRV messen“ im Start-Screen."
                )
                return@Column
            }
            val (statusText, statusColor) = when (readiness.status) {
                ReadinessStatus.BELOW -> "Unter Normalbereich – Erholung priorisieren" to CautionOrange
                ReadinessStatus.NORMAL -> "Im Normalbereich" to ConnectedGreen
                ReadinessStatus.ABOVE -> "Über Normalbereich" to SecondaryBlue
                null -> "Noch keine Einordnung" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(Modifier.fillMaxWidth()) {
                Metric("RMSSD Ø 7 T", readiness.rmssd7?.let { "$it ms" } ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric("MESSUNGEN 7 T", readiness.measurementsLast7.toString(), Modifier.weight(1f))
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            if (readiness.status == null) {
                Hint("Einordnung ab 3 Messungen in 7 Tagen und 7 Messungen insgesamt.")
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
            Hint(
                "Punkte: Tagesmessung, Linie: 7-Tage-Ø (ln RMSSD), Fläche: Normalbereich" +
                    (readiness.normalLow?.let { " (${exp(it).roundToInt()}–${exp(readiness.normalHigh!!).roundToInt()} ms)" } ?: "") +
                    ". Letzte ${readiness.days.size} Tage."
            )
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
            CardTitle("Ruhepuls")
            val measured = readiness?.restingHr7
            Row(Modifier.fillMaxWidth()) {
                Metric("GEMESSEN Ø 7 T", measured?.let { "$it bpm" } ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric("EINGESTELLT", currentRestingHr?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatisch übernehmen", style = MaterialTheme.typography.bodyMedium)
                    Hint("Nach jeder HRV-Messung, sobald 3 Messungen in 7 Tagen vorliegen. Bestimmt Karvonen-Zonen und TRIMP künftiger Trainings.")
                }
                Switch(checked = autoRestingHr, onCheckedChange = onAutoRestingHrChange)
            }
            if (!autoRestingHr && measured != null && measured != currentRestingHr) {
                OutlinedButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text("$measured bpm jetzt übernehmen")
                }
            }
        }
    }
}

@Composable
private fun HrrTrendCard(trend: HistoryViewModel.HrrTrend) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Herzfrequenz-Erholung (HRR60)")
            if (trend.points.isEmpty()) {
                Hint("Noch keine Trainings mit HRR60 in den letzten 8 Wochen (braucht einen Peak ≥ 70 % HRmax mit 60 s Nachlauf).")
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                Metric("Ø LETZTE 4 W", trend.avgLast4Weeks?.let { "${it.fmt(1)} bpm" } ?: "—", Modifier.weight(1f), PrimaryPurple)
                Metric("Ø 4 W DAVOR", trend.avgPrev4Weeks?.let { "${it.fmt(1)} bpm" } ?: "—", Modifier.weight(1f))
            }
            val cur = trend.avgLast4Weeks
            val prev = trend.avgPrev4Weeks
            if (cur != null && prev != null) {
                val delta = cur - prev
                Hint(
                    when {
                        delta >= 1.0 -> "▲ ${delta.fmt(1)} bpm schnellere Erholung als in den 4 Wochen davor."
                        delta <= -1.0 -> "▼ ${(-delta).fmt(1)} bpm langsamere Erholung als in den 4 Wochen davor."
                        else -> "Erholung stabil gegenüber den 4 Wochen davor."
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
            Hint("Je Training ein Punkt (letzte 8 Wochen), Linie: Ø letzte 4 Wochen. Höher = schnellere Erholung.")
        }
    }
}
