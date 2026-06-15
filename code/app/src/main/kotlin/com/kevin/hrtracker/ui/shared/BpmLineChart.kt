package com.kevin.hrtracker.ui.shared

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BpmLineChart(bpmData: List<Int>, modifier: Modifier = Modifier.fillMaxWidth()) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(bpmData) {
        if (bpmData.size >= 2) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction { lineSeries { series(bpmData) } }
            }
        }
    }
    if (bpmData.size >= 2) {
        CartesianChartHost(
            chart = rememberCartesianChart(rememberLineCartesianLayer()),
            modelProducer = modelProducer,
            modifier = modifier
        )
    }
}
