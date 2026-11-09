package com.executiveos.feature.templates

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.CaptureTemplate

@Composable
fun TemplatesScreen(
    onNavigateUp: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        ExecutiveTopBar(
            title = stringResource(R.string.templates_title),
            onNavigateUp = onNavigateUp,
        )
        val items = templates
        if (items == null) {
            LoadingState()
        } else {
            LazyColumn {
                items(items, key = { it.id }) { template ->
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = template.nextAction?.let { action -> { Text(action) } },
                        trailingContent = {
                            Icon(
                                painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable { onOpenTemplate(template.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TemplateEditorScreen(
    onClose: () -> Unit,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ExecutiveTopBar(title = stringResource(R.string.template_edit_title), onNavigateUp = onClose)
        val template = uiState.template
        if (template == null) {
            LoadingState()
        } else {
            TemplateForm(template, uiState, viewModel, onClose)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateForm(
    template: CaptureTemplate,
    uiState: TemplateEditorUiState,
    viewModel: TemplateEditorViewModel,
    onSaved: () -> Unit,
) {
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
            value = template.name,
            onValueChange = { name -> edit { copy(name = name) } },
            label = { Text(stringResource(R.string.template_name)) },
            isError = uiState.showNameError,
            supportingText = if (uiState.showNameError) {
                { Text(stringResource(R.string.template_name_required)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = template.titlePrefix,
            onValueChange = { prefix -> edit { copy(titlePrefix = prefix) } },
            label = { Text(stringResource(R.string.template_prefix)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            uiState.categories.forEach { category ->
                FilterChip(
                    selected = template.categoryId == category.id,
                    onClick = {
                        edit { copy(categoryId = category.id.takeUnless { it == categoryId }) }
                    },
                    label = { Text(category.name) },
                )
            }
        }
        TemplateField(R.string.field_what_to_do, template.whatToDo) { edit { copy(whatToDo = it) } }
        TemplateField(R.string.field_outcome, template.expectedOutcome) {
            edit { copy(expectedOutcome = it) }
        }
        TemplateField(R.string.field_next_action, template.nextAction) {
            edit { copy(nextAction = it) }
        }
        OutlinedTextField(
            value = template.estimatedMinutes?.toString().orEmpty(),
            onValueChange = { text ->
                if (text.isEmpty() || text.all(Char::isDigit)) {
                    edit { copy(estimatedMinutes = text.toIntOrNull()) }
                }
            },
            label = { Text(stringResource(R.string.template_estimated)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.save(onSaved) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun TemplateField(@StringRes label: Int, value: String?, onChange: (String?) -> Unit) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { text -> onChange(text.ifEmpty { null }) },
        label = { Text(stringResource(label)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}
