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
import com.kevin.shared.ble.ConnectionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import com.kevin.shared.domain.DeviceType
import com.kevin.shared.domain.DiscoveredDevice
import com.kevin.shared.domain.SavedDevice
import com.kevin.hrtracker.FeatureFlags
import com.kevin.hrtracker.ui.theme.LightPurple
import com.kevin.shared.ui.scan.BleStatusCard
import com.kevin.shared.ui.scan.DiscoveredDeviceItem
import com.kevin.shared.ui.scan.SavedDeviceItem
import com.kevin.hrtracker.ui.tutorial.TutorialOverlay
import com.kevin.hrtracker.ui.tutorial.TutorialStep
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.hrtracker.ui.tutorial.rememberTutorialAnchors
import com.kevin.hrtracker.ui.tutorial.tutorialAnchor


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onSessionStarted: (label: String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onResumeSession: () -> Unit = {}
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

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("scan").collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
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
            TextButton(onClick = onNavigateToSettings, modifier = Modifier.tutorialAnchor(tutorialAnchors, "scan_settings")) {
                Text("⚙", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Box(Modifier.tutorialAnchor(tutorialAnchors, "scan_status")) {
            BleStatusCard(
                connectionState = connectionState,
                autoConnect = autoConnect,
                onDisconnect = { viewModel.disconnect() },
                onToggleAutoConnect = { viewModel.toggleAutoConnect() }
            )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onResumeSession,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fortsetzen")
                        }
                        Button(
                            onClick = { viewModel.stopSession() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stoppen")
                        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(tutorialAnchors, "scan_start")
            ) {
                Text(if (isScanning) "Suche läuft…" else "Suche starten")
            }
            if (isScanning && discoveredDevices.none { it is DiscoveredDevice.Real }) {
                Text("Scan läuft…", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(discoveredDevices, key = { it.address }) { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        onClick = { viewModel.connectToDiscovered(device) },
                        iconAndSubtitle = { d ->
                            when {
                                d is DiscoveredDevice.Fake -> Icons.Default.Bluetooth to "Simuliertes Testgerät"
                                FeatureFlags.SMARTWATCH_ENABLED && d.deviceType == DeviceType.SMARTWATCH -> Icons.Default.Watch to "Smartwatch · HR-Broadcast"
                                d.deviceType == DeviceType.CHEST_STRAP -> Icons.Default.Favorite to "Brustgurt"
                                else -> Icons.Default.Bluetooth to d.address
                            }
                        }
                    )
                }
            }
        }
    }

        TutorialOverlay(
            steps = listOf(
                TutorialStep("scan_status", "Verbindungsstatus", "Hier siehst du, ob dein Brustgurt verbunden ist."),
                TutorialStep("scan_start", "Geräte suchen", "Starte hier die Bluetooth-Suche nach deinem Brustgurt."),
                TutorialStep("scan_settings", "Einstellungen", "Hier passt du Alter, Ruhepuls und HR-Zonen an.")
            ),
            anchors = tutorialAnchors,
            visible = !tutorialSeen,
            onFinish = { tutorialViewModel.markSeen("scan") }
        )
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
