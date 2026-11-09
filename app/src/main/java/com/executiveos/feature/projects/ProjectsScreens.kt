package com.executiveos.feature.projects

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.EmptyState
import com.executiveos.core.designsystem.component.ErrorState
import com.executiveos.core.designsystem.component.ExecutiveDatePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.projects.MilestoneKind
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectMilestone
import com.executiveos.domain.projects.ProjectProgress
import com.executiveos.domain.projects.ProjectRisk
import com.executiveos.domain.projects.ProjectStatus
import com.executiveos.domain.projects.ProjectValidationError
import com.executiveos.feature.tasks.SaveNotice
import com.executiveos.feature.tasks.labelRes
import java.time.LocalDate

const val PROJECTS_NEW_TAG = "projects_new"
const val PROJECTS_NEW_FIELD_TAG = "projects_new_field"
const val PROJECT_ADD_MILESTONE_TAG = "project_add_milestone"
const val PROJECT_MILESTONE_NAME_TAG = "project_milestone_name"
const val PROJECT_ADD_RISK_TAG = "project_add_risk"
const val PROJECT_ADD_TASK_TAG = "project_add_task"

private const val MAX_SEVERITY = 5
private const val FULL_PROGRESS = 100f

@StringRes
private fun ProjectStatus.labelRes(): Int = when (this) {
    ProjectStatus.PLANNED -> R.string.project_status_planned
    ProjectStatus.ACTIVE -> R.string.project_status_active
    ProjectStatus.ON_HOLD -> R.string.project_status_on_hold
    ProjectStatus.AT_RISK -> R.string.project_status_at_risk
    ProjectStatus.COMPLETED -> R.string.project_status_completed
    ProjectStatus.CANCELLED -> R.string.project_status_cancelled
}

@StringRes
private fun MilestoneKind.labelRes(): Int = when (this) {
    MilestoneKind.MILESTONE -> R.string.milestone_kind_milestone
    MilestoneKind.DELIVERABLE -> R.string.milestone_kind_deliverable
}

