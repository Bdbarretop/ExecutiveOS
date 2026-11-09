package com.executiveos.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.theme.Spacing

@Composable
fun PrioritySettingsScreen(
    onClose: () -> Unit,
    viewModel: PrioritySettingsViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val pending by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.closeEvents.collect { onClose() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ExecutiveTopBar(
            title = stringResource(R.string.priority_settings_title),
            onNavigateUp = onClose,
        )
        val current = form
        if (current == null) {
            LoadingState()
        } else {
            SettingsForm(current, viewModel)
        }
    }

    pending?.let { changes ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(stringResource(R.string.priority_settings_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.priority_settings_confirm_message,
                        changes,
                        changes,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSave) {
                    Text(stringResource(R.string.action_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmation) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsForm(form: PrioritySettingsForm, viewModel: PrioritySettingsViewModel) {
    val update = viewModel::onFormChange
    val isValid = form.toSettings() != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GroupTitle(R.string.priority_settings_weights)
        NumberField(R.string.priority_settings_impact, form.impact) { update { copy(impact = it) } }
        NumberField(R.string.priority_settings_urgency, form.urgency) { update { copy(urgency = it) } }
        NumberField(R.string.priority_settings_delay, form.delay) { update { copy(delay = it) } }
        GroupTitle(R.string.priority_settings_thresholds)
        DecimalField(R.string.priority_settings_critical, form.critical) { update { copy(critical = it) } }
        DecimalField(R.string.priority_settings_high, form.high) { update { copy(high = it) } }
        DecimalField(R.string.priority_settings_medium, form.medium) { update { copy(medium = it) } }
        if (!isValid) {
            Text(
                text = stringResource(R.string.priority_settings_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = viewModel::requestSave,
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
        TextButton(onClick = viewModel::resetToDefaults) {
            Text(stringResource(R.string.priority_settings_reset))
        }
    }
}

@Composable
private fun GroupTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun NumberField(@StringRes label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (text.all(Char::isDigit)) onChange(text) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DecimalField(@StringRes label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
