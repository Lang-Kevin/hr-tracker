package com.kevin.hrtracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.ZoneId
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.hrtracker.R
import com.kevin.hrtracker.data.entity.Session
import com.kevin.hrtracker.data.entity.isHrvMeasurement
import com.kevin.hrtracker.domain.HrvPosture
import com.kevin.hrtracker.domain.LoadMetric
import com.kevin.hrtracker.domain.SessionMetrics
import com.kevin.shared.format.DateStyle
import com.kevin.shared.format.DurationStyle
import com.kevin.shared.format.formatDate
import com.kevin.shared.format.formatDateRange
import com.kevin.shared.format.formatElapsed
import com.kevin.shared.format.formatMeasure
import com.kevin.shared.ui.chart.ZoneDistributionBar
import com.kevin.shared.ui.components.AppTabRow
import com.kevin.shared.ui.components.EmptyState
import com.kevin.shared.ui.components.ListCard
import com.kevin.shared.ui.components.ListCardValue
import com.kevin.shared.ui.components.StatCell
import com.kevin.shared.ui.components.StatGrid
import com.kevin.shared.ui.components.SwipeToDelete
import com.kevin.shared.ui.dialog.DateRangeFilterDialog
import com.kevin.shared.ui.overlay.TutorialSpotlight
import com.kevin.shared.ui.overlay.TutorialStep
import com.kevin.shared.ui.overlay.rememberTutorialAnchors
import com.kevin.shared.ui.overlay.tutorialAnchor
import com.kevin.shared.ui.selection.SelectionHeader
import com.kevin.shared.ui.session.CategoryFilterRow
import com.kevin.shared.ui.session.TrashSessionItem
import com.kevin.shared.ui.session.SoftDeleteConfirmationDialog
import com.kevin.shared.ui.session.TrashRetention
import com.kevin.shared.ui.session.TrashTab
import com.kevin.shared.ui.session.durationString
import com.kevin.shared.ui.session.toDateString
import com.kevin.shared.ui.theme.AppTheme
import com.kevin.hrtracker.ui.tutorial.TutorialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.filteredSessions.collectAsStateWithLifecycle()
    val groupedSessions by viewModel.groupedSessions.collectAsStateWithLifecycle()
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
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()

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
            onConfirm = { start, end ->
                viewModel.setDateRange(start, end)
                showDateRangePicker = false
            },
            onDismiss = { showDateRangePicker = false },
            initialStart = dateRange?.first,
            initialEnd = dateRange?.second
        )
    }

    if (showCategoryFilter) {
        ModalBottomSheet(onDismissRequest = { showCategoryFilter = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.lg)
                    .padding(bottom = AppTheme.spacing.xl)
            ) {
                Text(
                    stringResource(R.string.history_filter_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = AppTheme.spacing.sm)
                )
                if (availableLabels.isEmpty()) {
                    Text(
                        stringResource(R.string.history_filter_no_labels),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AppTheme.spacing.sm)
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
            .padding(horizontal = AppTheme.spacing.lg)
    ) {
        Spacer(Modifier.height(AppTheme.spacing.lg))
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

        AppTabRow(
            titles = listOf(
                stringResource(R.string.history_tab_history),
                stringResource(R.string.history_tab_stats),
                stringResource(R.string.history_tab_form),
                stringResource(R.string.history_tab_trash)
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.tutorialAnchor(tutorialAnchors, "history_tabs")
        )

        Spacer(Modifier.height(AppTheme.spacing.sm))

        when (selectedTab) {
            0 -> {
                if (sessions.isNotEmpty()) {
                    StatGrid(
                        cells = listOf(
                            StatCell(stringResource(R.string.history_summary_workouts), summaryStats.sessionCount.toString()),
                            StatCell(stringResource(R.string.history_summary_total_time), durationString(summaryStats.totalDurationS)),
                            StatCell(stringResource(R.string.history_summary_avg_bpm), summaryStats.avgBpm?.toString() ?: "—"),
                            StatCell(stringResource(R.string.history_summary_longest), durationString(summaryStats.longestDurationS))
                        ),
                        columns = 4
                    )
                    Spacer(Modifier.height(AppTheme.spacing.sm))
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
                    Spacer(Modifier.height(AppTheme.spacing.xs))
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
                                label = { Text(formatDateRange(start, end, DateStyle.ShortDate, locale)) },
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
                    EmptyState(
                        title = stringResource(R.string.history_empty_title),
                        icon = Icons.Default.Favorite,
                        body = stringResource(R.string.history_empty_text)
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                    modifier = Modifier.tutorialAnchor(tutorialAnchors, "history_list")
                ) {
                    groupedSessions.forEach { group ->
                        item(key = "week-${group.weekStart}") {
                            Text(
                                weekHeader(group, locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = AppTheme.spacing.sm)
                            )
                        }
                        items(group.items, key = { it.id }) { session ->
                            SwipeToDelete(
                                onDelete = { viewModel.moveToTrash(listOf(session.id)) },
                                enabled = !isSelectionMode
                            ) {
                                SessionCard(
                                    session = session,
                                    selected = session.id in selection,
                                    selectionMode = isSelectionMode,
                                    locale = locale,
                                    zone = zone,
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

        TutorialSpotlight(
            steps = listOf(
                TutorialStep("history_tabs", stringResource(R.string.history_tutorial_tabs_title), stringResource(R.string.history_tutorial_tabs_text)),
                TutorialStep("history_filter", stringResource(R.string.history_tutorial_filter_title), stringResource(R.string.history_tutorial_filter_text)),
                TutorialStep("history_list", stringResource(R.string.history_tutorial_list_title), stringResource(R.string.history_tutorial_list_text))
            ),
            anchors = tutorialAnchors,
            visible = tutorialSeen == false && sessions.isNotEmpty() && selectedTab == 0,
            onFinish = { tutorialViewModel.markSeen("history") },
            skipLabel = stringResource(R.string.common_tutorial_skip),
            nextLabel = { n, m -> stringResource(R.string.common_tutorial_next_progress, n, m) },
            doneLabel = stringResource(R.string.common_tutorial_done),
        )
    }
}

@Composable
private fun weekHeader(group: WeekGroup<Session>, locale: Locale): String {
    val count = group.items.size
    val text = when (group.weeksAgo) {
        0 -> stringResource(R.string.history_week_this, count)
        1 -> stringResource(R.string.history_week_last, count)
        else -> stringResource(R.string.history_week_of, formatDate(group.weekStart, DateStyle.DayMonth, locale), count)
    }
    return text.uppercase(locale)
}

@Composable
private fun SessionCard(
    session: Session,
    selected: Boolean,
    selectionMode: Boolean,
    locale: Locale,
    zone: ZoneId,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val whenText = formatDate(session.startedAt, DateStyle.WeekdayTime, locale, zone)
    if (session.isHrvMeasurement) {
        val posture = session.posture?.let { p -> runCatching { HrvPosture.valueOf(p) }.getOrNull() }
        val postureLabel = when (posture) {
            HrvPosture.LYING -> stringResource(R.string.scan_hrv_posture_lying)
            HrvPosture.SITTING -> stringResource(R.string.scan_hrv_posture_sitting)
            HrvPosture.STANDING -> stringResource(R.string.scan_hrv_posture_standing)
            null -> null
        }
        ListCard(
            title = postureLabel?.let { stringResource(R.string.history_hrv_title, it) } ?: session.label,
            subtitle = stringResource(
                R.string.history_hrv_subtitle,
                whenText,
                session.rmssd?.let { formatMeasure(it.toDouble(), "ms", 0, locale) } ?: "—"
            ),
            selected = selected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        val activeSeconds = (session.activeMs ?: session.endedAt?.let { it - session.startedAt } ?: 0L) / 1000L
        ListCard(
            title = session.label,
            subtitle = session.avgBpm?.let { stringResource(R.string.history_item_subtitle, whenText, it) } ?: whenText,
            selected = selected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            trailing = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                    ListCardValue(formatElapsed(activeSeconds, DurationStyle.Compact))
                    SessionMetrics.decodeZoneSeconds(session.zoneSeconds)?.let {
                        ZoneDistributionBar(it, Modifier.width(48.dp).height(4.dp))
                    }
                }
            }
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
                                            if (fraction > 0f) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                                color = if (week.sessionCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.primary,
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


