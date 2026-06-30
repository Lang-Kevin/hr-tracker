package com.kevin.hrtracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import com.kevin.hrtracker.domain.HrSource
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.ui.theme.PrimaryPurple
import com.kevin.hrtracker.ui.theme.ZoneColors
import com.kevin.hrtracker.FeatureFlags
import com.kevin.hrtracker.ui.tutorial.TutorialOverlay
import com.kevin.hrtracker.ui.tutorial.TutorialStep
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.hrtracker.ui.tutorial.rememberTutorialAnchors
import com.kevin.hrtracker.ui.tutorial.tutorialAnchor

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val healthImportStatus by viewModel.healthImportStatus.collectAsStateWithLifecycle()
    val hcPermissions = remember { setOf(HealthPermission.getReadPermission(HeartRateRecord::class)) }
    val requestHcPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(hcPermissions)) viewModel.importRestingHrFromHealthConnect()
    }

    var ageText by remember(settings.age) { mutableStateOf(settings.age.toString()) }
    var manualMaxHrText by remember(settings.manualMaxHr) {
        mutableStateOf(settings.manualMaxHr?.toString() ?: "")
    }
    var restingHrText by remember(settings.restingHr) {
        mutableStateOf(settings.restingHr?.toString() ?: "")
    }

    val ageError = ageText.toIntOrNull()?.let { it !in 10..99 } ?: ageText.isNotEmpty()
    val maxHrError = manualMaxHrText.isNotEmpty() &&
        (manualMaxHrText.toIntOrNull()?.let { it !in 100..250 } ?: true)
    val restingHrError = restingHrText.isNotEmpty() &&
        (restingHrText.toIntOrNull()?.let { it !in 20..100 } ?: true)

    val tanakaMaxHr = HrZoneCalculator.tanakaMaxHr(settings.age)
    val effectiveMaxHr = settings.maxHrUsed
    val model = if (settings.restingHr != null) "Karvonen (HRR)" else "%HRmax"

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("settings").collectAsStateWithLifecycle()

    // Task 4: debugMode hoisted before HR-Quelle Card
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()

    // Task 2: boundaries-based custom zone state
    var customZonesEnabled by remember(settings.customZones != null) {
        mutableStateOf(settings.customZones != null)
    }
    val defaultBoundaries = HrZoneCalculator.zonesToBoundaries(
        HrZoneCalculator.calculateZones(effectiveMaxHr, settings.restingHr)
    )
    var boundaries by remember(customZonesEnabled) {
        mutableStateOf(
            settings.customZones?.let { HrZoneCalculator.zonesToBoundaries(it) } ?: defaultBoundaries
        )
    }
    var touched by remember(customZonesEnabled) { mutableStateOf(setOf<Int>()) }

    // Task 3: zone info dialog state
    var showZoneInfo by remember { mutableStateOf(false) }

    if (showZoneInfo) {
        AlertDialog(
            onDismissRequest = { showZoneInfo = false },
            title = { Text("Was bedeuten die Zonen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZONE_DESCRIPTIONS.forEach { (z, desc) ->
                        val zoneColor = ZoneColors.getOrElse(z - 1) { MaterialTheme.colorScheme.primary }
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = zoneColor,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "Z$z",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                            Text(desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZoneInfo = false }) { Text("Schließen") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth().tutorialAnchor(tutorialAnchors, "settings_hr")) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Herzfrequenz",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                NumberField(
                    label = "Alter",
                    value = ageText,
                    onValueChange = { ageText = it },
                    onDone = { it.toIntOrNull()?.let { v -> if (v in 10..99) viewModel.setAge(v) } },
                    isError = ageError,
                    supportingText = if (ageError) "Alter muss zwischen 10 und 99 liegen" else null
                )
                Text(
                    "Tanaka HRmax: $tanakaMaxHr  •  Aktiv: $effectiveMaxHr BPM",
                    style = MaterialTheme.typography.bodySmall
                )

                NumberField(
                    label = "Manueller HRmax (leer = Tanaka)",
                    value = manualMaxHrText,
                    onValueChange = { manualMaxHrText = it },
                    onDone = {
                        viewModel.setManualMaxHr(it.toIntOrNull()?.takeIf { v -> v in 100..250 })
                    },
                    isError = maxHrError,
                    supportingText = if (maxHrError) "HRmax muss zwischen 100 und 250 liegen" else null
                )

                NumberField(
                    label = "Ruhepuls (leer = %HRmax-Modell)",
                    value = restingHrText,
                    onValueChange = { restingHrText = it },
                    onDone = {
                        viewModel.setRestingHr(it.toIntOrNull()?.takeIf { v -> v in 20..100 })
                    },
                    isError = restingHrError,
                    supportingText = if (restingHrError) "Ruhepuls muss zwischen 20 und 100 liegen" else null
                )
                if (FeatureFlags.SMARTWATCH_ENABLED && viewModel.isHealthConnectAvailable) {
                    OutlinedButton(
                        onClick = { requestHcPermissions.launch(hcPermissions) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = healthImportStatus != HealthImportStatus.LOADING
                    ) {
                        if (healthImportStatus == HealthImportStatus.LOADING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Von Smartwatch importieren")
                    }
                    if (healthImportStatus == HealthImportStatus.NO_DATA) {
                        Text(
                            "Kein Ruhepuls in Health Connect gefunden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text("Zonen-Modell: $model", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
                Text(
                    "Ziel-Zone",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..5).forEach { z ->
                        val selected = settings.targetZone == z
                        val zoneColor = ZoneColors.getOrElse(z - 1) { MaterialTheme.colorScheme.primary }
                        val contentColor = if (zoneColor.luminance() > 0.5f)
                            MaterialTheme.colorScheme.onSurface
                        else
                            androidx.compose.ui.graphics.Color.White
                        SegmentedButton(
                            selected = selected,
                            onClick = { viewModel.setTargetZone(z) },
                            shape = SegmentedButtonDefaults.itemShape(index = z - 1, count = 5),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = zoneColor,
                                activeContentColor = contentColor
                            )
                        ) {
                            Text("Z$z")
                        }
                    }
                }
            }
        }

        // Task 2 + Task 3: Zonen-Vorschau Card with boundaries editor and HelpOutline icon
        Card(modifier = Modifier.fillMaxWidth().tutorialAnchor(tutorialAnchors, "settings_zones")) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Zonen-Vorschau",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Task 3: HelpOutline button
                        IconButton(onClick = { showZoneInfo = true }) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "Was bedeuten die Zonen?"
                            )
                        }
                        Text("Eigene Werte", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = customZonesEnabled,
                            onCheckedChange = { enabled ->
                                customZonesEnabled = enabled
                                if (!enabled) viewModel.setCustomZones(null)
                            }
                        )
                    }
                }
                HorizontalDivider()
                if (customZonesEnabled) {
                    // Task 2: boundaries-based editor (5 zones, 6 boundaries)
                    (0..4).forEach { i ->
                        val zoneColor = ZoneColors.getOrElse(i) { MaterialTheme.colorScheme.primary }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = zoneColor,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.size(12.dp)
                                ) {}
                                Text("Zone ${i + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Min field — boundary index i
                                OutlinedTextField(
                                    value = if (i in touched) boundaries[i].toString() else "",
                                    onValueChange = { v ->
                                        val idx = i
                                        when {
                                            v.isBlank() -> {
                                                boundaries = boundaries.toMutableList().also { it[idx] = defaultBoundaries[idx] }
                                                touched = touched - idx
                                            }
                                            v.toIntOrNull() != null -> {
                                                val parsed = v.toInt()
                                                val adjusted = HrZoneCalculator.adjustBoundary(boundaries, idx, parsed)
                                                val changed = adjusted.indices.filter { adjusted[it] != boundaries[it] }.toSet()
                                                touched = touched + changed + idx
                                                boundaries = adjusted
                                            }
                                        }
                                    },
                                    label = { Text("Min BPM") },
                                    placeholder = { Text(defaultBoundaries[i].toString()) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                // Max field — boundary index i+1
                                OutlinedTextField(
                                    value = if ((i + 1) in touched) boundaries[i + 1].toString() else "",
                                    onValueChange = { v ->
                                        val idx = i + 1
                                        when {
                                            v.isBlank() -> {
                                                boundaries = boundaries.toMutableList().also { it[idx] = defaultBoundaries[idx] }
                                                touched = touched - idx
                                            }
                                            v.toIntOrNull() != null -> {
                                                val parsed = v.toInt()
                                                val adjusted = HrZoneCalculator.adjustBoundary(boundaries, idx, parsed)
                                                val changed = adjusted.indices.filter { adjusted[it] != boundaries[it] }.toSet()
                                                touched = touched + changed + idx
                                                boundaries = adjusted
                                            }
                                        }
                                    },
                                    label = { Text("Max BPM") },
                                    placeholder = { Text(defaultBoundaries[i + 1].toString()) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.setCustomZones(HrZoneCalculator.boundariesToZones(boundaries))
                        },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Speichern") }
                } else {
                    settings.effectiveZones.forEach { z ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val zoneColor = ZoneColors.getOrElse(z.zone - 1) { MaterialTheme.colorScheme.primary }
                                Surface(
                                    color = zoneColor,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.size(16.dp)
                                ) {}
                                Text("Z${z.zone}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("${z.lo} – ${z.hi} BPM", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Task 1: Chart dynamic scale switch
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Diagramm: dynamische Skalierung (Standard)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Switch(
                    checked = settings.chartDynamicScale,
                    onCheckedChange = { viewModel.setChartDynamicScale(it) }
                )
            }
        }

        // Task 4: HR-Quelle only in debug mode
        if (debugMode) {
            Card(modifier = Modifier.fillMaxWidth().tutorialAnchor(tutorialAnchors, "settings_source")) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "HR-Quelle",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        "Herzfrequenzquelle für Aufzeichnungen",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (FeatureFlags.SMARTWATCH_ENABLED) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = settings.hrSource == HrSource.BLE,
                                onClick = { viewModel.setHrSource(HrSource.BLE) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("BLE-Sensor")
                            }
                            SegmentedButton(
                                selected = settings.hrSource == HrSource.WATCH,
                                onClick = { viewModel.setHrSource(HrSource.WATCH) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Galaxy Watch")
                            }
                        }
                    } else {
                        Text(
                            "HR-Quelle: BLE-Sensor",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (FeatureFlags.SMARTWATCH_ENABLED && settings.hrSource == HrSource.WATCH) {
                        Text(
                            "Watch-Aufnahmen haben keine RR-Daten — HRV zeigt \"–\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Debug-Modus",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        "Schaltet Test-Funktionen frei (z. B. Pseudo-Sensor beim Scan)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = debugMode, onCheckedChange = { viewModel.setDebugMode(it) })
            }
        }
    }

        TutorialOverlay(
            steps = buildList {
                add(TutorialStep("settings_hr", "Herzfrequenz", "Trage Alter und Ruhepuls ein — daraus berechnen wir deine Trainingszonen."))
                add(TutorialStep("settings_zones", "Zonen-Vorschau", "Hier siehst du deine berechneten Zonen oder kannst eigene Werte eintragen."))
                if (debugMode) add(TutorialStep("settings_source", "HR-Quelle", "Wähle, ob die Herzfrequenz vom Brustgurt oder der Smartwatch kommt."))
            },
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("settings") }
        )
    }
}

private val ZONE_DESCRIPTIONS = listOf(
    1 to "Regeneration — sehr leichte Belastung, aktive Erholung",
    2 to "Fettverbrennung — lockeres Tempo, aerobe Basis",
    3 to "Aerob — moderates Training, Ausdaueraufbau",
    4 to "Anaerob — intensive Belastung, Laktatschwelle",
    5 to "VO₂max — maximale Intensität, kurze Intervalle"
)

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: (String) -> Unit,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        isError = isError,
        supportingText = supportingText?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } },
        trailingIcon = {
            TextButton(onClick = { onDone(value) }) { Text("OK") }
        }
    )
}
