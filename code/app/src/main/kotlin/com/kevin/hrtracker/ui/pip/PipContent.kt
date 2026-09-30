package com.kevin.hrtracker.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.R
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.shared.ui.theme.ZoneColors

@Composable
fun PipContent(viewModel: PipViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color = state.zone?.let { ZoneColors.getOrElse(it - 1) { fallback } } ?: fallback

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state.variant) {
            WidgetVariant.MINIMAL -> MinimalLayout(state, accent)
            WidgetVariant.STANDARD -> StandardLayout(state, accent)
            WidgetVariant.ZONE -> ZoneLayout(state, accent)
            WidgetVariant.TIMER -> TimerLayout(state, accent)
        }
    }
}

@Composable
private fun MinimalLayout(state: PipUiState, accent: Color) {
    BpmRow(state.bpm, accent, valueSize = 48.sp, iconSize = 30.dp)
    if (state.paused) PauseLine()
}

@Composable
private fun StandardLayout(state: PipUiState, accent: Color) {
    BpmRow(state.bpm, accent, valueSize = 34.sp, iconSize = 24.dp)
    Text(
        text = state.zone?.let { stringResource(R.string.pip_zone_value, it) } ?: stringResource(R.string.pip_zone_none),
        fontSize = 14.sp,
        color = accent
    )
    Text(
        text = if (state.paused) stringResource(R.string.pip_paused) else state.elapsedText,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ZoneLayout(state: PipUiState, accent: Color) {
    Text(
        text = state.zone?.toString() ?: "--",
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = accent
    )
    Text(
        text = stringResource(R.string.pip_zone_caption),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    BpmRow(state.bpm, accent, valueSize = 18.sp, iconSize = 14.dp)
    if (state.paused) PauseLine()
}

@Composable
private fun TimerLayout(state: PipUiState, accent: Color) {
    Text(
        text = if (state.paused) stringResource(R.string.pip_paused) else state.elapsedText,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    BpmRow(state.bpm, accent, valueSize = 18.sp, iconSize = 14.dp)
}

@Composable
private fun BpmRow(bpm: Int?, accent: Color, valueSize: TextUnit, iconSize: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = bpm?.toString() ?: "--",
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.pip_bpm_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PauseLine() {
    Text(
        text = stringResource(R.string.pip_paused),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
