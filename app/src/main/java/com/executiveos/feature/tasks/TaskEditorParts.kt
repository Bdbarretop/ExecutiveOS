package com.executiveos.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.executiveos.R
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskHistoryEntry
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.CriticalObligation
import com.executiveos.domain.priority.PriorityAssessment
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.tasks.TaskStatusRules
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val HISTORY_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.forLanguageTag("es"))

private val SELECTABLE_STATUSES = listOf(
    TaskStatus.PENDING,
    TaskStatus.IN_PROGRESS,
    TaskStatus.WAITING,
    TaskStatus.BLOCKED,
    TaskStatus.COMPLETED,
    TaskStatus.CANCELLED,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusSelector(
    status: TaskStatus,
    hasOpenWork: Boolean,
    onStatusSelected: (TaskStatus) -> Unit,
) {
    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Text(stringResource(R.string.task_status_label), style = MaterialTheme.typography.titleSmall)
        if (status == TaskStatus.INBOX || status == TaskStatus.ARCHIVED) {
            Text(stringResource(status.labelRes()))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SELECTABLE_STATUSES.forEach { option ->
                FilterChip(
                    selected = status == option,
                    enabled = status == option || TaskStatusRules.canTransition(status, option),
                    onClick = {
                        when {
                            status == option -> Unit
                            option == TaskStatus.COMPLETED && hasOpenWork -> confirmComplete = true
                            else -> onStatusSelected(option)
                        }
                    },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
    }
    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text(stringResource(R.string.complete_open_title)) },
            text = { Text(stringResource(R.string.complete_open_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmComplete = false
                        onStatusSelected(TaskStatus.COMPLETED)
                    },
                ) {
                    Text(stringResource(R.string.tasks_action_complete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrioritySummary(assessment: PriorityAssessment) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.priority_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                assessment.obligation?.let { ObligationBadge(it) }
                PriorityBadge(assessment.effectiveLevel)
            }
            Text(priorityExplanation(assessment), style = MaterialTheme.typography.bodyMedium)
            assessment.obligation?.let { obligation ->
                Text(
                    text = stringResource(
                        R.string.obligation_warning,
                        stringResource(obligation.labelRes()),
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ScoreDetails(assessment)
        }
    }
}

@Composable
private fun ScoreDetails(assessment: PriorityAssessment) {
    assessment.score?.let { score ->
        Text(
            text = stringResource(R.string.priority_score, formatHundredths(score)),
            style = MaterialTheme.typography.labelLarge,
        )
        assessment.contributions.forEach { contribution ->
            Text(
                text = stringResource(
                    R.string.priority_breakdown,
                    stringResource(contribution.scale.labelRes()),
                    contribution.value,
                    formatHundredths(contribution.weightPercent),
                    formatHundredths(contribution.points),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (assessment.manualLevel != null) {
        val calculated = assessment.calculatedLevel?.let { stringResource(it.labelRes()) }
            ?: stringResource(R.string.level_pending)
        Text(
            text = stringResource(
                R.string.priority_manual_note,
                assessment.manualReason.orEmpty(),
                calculated,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualPriorityFields(task: Task, edit: (Task.() -> Task) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.priority_manual_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = task.manualPriority == null,
                onClick = { edit { copy(manualPriority = null, manualPriorityReason = null) } },
                label = { Text(stringResource(R.string.priority_manual_none)) },
            )
            PriorityLevel.entries.forEach { level ->
                FilterChip(
                    selected = task.manualPriority == level,
                    onClick = { edit { copy(manualPriority = level) } },
                    label = { Text(stringResource(level.labelRes())) },
                )
            }
        }
        if (task.manualPriority != null) {
            OutlinedTextField(
                value = task.manualPriorityReason.orEmpty(),
                onValueChange = { text -> edit { copy(manualPriorityReason = text.ifEmpty { null }) } },
                label = { Text(stringResource(R.string.priority_manual_reason)) },
                isError = task.manualPriorityReason.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObligationFields(task: Task, edit: (Task.() -> Task) -> Unit) {
    Column {
        Text(stringResource(R.string.obligation_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = task.obligation == null,
                onClick = { edit { copy(obligation = null) } },
                label = { Text(stringResource(R.string.obligation_none)) },
            )
            CriticalObligation.entries.forEach { option ->
                FilterChip(
                    selected = task.obligation == option,
                    onClick = { edit { copy(obligation = option) } },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
    }
}

@Composable
fun SubtasksSection(
    subtasks: List<TaskReference>,
    onOpen: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    subtasks.forEach { subtask -> ReferenceRow(subtask, onOpen) }
    var title by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.subtask_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = title.isNotBlank(),
            onClick = {
                onAdd(title)
                title = ""
            },
        ) {
            Text(stringResource(R.string.subtask_add))
        }
    }
}

@Composable
fun DependenciesSection(
    dependencies: List<TaskReference>,
    candidates: List<TaskReference>,
    onOpen: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    dependencies.forEach { dependency ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReferenceRow(dependency, onOpen, modifier = Modifier.weight(1f))
            IconButton(onClick = { onRemove(dependency.id) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.dependency_remove, dependency.title),
                )
            }
        }
    }
    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.dependency_add)) }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.dependency_pick)) },
            text = {
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.dependency_none_available))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.id }) { candidate ->
                            Text(
                                text = candidate.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAdd(candidate.id)
                                        picking = false
                                    }
                                    .padding(vertical = Spacing.sm),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReferenceRow(
    reference: TaskReference,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(reference.id) }
            .padding(vertical = Spacing.xs),
    ) {
        Text(reference.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(reference.status.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HistorySection(history: List<TaskHistoryEntry>) {
    if (history.isEmpty()) {
        Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    history.forEach { entry ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(entry.action.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = HISTORY_FORMAT.format(entry.createdAt.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DelegateTaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var person by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbox_delegate_title)) },
        text = {
            OutlinedTextField(
                value = person,
                onValueChange = { person = it },
                label = { Text(stringResource(R.string.inbox_delegate_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(enabled = person.isNotBlank(), onClick = { onConfirm(person) }) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
