package com.executiveos.feature.task

import androidx.activity.compose.BackHandler
import androidx.annotation.ArrayRes
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.EmptyState
import com.executiveos.core.designsystem.component.ExecutiveDatePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.core.navigation.TaskSection
import com.executiveos.domain.capture.MissingField
import com.executiveos.domain.capture.TaskValidationError
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId

const val TASK_NEXT_ACTION_TAG = "task_next_action"

private enum class EditorSection(@StringRes val title: Int) {
    IDENTIFICATION(R.string.section_a),
    CONTEXT(R.string.section_b),
    IMPORTANCE(R.string.section_c),
    OUTCOME(R.string.section_d),
    NEXT_ACTION(R.string.section_e),
    PRIORITY(R.string.section_f),
    DATES(R.string.section_g),
    PEOPLE(R.string.section_h),
    DOCUMENTATION(R.string.section_i),
}

/** Cada acceso de la captura abre solo lo que hace falta. */
private fun initiallyExpanded(section: TaskSection?): List<String> = when (section) {
    null -> listOf(EditorSection.IDENTIFICATION)
    TaskSection.CONTEXT -> listOf(EditorSection.CONTEXT)
    TaskSection.IMPORTANCE -> listOf(EditorSection.IMPORTANCE, EditorSection.PRIORITY)
    TaskSection.NEXT_ACTION -> listOf(EditorSection.NEXT_ACTION)
    TaskSection.MORE -> listOf(
        EditorSection.OUTCOME,
        EditorSection.DATES,
        EditorSection.PEOPLE,
        EditorSection.DOCUMENTATION,
    )
}.map { it.name }

@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val close = { viewModel.flush(onClose) }
    var confirmConvert by remember { mutableStateOf(false) }

    BackHandler(onBack = close)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box {
            ExecutiveTopBar(title = stringResource(R.string.task_title), onNavigateUp = close)
            if (uiState.task?.sourceInboxItemId != null) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    EditorMenu(onConvert = { confirmConvert = true })
                }
            }
        }
        val task = uiState.task
        when {
            uiState.isLoading -> LoadingState()
            task == null -> EmptyState(
                title = stringResource(R.string.task_title),
                message = stringResource(R.string.task_not_found),
            )
            else -> EditorForm(task, uiState, viewModel)
        }
    }

    if (confirmConvert) {
        AlertDialog(
            onDismissRequest = { confirmConvert = false },
            title = { Text(stringResource(R.string.task_convert_project_title)) },
            text = { Text(stringResource(R.string.task_convert_project_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.convertToProject(onClose) }) {
                    Text(stringResource(R.string.action_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmConvert = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EditorMenu(onConvert: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.action_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inbox_action_to_project)) },
                onClick = {
                    expanded = false
                    onConvert()
                },
            )
        }
    }
}

@Composable
private fun EditorForm(task: Task, uiState: TaskEditorUiState, viewModel: TaskEditorViewModel) {
    var expanded by rememberSaveable {
        mutableStateOf(initiallyExpanded(viewModel.initialSection))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.xl),
    ) {
        EditorStatus(uiState)
        EditorSection.entries.forEach { section ->
            val isExpanded = section.name in expanded
            SectionHeader(section.title, isExpanded) {
                expanded = if (isExpanded) expanded - section.name else expanded + section.name
            }
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SectionBody(section, task, uiState, viewModel)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Icon(
            painter = painterResource(
                if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
            ),
            contentDescription = null,
        )
    }
}

