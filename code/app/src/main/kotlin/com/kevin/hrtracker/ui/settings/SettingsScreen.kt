package com.kevin.hrtracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.kevin.hrtracker.R
import com.kevin.hrtracker.domain.HrZoneCalculator
import com.kevin.hrtracker.domain.WidgetVariant
import com.kevin.hrtracker.domain.ZoneModel
import com.kevin.hrtracker.domain.Sex
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.shared.ui.components.SegmentedControl
import com.kevin.shared.ui.input.NumberField
import com.kevin.shared.ui.input.NumberInput
import com.kevin.shared.ui.input.isError
import com.kevin.shared.ui.input.parseNumberInput
import com.kevin.shared.ui.overlay.TutorialSpotlight
import com.kevin.shared.ui.overlay.TutorialStep
import com.kevin.shared.ui.overlay.rememberTutorialAnchors
import com.kevin.shared.ui.overlay.tutorialAnchor
import com.kevin.shared.ui.theme.AppTheme
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val tanakaMaxHr = HrZoneCalculator.tanakaMaxHr(settings.age)
    val effectiveMaxHr = settings.maxHrUsed
    val observedMaxHr by viewModel.observedMaxHr.collectAsStateWithLifecycle()

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("settings").collectAsStateWithLifecycle()

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
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                    ZONE_DESCRIPTIONS.forEach { (z, desc) ->
                        val zoneColor = AppTheme.colors.zones.getOrElse(z - 1) { MaterialTheme.colorScheme.primary }
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
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
                                        color = AppTheme.colors.onMedia
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
            .padding(AppTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SettingsGroup(stringResource(R.string.settings_section_heart_rate), Modifier.tutorialAnchor(tutorialAnchors, "settings_hr")) {
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            IntSettingField(
                current = settings.manualMaxHr,
                range = 100..250,
                label = stringResource(R.string.settings_manual_hrmax),
                error = stringResource(R.string.settings_hrmax_error),
                onCommit = { viewModel.setManualMaxHr(it) }
            )
            IntSettingField(
                current = settings.restingHr,
                range = 20..100,
                label = stringResource(R.string.settings_resting_hr),
                error = stringResource(R.string.settings_resting_hr_error),
                supporting = if (settings.autoRestingHr) stringResource(R.string.settings_resting_hr_auto) else null,
                onCommit = { viewModel.setRestingHr(it) }
            )
        }

        SettingsGroup(stringResource(R.string.settings_section_body)) {
            // age is never cleared: commit only a valid value
            IntSettingField(
                current = settings.age,
                range = 10..99,
                label = stringResource(R.string.settings_age),
                error = stringResource(R.string.settings_age_error),
                onCommit = { it?.let(viewModel::setAge) },
                commitNull = false
            )
            IntSettingField(
                current = settings.weightKg,
                range = 30..250,
                label = stringResource(R.string.settings_weight),
                error = stringResource(R.string.settings_weight_error),
                onCommit = { viewModel.setWeightKg(it) }
            )
            val sexOptions = listOf(Sex.MALE, Sex.FEMALE)
            SegmentedControl(
                options = sexOptions.map { stringResource(it.labelRes) },
                selectedIndex = sexOptions.indexOf(settings.sex),
                onSelect = { viewModel.setSex(if (settings.sex == sexOptions[it]) null else sexOptions[it]) },
            )
            if (settings.sex == null || settings.weightKg == null) {
                Text(
                    stringResource(R.string.settings_calories_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        SettingsGroup(stringResource(R.string.settings_section_target_zone), Modifier.tutorialAnchor(tutorialAnchors, "settings_zones")) {
            Text(stringResource(R.string.settings_zone_model), style = MaterialTheme.typography.titleSmall)
            SegmentedControl(
                options = listOf("%HRmax", "Karvonen"), // i18n-ignore: method names
                selectedIndex = settings.zoneModel.ordinal,
                onSelect = { viewModel.setZoneModel(ZoneModel.entries[it]) },
            )
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SegmentedControl(
                options = (1..5).map { "Z$it" },
                selectedIndex = settings.targetZone - 1,
                onSelect = { viewModel.setTargetZone(it + 1) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_section_zone_preview), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
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
            if (customZonesEnabled) {
                // Task 2: boundaries-based editor (5 zones, 6 boundaries)
                (0..4).forEach { i ->
                    val zoneColor = AppTheme.colors.zones.getOrElse(i) { MaterialTheme.colorScheme.primary }
                    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
                        ) {
                            Surface(
                                color = zoneColor,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(12.dp)
                            ) {}
                            Text(stringResource(R.string.settings_zone_n, i + 1), style = MaterialTheme.typography.labelMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
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
                    val isTarget = z.zone == settings.targetZone
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isTarget) Modifier.background(AppTheme.colors.accentTint, MaterialTheme.shapes.small) else Modifier)
                            .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(AppTheme.colors.zones[z.zone - 1], CircleShape))
                        Spacer(Modifier.width(AppTheme.spacing.sm))
                        Text("Z${z.zone}" + if (isTarget) " " + stringResource(R.string.settings_zone_target_suffix) else "", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.settings_zone_range, z.lo, z.hi), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_section_display)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_chart_dynamic_scale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = AppTheme.spacing.sm)
                )
                Switch(
                    checked = settings.chartDynamicScale,
                    onCheckedChange = { viewModel.setChartDynamicScale(it) }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(stringResource(R.string.settings_section_widget), style = MaterialTheme.typography.titleSmall)
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
            SegmentedControl(
                options = variants.map { stringResource(it.second) },
                selectedIndex = variants.indexOfFirst { it.first == settings.widgetVariant },
                onSelect = { viewModel.setWidgetVariant(variants[it].first) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = AppTheme.spacing.sm)) {
                    Text(stringResource(R.string.settings_debug_mode), style = MaterialTheme.typography.titleSmall)
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
        TutorialSpotlight(
            steps = buildList {
                add(hrStep)
                add(zonesStep)
                if (debugMode) add(sourceStep)
            },
            anchors = tutorialAnchors,
            visible = tutorialSeen == false,
            onFinish = { tutorialViewModel.markSeen("settings") },
            skipLabel = stringResource(R.string.common_tutorial_skip),
            nextLabel = { n, m -> stringResource(R.string.common_tutorial_next_progress, n, m) },
            doneLabel = stringResource(R.string.common_tutorial_done),
        )
    }
}

/** Uppercase section title above a surface card; dividers between blocks are placed by the caller. */
@Composable
private fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
        Text(
            title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
                content = content
            )
        }
    }
}

/**
 * Integer field on the lib [NumberField]. Commits on IME done or OK button (as before):
 * valid -> value, otherwise null when [commitNull] (today's clear semantics).
 */
@Composable
private fun IntSettingField(
    current: Int?,
    range: IntRange,
    label: String,
    error: String,
    onCommit: (Int?) -> Unit,
    supporting: String? = null,
    commitNull: Boolean = true
) {
    var text by rememberSaveable(current) { mutableStateOf(current?.toString().orEmpty()) }
    val parsed = parseNumberInput(text, range = range.first.toDouble()..range.last.toDouble(), decimals = 0)
    NumberField(
        value = text,
        onValueChange = { text = it },
        label = label,
        modifier = Modifier.fillMaxWidth(),
        errorText = if (parsed.isError) error else null,
        supportingText = supporting,
        onDone = {
            val v = (parsed as? NumberInput.Valid)?.value?.toInt()
            if (v != null || commitNull) onCommit(v)
        },
        showDoneButton = true
    )
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
