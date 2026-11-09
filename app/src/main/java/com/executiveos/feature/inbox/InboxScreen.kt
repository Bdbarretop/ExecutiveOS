package com.executiveos.feature.inbox

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.EmptyState
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.InboxState
import com.executiveos.domain.model.ProcessedAs

private sealed interface InboxDialog {
    data class Delegate(val itemId: String) : InboxDialog
    data class Postpone(val itemIds: Set<String>) : InboxDialog
    data class Delete(val itemIds: Set<String>) : InboxDialog
    data class Original(val entry: InboxEntry) : InboxDialog
}

fun inboxMenuTag(itemId: String): String = "inbox_menu_$itemId"

@Composable
fun InboxScreen(
    onOpenTask: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var dialog by remember { mutableStateOf<InboxDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.selectedIds.isEmpty()) {
                ExecutiveTopBar(title = stringResource(R.string.section_inbox))
            } else {
                SelectionTopBar(uiState.selectedIds.size, viewModel::clearSelection)
                BatchActions(
                    onToTasks = { viewModel.convertToTasks(uiState.selectedIds) },
                    onPostpone = { dialog = InboxDialog.Postpone(uiState.selectedIds) },
                    onArchive = { viewModel.archive(uiState.selectedIds) },
                    onDelete = { dialog = InboxDialog.Delete(uiState.selectedIds) },
                )
            }
            InboxTabs(uiState, viewModel::onTabSelected)
            InboxContent(
                uiState = uiState,
                onEntryClick = { entry ->
                    when {
                        uiState.selectedIds.isNotEmpty() -> viewModel.onToggleSelection(entry.item.id)
                        entry.opensTask() -> entry.taskId?.let(onOpenTask)
                        else -> dialog = InboxDialog.Original(entry)
                    }
                },
                onEntryLongClick = { entry ->
                    if (uiState.tab != InboxTab.PROCESSED) viewModel.onToggleSelection(entry.item.id)
                },
                menu = { entry ->
                    EntryMenu(
                        entry = entry,
                        onOpen = { entry.taskId?.let(onOpenTask) },
                        onToTask = { viewModel.convertToTasks(listOf(entry.item.id)) },
                        onToProject = { viewModel.convertToProject(entry.item.id) },
                        onToNote = { viewModel.convertToNote(entry.item.id) },
                        onDelegate = { dialog = InboxDialog.Delegate(entry.item.id) },
                        onPostpone = { dialog = InboxDialog.Postpone(setOf(entry.item.id)) },
                        onArchive = { viewModel.archive(listOf(entry.item.id)) },
                        onDelete = { dialog = InboxDialog.Delete(setOf(entry.item.id)) },
                    )
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    dialog?.let { current ->
        InboxDialogs(
            dialog = current,
            onDismiss = { dialog = null },
            viewModel = viewModel,
        )
    }
}

private fun InboxEntry.opensTask(): Boolean = taskId != null &&
    item.state != InboxState.ARCHIVED &&
    (item.processedAs == null || item.processedAs == ProcessedAs.TASK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClear: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.inbox_selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        },
    )
}

