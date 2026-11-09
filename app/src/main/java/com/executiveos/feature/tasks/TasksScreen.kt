package com.executiveos.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.EmptyState
import com.executiveos.core.designsystem.component.ExecutiveDatePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.model.effectiveDate
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.tasks.EstimateFilter
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskFilters
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskView
import java.time.LocalDate

const val TASKS_NEW_TAG = "tasks_new"
const val TASKS_SEARCH_TAG = "tasks_search"
const val TASKS_NEW_FIELD_TAG = "tasks_new_field"

@Composable
fun TasksScreen(
    onOpenTask: (String) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showNewTask by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TasksEvent.OpenTask -> onOpenTask(event.taskId)
                is TasksEvent.Message -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(event.text))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box {
                ExecutiveTopBar(title = stringResource(R.string.section_tasks))
                TextButton(
                    onClick = { showNewTask = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Spacing.xs)
                        .testTag(TASKS_NEW_TAG),
                ) {
                    Text(stringResource(R.string.tasks_new))
                }
            }
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.tasks_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .testTag(TASKS_SEARCH_TAG),
            )
            ViewChips(uiState, viewModel::onViewSelected, onOpenFilters = { showFilters = true })
            TaskListContent(uiState, onOpenTask, viewModel)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showNewTask) {
        NewTaskDialog(
            onDismiss = { showNewTask = false },
            onCreate = { title ->
                viewModel.create(title)
                showNewTask = false
            },
        )
    }
    if (showFilters) {
        FiltersSheet(uiState, viewModel, onDismiss = { showFilters = false })
    }
}

@Composable
private fun ViewChips(
    uiState: TasksUiState,
    onSelected: (TaskView) -> Unit,
    onOpenFilters: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            val active = uiState.filters.activeCount
            FilterChip(
                selected = active > 0,
                onClick = onOpenFilters,
                label = {
                    Text(
                        if (active > 0) {
                            stringResource(R.string.tasks_filters_active, active)
                        } else {
                            stringResource(R.string.tasks_filters)
                        },
                    )
                },
            )
        }
        items(TaskView.entries) { view ->
            FilterChip(
                selected = uiState.view == view,
                onClick = { onSelected(view) },
                label = { Text(stringResource(view.labelRes())) },
            )
        }
    }
}

