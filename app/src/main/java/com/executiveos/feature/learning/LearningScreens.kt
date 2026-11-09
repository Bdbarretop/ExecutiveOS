package com.executiveos.feature.learning

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.EmptyState
import com.executiveos.core.designsystem.component.ErrorState
import com.executiveos.core.designsystem.component.ExecutiveDatePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.formatDuration
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.learning.LearningCatalog
import com.executiveos.domain.learning.LearningGoal
import com.executiveos.domain.learning.LearningGoalStatus
import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.LearningValidationError
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.feature.metrics.MetricPeriodChips
import com.executiveos.feature.tasks.SaveNotice
import java.time.LocalDate

const val LEARNING_NEW_TAG = "learning_new"
const val LEARNING_TITLE_FIELD_TAG = "learning_title_field"
const val LEARNING_ADD_SESSION_TAG = "learning_add_session"
const val LEARNING_MINUTES_TAG = "learning_minutes"

private const val MAX_MASTERY = 5

@StringRes
private fun LearningGoalStatus.labelRes(): Int = when (this) {
    LearningGoalStatus.ACTIVE -> R.string.project_status_active
    LearningGoalStatus.PAUSED -> R.string.project_status_on_hold
    LearningGoalStatus.COMPLETED -> R.string.project_status_completed
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
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LearningScreen(
    onNavigateUp: () -> Unit,
    onOpenGoal: (String) -> Unit,
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showNew by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LearningEvent.OpenGoal -> onOpenGoal(event.goalId)
                is LearningEvent.Message -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(event.text))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(
                title = stringResource(R.string.section_learning),
                onNavigateUp = onNavigateUp,
                actions = {
                    TextButton(
                        onClick = { showNew = true },
                        modifier = Modifier.testTag(LEARNING_NEW_TAG),
                    ) {
                        Text(stringResource(R.string.learning_new))
                    }
                },
            )
            val state = uiState
            if (state == null) {
                LoadingState()
            } else {
                LearningContent(state, viewModel::selectPeriod, onOpenGoal)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showNew) {
        NewGoalDialog(
            onDismiss = { showNew = false },
            onConfirm = { title, topic ->
                showNew = false
                viewModel.createGoal(title, topic)
            },
        )
    }
}

@Composable
private fun LearningContent(
    state: LearningUiState,
    onSelectPeriod: (MetricPeriod) -> Unit,
    onOpenGoal: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MetricPeriodChips(state.period, onSelectPeriod)
        HoursCard(state)
        SectionTitle(R.string.learning_goals)
        if (state.goals.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.learning_empty_title),
                message = stringResource(R.string.learning_empty_message),
                iconRes = R.drawable.ic_school,
            )
        }
        state.goals.forEach { item -> GoalCard(item, onClick = { onOpenGoal(item.goal.id) }) }
    }
}

