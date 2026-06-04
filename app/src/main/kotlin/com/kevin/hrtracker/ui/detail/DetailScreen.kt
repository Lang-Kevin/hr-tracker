package com.kevin.hrtracker.ui.detail

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kevin.hrtracker.ui.history.durationString

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val zoneDistrib by viewModel.zoneDistribution.collectAsStateWithLifecycle()

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(samples) {
        if (samples.size >= 2) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction { lineSeries { series(samples.map { it.bpm }) } }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text(
                session?.label ?: "Session",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        session?.let { s ->
            Text(
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(s.startedAt)),
                style = MaterialTheme.typography.bodyMedium
            )
            s.endedAt?.let { end ->
                Text(
                    "Dauer: ${durationString((end - s.startedAt) / 1000)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (samples.size >= 2) {
            Text("BPM-Verlauf", style = MaterialTheme.typography.titleSmall)
            CartesianChartHost(
                chart = rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        } else if (samples.isEmpty()) {
            Text("Keine Samples aufgezeichnet.", style = MaterialTheme.typography.bodySmall)
        }

        stats?.let { st ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Statistik", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    StatRow("Ø BPM", "${st.avgBpm}")
                    StatRow("Max BPM", "${st.maxBpm}")
                    StatRow("Min BPM", "${st.minBpm}")
                    StatRow("Samples", "${st.sampleCount}")
                }
            }
        }

        if (zoneDistrib.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Zeit pro Zone", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    val totalSec = zoneDistrib.values.sum().coerceAtLeast(1)
                    for (z in 1..5) {
                        val sec = zoneDistrib[z] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Z$z", modifier = Modifier.width(28.dp))
                            LinearProgressIndicator(
                                progress = { sec / totalSec.toFloat() },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text("%02d:%02d".format(sec / 60, sec % 60),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    val intent = viewModel.export(context) ?: return@launch
                    context.startActivity(Intent.createChooser(intent, "Session exportieren"))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Als JSON exportieren")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
