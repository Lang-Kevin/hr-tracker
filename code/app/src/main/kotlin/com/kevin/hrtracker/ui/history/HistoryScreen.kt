package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.R
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.domain.LoadMetric
import com.kevin.shared.ui.theme.PrimaryPurple
import com.kevin.shared.ui.selection.SelectionHeader
import com.kevin.shared.ui.session.CategoryFilterRow
import com.kevin.shared.ui.session.SessionListItem
import com.kevin.shared.ui.session.SummaryCard
import com.kevin.shared.ui.session.TrashSessionItem
import com.kevin.shared.ui.session.SoftDeleteConfirmationDialog
import com.kevin.shared.ui.session.TrashRetention
import com.kevin.shared.ui.session.TrashTab
import com.kevin.shared.ui.session.durationString
import com.kevin.shared.ui.session.toDateString
import com.kevin.hrtracker.ui.tutorial.TutorialOverlay
import com.kevin.hrtracker.ui.tutorial.TutorialStep
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel
import com.kevin.hrtracker.ui.tutorial.rememberTutorialAnchors
import com.kevin.hrtracker.ui.tutorial.tutorialAnchor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.filteredSessions.collectAsStateWithLifecycle()
    val availableLabels by viewModel.availableLabels.collectAsStateWithLifecycle()
    val labelFilter by viewModel.labelFilter.collectAsStateWithLifecycle()
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    val trashSessions by viewModel.trashSessions.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val isSelectionMode = selection.isActive
    val summaryStats by viewModel.summaryStats.collectAsStateWithLifecycle()
    val weeklyData by viewModel.weeklyData.collectAsStateWithLifecycle()
    val trimpHistory by viewModel.trimpHistory.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val loadMetric by viewModel.loadMetric.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val hrrTrend by viewModel.hrrTrend.collectAsStateWithLifecycle()
    val currentRestingHr by viewModel.currentRestingHr.collectAsStateWithLifecycle()
    val autoRestingHr by viewModel.autoRestingHr.collectAsStateWithLifecycle()
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    SoftDeleteConfirmationDialog(
        pendingIds = pendingDeleteIds,
        retention = TrashRetention.ON_NEXT_APP_START,
        onConfirm = { ids ->
            viewModel.moveToTrash(ids)
            pendingDeleteIds = null
        },
        onDismiss = { pendingDeleteIds = null }
    )

    if (showDateRangePicker) {
        DateRangeFilterDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setDateRange(start, end)
                showDateRangePicker = false
            }
        )
    }

    if (showCategoryFilter) {
        ModalBottomSheet(onDismissRequest = { showCategoryFilter = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    stringResource(R.string.history_filter_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (availableLabels.isEmpty()) {
                    Text(
                        stringResource(R.string.history_filter_no_labels),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    CategoryFilterRow(
                        categories = availableLabels,
                        filter = labelFilter,
                        onToggle = { viewModel.toggleLabelFilter(it) }
                    )
                }
            }
        }
    }

    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialAnchors = rememberTutorialAnchors()
    val tutorialSeen by tutorialViewModel.seenState("history").collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        if (isSelectionMode) {
            // ponytail: SelectionHeader registriert den BackHandler selbst, daher nur im Auswahlmodus komponieren.
            SelectionHeader(
                count = selection.size,
                onClear = { viewModel.clearSelection() },
                onDelete = { pendingDeleteIds = selection.ids.toList() }
            )
        } else {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
        }

        TabRow(
            selectedTabIndex = selectedTab,
            contentColor = PrimaryPurple,
            modifier = Modifier.tutorialAnchor(tutorialAnchors, "history_tabs")
        ) {
            listOf(
                stringResource(R.string.history_tab_history),
                stringResource(R.string.history_tab_stats),
                stringResource(R.string.history_tab_form),
                stringResource(R.string.history_tab_trash)
            ).forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                if (sessions.isNotEmpty()) {
                    SummaryCard(listOf(
                        stringResource(R.string.history_summary_workouts) to summaryStats.sessionCount.toString(),
                        stringResource(R.string.history_summary_total_time) to durationString(summaryStats.totalDurationS),
                        stringResource(R.string.history_summary_avg_bpm) to (summaryStats.avgBpm?.toString() ?: "—"),
                        stringResource(R.string.history_summary_longest) to durationString(summaryStats.longestDurationS)
                    ))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.tutorialAnchor(tutorialAnchors, "history_filter")) {
                        if (!labelFilter.isActive) {
                            AssistChip(
                                onClick = { showCategoryFilter = true },
                                label = { Text(stringResource(R.string.history_filter_chip)) },
                                leadingIcon = {
                                    Icon(Icons.Default.FilterList, contentDescription = null)
                                }
                            )
                        } else {
                            FilterChip(
                                selected = true,
                                onClick = { showCategoryFilter = true },
                                label = { Text(stringResource(R.string.history_filter_chip_active, labelFilter.selected.size)) },
                                leadingIcon = {
                                    Icon(Icons.Default.FilterList, contentDescription = null)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dateRange == null) {
                            AssistChip(
                                onClick = { showDateRangePicker = true },
                                label = { Text(stringResource(R.string.history_date_filter)) },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                            )
                        } else {
                            val (start, end) = dateRange!!
                            InputChip(
                                selected = true,
                                onClick = { showDateRangePicker = true },
                                label = { Text("${start.toShortDateString()} – ${end.toShortDateString()}") },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.clearDateRange() }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.history_date_filter_clear),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                if (sessions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).alpha(0.3f),
                            tint = PrimaryPurple
                        )
                        Text(
                            stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.history_empty_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.tutorialAnchor(tutorialAnchors, "history_list")
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (!isSelectionMode && value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDeleteIds = listOf(session.id)
                                    false
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CardDefaults.shape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            SessionListItem(
                                label = session.label,
                                startedAt = session.startedAt,
                                endedAt = session.endedAt,
                                isSelected = session.id in selection,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) viewModel.toggleSelection(session.id)
                                    else onSessionClick(session.id)
                                },
                                onLongClick = { viewModel.startSelection(session.id) }
                            )
                        }
                    }
                }
            }
            1 -> StatistikTab(weeklyData, trimpHistory, loadState, loadMetric, viewModel::setLoadMetric)
            2 -> FormTab(
                readiness = readiness,
                hrrTrend = hrrTrend,
                currentRestingHr = currentRestingHr,
                autoRestingHr = autoRestingHr,
                onAutoRestingHrChange = { viewModel.setAutoRestingHr(it) },
                onApplyRestingHr = { viewModel.applyRestingHrFromHrv() }
            )
            3 -> {
                val trashItems = remember(trashSessions) {
                    trashSessions.map { TrashSessionItem(it.id, it.label, it.startedAt) }
                }
                TrashTab(
                    items = trashItems,
                    retention = TrashRetention.ON_NEXT_APP_START,
                    onRestore = { viewModel.restoreSessions(listOf(it)) }
                )
            }
        }
    }

        TutorialOverlay(
            steps = listOf(
                TutorialStep("history_tabs", stringResource(R.string.history_tutorial_tabs_title), stringResource(R.string.history_tutorial_tabs_text)),
                TutorialStep("history_filter", stringResource(R.string.history_tutorial_filter_title), stringResource(R.string.history_tutorial_filter_text)),
                TutorialStep("history_list", stringResource(R.string.history_tutorial_list_title), stringResource(R.string.history_tutorial_list_text))
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false && sessions.isNotEmpty() && selectedTab == 0,
            onFinish = { tutorialViewModel.markSeen("history") }
        )
    }
}