@Composable
private fun HoursCard(state: LearningUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SectionTitle(R.string.learning_hours_title)
            ValueRow(stringResource(R.string.learning_total), formatDuration(state.totalMinutes))
            if (state.byTopic.isEmpty()) {
                Text(
                    text = stringResource(R.string.learning_no_hours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.learning_by_topic), style = MaterialTheme.typography.labelLarge)
                state.byTopic.forEach { ValueRow(it.topic, formatDuration(it.minutes)) }
                Text(stringResource(R.string.learning_by_week), style = MaterialTheme.typography.labelLarge)
                state.byWeek.forEach {
                    ValueRow(
                        stringResource(R.string.review_week_of, it.weekStart.toDisplayText()),
                        formatDuration(it.minutes),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(item: GoalListItem, onClick: () -> Unit) {
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
            Text(
                text = listOf(stringResource(item.goal.status.labelRes()), item.goal.topic).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = item.goal.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    stringResource(R.string.learning_goal_total, formatDuration(item.totalMinutes)),
                    item.goal.targetDate?.toDisplayText(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicSuggestions(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LearningCatalog.INITIAL_TOPICS.forEach { topic ->
            FilterChip(
                selected = selected == topic,
                onClick = { onSelect(topic) },
                label = { Text(topic) },
            )
        }
    }
}

@Composable
private fun NewGoalDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learning_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.learning_goal_field)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LEARNING_TITLE_FIELD_TAG),
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.learning_topic)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TopicSuggestions(topic, onSelect = { topic = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && topic.isNotBlank(),
                onClick = { onConfirm(title, topic) },
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
fun LearningGoalScreen(
    onClose: () -> Unit,
    viewModel: LearningGoalViewModel = hiltViewModel(),
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
            ExecutiveTopBar(title = stringResource(R.string.learning_goal_title), onNavigateUp = close)
            val goal = uiState.goal
            when {
                uiState.isLoading -> LoadingState()
                goal == null -> ErrorState(message = stringResource(R.string.learning_goal_not_found))
                else -> GoalForm(goal, uiState, viewModel)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalForm(
    goal: LearningGoal,
    uiState: LearningGoalUiState,
    viewModel: LearningGoalViewModel,
) {
    val edit = viewModel::onEdit
    var pickDate by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SaveNotice(uiState.saveState)
        RequiredField(
            label = R.string.learning_goal_field,
            value = goal.title,
            error = R.string.learning_title_required.takeIf {
                LearningValidationError.BLANK_TITLE in uiState.errors
            },
            onChange = { title -> edit { copy(title = title) } },
        )
        RequiredField(
            label = R.string.learning_topic,
            value = goal.topic,
            error = R.string.learning_topic_required.takeIf {
                LearningValidationError.BLANK_TOPIC in uiState.errors
            },
            onChange = { topic -> edit { copy(topic = topic) } },
        )
        TopicSuggestions(goal.topic, onSelect = { topic -> edit { copy(topic = topic) } })
        OptionalField(R.string.learning_course, goal.course) { edit { copy(course = it) } }
        OptionalField(R.string.learning_materials, goal.materials) { edit { copy(materials = it) } }
        OptionalField(R.string.learning_evidence, goal.evidence) { edit { copy(evidence = it) } }
        Text(stringResource(R.string.learning_mastery), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(
                selected = goal.masteryLevel == null,
                onClick = { edit { copy(masteryLevel = null) } },
                label = { Text(stringResource(R.string.learning_mastery_none)) },
            )
            (1..MAX_MASTERY).forEach { level ->
                FilterChip(
                    selected = goal.masteryLevel == level,
                    onClick = { edit { copy(masteryLevel = level) } },
                    label = { Text(level.toString()) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.field_target_date), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = goal.targetDate?.toDisplayText() ?: stringResource(R.string.field_no_date),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (goal.targetDate != null) {
                TextButton(onClick = { edit { copy(targetDate = null) } }) {
                    Text(stringResource(R.string.action_clear))
                }
            }
            TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.action_choose)) }
        }
        Text(stringResource(R.string.task_status_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LearningGoalStatus.entries.forEach { status ->
                FilterChip(
                    selected = goal.status == status,
                    onClick = { edit { copy(status = status) } },
                    label = { Text(stringResource(status.labelRes())) },
                )
            }
        }
        SessionsSection(uiState, viewModel)
    }
    if (pickDate) {
        ExecutiveDatePickerDialog(
            initialDate = goal.targetDate,
            onDismiss = { pickDate = false },
            onDateSelected = { date ->
                edit { copy(targetDate = date) }
                pickDate = false
            },
        )
    }
}

@Composable
private fun RequiredField(
    @StringRes label: Int,
    value: String,
    @StringRes error: Int?,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OptionalField(@StringRes label: Int, value: String?, onChange: (String?) -> Unit) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { text -> onChange(text.ifEmpty { null }) },
        label = { Text(stringResource(label)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SessionsSection(uiState: LearningGoalUiState, viewModel: LearningGoalViewModel) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { SectionTitle(R.string.learning_sessions) }
            TextButton(
                onClick = { showAdd = true },
                modifier = Modifier.testTag(LEARNING_ADD_SESSION_TAG),
            ) {
                Text(stringResource(R.string.learning_session_add))
            }
        }
        Text(
            text = stringResource(R.string.learning_goal_total, formatDuration(uiState.totalMinutes)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (uiState.sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.learning_sessions_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        uiState.sessions.forEach { session ->
            SessionRow(session, onDelete = { deletingId = session.id })
        }
    }
    if (showAdd) {
        AddSessionDialog(
            initialDate = viewModel.today(),
            onDismiss = { showAdd = false },
            onAdd = { date, minutes, notes, evaluation ->
                showAdd = false
                viewModel.addSession(date, minutes, notes, evaluation)
            },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.learning_session_delete_title)) },
            text = { Text(stringResource(R.string.learning_session_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingId = null
                        viewModel.deleteSession(id)
                    },
                ) {
                    Text(stringResource(R.string.inbox_action_delete))
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
private fun SessionRow(session: LearningSession, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${session.date.toDisplayText()} · ${formatDuration(session.minutes)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            session.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            session.evaluation?.let {
                Text(
                    text = stringResource(R.string.learning_evaluation_value, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(
                    R.string.learning_session_delete,
                    session.date.toDisplayText(),
                ),
            )
        }
    }
}

@Composable
private fun AddSessionDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (LocalDate, Int, String?, String?) -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var evaluation by rememberSaveable { mutableStateOf("") }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val parsedMinutes = minutes.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learning_session_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(date.toDisplayText(), modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickDate = true }) {
                        Text(stringResource(R.string.action_choose))
                    }
                }
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { text -> if (text.all(Char::isDigit)) minutes = text },
                    label = { Text(stringResource(R.string.learning_session_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LEARNING_MINUTES_TAG),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.learning_session_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = evaluation,
                    onValueChange = { evaluation = it },
                    label = { Text(stringResource(R.string.learning_session_evaluation)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedMinutes != null,
                onClick = { parsedMinutes?.let { onAdd(date, it, notes, evaluation) } },
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
            initialDate = date,
            onDismiss = { pickDate = false },
            onDateSelected = {
                date = it
                pickDate = false
            },
        )
    }
}
