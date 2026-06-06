package com.kevin.hrtracker.ui.scan

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kevin.hrtracker.ble.ConnectionState
import com.kevin.hrtracker.ble.ConnectionState.Reconnecting
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import com.kevin.hrtracker.domain.DiscoveredDevice
import com.kevin.hrtracker.domain.DeviceType
import com.kevin.hrtracker.domain.SavedDevice

private val TRAINING_TYPES = listOf(
    "Allgemeines Training",
    "Beachvolleyball",
    "Trainingbike",
    "Volleyball"
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onSessionStarted: (label: String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()

    var showStartDialog by remember { mutableStateOf(false) }
    var showSmartWatchHelp by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    // Reset isStarting once session is confirmed active
    LaunchedEffect(activeSessionId) { if (activeSessionId != null) isStarting = false }

    if (showStartDialog) {
        StartTrainingDialog(
            onStart = { label ->
                showStartDialog = false
                isStarting = true
                onSessionStarted(label)
            },
            onDismiss = { showStartDialog = false }
        )
    }
    if (showSmartWatchHelp) {
        SmartWatchHelpDialog(onDismiss = { showSmartWatchHelp = false })
    }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions)

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) viewModel.startScan()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("HR Tracker", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onNavigateToHistory) { Text("Verlauf") }
            TextButton(onClick = onNavigateToSettings) { Text("⚙") }
        }

        val statusText = when (connectionState) {
            is Reconnecting -> "Verbindung verloren — reconnecting…"
            is ConnectionState.Error -> "Fehler: ${(connectionState as ConnectionState.Error).reason}"
            else -> connectionState::class.simpleName ?: ""
        }
        val statusColor = if (connectionState is ConnectionState.Error)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text("Status: $statusText", style = MaterialTheme.typography.bodyMedium, color = statusColor)
                if (connectionState !is ConnectionState.Disconnected) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.disconnect() }) { Text("Trennen") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Connect", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = autoConnect,
                    onCheckedChange = { viewModel.toggleAutoConnect() }
                )
            }
        }

        if (!permissionState.allPermissionsGranted) {
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Bluetooth-Berechtigung gewähren")
            }
        } else {
            if (connectionState is ConnectionState.Ready) {
                HorizontalDivider()
                if (activeSessionId == null) {
                    Button(
                        onClick = { if (!isStarting) showStartDialog = true },
                        enabled = !isStarting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isStarting) "Starte…" else "Training starten")
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopSession() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Session stoppen  (ID: $activeSessionId)")
                    }
                }
                HorizontalDivider()
            }

            if (savedDevices.isNotEmpty()) {
                Text("Gemerkte Geräte", style = MaterialTheme.typography.titleSmall)
                savedDevices.forEach { device ->
                    SavedDeviceItem(
                        device = device,
                        onClick = { viewModel.connectToSaved(device) },
                        onForget = { viewModel.forgetDevice(device) }
                    )
                }
                HorizontalDivider()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Verfügbare HR-Geräte:", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showSmartWatchHelp = true }) {
                    Text("Smartwatch verbinden ?", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (discoveredDevices.none { it is DiscoveredDevice.Real }) {
                Text("Scan läuft…", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(discoveredDevices, key = { it.address }) { device ->
                    DeviceItem(device = device) { viewModel.connectToDiscovered(device) }
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceItem(device: SavedDevice, onClick: () -> Unit, onForget: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onForget) { Text("Vergessen") }
        }
    }
}

@Composable
private fun DeviceItem(device: DiscoveredDevice, onClick: () -> Unit) {
    val (icon, subtitle) = when {
        device is DiscoveredDevice.Fake -> Icons.Default.Bluetooth to "Simuliertes Testgerät"
        device.deviceType == DeviceType.SMARTWATCH -> Icons.Default.Watch to "Smartwatch · HR-Broadcast"
        device.deviceType == DeviceType.CHEST_STRAP -> Icons.Default.Favorite to "Brustgurt"
        else -> Icons.Default.Bluetooth to device.address
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null)
            Column {
                Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StartTrainingDialog(onStart: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(TRAINING_TYPES[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trainingstyp wählen") },
        text = {
            Column {
                TRAINING_TYPES.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == type, onClick = { selected = type })
                        Spacer(Modifier.width(8.dp))
                        Text(type, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(selected) }) { Text("Starten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun SmartWatchHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smartwatch verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Aktiviere den HR-Broadcast-Modus auf deiner Uhr, dann erscheint sie in der Geräteliste:",
                    style = MaterialTheme.typography.bodySmall
                )
                BrandHint("Garmin", "Einstellungen → Herzfrequenz → HR-Broadcast einschalten")
                BrandHint("Polar", "Polar Flow App → Gerät → HR-Broadcast aktivieren")
                BrandHint("Samsung Galaxy Watch", "Samsung Health → Training → HR-Monitor → Externe Messung")
                BrandHint("Suunto", "SuuntoLink → Einstellungen → Herzfrequenz-Übertragung")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun BrandHint(brand: String, hint: String) {
    Column {
        Text(brand, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}
