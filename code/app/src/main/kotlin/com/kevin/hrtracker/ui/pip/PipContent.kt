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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.R
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.shared.ui.theme.AppTheme

@Composable
fun PipContent(viewModel: PipViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color = state.zone?.let { AppTheme.colors.zones.getOrElse(it - 1) { fallback } } ?: fallback

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs),
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
    BpmRow(state.bpm, accent, valueStyle = MaterialTheme.typography.displayMedium, iconSize = 30.dp)
    if (state.paused) PauseLine()
}

@Composable
private fun StandardLayout(state: PipUiState, accent: Color) {
    BpmRow(state.bpm, accent, valueStyle = MaterialTheme.typography.headlineLarge, iconSize = 24.dp)
    Text(
        text = state.zone?.let { stringResource(R.string.pip_zone_value, it) } ?: stringResource(R.string.pip_zone_none),
        style = MaterialTheme.typography.bodyMedium,
        color = accent
    )
    Text(
        text = if (state.paused) stringResource(R.string.pip_paused) else state.elapsedText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ZoneLayout(state: PipUiState, accent: Color) {
    Text(
        text = state.zone?.toString() ?: "--",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = accent
    )
    Text(
        text = stringResource(R.string.pip_zone_caption),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    BpmRow(state.bpm, accent, valueStyle = MaterialTheme.typography.titleMedium, iconSize = 14.dp)
    if (state.paused) PauseLine()
}

@Composable
private fun TimerLayout(state: PipUiState, accent: Color) {
    Text(
        text = if (state.paused) stringResource(R.string.pip_paused) else state.elapsedText,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    BpmRow(state.bpm, accent, valueStyle = MaterialTheme.typography.titleMedium, iconSize = 14.dp)
}

@Composable
private fun BpmRow(bpm: Int?, accent: Color, valueStyle: TextStyle, iconSize: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(AppTheme.spacing.sm))
        Text(
            text = bpm?.toString() ?: "--",
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(AppTheme.spacing.xs))
        Text(
            text = stringResource(R.string.pip_bpm_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PauseLine() {
    Text(
        text = stringResource(R.string.pip_paused),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