@Composable
fun ProjectsScreen(
    onOpenProject: (String) -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showNew by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectsEvent.OpenProject -> onOpenProject(event.projectId)
                is ProjectsEvent.Message -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(event.text))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(
                title = stringResource(R.string.section_projects),
                actions = {
                    TextButton(
                        onClick = { showNew = true },
                        modifier = Modifier.testTag(PROJECTS_NEW_TAG),
                    ) {
                        Text(stringResource(R.string.projects_new))
                    }
                },
            )
            val current = projects
            when {
                current == null -> LoadingState()
                current.isEmpty() -> EmptyState(
                    title = stringResource(R.string.projects_empty_title),
                    message = stringResource(R.string.projects_empty_message),
                    iconRes = R.drawable.ic_folder,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        end = Spacing.md,
                        bottom = Spacing.fabClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(current, key = { it.project.id }) { item ->
                        ProjectCard(item, onClick = { onOpenProject(item.project.id) })
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showNew) {
        TextPromptDialog(
            title = R.string.projects_new,
            label = R.string.projects_new_field,
            fieldTag = PROJECTS_NEW_FIELD_TAG,
            onDismiss = { showNew = false },
            onConfirm = { name ->
                showNew = false
                viewModel.create(name)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(item: ProjectListItem, onClick: () -> Unit) {
    val progress = item.progress
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(item.project.status.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (progress.isDelayed) DelayedLabel()
            }
            Text(
                text = item.project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressSummary(progress)
            NextMilestoneText(progress)
            Text(
                text = workloadText(progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DelayedLabel() {
    Text(
        text = stringResource(R.string.projects_delayed),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun workloadText(progress: ProjectProgress): String = listOf(
    pluralStringResource(R.plurals.project_open_tasks, progress.openTasks, progress.openTasks),
    pluralStringResource(R.plurals.project_open_risks, progress.openRisks, progress.openRisks),
).joinToString(" · ")

@Composable
private fun NextMilestoneText(progress: ProjectProgress) {
    val next = progress.nextMilestone ?: return
    val date = next.dueDate
    Text(
        text = if (date != null) {
            stringResource(R.string.project_next_milestone_date, next.name, date.toDisplayText())
        } else {
            stringResource(R.string.project_next_milestone, next.name)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ProgressSummary(progress: ProjectProgress) {
    val percent = progress.percent
    if (percent == null) {
        Text(
            text = stringResource(R.string.projects_no_milestones),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LinearProgressIndicator(
        progress = { percent / FULL_PROGRESS },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(
            R.string.projects_progress,
            percent,
            progress.completedMilestones,
            progress.totalMilestones,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

private enum class ProjectDateField { START, TARGET }

@Composable
fun ProjectEditorScreen(
    onClose: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val close = { viewModel.flush(onClose) }
    BackHandler(onBack = close)

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(title = stringResource(R.string.project_title), onNavigateUp = close)
            val project = uiState.project
            when {
                uiState.isLoading -> LoadingState()
                project == null -> ErrorState(message = stringResource(R.string.project_not_found))
                else -> ProjectForm(project, uiState, viewModel, onOpenTask)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ProjectForm(
    project: Project,
    uiState: ProjectEditorUiState,
    viewModel: ProjectEditorViewModel,
    onOpenTask: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SaveNotice(uiState.saveState)
        uiState.progress?.let { TrackingCard(it, project) }
        ProjectFields(project, uiState.errors, viewModel)
        MilestonesSection(uiState.milestones, viewModel)
        RisksSection(uiState.risks, viewModel)
        TasksSection(uiState.tasks, viewModel, onOpenTask)
    }
}

@Composable
private fun TrackingCard(progress: ProjectProgress, project: Project) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SectionHeader(R.string.project_tracking)
            if (progress.isDelayed) DelayedLabel()
            ProgressSummary(progress)
            NextMilestoneText(progress)
            if (progress.overdueMilestones > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.project_overdue_milestones,
                        progress.overdueMilestones,
                        progress.overdueMilestones,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(workloadText(progress), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = progress.forecastCompletion?.let {
                    stringResource(R.string.project_forecast, it.toDisplayText())
                } ?: stringResource(R.string.project_forecast_none),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (project.targetDate != null) DeviationText(progress.deviationDays)
        }
    }
}

@Composable
private fun DeviationText(days: Long?) {
    val value = days?.toInt() ?: return
    Text(
        text = when {
            value > 0 -> pluralStringResource(R.plurals.project_deviation_late, value, value)
            value < 0 -> pluralStringResource(R.plurals.project_deviation_early, -value, -value)
            else -> stringResource(R.string.project_deviation_none)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectFields(
    project: Project,
    errors: List<ProjectValidationError>,
    viewModel: ProjectEditorViewModel,
) {
    val edit = viewModel::onEdit
    var dateField by rememberSaveable { mutableStateOf<ProjectDateField?>(null) }
    val nameError = ProjectValidationError.BLANK_NAME in errors
    OutlinedTextField(
        value = project.name,
        onValueChange = { name -> edit { copy(name = name) } },
        label = { Text(stringResource(R.string.project_name)) },
        isError = nameError,
        supportingText = if (nameError) {
            { Text(stringResource(R.string.project_name_required)) }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OptionalField(R.string.field_description, project.description) { edit { copy(description = it) } }
    OptionalField(R.string.project_objective, project.objective) { edit { copy(objective = it) } }
    OptionalField(R.string.project_justification, project.justification) {
        edit { copy(justification = it) }
    }
    OptionalField(R.string.project_owner, project.owner, singleLine = true) {
        edit { copy(owner = it) }
    }
    DateRow(
        label = R.string.field_start_date,
        date = project.startDate,
        onPick = { dateField = ProjectDateField.START },
        onClear = { edit { copy(startDate = null) } },
    )
    DateRow(
        label = R.string.field_target_date,
        date = project.targetDate,
        onPick = { dateField = ProjectDateField.TARGET },
        onClear = { edit { copy(targetDate = null) } },
    )
    if (ProjectValidationError.TARGET_BEFORE_START in errors) {
        Text(
            text = stringResource(R.string.error_target_before_start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(stringResource(R.string.task_status_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ProjectStatus.entries.forEach { status ->
            FilterChip(
                selected = project.status == status,
                onClick = { edit { copy(status = status) } },
                label = { Text(stringResource(status.labelRes())) },
            )
        }
    }
    Text(stringResource(R.string.priority_title), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = project.priority == null,
            onClick = { edit { copy(priority = null) } },
            label = { Text(stringResource(R.string.project_priority_none)) },
        )
        PriorityLevel.entries.forEach { level ->
            FilterChip(
                selected = project.priority == level,
                onClick = { edit { copy(priority = level) } },
                label = { Text(stringResource(level.labelRes())) },
            )
        }
    }
    OptionalField(R.string.field_notes, project.notes) { edit { copy(notes = it) } }

    dateField?.let { field ->
        ExecutiveDatePickerDialog(
            initialDate = if (field == ProjectDateField.START) project.startDate else project.targetDate,
            onDismiss = { dateField = null },
            onDateSelected = { date ->
                edit {
                    if (field == ProjectDateField.START) copy(startDate = date) else copy(targetDate = date)
                }
                dateField = null
            },
        )
    }
}

@Composable
private fun OptionalField(
    @StringRes label: Int,
    value: String?,
    singleLine: Boolean = false,
    onChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { text -> onChange(text.ifEmpty { null }) },
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DateRow(
    @StringRes label: Int,
    date: LocalDate?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Text(
                text = date?.toDisplayText() ?: stringResource(R.string.field_no_date),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (date != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
        }
        TextButton(onClick = onPick) { Text(stringResource(R.string.action_choose)) }
    }
}

@Composable
private fun SectionHeader(
    @StringRes title: Int,
    actionTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (onAction != null && actionTag != null) {
            TextButton(onClick = onAction, modifier = Modifier.testTag(actionTag)) {
                Text(stringResource(R.string.subtask_add))
            }
        }
    }
}

@Composable
private fun MilestonesSection(
    milestones: List<ProjectMilestone>,
    viewModel: ProjectEditorViewModel,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(
            title = R.string.project_milestones,
            actionTag = PROJECT_ADD_MILESTONE_TAG,
            onAction = { showAdd = true },
        )
        Text(
            text = stringResource(R.string.project_milestones_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (milestones.isEmpty()) {
            Text(
                text = stringResource(R.string.project_milestones_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        milestones.forEach { milestone ->
            MilestoneRow(
                milestone = milestone,
                onToggle = { viewModel.setMilestoneCompleted(milestone.id, it) },
                onDelete = { deletingId = milestone.id },
            )
        }
    }
    if (showAdd) {
        AddMilestoneDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, kind, weight, due ->
                showAdd = false
                viewModel.addMilestone(name, kind, weight, due)
            },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.milestone_delete_title)) },
            text = { Text(stringResource(R.string.milestone_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingId = null
                        viewModel.deleteMilestone(id)
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MilestoneRow(
    milestone: ProjectMilestone,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = milestone.isCompleted, onCheckedChange = onToggle)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else null,
            )
            val details = stringResource(
                R.string.milestone_details,
                stringResource(milestone.kind.labelRes()),
                milestone.weight,
            )
            Text(
                text = listOfNotNull(details, milestone.dueDate?.toDisplayText()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.milestone_remove, milestone.name),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMilestoneDialog(
    onDismiss: () -> Unit,
    onAdd: (String, MilestoneKind, Int, LocalDate?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MilestoneKind.MILESTONE) }
    var weight by rememberSaveable { mutableStateOf("1") }
    var due by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val parsedWeight = weight.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.milestone_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PROJECT_MILESTONE_NAME_TAG),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MilestoneKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { text -> if (text.all(Char::isDigit)) weight = text },
                    label = { Text(stringResource(R.string.milestone_weight)) },
                    isError = parsedWeight == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRow(
                    label = R.string.milestone_due,
                    date = due,
                    onPick = { pickDate = true },
                    onClear = { due = null },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsedWeight != null,
                onClick = { parsedWeight?.let { onAdd(name, kind, it, due) } },
            ) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
    if (pickDate) {
        ExecutiveDatePickerDialog(
            initialDate = due,
            onDismiss = { pickDate = false },
            onDateSelected = {
                due = it
                pickDate = false
            },
        )
    }
}

@Composable
private fun RisksSection(risks: List<ProjectRisk>, viewModel: ProjectEditorViewModel) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(
            title = R.string.project_risks,
            actionTag = PROJECT_ADD_RISK_TAG,
            onAction = { showAdd = true },
        )
        if (risks.isEmpty()) {
            Text(
                text = stringResource(R.string.project_risks_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        risks.sortedWith(compareBy({ !it.isOpen }, { -it.severity })).forEach { risk ->
            RiskRow(risk, onToggle = { viewModel.setRiskOpen(risk.id, !risk.isOpen) })
        }
    }
    if (showAdd) {
        AddRiskDialog(
            onDismiss = { showAdd = false },
            onAdd = { description, mitigation, severity ->
                showAdd = false
                viewModel.addRisk(description, mitigation, severity)
            },
        )
    }
}

@Composable
private fun RiskRow(risk: ProjectRisk, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = risk.description,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (risk.isOpen) null else TextDecoration.LineThrough,
            )
            val status = stringResource(R.string.risk_severity_value, risk.severity)
            val closed = stringResource(R.string.risk_closed).takeUnless { risk.isOpen }
            Text(
                text = listOfNotNull(status, closed).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            risk.mitigation?.let {
                Text(
                    text = stringResource(R.string.risk_mitigation_value, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(onClick = onToggle) {
            Text(stringResource(if (risk.isOpen) R.string.risk_close else R.string.risk_reopen))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRiskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String?, Int) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var mitigation by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.risk_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.risk_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = mitigation,
                    onValueChange = { mitigation = it },
                    label = { Text(stringResource(R.string.risk_mitigation)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.risk_severity), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    (1..MAX_SEVERITY).forEach { value ->
                        FilterChip(
                            selected = severity == value,
                            onClick = { severity = value },
                            label = { Text(value.toString()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && severity != null,
                onClick = { severity?.let { onAdd(description, mitigation, it) } },
            ) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TasksSection(
    tasks: List<TaskReference>,
    viewModel: ProjectEditorViewModel,
    onOpenTask: (String) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(
            title = R.string.project_tasks,
            actionTag = PROJECT_ADD_TASK_TAG,
            onAction = { showAdd = true },
        )
        if (tasks.isEmpty()) {
            Text(
                text = stringResource(R.string.project_tasks_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        tasks.forEach { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTask(task.id) }
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(task.status.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    if (showAdd) {
        TextPromptDialog(
            title = R.string.tasks_new,
            label = R.string.tasks_new_field,
            fieldTag = PROJECTS_NEW_FIELD_TAG,
            onDismiss = { showAdd = false },
            onConfirm = { title ->
                showAdd = false
                viewModel.addTask(title)
            },
        )
    }
}

@Composable
private fun TextPromptDialog(
    @StringRes title: Int,
    @StringRes label: Int,
    fieldTag: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(fieldTag),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
