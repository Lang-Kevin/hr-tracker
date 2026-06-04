package com.kevin.hrtracker.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val zoneColors = listOf(
    androidx.compose.ui.graphics.Color(0xFF4CAF50), // Z1 green
    androidx.compose.ui.graphics.Color(0xFF8BC34A), // Z2 light-green
    androidx.compose.ui.graphics.Color(0xFFFF9800), // Z3 orange
    androidx.compose.ui.graphics.Color(0xFFFF5722), // Z4 deep-orange
    androidx.compose.ui.graphics.Color(0xFFF44336)  // Z5 red
)

@Composable
fun LiveScreen(
    onStopSession: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val currentBpm by viewModel.currentBpm.collectAsStateWithLifecycle()
    val bpmHistory by viewModel.bpmHistory.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val currentZone by viewModel.currentZone.collectAsStateWithLifecycle()

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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val mm = elapsed / 60
        val ss = elapsed % 60
        Text("%02d:%02d".format(mm, ss), style = MaterialTheme.typography.displaySmall)

        currentZone?.let { zone ->
            val color = zoneColors.getOrElse(zone - 1) { MaterialTheme.colorScheme.primary }
            Surface(color = color, shape = MaterialTheme.shapes.small) {
                Text(
                    "Zone $zone",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        Text(
            text = currentBpm?.let { "$it BPM" } ?: "Warte auf Daten…",
            style = MaterialTheme.typography.displayLarge
        )

        if (bpmHistory.size >= 2) {
            CartesianChartHost(
                chart = rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStopSession,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Session beenden")
        }
    }
}