private fun Long.toShortDateString(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yy"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeFilterDialog(
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null) onConfirm(start, end)
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
            ) { Text(stringResource(R.string.history_date_picker_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_date_picker_cancel)) }
        }
    ) {
        DateRangePicker(
            state = state,
            title = { Text(stringResource(R.string.history_date_picker_title), modifier = Modifier.padding(16.dp)) }
        )
    }
}

@Composable
private fun StatistikTab(
    weeklyData: List<HistoryViewModel.WeekStats>,
    trimpHistory: List<HistoryViewModel.SessionTrimpEntry>,
    loadState: HistoryViewModel.LoadState,
    loadMetric: LoadMetric,
    onLoadMetricChange: (LoadMetric) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LoadCard(loadState, loadMetric, onLoadMetricChange) }
        item {
            val maxDur = weeklyData.maxOfOrNull { it.totalDurationMin }?.coerceAtLeast(1) ?: 1
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.history_stats_active_per_week),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        weeklyData.forEach { week ->
                            val fraction = (week.totalDurationMin.toFloat() / maxDur).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction.coerceAtLeast(0.03f))
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(
                                            if (fraction > 0f) PrimaryPurple
                                            else PrimaryPurple.copy(alpha = 0.15f)
                                        )
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        weeklyData.forEach { week ->
                            Text(
                                week.weekLabel.take(5),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.history_stats_last_6_weeks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.history_stats_col_week), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                        Text(stringResource(R.string.history_stats_col_count), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                        Text(stringResource(R.string.history_stats_col_min), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f))
                        Text(stringResource(R.string.history_stats_col_avg_bpm), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.9f))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    weeklyData.forEach { week ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(week.weekLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                            Text(
                                if (week.sessionCount > 0) "${week.sessionCount}" else "—",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.7f),
                                color = if (week.sessionCount > 0) PrimaryPurple else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (week.totalDurationMin > 0) "${week.totalDurationMin}" else "—",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                week.avgBpm?.toString() ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
            }
        }

        if (trimpHistory.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.history_stats_trimp_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(trimpHistory, key = { it.sessionId }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.startedAt.toDateString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "TRIMP ${entry.trimp}",
                            style = MaterialTheme.typography.titleSmall,
                            color = PrimaryPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.history_stats_no_trimp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}


