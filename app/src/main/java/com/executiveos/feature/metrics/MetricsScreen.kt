package com.executiveos.feature.metrics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.formatDuration
import com.executiveos.core.designsystem.component.toMonthTitle
import com.executiveos.core.designsystem.component.toShortDayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.metrics.CategoryMinutes
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.domain.metrics.ProjectProgressLine
import com.executiveos.domain.metrics.Ratio
import com.executiveos.domain.model.Category

@StringRes
private fun MetricPeriod.labelRes(): Int = when (this) {
    MetricPeriod.WEEK -> R.string.planning_view_week
    MetricPeriod.MONTH -> R.string.planning_view_month
    MetricPeriod.QUARTER -> R.string.metrics_period_quarter
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricPeriodChips(selected: MetricPeriod, onSelect: (MetricPeriod) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MetricPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(stringResource(period.labelRes())) },
            )
        }
    }
}

@Composable
fun MetricsScreen(
    onNavigateUp: () -> Unit,
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        ExecutiveTopBar(title = stringResource(R.string.section_metrics), onNavigateUp = onNavigateUp)
        val state = uiState
        if (state == null) {
            LoadingState()
        } else {
            MetricsContent(state, viewModel)
        }
    }
}

@Composable
private fun MetricsContent(state: MetricsUiState, viewModel: MetricsViewModel) {
    val metrics = state.metrics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MetricPeriodChips(state.period, viewModel::selectPeriod)
        PeriodHeader(state, viewModel)
        CategoryChips(state.categories, state.categoryId, viewModel::selectCategory)
        MetricCard(R.string.metrics_priority, R.string.metrics_priority_def) {
            ValueText(ratioText(metrics.priorityCompletion))
        }
        MetricCard(R.string.metrics_compliance, R.string.metrics_compliance_def) {
            ValueText(ratioText(metrics.compliance))
        }
        MetricCard(R.string.metrics_overdue, R.string.metrics_overdue_def) {
            ValueText(metrics.overdueTasks.toString())
        }
        MetricCard(R.string.metrics_blocked, R.string.metrics_blocked_def) {
            ValueText(metrics.blockedTasks.toString())
        }
        MetricCard(R.string.metrics_estimate, R.string.metrics_estimate_def) {
            ValueText(
                if (metrics.estimatedMinutes == 0 && metrics.actualMinutes == 0) {
                    stringResource(R.string.metrics_no_data)
                } else {
                    stringResource(
                        R.string.metrics_estimated_actual,
                        formatDuration(metrics.estimatedMinutes),
                        formatDuration(metrics.actualMinutes),
                    )
                },
            )
        }
        MetricCard(R.string.metrics_deep_work, R.string.metrics_deep_work_def) {
            ValueText(formatDuration(metrics.deepWorkMinutes))
        }
        MetricCard(R.string.metrics_learning, R.string.metrics_learning_def) {
            ValueText(formatDuration(metrics.learningMinutes))
        }
        MetricCard(R.string.metrics_projects, R.string.metrics_projects_def) {
            ProjectLines(metrics.projects)
        }
        MetricCard(R.string.metrics_by_category, R.string.metrics_by_category_def) {
            CategoryDistribution(metrics.timeByCategory)
        }
    }
}

@Composable
private fun PeriodHeader(state: MetricsUiState, viewModel: MetricsViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previous) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.planning_previous),
                modifier = Modifier.rotate(180f),
            )
        }
        Text(
            text = if (state.period == MetricPeriod.MONTH) {
                state.metrics.span.from.toMonthTitle()
            } else {
                stringResource(
                    R.string.planning_week_range,
                    state.metrics.span.from.toShortDayText(),
                    state.metrics.span.to.toShortDayText(),
                )
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::next) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.planning_next),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.filter_category), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.metrics_all_categories)) },
            )
            categories.forEach { category ->
                FilterChip(
                    selected = selectedId == category.id,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    @StringRes title: Int,
    @StringRes definition: Int,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            content()
            Text(
                text = stringResource(definition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueText(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun ratioText(ratio: Ratio): String {
    val percent = ratio.percent ?: return stringResource(R.string.metrics_no_data)
    return stringResource(R.string.metrics_ratio, ratio.done, ratio.total, percent)
}

@Composable
private fun ProjectLines(projects: List<ProjectProgressLine>) {
    if (projects.isEmpty()) {
        Text(stringResource(R.string.metrics_projects_none), style = MaterialTheme.typography.bodyMedium)
    }
    projects.forEach { project ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (project.isDelayed) {
                Text(
                    text = stringResource(R.string.projects_delayed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = project.percent?.let { stringResource(R.string.metrics_project_percent, it) }
                    ?: stringResource(R.string.metrics_project_unmeasured),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CategoryDistribution(categories: List<CategoryMinutes>) {
    if (categories.isEmpty()) {
        Text(stringResource(R.string.metrics_no_data), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val total = categories.sumOf { it.minutes }.coerceAtLeast(1).toFloat()
    categories.forEach { entry ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.category ?: stringResource(R.string.metrics_no_category),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(entry.minutes), style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { entry.minutes / total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
