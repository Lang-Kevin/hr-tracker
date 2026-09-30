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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.kevin.hrtracker.R
import com.kevin.shared.ble.ConnectionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import com.kevin.shared.domain.DeviceType
import com.kevin.shared.domain.DiscoveredDevice
import com.kevin.shared.domain.SavedDevice
import com.kevin.shared.ui.theme.LightPurple
import com.kevin.shared.ui.scan.BleStatusCard
import com.kevin.shared.ui.scan.DiscoveredDeviceItem
import com.kevin.shared.ui.scan.SavedDeviceItem
import com.kevin.shared.ui.LabelPickerDialog
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
    onHrvSessionStarted: (seconds: Int) -> Unit = {},
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
    var showHrvDialog by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    // Reset isStarting once session is confirmed active
    LaunchedEffect(activeSessionId) { if (activeSessionId != null) isStarting = false }

    if (showStartDialog) {
        LabelPickerDialog(
            title = stringResource(R.string.scan_label_picker_title),
            items = trainingLabels,
            initialSelection = null,
            confirmText = stringResource(R.string.scan_label_picker_confirm),
            onConfirm = { label ->
                isStarting = true
                onSessionStarted(label)
                showStartDialog = false
            },
            onDismiss = { showStartDialog = false },
            dismissText = null,
            addFieldLabel = stringResource(R.string.scan_label_picker_add),
            onAdd = viewModel::addTrainingLabel,
            onDelete = null
        )
    }
    if (showHrvDialog) {
        HrvDurationDialog(
            onSelect = { seconds ->
                showHrvDialog = false
                isStarting = true
                onHrvSessionStarted(seconds)
            },
            onDismiss = { showHrvDialog = false }
        )
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

    // POST_NOTIFICATIONS: requested after BLE is granted (no double dialog). Not gating —
    // the recording works without it, only the FGS notification stays hidden.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(permissionState.allPermissionsGranted) {
            if (permissionState.allPermissionsGranted && notifPermission.status != PermissionStatus.Granted) {
                notifPermission.launchPermissionRequest()
            }
        }
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
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = LightPurple
            )
            TextButton(onClick = onNavigateToHistory) {
                Text(stringResource(R.string.scan_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.tutorialAnchor(tutorialAnchors, "scan_settings")
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.scan_settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(stringResource(R.string.scan_grant_permission))
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
                        Text(if (isStarting) stringResource(R.string.scan_starting) else stringResource(R.string.scan_start_workout))
                    }
                    OutlinedButton(
                        onClick = { if (!isStarting) showHrvDialog = true },
                        enabled = !isStarting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.scan_measure_hrv))
                    }
                }
                HorizontalDivider()
            }

            if (savedDevices.isNotEmpty()) {
                Text(
                    stringResource(R.string.scan_saved_devices),
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
                    stringResource(R.string.scan_available_devices),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(tutorialAnchors, "scan_start")
            ) {
                Text(if (isScanning) stringResource(R.string.scan_searching) else stringResource(R.string.scan_start_search))
            }
            if (isScanning && discoveredDevices.none { it is DiscoveredDevice.Real }) {
                Text(stringResource(R.string.scan_scan_running), style = MaterialTheme.typography.bodySmall)
            }
            val simulatedDeviceText = stringResource(R.string.scan_device_simulated)
            val chestStrapText = stringResource(R.string.scan_device_chest_strap)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(discoveredDevices, key = { it.address }) { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        onClick = { viewModel.connectToDiscovered(device) },
                        iconAndSubtitle = { d ->
                            when {
                                d is DiscoveredDevice.Fake -> Icons.Default.Bluetooth to simulatedDeviceText
                                d.deviceType == DeviceType.CHEST_STRAP -> Icons.Default.Favorite to chestStrapText
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
                TutorialStep("scan_status", stringResource(R.string.scan_tutorial_status_title), stringResource(R.string.scan_tutorial_status_text)),
                TutorialStep("scan_start", stringResource(R.string.scan_tutorial_search_title), stringResource(R.string.scan_tutorial_search_text)),
                TutorialStep("scan_settings", stringResource(R.string.scan_tutorial_settings_title), stringResource(R.string.scan_tutorial_settings_text))
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("scan") }
        )
    }
}

@Composable
private fun HrvDurationDialog(onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_hrv_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(stringResource(R.string.scan_hrv_super_short), stringResource(R.string.scan_hrv_seconds, 30), 30),
                    Triple(stringResource(R.string.scan_hrv_short), stringResource(R.string.scan_hrv_minutes, 1), 60),
                    Triple(stringResource(R.string.scan_hrv_full), stringResource(R.string.scan_hrv_minutes, 5), 300),
                ).forEach { (name, duration, seconds) ->
                    OutlinedButton(
                        onClick = { onSelect(seconds) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.scan_hrv_option, name, duration))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_cancel)) } }
    )
}
