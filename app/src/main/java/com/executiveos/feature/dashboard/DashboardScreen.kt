package com.executiveos.feature.dashboard

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.EmptyState
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.formatDuration
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.dashboard.AlertType
import com.executiveos.domain.dashboard.Dashboard
import com.executiveos.domain.dashboard.DashboardAlert
import com.executiveos.domain.dashboard.DashboardIndicators
import com.executiveos.domain.dashboard.FocusTask
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.effectiveDate
import com.executiveos.domain.planning.CapacitySummary
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskView
import com.executiveos.feature.tasks.ObligationBadge
import com.executiveos.feature.tasks.PriorityBadge
import com.executiveos.feature.tasks.priorityExplanation

private class DashboardNavigation(
    val onOpenTask: (String) -> Unit,
    val onOpenView: (TaskView) -> Unit,
    val onOpenProjects: () -> Unit,
    val onOpenPlanning: () -> Unit,
    val onOpenBlock: (String) -> Unit,
) {
    fun open(alert: AlertType) = when (alert) {
        AlertType.OVERDUE_COMMITMENTS -> onOpenView(TaskView.OVERDUE)
        AlertType.UPCOMING_COMMITMENTS -> onOpenView(TaskView.UPCOMING)
        AlertType.BLOCKED_TASKS -> onOpenView(TaskView.BLOCKED)
        AlertType.PENDING_DEPENDENCIES -> onOpenView(TaskView.ALL)
        AlertType.CRITICAL_WITHOUT_NEXT_ACTION -> onOpenView(TaskView.CRITICAL)
        AlertType.OVERLOADED_DAYS -> onOpenPlanning()
        AlertType.DELAYED_PROJECTS -> onOpenProjects()
    }
}

@Composable
fun DashboardScreen(
    onOpenTask: (String) -> Unit,
    onOpenView: (TaskView) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenPlanning: () -> Unit,
    onOpenBlock: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val navigation = DashboardNavigation(
        onOpenTask,
        onOpenView,
        onOpenProjects,
        onOpenPlanning,
        onOpenBlock,
    )
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(title = stringResource(R.string.section_home))
            val current = dashboard
            if (current == null) {
                LoadingState()
            } else {
                DashboardContent(
                    dashboard = current,
                    viewModel = viewModel,
                    navigation = navigation,
                    onChoose = { showPicker = true },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showPicker) {
        FocusPickerDialog(
            candidates = dashboard?.focusCandidates.orEmpty(),
            onDismiss = { showPicker = false },
            onPick = { taskId ->
                viewModel.pin(taskId)
                showPicker = false
            },
        )
    }
}

@Composable
private fun DashboardContent(
    dashboard: Dashboard,
    viewModel: DashboardViewModel,
    navigation: DashboardNavigation,
    onChoose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = viewModel.today().toDisplayText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FocusSection(dashboard, viewModel, navigation.onOpenTask, onChoose)
        if (dashboard.alerts.isNotEmpty()) AlertsSection(dashboard.alerts, navigation)
        IndicatorsSection(dashboard.indicators, dashboard.capacityThisWeek, navigation.onOpenView)
        NextHoursSection(dashboard, viewModel, navigation)
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FocusSection(
    dashboard: Dashboard,
    viewModel: DashboardViewModel,
    onOpenTask: (String) -> Unit,
    onChoose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { SectionTitle(R.string.home_focus_title) }
            if (dashboard.isFocusAdjusted) {
                TextButton(onClick = viewModel::resetFocus) {
                    Text(stringResource(R.string.home_focus_reset))
                }
            }
            TextButton(onClick = onChoose) { Text(stringResource(R.string.home_focus_choose)) }
        }
        if (dashboard.focus.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.home_focus_empty_title),
                message = stringResource(R.string.home_focus_empty_message),
                iconRes = R.drawable.ic_task,
            )
        }
        dashboard.focus.forEach { focus ->
            FocusCard(
                focus = focus,
                onOpen = { onOpenTask(focus.ranked.item.task.id) },
                onComplete = { viewModel.complete(focus.ranked.item.task.id) },
                onRemove = { viewModel.dismiss(focus.ranked.item.task.id) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusCard(
    focus: FocusTask,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
) {
    val item = focus.ranked.item
    val task = item.task
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                focus.ranked.priority.obligation?.let { ObligationBadge(it) }
                PriorityBadge(focus.ranked.priority.effectiveLevel)
                if (focus.isEmergency) CardLabel(R.string.home_focus_urgent)
                if (focus.isPinned) CardLabel(R.string.home_focus_pinned)
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = taskDetails(task, item.projectName)
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            task.nextAction?.let { action ->
                Text(
                    text = stringResource(R.string.tasks_next_action, action),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(
                    R.string.home_focus_reason,
                    priorityExplanation(focus.ranked.priority),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.home_focus_remove)) }
                TextButton(onClick = onComplete) {
                    Text(stringResource(R.string.tasks_action_complete))
                }
            }
        }
    }
}