@Composable
private fun EditorStatus(uiState: TaskEditorUiState) {
    val hasNotice = uiState.errors.isNotEmpty() ||
        uiState.missingFields.isNotEmpty() ||
        uiState.suggestSplitting ||
        uiState.saveState != SaveState.IDLE
    if (!hasNotice) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            uiState.errors.forEach { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.missingFields.isNotEmpty()) {
                val names = uiState.missingFields.map { stringResource(it.labelRes()) }
                Text(
                    text = stringResource(R.string.task_missing_prefix, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.suggestSplitting) {
                Text(
                    text = stringResource(R.string.task_suggest_split),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            saveLabel(uiState.saveState)?.let { label ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@StringRes
private fun saveLabel(state: SaveState): Int? = when (state) {
    SaveState.IDLE -> null
    SaveState.SAVED -> R.string.task_saved
    SaveState.INVALID -> R.string.task_invalid
    SaveState.FAILED -> R.string.task_save_failed
}

@StringRes
private fun MissingField.labelRes(): Int = when (this) {
    MissingField.WHAT_TO_DO -> R.string.missing_what_to_do
    MissingField.IMPORTANCE -> R.string.missing_importance
    MissingField.EXPECTED_OUTCOME -> R.string.missing_outcome
    MissingField.NEXT_ACTION -> R.string.missing_next_action
    MissingField.PRIORITY -> R.string.missing_priority
}

@StringRes
private fun TaskValidationError.messageRes(): Int = when (this) {
    TaskValidationError.BLANK_TITLE -> R.string.error_blank_title
    TaskValidationError.NEGATIVE_TIME -> R.string.error_negative_time
    TaskValidationError.TARGET_BEFORE_START -> R.string.error_target_before_start
    TaskValidationError.DEADLINE_BEFORE_START -> R.string.error_deadline_before_start
    TaskValidationError.TARGET_AFTER_DEADLINE -> R.string.error_target_after_deadline
}

@Composable
private fun SectionBody(
    section: EditorSection,
    task: Task,
    uiState: TaskEditorUiState,
    viewModel: TaskEditorViewModel,
) {
    val edit = viewModel::onEdit
    when (section) {
        EditorSection.IDENTIFICATION -> IdentificationFields(task, uiState, viewModel)
        EditorSection.CONTEXT -> {
            TaskField(R.string.field_what_to_do, task.whatToDo) { edit { copy(whatToDo = it) } }
            TaskField(R.string.field_why_needed, task.whyNeeded) { edit { copy(whyNeeded = it) } }
            TaskField(R.string.field_requested_by, task.requestedBy) { edit { copy(requestedBy = it) } }
            TaskField(R.string.field_origin, task.origin) { edit { copy(origin = it) } }
            TaskField(R.string.field_context, task.contextToRemember) {
                edit { copy(contextToRemember = it) }
            }
            TaskField(R.string.field_documents, task.requiredDocuments) {
                edit { copy(requiredDocuments = it) }
            }
        }
        EditorSection.IMPORTANCE -> {
            TaskField(R.string.field_impact_text, task.expectedImpact) {
                edit { copy(expectedImpact = it) }
            }
            TaskField(R.string.field_consequence_not_done, task.consequenceIfNotDone) {
                edit { copy(consequenceIfNotDone = it) }
            }
            TaskField(R.string.field_consequence_late, task.consequenceIfLate) {
                edit { copy(consequenceIfLate = it) }
            }
            TaskField(R.string.field_affected, task.affectedParties) {
                edit { copy(affectedParties = it) }
            }
            TaskField(R.string.field_risk, task.relatedRisk) { edit { copy(relatedRisk = it) } }
        }
        EditorSection.OUTCOME ->
            TaskField(R.string.field_outcome, task.expectedOutcome) {
                edit { copy(expectedOutcome = it) }
            }
        EditorSection.NEXT_ACTION ->
            TaskField(
                label = R.string.field_next_action,
                value = task.nextAction,
                modifier = Modifier.testTag(TASK_NEXT_ACTION_TAG),
            ) { edit { copy(nextAction = it) } }
        EditorSection.PRIORITY -> {
            ScaleSelector(R.string.field_impact_scale, R.array.impact_levels, task.impact) {
                edit { copy(impact = it) }
            }
            ScaleSelector(R.string.field_urgency_scale, R.array.urgency_levels, task.urgency) {
                edit { copy(urgency = it) }
            }
            ScaleSelector(R.string.field_delay_scale, R.array.delay_levels, task.delayRisk) {
                edit { copy(delayRisk = it) }
            }
        }
        EditorSection.DATES -> DateFields(task, edit)
        EditorSection.PEOPLE -> PeopleFields(task, edit)
        EditorSection.DOCUMENTATION -> {
            TaskField(R.string.field_notes, task.notes) { edit { copy(notes = it) } }
            TaskField(R.string.field_links, task.links) { edit { copy(links = it) } }
            TaskField(R.string.field_references, task.references) { edit { copy(references = it) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentificationFields(
    task: Task,
    uiState: TaskEditorUiState,
    viewModel: TaskEditorViewModel,
) {
    val edit = viewModel::onEdit
    OutlinedTextField(
        value = task.title,
        onValueChange = { title -> edit { copy(title = title) } },
        label = { Text(stringResource(R.string.field_title)) },
        isError = task.title.isBlank(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    TaskField(R.string.field_description, task.description) { edit { copy(description = it) } }
    Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        uiState.categories.forEach { category ->
            FilterChip(
                selected = task.categoryId == category.id,
                onClick = {
                    edit { copy(categoryId = category.id.takeUnless { it == categoryId }) }
                },
                label = { Text(category.name) },
            )
        }
    }
    ProjectSelector(task, uiState, viewModel::onProjectSelected)
    var tagsText by rememberSaveable { mutableStateOf(task.tags.joinToString(", ")) }
    OutlinedTextField(
        value = tagsText,
        onValueChange = { text ->
            tagsText = text
            edit { copy(tags = text.split(",").map(String::trim).filter(String::isNotEmpty)) }
        },
        label = { Text(stringResource(R.string.field_tags)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectSelector(
    task: Task,
    uiState: TaskEditorUiState,
    onSelected: (String?) -> Unit,
) {
    if (uiState.projects.isEmpty()) return
    Text(stringResource(R.string.field_project), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = task.projectId == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.field_project_none)) },
        )
        uiState.projects.forEach { project ->
            FilterChip(
                selected = task.projectId == project.id,
                onClick = { onSelected(project.id) },
                label = { Text(project.name) },
            )
        }
    }
    val last = uiState.projects.firstOrNull { it.id == uiState.lastProjectId }
    if (task.projectId == null && last != null) {
        TextButton(onClick = { onSelected(last.id) }) {
            Text(stringResource(R.string.field_project_last, last.name))
        }
    }
}

@Composable
private fun TaskField(
    @StringRes label: Int,
    value: String?,
    modifier: Modifier = Modifier,
    onChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { text -> onChange(text.ifEmpty { null }) },
        label = { Text(stringResource(label)) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleSelector(
    @StringRes label: Int,
    @ArrayRes levels: Int,
    value: Int?,
    onChange: (Int?) -> Unit,
) {
    val descriptions = stringArrayResource(levels)
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (1..descriptions.size).forEach { level ->
                FilterChip(
                    selected = value == level,
                    onClick = { onChange(level.takeUnless { it == value }) },
                    label = { Text(level.toString()) },
                )
            }
        }
        if (value != null) {
            Text(
                text = descriptions[value - 1],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateFields(task: Task, edit: (Task.() -> Task) -> Unit) {
    val created = task.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
    Text(
        text = stringResource(R.string.field_created, created.toDisplayText()),
        style = MaterialTheme.typography.bodyMedium,
    )
    DateRow(R.string.field_start_date, task.startDate) { edit { copy(startDate = it) } }
    DateRow(R.string.field_target_date, task.targetDate) { edit { copy(targetDate = it) } }
    DateRow(R.string.field_deadline_date, task.deadlineDate) { edit { copy(deadlineDate = it) } }
    MinutesField(R.string.field_estimated, task.estimatedMinutes) {
        edit { copy(estimatedMinutes = it) }
    }
    MinutesField(R.string.field_actual, task.actualMinutes) { edit { copy(actualMinutes = it) } }
}

@Composable
private fun DateRow(@StringRes label: Int, value: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun MinutesField(@StringRes label: Int, value: Int?, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text ->
            // Solo admite dígitos, de modo que no se pueden introducir tiempos negativos.
            if (text.isEmpty() || text.all(Char::isDigit)) onChange(text.toIntOrNull())
        },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleFields(task: Task, edit: (Task.() -> Task) -> Unit) {
    TaskField(R.string.field_owner, task.owner) { edit { copy(owner = it) } }
    TaskField(R.string.field_involved, task.involvedPeople) { edit { copy(involvedPeople = it) } }
    TaskField(R.string.field_dependencies, task.dependenciesNote) {
        edit { copy(dependenciesNote = it) }
    }
    Text(stringResource(R.string.field_delegation), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DelegationStatus.entries.forEach { status ->
            val label = when (status) {
                DelegationStatus.NOT_DELEGATED -> R.string.delegation_none
                DelegationStatus.DELEGATED -> R.string.delegation_delegated
                DelegationStatus.FOLLOWING_UP -> R.string.delegation_following
            }
            FilterChip(
                selected = task.delegationStatus == status,
                onClick = { edit { copy(delegationStatus = status) } },
                label = { Text(stringResource(label)) },
            )
        }
    }
}
