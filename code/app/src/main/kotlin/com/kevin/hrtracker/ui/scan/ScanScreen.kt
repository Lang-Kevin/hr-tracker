package com.kevin.hrtracker.ui.scan

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import com.kevin.hrtracker.domain.DiscoveredDevice
import com.kevin.hrtracker.domain.DeviceType
import com.kevin.hrtracker.domain.SavedDevice
import com.kevin.hrtracker.FeatureFlags
import com.kevin.hrtracker.ui.theme.ConnectedGreen
import com.kevin.hrtracker.ui.theme.ErrorRed
import com.kevin.hrtracker.ui.theme.LightPurple
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.SurfaceDark


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
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val trainingLabels by viewModel.trainingLabels.collectAsStateWithLifecycle()

    var showStartDialog by remember { mutableStateOf(false) }
    var showSmartWatchHelp by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    // Reset isStarting once session is confirmed active
    LaunchedEffect(activeSessionId) { if (activeSessionId != null) isStarting = false }

    if (showStartDialog) {
        StartTrainingDialog(
            labels = trainingLabels,
            onAddLabel = viewModel::addTrainingLabel,
            onStart = { label ->
                showStartDialog = false
                isStarting = true
                onSessionStarted(label)
            },
            onDismiss = { showStartDialog = false }
        )
    }
    if (FeatureFlags.SMARTWATCH_ENABLED && showSmartWatchHelp) {
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
            Text(
                "HR Tracker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = LightPurple
            )
            TextButton(onClick = onNavigateToHistory) {
                Text("Verlauf", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onNavigateToSettings) {
                Text("⚙", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
        val dotColor = when {
            connectionState is ConnectionState.Ready -> ConnectedGreen
            connectionState is ConnectionState.Error -> ErrorRed
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusCardBorder = if (connectionState is ConnectionState.Ready)
            BorderStroke(1.dp, PrimaryPurple) else null
        val statusCardBg = if (connectionState is ConnectionState.Error)
            ErrorRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = statusCardBg),
                border = statusCardBorder
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Status: $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (connectionState !is ConnectionState.Disconnected) {
                        TextButton(onClick = { viewModel.disconnect() }) { Text("Trennen") }
                    }
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
                Text(
                    "Gemerkte Geräte",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
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
                Text(
                    "Verfügbare HR-Geräte:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                if (FeatureFlags.SMARTWATCH_ENABLED) {
                    TextButton(onClick = { showSmartWatchHelp = true }) {
                        Text("Smartwatch verbinden ?", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "Suche läuft…" else "Suche starten")
            }
            if (isScanning && discoveredDevices.none { it is DiscoveredDevice.Real }) {
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onForget) { Text("Vergessen") }
        }
    }
}

@Composable
private fun DeviceItem(device: DiscoveredDevice, onClick: () -> Unit) {
    val (icon, subtitle) = when {
        device is DiscoveredDevice.Fake -> Icons.Default.Bluetooth to "Simuliertes Testgerät"
        FeatureFlags.SMARTWATCH_ENABLED && device.deviceType == DeviceType.SMARTWATCH -> Icons.Default.Watch to "Smartwatch · HR-Broadcast"
        device.deviceType == DeviceType.CHEST_STRAP -> Icons.Default.Favorite to "Brustgurt"
        else -> Icons.Default.Bluetooth to device.address
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
private fun StartTrainingDialog(
    labels: List<String>,
    onAddLabel: (String) -> Unit,
    onStart: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(labels) { mutableStateOf(labels.firstOrNull() ?: "") }
    var newLabelText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trainingstyp wählen") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(labels) { type ->
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newLabelText,
                        onValueChange = { newLabelText = it },
                        label = { Text("Neue Art") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onAddLabel(newLabelText)
                            newLabelText = ""
                        },
                        enabled = newLabelText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(selected) }, enabled = selected.isNotEmpty()) { Text("Starten") }
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
