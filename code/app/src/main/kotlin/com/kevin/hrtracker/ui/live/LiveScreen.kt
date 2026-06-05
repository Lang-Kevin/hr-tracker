package com.kevin.hrtracker.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.ui.theme.ZoneColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val currentZone by viewModel.currentZone.collectAsStateWithLifecycle()
    val targetZone by viewModel.targetZone.collectAsStateWithLifecycle()
    val timeInZone by viewModel.timeInZone.collectAsStateWithLifecycle()
    val percentInTargetZone by viewModel.percentInTargetZone.collectAsStateWithLifecycle()
    val zoneBounds by viewModel.zoneBounds.collectAsStateWithLifecycle()
    val sessionLabel by viewModel.sessionLabel.collectAsStateWithLifecycle()

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(bpmHistory) {
        if (bpmHistory.size >= 2) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction { lineSeries { series(bpmHistory) } }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text("Live-Training", style = MaterialTheme.typography.headlineMedium)
        if (sessionLabel != null) {
            Text(sessionLabel!!, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Gesamtzeit
        val mm = elapsed / 60
        val ss = elapsed % 60
        Text("Gesamtzeit", style = MaterialTheme.typography.bodySmall)
        Text("%02d:%02d".format(mm, ss), style = MaterialTheme.typography.displaySmall)

        // BPM-Sektion
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                currentZone?.let { zone ->
                    val color = ZoneColors.getOrElse(zone - 1) { MaterialTheme.colorScheme.primary }
                    Surface(color = color, shape = MaterialTheme.shapes.small) {
                        Text(
                            "Zone $zone",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = currentBpm?.let { "$it BPM" } ?: "Warte auf Daten…",
                    style = MaterialTheme.typography.displayLarge
                )
                if (averageBpm != null) {
                    Text(
                        "BPM Ø  $averageBpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Ziel-Zone Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ZIEL", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val tzColor = ZoneColors.getOrElse(targetZone - 1) { MaterialTheme.colorScheme.primary }
                    Surface(color = tzColor, shape = MaterialTheme.shapes.small) {
                        Text(
                            "Zone $targetZone",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
                val tzSeconds = timeInZone[targetZone] ?: 0L
                val tzMm = tzSeconds / 60
                val tzSs = tzSeconds % 60
                Text(
                    "Zeit in Ziel-Zone: %02d:%02d".format(tzMm, tzSs),
                    style = MaterialTheme.typography.bodyMedium
                )
                val pct = percentInTargetZone ?: 0f
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${(pct * 100).toInt()}% in Ziel-Zone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Zeit in Zone Tabelle
        if (zoneBounds.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Zeit in Zone", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    zoneBounds.forEach { z ->
                        val zSeconds = timeInZone[z.zone] ?: 0L
                        val zMm = zSeconds / 60
                        val zSs = zSeconds % 60
                        val zColor = ZoneColors.getOrElse(z.zone - 1) { MaterialTheme.colorScheme.primary }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = zColor,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.size(12.dp)
                                ) {}
                                Text("Z${z.zone}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${z.lo} – ${z.hi}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "%02d:%02d".format(zMm, zSs),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // BPM-Chart
        if (bpmHistory.size >= 2) {
            CartesianChartHost(
                chart = rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onAbortSession,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error
                )
            ) {
                Text("Abbrechen")
            }
            Button(
                onClick = onStopSession,
                modifier = Modifier.weight(1f)
            ) {
                Text("Abschließen")
            }
        }
    }
}