@Composable
private fun TaskListContent(
    uiState: TasksUiState,
    onOpenTask: (String) -> Unit,
    viewModel: TasksViewModel,
) {
    when {
        uiState.isLoading -> LoadingState()
        uiState.tasks.isEmpty() -> EmptyState(
            title = stringResource(R.string.tasks_empty_title),
            message = stringResource(R.string.tasks_empty_message),
            iconRes = R.drawable.ic_task,
            modifier = Modifier.padding(top = Spacing.xl),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
        ) {
            items(uiState.tasks, key = { it.item.task.id }) { ranked ->
                TaskRow(ranked, onOpenTask, viewModel)
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(ranked: RankedTask, onOpenTask: (String) -> Unit, viewModel: TasksViewModel) {
    val task = ranked.item.task
    val isFinished = task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTask(task.id) }
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ranked.priority.obligation?.let { ObligationBadge(it) }
                PriorityBadge(ranked.priority.effectiveLevel)
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (isFinished) TextDecoration.LineThrough else null,
            )
            task.nextAction?.let { action ->
                Text(
                    text = stringResource(R.string.tasks_next_action, action),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val summary = rowSummary(ranked)
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TaskRowMenu(task.status, task.id, viewModel)
    }
}

@Composable
private fun rowSummary(ranked: RankedTask): String {
    val item = ranked.item
    val task = item.task
    val parts = buildList {
        add(stringResource(task.status.labelRes()))
        item.projectName?.let(::add)
        item.parentTitle?.let { add(stringResource(R.string.tasks_subtask_of, it)) }
        task.deadlineDate?.let { add(stringResource(R.string.inbox_deadline_label, it.toDisplayText())) }
        if (task.deadlineDate == null) {
            task.effectiveDate?.let { add(stringResource(R.string.inbox_target_label, it.toDisplayText())) }
        }
        task.estimatedMinutes?.let { add(stringResource(R.string.tasks_minutes, it)) }
        if (item.openDependencies > 0) {
            add(pluralStringResource(R.plurals.tasks_blocked_by, item.openDependencies, item.openDependencies))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun TaskRowMenu(status: TaskStatus, taskId: String, viewModel: TasksViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val isOpen = TaskQueries.isOpen(status)
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.action_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val entry: @Composable (Int, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            if (isOpen) {
                entry(R.string.tasks_action_complete) { viewModel.complete(taskId) }
                if (status != TaskStatus.IN_PROGRESS) {
                    entry(R.string.tasks_action_start) { viewModel.setInProgress(taskId) }
                }
            }
            entry(R.string.tasks_action_duplicate) { viewModel.duplicate(taskId) }
            if (status != TaskStatus.ARCHIVED) {
                entry(R.string.tasks_action_archive) { viewModel.archive(taskId) }
            }
        }
    }
}

@Composable
private fun NewTaskDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_new)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.tasks_new_field)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.testTag(TASKS_NEW_FIELD_TAG),
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title) }) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(
    uiState: TasksUiState,
    viewModel: TasksViewModel,
    onDismiss: () -> Unit,
) {
    val filters = uiState.filters
    val options = uiState.options
    val update = viewModel::onFiltersChange
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TextButton(onClick = viewModel::clearFilters) {
                Text(stringResource(R.string.tasks_filters_clear))
            }
            ChoiceGroup(
                title = R.string.filter_priority,
                choices = PriorityLevel.entries.map { it to stringResource(it.labelRes()) },
                isSelected = { it in filters.levels },
                onToggle = { level -> update { copy(levels = levels.toggle(level)) } },
            )
            ChoiceGroup(
                title = R.string.filter_status,
                choices = FILTERABLE_STATUSES.map { it to stringResource(it.labelRes()) },
                isSelected = { it in filters.statuses },
                onToggle = { status -> update { copy(statuses = statuses.toggle(status)) } },
            )
            ChoiceGroup(
                title = R.string.filter_category,
                choices = options.categories.map { it.value to it.label },
                isSelected = { it in filters.categoryIds },
                onToggle = { id -> update { copy(categoryIds = categoryIds.toggle(id)) } },
            )
            ChoiceGroup(
                title = R.string.filter_project,
                choices = options.projects.map { it.value to it.label },
                isSelected = { it in filters.projectIds },
                onToggle = { id -> update { copy(projectIds = projectIds.toggle(id)) } },
            )
            ChoiceGroup(
                title = R.string.filter_owner,
                choices = options.owners.map { it to it },
                isSelected = { it in filters.owners },
                onToggle = { owner -> update { copy(owners = owners.toggle(owner)) } },
            )
            ChoiceGroup(
                title = R.string.filter_tags,
                choices = options.tags.map { it to it },
                isSelected = { it in filters.tags },
                onToggle = { tag -> update { copy(tags = tags.toggle(tag)) } },
            )
            ChoiceGroup(
                title = R.string.filter_estimate,
                choices = EstimateFilter.entries.map { it to stringResource(it.labelRes()) },
                isSelected = { it == filters.estimate },
                onToggle = { value ->
                    update { copy(estimate = value.takeUnless { it == estimate }) }
                },
            )
            DateFilter(R.string.filter_date_from, filters.dateFrom) { date ->
                update { copy(dateFrom = date) }
            }
            DateFilter(R.string.filter_date_to, filters.dateTo) { date ->
                update { copy(dateTo = date) }
            }
        }
    }
}

private val FILTERABLE_STATUSES = listOf(
    TaskStatus.PENDING,
    TaskStatus.IN_PROGRESS,
    TaskStatus.WAITING,
    TaskStatus.BLOCKED,
    TaskStatus.COMPLETED,
    TaskStatus.CANCELLED,
    TaskStatus.ARCHIVED,
)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(
    @StringRes title: Int,
    choices: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
) {
    if (choices.isEmpty()) return
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            choices.forEach { (value, label) ->
                FilterChip(
                    selected = isSelected(value),
                    onClick = { onToggle(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun DateFilter(@StringRes label: Int, value: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
            Text(value?.toDisplayText() ?: stringResource(R.string.field_no_date))
        }
        if (value != null) {
            TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.action_clear)) }
        }
        TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.action_choose)) }
    }
    if (showPicker) {
        ExecutiveDatePickerDialog(
            initialDate = value,
            onDismiss = { showPicker = false },
            onDateSelected = {
                onChange(it)
                showPicker = false
            },
        )
    }
}
