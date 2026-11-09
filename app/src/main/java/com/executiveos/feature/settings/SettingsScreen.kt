package com.executiveos.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ErrorState
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.model.ThemeMode

@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onThemeSelected = viewModel::onThemeSelected,
        onNavigateUp = onNavigateUp,
        onOpenTemplates = onOpenTemplates,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ExecutiveTopBar(
            title = stringResource(R.string.section_settings),
            onNavigateUp = onNavigateUp,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.fabClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AppearanceSection(uiState.themeMode, onThemeSelected)
            CaptureSection(onOpenTemplates)
            SystemStatusSection(uiState.databaseStatus)
        }
    }
}

@Composable
private fun AppearanceSection(selected: ThemeMode, onThemeSelected: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        SectionTitle(R.string.settings_appearance)
        ThemeMode.entries.forEach { mode ->
            ThemeOption(
                mode = mode,
                selected = mode == selected,
                onClick = { onThemeSelected(mode) },
            )
        }
    }
}

@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = stringResource(mode.labelRes()))
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun CaptureSection(onOpenTemplates: () -> Unit) {
    Column {
        SectionTitle(R.string.settings_capture)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTemplates)
                .padding(vertical = Spacing.sm),
        ) {
            Text(stringResource(R.string.settings_templates))
            Text(
                text = stringResource(R.string.settings_templates_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemStatusSection(status: DatabaseStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(R.string.settings_system_status)
        when (status) {
            DatabaseStatus.Loading -> LoadingState()
            DatabaseStatus.Unavailable ->
                ErrorState(message = stringResource(R.string.settings_database_unavailable))
            is DatabaseStatus.Ready -> {
                StatusRow(
                    label = stringResource(R.string.settings_database),
                    value = stringResource(R.string.settings_database_active),
                )
                StatusRow(
                    label = stringResource(R.string.settings_categories),
                    value = status.categoryCount.toString(),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