@Composable
private fun CardLabel(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun taskDetails(task: Task, projectName: String?): String {
    val parts = buildList {
        projectName?.let(::add)
        task.deadlineDate?.let { add(stringResource(R.string.inbox_deadline_label, it.toDisplayText())) }
        if (task.deadlineDate == null) {
            task.effectiveDate?.let { add(stringResource(R.string.inbox_target_label, it.toDisplayText())) }
        }
        task.estimatedMinutes?.let { add(formatDuration(it)) }
    }
    return parts.joinToString(" · ")
}

@PluralsRes
private fun AlertType.labelRes(): Int = when (this) {
    AlertType.OVERDUE_COMMITMENTS -> R.plurals.alert_overdue
    AlertType.UPCOMING_COMMITMENTS -> R.plurals.alert_upcoming
    AlertType.BLOCKED_TASKS -> R.plurals.alert_blocked
    AlertType.PENDING_DEPENDENCIES -> R.plurals.alert_dependencies
    AlertType.CRITICAL_WITHOUT_NEXT_ACTION -> R.plurals.alert_no_next_action
    AlertType.OVERLOADED_DAYS -> R.plurals.alert_overloaded_days
    AlertType.DELAYED_PROJECTS -> R.plurals.alert_delayed_projects
}

@Composable
private fun AlertsSection(alerts: List<DashboardAlert>, navigation: DashboardNavigation) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(R.string.home_alerts_title)
        alerts.forEach { alert ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigation.open(alert.type) },
            ) {
                Text(
                    text = pluralStringResource(alert.type.labelRes(), alert.count, alert.count),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndicatorsSection(
    indicators: DashboardIndicators,
    capacity: CapacitySummary,
    onOpenView: (TaskView) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(R.string.home_indicators_title)
        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val tile: @Composable (Int, Int, TaskView) -> Unit = { label, value, view ->
                IndicatorTile(label, value, Modifier.weight(1f)) { onOpenView(view) }
            }
            tile(R.string.home_pending, indicators.pending, TaskView.ALL)
            tile(R.string.view_overdue, indicators.overdue, TaskView.OVERDUE)
            tile(R.string.view_critical, indicators.critical, TaskView.CRITICAL)
            tile(R.string.view_high, indicators.high, TaskView.HIGH)
            tile(R.string.view_in_progress, indicators.inProgress, TaskView.IN_PROGRESS)
            tile(R.string.view_blocked, indicators.blocked, TaskView.BLOCKED)
            tile(R.string.home_completed_week, indicators.completedThisWeek, TaskView.COMPLETED)
        }
        TimeRow(R.string.home_time_estimated, formatDuration(indicators.estimatedMinutesThisWeek))
        TimeRow(R.string.home_time_planned, formatDuration(capacity.plannedMinutes))
        TimeRow(R.string.home_time_available, formatDuration(capacity.remainingMinutes))
    }
}

@Composable
private fun IndicatorTile(
    @StringRes label: Int,
    value: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeRow(@StringRes label: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NextHoursSection(
    dashboard: Dashboard,
    viewModel: DashboardViewModel,
    navigation: DashboardNavigation,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(R.string.home_next_title)
        if (dashboard.next24Hours.isEmpty() && dashboard.nextBlocks.isEmpty()) {
            Text(stringResource(R.string.home_next_empty), style = MaterialTheme.typography.bodyMedium)
        }
        val today = viewModel.today()
        dashboard.nextBlocks.forEach { block ->
            val start = block.startAt.atZone(viewModel.zone)
            AgendaRow(
                label = stringResource(dayLabel(start.toLocalDate() == today)) + " " +
                    start.toLocalTime().toDisplayText(),
                title = block.title,
                onClick = { navigation.onOpenBlock(block.id) },
            )
        }
        dashboard.next24Hours.forEach { ranked ->
            val task = ranked.item.task
            AgendaRow(
                label = stringResource(dayLabel(task.effectiveDate == today)),
                title = task.title,
                onClick = { navigation.onOpenTask(task.id) },
            )
        }
    }
}

@StringRes
private fun dayLabel(isToday: Boolean): Int =
    if (isToday) R.string.home_day_today else R.string.home_day_tomorrow

@Composable
private fun AgendaRow(label: String, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FocusPickerDialog(
    candidates: List<RankedTask>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_focus_pick_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.home_focus_pick_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { it.item.task.id }) { candidate ->
                        Text(
                            text = candidate.item.task.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(candidate.item.task.id) }
                                .padding(vertical = Spacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
