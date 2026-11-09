package com.executiveos.feature.planning

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ErrorState
import com.executiveos.core.designsystem.component.ExecutiveDatePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTimePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.BlockValidationError

const val BLOCK_TITLE_TAG = "block_title"
const val BLOCK_SAVE_TAG = "block_save"

private enum class BlockPicker { DATE, START, END, TASK }

@Composable
fun BlockEditorScreen(
    onClose: () -> Unit,
    viewModel: BlockEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BlockEditorEvent.Close -> onClose()
                is BlockEditorEvent.Message -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(event.text))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(
                title = stringResource(
                    if (uiState.isNew) R.string.block_new_title else R.string.block_edit_title,
                ),
                onNavigateUp = onClose,
            )
            val draft = uiState.draft
            when {
                uiState.isLoading -> LoadingState()
                draft == null -> ErrorState(message = stringResource(R.string.block_not_found))
                else -> BlockForm(draft, uiState, viewModel)
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
private fun BlockForm(
    draft: BlockDraft,
    uiState: BlockEditorUiState,
    viewModel: BlockEditorViewModel,
) {
    var picker by rememberSaveable { mutableStateOf<BlockPicker?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val edit = viewModel::onEdit
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { title -> edit { copy(title = title) } },
            label = { Text(stringResource(R.string.block_field_title)) },
            isError = uiState.showTitleError,
            supportingText = if (uiState.showTitleError) {
                { Text(stringResource(R.string.block_title_required)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BLOCK_TITLE_TAG),
        )
        Text(stringResource(R.string.block_field_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BlockType.entries.forEach { type ->
                FilterChip(
                    selected = draft.type == type,
                    onClick = { edit { copy(type = type) } },
                    label = { Text(stringResource(type.labelRes())) },
                )
            }
        }
        PickerRow(R.string.block_field_date, draft.date.toDisplayText()) { picker = BlockPicker.DATE }
        PickerRow(R.string.block_field_start, draft.start.toDisplayText()) { picker = BlockPicker.START }
        PickerRow(R.string.block_field_end, draft.end.toDisplayText()) { picker = BlockPicker.END }
        if (BlockValidationError.END_NOT_AFTER_START in uiState.errors) {
            WarningText(stringResource(R.string.block_end_error))
        }
        PickerRow(
            label = R.string.block_field_task,
            value = uiState.taskTitle ?: stringResource(R.string.block_task_none),
            onClear = if (draft.taskId != null) {
                { viewModel.onTaskSelected(null) }
            } else {
                null
            },
            onPick = { picker = BlockPicker.TASK },
        )
        OutlinedTextField(
            value = draft.actualMinutes?.toString().orEmpty(),
            onValueChange = { text ->
                if (text.all(Char::isDigit)) edit { copy(actualMinutes = text.toIntOrNull()) }
            },
            label = { Text(stringResource(R.string.block_field_actual)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.conflicts.isNotEmpty()) {
            WarningText(
                stringResource(
                    R.string.block_warning_overlap,
                    uiState.conflicts.joinToString(", ") { it.title },
                ),
            )
        }
        if (uiState.exceedsTarget) WarningText(stringResource(R.string.block_warning_target))
        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BLOCK_SAVE_TAG),
        ) {
            Text(stringResource(R.string.action_save))
        }
        if (!uiState.isNew) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.block_delete))
            }
        }
    }

    BlockPickerDialog(picker, draft, uiState.tasks, viewModel, onDone = { picker = null })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.block_delete_title)) },
            text = { Text(stringResource(R.string.block_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.inbox_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun BlockPickerDialog(
    picker: BlockPicker?,
    draft: BlockDraft,
    tasks: List<TaskReference>,
    viewModel: BlockEditorViewModel,
    onDone: () -> Unit,
) {
    when (picker) {
        null -> Unit
        BlockPicker.DATE -> ExecutiveDatePickerDialog(
            initialDate = draft.date,
            onDismiss = onDone,
            onDateSelected = { date ->
                viewModel.onEdit { copy(date = date) }
                onDone()
            },
        )
        BlockPicker.START -> ExecutiveTimePickerDialog(
            initialTime = draft.start,
            onDismiss = onDone,
            onTimeSelected = { time ->
                viewModel.onStartSelected(time)
                onDone()
            },
        )
        BlockPicker.END -> ExecutiveTimePickerDialog(
            initialTime = draft.end,
            onDismiss = onDone,
            onTimeSelected = { time ->
                viewModel.onEdit { copy(end = time) }
                onDone()
            },
        )
        BlockPicker.TASK -> TaskPickerDialog(
            tasks = tasks,
            onDismiss = onDone,
            onPick = { task ->
                viewModel.onTaskSelected(task)
                onDone()
            },
        )
    }
}

@Composable
private fun PickerRow(
    @StringRes label: Int,
    value: String,
    onClear: (() -> Unit)? = null,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (onClear != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
        }
        TextButton(onClick = onPick) { Text(stringResource(R.string.action_choose)) }
    }
}

@Composable
private fun WarningText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun TaskPickerDialog(
    tasks: List<TaskReference>,
    onDismiss: () -> Unit,
    onPick: (TaskReference) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_task_pick)) },
        text = {
            if (tasks.isEmpty()) {
                Text(stringResource(R.string.block_task_pick_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        Text(
                            text = task.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(task) }
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