@Composable
private fun BatchActions(
    onToTasks: () -> Unit,
    onPostpone: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onToTasks) { Text(stringResource(R.string.inbox_batch_to_task)) }
        TextButton(onClick = onPostpone) { Text(stringResource(R.string.inbox_action_postpone)) }
        TextButton(onClick = onArchive) { Text(stringResource(R.string.inbox_action_archive)) }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.inbox_action_delete)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTabs(uiState: InboxUiState, onSelected: (InboxTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal) {
        InboxTab.entries.forEach { tab ->
            val (label, count) = when (tab) {
                InboxTab.PENDING -> R.string.inbox_tab_pending to uiState.pending.size
                InboxTab.POSTPONED -> R.string.inbox_tab_postponed to uiState.postponed.size
                InboxTab.PROCESSED -> R.string.inbox_tab_processed to uiState.processed.size
            }
            Tab(
                selected = uiState.tab == tab,
                onClick = { onSelected(tab) },
                text = {
                    Text(
                        text = stringResource(label, count),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun InboxContent(
    uiState: InboxUiState,
    onEntryClick: (InboxEntry) -> Unit,
    onEntryLongClick: (InboxEntry) -> Unit,
    menu: @Composable (InboxEntry) -> Unit,
) {
    when {
        uiState.isLoading -> LoadingState()
        uiState.visible.isEmpty() -> TabEmptyState(uiState.tab)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
        ) {
            items(uiState.visible, key = { it.item.id }) { entry ->
                InboxRow(
                    entry = entry,
                    selected = entry.item.id in uiState.selectedIds,
                    selecting = uiState.selectedIds.isNotEmpty(),
                    onClick = { onEntryClick(entry) },
                    onLongClick = { onEntryLongClick(entry) },
                    menu = { menu(entry) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TabEmptyState(tab: InboxTab) {
    val (title, message) = when (tab) {
        InboxTab.PENDING ->
            R.string.inbox_empty_pending_title to R.string.inbox_empty_pending_message
        InboxTab.POSTPONED ->
            R.string.inbox_empty_postponed_title to R.string.inbox_empty_postponed_message
        InboxTab.PROCESSED ->
            R.string.inbox_empty_processed_title to R.string.inbox_empty_processed_message
    }
    EmptyState(
        title = stringResource(title),
        message = stringResource(message),
        iconRes = R.drawable.ic_inbox,
        modifier = Modifier.padding(top = Spacing.xl),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxRow(
    entry: InboxEntry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = entrySummary(entry)
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!selecting) menu()
    }
}

@Composable
private fun entrySummary(entry: InboxEntry): String {
    val item = entry.item
    val parts = buildList {
        entry.categoryName?.let(::add)
        item.capturedDate?.let { date ->
            val label = if (item.dateIsDeadline) {
                R.string.inbox_deadline_label
            } else {
                R.string.inbox_target_label
            }
            add(stringResource(label, date.toDisplayText()))
        }
        if (item.state == InboxState.POSTPONED) {
            item.postponedUntil?.let {
                add(stringResource(R.string.inbox_postponed_until, it.toDisplayText()))
            }
        }
        closedLabel(entry)?.let { add(stringResource(it)) }
    }
    return parts.joinToString(" · ")
}

@StringRes
private fun closedLabel(entry: InboxEntry): Int? = when {
    entry.item.state == InboxState.ARCHIVED -> R.string.inbox_status_archived
    entry.item.processedAs == ProcessedAs.TASK -> R.string.inbox_status_task
    entry.item.processedAs == ProcessedAs.PROJECT -> R.string.inbox_status_project
    entry.item.processedAs == ProcessedAs.NOTE -> R.string.inbox_status_note
    else -> null
}

@Composable
private fun EntryMenu(
    entry: InboxEntry,
    onOpen: () -> Unit,
    onToTask: () -> Unit,
    onToProject: () -> Unit,
    onToNote: () -> Unit,
    onDelegate: () -> Unit,
    onPostpone: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(inboxMenuTag(entry.item.id)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.action_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val item: @Composable (Int, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            val isOpen = entry.item.state == InboxState.PENDING ||
                entry.item.state == InboxState.POSTPONED
            if (entry.taskId != null) item(R.string.inbox_action_open, onOpen)
            if (isOpen) {
                item(R.string.inbox_action_to_task, onToTask)
                item(R.string.inbox_action_to_project, onToProject)
                item(R.string.inbox_action_to_note, onToNote)
                item(R.string.inbox_action_delegate, onDelegate)
                item(R.string.inbox_action_postpone, onPostpone)
                item(R.string.inbox_action_archive, onArchive)
            }
            item(R.string.inbox_action_delete, onDelete)
        }
    }
}

@Composable
private fun InboxDialogs(
    dialog: InboxDialog,
    onDismiss: () -> Unit,
    viewModel: InboxViewModel,
) {
    when (dialog) {
        is InboxDialog.Delegate -> DelegateDialog(onDismiss) { person ->
            viewModel.delegate(dialog.itemId, person)
            onDismiss()
        }
        is InboxDialog.Postpone -> PostponeDialog(onDismiss) { option ->
            viewModel.postpone(dialog.itemIds, option)
            onDismiss()
        }
        is InboxDialog.Delete -> DeleteDialog(onDismiss) {
            viewModel.delete(dialog.itemIds)
            onDismiss()
        }
        is InboxDialog.Original -> OriginalDialog(dialog.entry, onDismiss)
    }
}

@Composable
private fun DelegateDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var person by remember { mutableStateOf("") }
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

@Composable
private fun PostponeDialog(onDismiss: () -> Unit, onSelected: (PostponeOption) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbox_postpone_title)) },
        text = {
            Column {
                PostponeOption.entries.forEach { option ->
                    val label = when (option) {
                        PostponeOption.TOMORROW -> R.string.inbox_postpone_tomorrow
                        PostponeOption.IN_THREE_DAYS -> R.string.inbox_postpone_three_days
                        PostponeOption.NEXT_WEEK -> R.string.inbox_postpone_next_week
                    }
                    TextButton(onClick = { onSelected(option) }) { Text(stringResource(label)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbox_delete_title)) },
        text = { Text(stringResource(R.string.inbox_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.inbox_action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun OriginalDialog(entry: InboxEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbox_original_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(entry.item.originalTitle, style = MaterialTheme.typography.titleMedium)
                Text(entry.item.originalDetails ?: stringResource(R.string.inbox_original_no_details))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
