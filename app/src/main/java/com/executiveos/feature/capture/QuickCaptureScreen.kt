package com.executiveos.feature.capture

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveDatePickerDialog
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.core.navigation.TaskSection
import com.executiveos.domain.capture.SimilarTask
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.DateOption
import kotlinx.coroutines.launch

const val CAPTURE_TITLE_TAG = "capture_title"
const val CAPTURE_SAVE_TAG = "capture_save"

@Composable
fun QuickCaptureScreen(
    onClose: () -> Unit,
    onOpenTask: (taskId: String, section: TaskSection?) -> Unit,
    onManageTemplates: () -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val titleFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val close = { viewModel.flushDraft(onClose) }

    BackHandler(onBack = close)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickCaptureEvent.OpenTask -> onOpenTask(event.taskId, event.section)
                QuickCaptureEvent.Saved -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(R.string.capture_saved))
                }.also { titleFocus.requestFocus() }
                QuickCaptureEvent.SaveFailed -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.capture_save_failed))
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
            ExecutiveTopBar(title = stringResource(R.string.capture_title), onNavigateUp = close)
            val draft = uiState.draft
            if (draft == null) {
                LoadingState()
            } else {
                CaptureForm(
                    uiState = uiState,
                    draft = draft,
                    titleFocus = titleFocus,
                    viewModel = viewModel,
                    onOpenSimilar = { onOpenTask(it.id, null) },
                    onManageTemplates = { viewModel.flushDraft(onManageTemplates) },
                )
                LaunchedEffect(Unit) { titleFocus.requestFocus() }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CaptureForm(
    uiState: QuickCaptureUiState,
    draft: CaptureDraft,
    titleFocus: FocusRequester,
    viewModel: QuickCaptureViewModel,
    onOpenSimilar: (SimilarTask) -> Unit,
    onManageTemplates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TitleField(draft.title, uiState.showTitleError, titleFocus, viewModel::onTitleChange)
        if (uiState.similarTasks.isNotEmpty()) {
            SimilarTasksNotice(uiState.similarTasks, onOpenSimilar)
        }
        CategoryChips(uiState, draft, viewModel::onCategorySelected)
        DateSection(draft, viewModel)
        DetailsSection(draft.details, viewModel::onDetailsChange)
        TemplateChips(uiState, draft, viewModel, onManageTemplates)
        Button(
            onClick = viewModel::save,
            enabled = !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CAPTURE_SAVE_TAG),
        ) {
            Text(stringResource(R.string.capture_save))
        }
        ProgressiveActions(viewModel::saveAndOpen)
    }
}

@Composable
private fun TitleField(
    title: String,
    showError: Boolean,
    focusRequester: FocusRequester,
    onChange: (String) -> Unit,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(title, TextRange(title.length)))
    }
    // Sincroniza cambios externos (plantilla o nueva captura) dejando el cursor al final.
    LaunchedEffect(title) {
        if (title != field.text) field = TextFieldValue(title, TextRange(title.length))
    }
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onChange(it.text)
        },
        label = { Text(stringResource(R.string.capture_field_title)) },
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(R.string.capture_title_required)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag(CAPTURE_TITLE_TAG),
    )
}

@Composable
private fun SimilarTasksNotice(tasks: List<SimilarTask>, onOpen: (SimilarTask) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.capture_possible_duplicate),
                style = MaterialTheme.typography.labelLarge,
            )
            tasks.forEach { task ->
                Text(
                    text = task.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(task) }
                        .padding(vertical = Spacing.xs),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    uiState: QuickCaptureUiState,
    draft: CaptureDraft,
    onSelected: (String) -> Unit,
) {
    Column {
        FieldLabel(R.string.capture_category)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            uiState.categories.forEach { category ->
                FilterChip(
                    selected = draft.categoryId == category.id,
                    onClick = { onSelected(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSection(draft: CaptureDraft, viewModel: QuickCaptureViewModel) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column {
        FieldLabel(R.string.capture_date)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DateOption.entries.forEach { option ->
                FilterChip(
                    selected = draft.dateOption == option,
                    onClick = {
                        if (option == DateOption.CUSTOM) {
                            showPicker = true
                        } else {
                            viewModel.onDateOptionSelected(option)
                        }
                    },
                    label = { Text(dateOptionLabel(option, draft)) },
                )
            }
        }
        if (draft.dateOption != DateOption.NONE) {
            DeadlineSwitch(draft.dateIsDeadline, viewModel::onDeadlineChanged)
        }
    }
    if (showPicker) {
        ExecutiveDatePickerDialog(
            initialDate = draft.customDate,
            onDismiss = { showPicker = false },
            onDateSelected = {
                viewModel.onCustomDateSelected(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun dateOptionLabel(option: DateOption, draft: CaptureDraft): String = when (option) {
    DateOption.TODAY -> stringResource(R.string.capture_date_today)
    DateOption.TOMORROW -> stringResource(R.string.capture_date_tomorrow)
    DateOption.THIS_WEEK -> stringResource(R.string.capture_date_this_week)
    DateOption.CUSTOM ->
        draft.customDate?.toDisplayText() ?: stringResource(R.string.capture_date_custom)
    DateOption.NONE -> stringResource(R.string.capture_date_none)
}

@Composable
private fun DeadlineSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.capture_is_deadline))
            Text(
                text = stringResource(R.string.capture_is_deadline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DetailsSection(details: String, onChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(details.isNotEmpty()) }
    if (expanded || details.isNotEmpty()) {
        OutlinedTextField(
            value = details,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.capture_field_details)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.capture_add_details))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateChips(
    uiState: QuickCaptureUiState,
    draft: CaptureDraft,
    viewModel: QuickCaptureViewModel,
    onManage: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FieldLabel(R.string.capture_templates)
            TextButton(onClick = onManage) {
                Text(stringResource(R.string.capture_manage_templates))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            uiState.templates.forEach { template ->
                FilterChip(
                    selected = draft.templateId == template.id,
                    onClick = { viewModel.onTemplateSelected(template) },
                    label = { Text(template.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressiveActions(onOpen: (TaskSection) -> Unit) {
    Column {
        FieldLabel(R.string.capture_complete_now)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ProgressiveChip(R.string.capture_add_context) { onOpen(TaskSection.CONTEXT) }
            ProgressiveChip(R.string.capture_evaluate_importance) { onOpen(TaskSection.IMPORTANCE) }
            ProgressiveChip(R.string.capture_define_next_action) { onOpen(TaskSection.NEXT_ACTION) }
            ProgressiveChip(R.string.capture_more_options) { onOpen(TaskSection.MORE) }
        }
    }
}

@Composable
private fun ProgressiveChip(@StringRes label: Int, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun FieldLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
