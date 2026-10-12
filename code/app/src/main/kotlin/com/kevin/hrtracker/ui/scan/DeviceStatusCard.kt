package com.kevin.hrtracker.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevin.hrtracker.R
import com.kevin.shared.format.formatPercent
import com.kevin.shared.ui.components.ListCard
import com.kevin.shared.ui.components.TextActionButton
import com.kevin.shared.ui.theme.AppTheme
import com.kevin.hrtracker.ui.currentLocale

/**
 * Spec §7.2 Scan: "<name> · <status>" plus "Battery n %" when the Battery Service answered;
 * otherwise status (and name, if known) only. Accent only on the ready dot and the Auto switch.
 */
@Composable
fun DeviceStatusCard(
    statusText: String,
    ready: Boolean,
    connected: Boolean,
    deviceName: String?,
    batteryLevel: Int?,
    autoConnect: Boolean,
    onDisconnect: () -> Unit,
    onToggleAutoConnect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
    ) {
        ListCard(
            title = deviceName?.let { stringResource(R.string.scan_status_title, it, statusText) } ?: statusText,
            subtitle = batteryLevel?.let {
                stringResource(R.string.scan_battery, formatPercent(it / 100f, locale))
            },
            modifier = Modifier.weight(1f),
            leading = {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (ready) MaterialTheme.colorScheme.primary else AppTheme.colors.textDisabled,
                            CircleShape,
                        )
                )
            },
            trailing = if (connected) {
                { TextActionButton(text = stringResource(R.string.scan_disconnect), onClick = onDisconnect) }
            } else null,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.scan_auto_connect).uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = autoConnect, onCheckedChange = onToggleAutoConnect)
        }
    }
}
