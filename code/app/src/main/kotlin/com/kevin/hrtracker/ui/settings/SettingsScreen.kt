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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.kevin.hrtracker.R
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneBounds
import com.kevin.hrtracker.domain.ZoneModel
import com.kevin.hrtracker.domain.Sex
import com.kevin.shared.ui.theme.PrimaryPurple
import com.kevin.shared.ui.theme.ZoneColors
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

    var weightText by remember(settings.weightKg) {
        mutableStateOf(settings.weightKg?.toString() ?: "")
    }
    val weightError = weightText.isNotBlank() && weightText.toIntOrNull()?.let { it in 30..250 } != true

    val tanakaMaxHr = HrZoneCalculator.tanakaMaxHr(settings.age)
    val effectiveMaxHr = settings.maxHrUsed
    val observedMaxHr by viewModel.observedMaxHr.collectAsStateWithLifecycle()

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
        HrZoneCalculator.calculateZones(effectiveMaxHr, settings.restingHr, settings.zoneModel)
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
            title = { Text(stringResource(R.string.settings_zone_info_title)) },
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
                            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZoneInfo = false }) { Text(stringResource(R.string.settings_close)) }
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
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth().tutorialAnchor(tutorialAnchors, "settings_hr")) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_section_heart_rate),
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                NumberField(
                    label = stringResource(R.string.settings_age),
                    value = ageText,
                    onValueChange = { ageText = it },
                    onDone = { it.toIntOrNull()?.let { v -> if (v in 10..99) viewModel.setAge(v) } },
                    isError = ageError,
                    supportingText = if (ageError) stringResource(R.string.settings_age_error) else null
                )
                Text(
                    stringResource(R.string.settings_hrmax_summary, tanakaMaxHr, effectiveMaxHr),
                    style = MaterialTheme.typography.bodySmall
                )
                observedMaxHr?.takeIf { it > effectiveMaxHr }?.let { observed ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.settings_measured_max_hr, observed),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.setManualMaxHr(observed) }) {
                            Text(stringResource(R.string.settings_apply))
                        }
                    }
                }

                NumberField(
                    label = stringResource(R.string.settings_manual_hrmax),
                    value = manualMaxHrText,
                    onValueChange = { manualMaxHrText = it },
                    onDone = {
                        viewModel.setManualMaxHr(it.toIntOrNull()?.takeIf { v -> v in 100..250 })
                    },
                    isError = maxHrError,
                    supportingText = if (maxHrError) stringResource(R.string.settings_hrmax_error) else null
                )

                NumberField(
                    label = stringResource(R.string.settings_resting_hr),
                    value = restingHrText,
                    onValueChange = { restingHrText = it },
                    onDone = {
                        viewModel.setRestingHr(it.toIntOrNull()?.takeIf { v -> v in 20..100 })
                    },
                    isError = restingHrError,
                    supportingText = if (restingHrError) stringResource(R.string.settings_resting_hr_error)
                        else if (settings.autoRestingHr) stringResource(R.string.settings_resting_hr_auto)
                        else null
                )

                Text(stringResource(R.string.settings_zone_model), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val zoneModelOptions = listOf(ZoneModel.HR_MAX to "%HRmax", ZoneModel.KARVONEN to "Karvonen")
                    zoneModelOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = settings.zoneModel == value,
                            onClick = { viewModel.setZoneModel(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = zoneModelOptions.size),
                            icon = {}
                        ) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (settings.zoneModel == ZoneModel.KARVONEN && settings.restingHr == null) {
                    Text(
                        stringResource(R.string.settings_zone_model_no_resting_hr),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_zone_model_explainer),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_section_body),
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                NumberField(
                    label = stringResource(R.string.settings_weight),
                    value = weightText,
                    onValueChange = { weightText = it },
                    onDone = { viewModel.setWeightKg(it.toIntOrNull()?.takeIf { v -> v in 30..250 }) },
                    isError = weightError,
                    supportingText = if (weightError) stringResource(R.string.settings_weight_error) else null
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val sexOptions = listOf(Sex.MALE, Sex.FEMALE)
                    sexOptions.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = settings.sex == value,
                            onClick = { viewModel.setSex(if (settings.sex == value) null else value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sexOptions.size),
                            // ponytail: kein Check-Icon — frisst ~28dp und laesst Label umbrechen
                            icon = {}
                        ) {
                            Text(stringResource(value.labelRes), maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (settings.sex == null || settings.weightKg == null) {
                    Text(
                        stringResource(R.string.settings_calories_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_section_target_zone),
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
                        stringResource(R.string.settings_section_zone_preview),
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Task 3: HelpOutline button
                        IconButton(onClick = { showZoneInfo = true }) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = stringResource(R.string.settings_zone_info_title)
                            )
                        }
                        Text(stringResource(R.string.settings_custom_values), style = MaterialTheme.typography.bodySmall)
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
                                Text(stringResource(R.string.settings_zone_n, i + 1), style = MaterialTheme.typography.labelMedium)
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
                                    label = { Text(stringResource(R.string.settings_min_bpm)) },
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
                                    label = { Text(stringResource(R.string.settings_max_bpm)) },
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
                    ) { Text(stringResource(R.string.settings_save)) }
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
                            Text("${z.lo} – ${z.hi} BPM", style = MaterialTheme.typography.bodyMedium) // i18n-ignore: unit/acronym, same in every language
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
                    stringResource(R.string.settings_chart_dynamic_scale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Switch(
                    checked = settings.chartDynamicScale,
                    onCheckedChange = { viewModel.setChartDynamicScale(it) }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_section_widget),
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.settings_widget_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                val variants = listOf(
                    WidgetVariant.MINIMAL to R.string.settings_widget_bpm,
                    WidgetVariant.STANDARD to R.string.settings_widget_standard,
                    WidgetVariant.ZONE to R.string.settings_widget_zone,
                    WidgetVariant.TIMER to R.string.settings_widget_time
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    variants.forEachIndexed { index, (variant, label) ->
                        SegmentedButton(
                            selected = settings.widgetVariant == variant,
                            onClick = { viewModel.setWidgetVariant(variant) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = variants.size),
                            // ponytail: kein Check-Icon — frisst ~28dp und laesst Label umbrechen
                            icon = {}
                        ) {
                            Text(stringResource(label), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        // Task 4: HR-Quelle only in debug mode
        // (Wear/Smartwatch entfernt — HR-Quelle ist immer BLE)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_debug_mode),
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_debug_mode_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = debugMode, onCheckedChange = { viewModel.setDebugMode(it) })
            }
        }
    }

        val hrStep = TutorialStep("settings_hr", stringResource(R.string.settings_section_heart_rate), stringResource(R.string.settings_tutorial_hr_text))
        val zonesStep = TutorialStep("settings_zones", stringResource(R.string.settings_section_zone_preview), stringResource(R.string.settings_tutorial_zones_text))
        val sourceStep = TutorialStep("settings_source", stringResource(R.string.settings_tutorial_source_title), stringResource(R.string.settings_tutorial_source_text))
        TutorialOverlay(
            steps = buildList {
                add(hrStep)
                add(zonesStep)
                if (debugMode) add(sourceStep)
            },
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("settings") }
        )
    }
}

private val ZONE_DESCRIPTIONS = listOf(
    1 to R.string.settings_zone_desc_1,
    2 to R.string.settings_zone_desc_2,
    3 to R.string.settings_zone_desc_3,
    4 to R.string.settings_zone_desc_4,
    5 to R.string.settings_zone_desc_5
)

@get:StringRes
private val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.settings_sex_male
        Sex.FEMALE -> R.string.settings_sex_female
    }

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
            TextButton(onClick = { onDone(value) }) { Text(stringResource(R.string.settings_ok)) }
        }
    )
}
