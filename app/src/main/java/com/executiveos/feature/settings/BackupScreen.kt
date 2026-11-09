package com.executiveos.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.LoadingState
import com.executiveos.core.designsystem.component.toDisplayText
import com.executiveos.core.designsystem.theme.Spacing
import com.executiveos.domain.backup.BackupSummary
import com.executiveos.domain.backup.ExportFormat

const val BACKUP_PROTECTED_TAG = "backup_protected"
const val BACKUP_PASSWORD_TAG = "backup_password"
const val BACKUP_PASSWORD_CONFIRM_TAG = "backup_password_confirm"
const val BACKUP_PASSWORD_ACCEPT_TAG = "backup_password_accept"

private const val ANY_FILE = "*/*"

@Composable
fun BackupScreen(
    onNavigateUp: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var plainFormat by rememberSaveable { mutableStateOf<ExportFormat?>(null) }

    val onDestination: (android.net.Uri?) -> Unit = { viewModel.onExportDestination(it?.toString()) }
    val protectedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        onDestination,
    )
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        onDestination,
    )
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
        onDestination,
    )
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { viewModel.inspect(it?.toString()) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExecutiveTopBar(title = stringResource(R.string.backup_title), onNavigateUp = onNavigateUp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                WarningCard()
                if (uiState.isWorking) LoadingState(message = stringResource(R.string.backup_working))
                SectionTitle(R.string.backup_export_section)
                BackupAction(
                    title = R.string.backup_protected,
                    description = R.string.backup_protected_description,
                    enabled = !uiState.isWorking,
                    modifier = Modifier.testTag(BACKUP_PROTECTED_TAG),
                    onClick = { showPasswordDialog = true },
                )
                BackupAction(
                    title = R.string.backup_plain,
                    description = R.string.backup_plain_description,
                    enabled = !uiState.isWorking,
                    onClick = { plainFormat = ExportFormat.PLAIN_JSON },
                )
                BackupAction(
                    title = R.string.backup_csv,
                    description = R.string.backup_csv_description,
                    enabled = !uiState.isWorking,
                    onClick = { plainFormat = ExportFormat.CSV_ZIP },
                )
                SectionTitle(R.string.backup_restore_section)
                BackupAction(
                    title = R.string.backup_restore,
                    description = R.string.backup_restore_description,
                    enabled = !uiState.isWorking,
                    onClick = { openLauncher.launch(arrayOf(ANY_FILE)) },
                )
                Text(
                    text = stringResource(R.string.backup_not_included),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showPasswordDialog) {
        NewPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                showPasswordDialog = false
                protectedLauncher.launch(viewModel.prepareExport(ExportFormat.PROTECTED_JSON, password))
            },
        )
    }
    plainFormat?.let { format ->
        PlainExportWarning(
            onDismiss = { plainFormat = null },
            onConfirm = {
                plainFormat = null
                val name = viewModel.prepareExport(format, null)
                if (format == ExportFormat.CSV_ZIP) zipLauncher.launch(name) else jsonLauncher.launch(name)
            },
        )
    }
    when (val stage = uiState.restoreStage) {
        RestoreStage.Idle -> Unit
        is RestoreStage.AskPassword -> AskPasswordDialog(
            wasWrong = stage.wasWrong,
            onDismiss = viewModel::dismissRestore,
            onConfirm = { password ->
                viewModel.dismissRestore()
                viewModel.inspect(stage.source, password)
            },
        )
        is RestoreStage.Confirm -> ConfirmRestoreDialog(
            summary = stage.summary,
            onDismiss = viewModel::dismissRestore,
            onConfirm = viewModel::confirmRestore,
        )
    }
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
private fun WarningCard() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(stringResource(R.string.backup_warning_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.backup_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BackupAction(
    @StringRes title: Int,
    @StringRes description: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    @StringRes label: Int,
    tag: String,
    isError: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

/** Con `remember`, no `rememberSaveable`: la contraseña no debe acabar en el estado guardado. */
@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val mismatch = repeated.isNotEmpty() && password != repeated
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.backup_password_hint, MIN_BACKUP_PASSWORD_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                )
                PasswordField(password, R.string.backup_password_field, BACKUP_PASSWORD_TAG) { password = it }
                PasswordField(
                    value = repeated,
                    label = R.string.backup_password_confirm,
                    tag = BACKUP_PASSWORD_CONFIRM_TAG,
                    isError = mismatch,
                    onChange = { repeated = it },
                )
                if (mismatch) {
                    Text(
                        text = stringResource(R.string.backup_password_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= MIN_BACKUP_PASSWORD_LENGTH && password == repeated,
                onClick = { onConfirm(password) },
                modifier = Modifier.testTag(BACKUP_PASSWORD_ACCEPT_TAG),
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
private fun AskPasswordDialog(
    wasWrong: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_ask_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PasswordField(
                    value = password,
                    label = R.string.backup_password_field,
                    tag = BACKUP_PASSWORD_TAG,
                    isError = wasWrong,
                    onChange = { password = it },
                )
                if (wasWrong) {
                    Text(
                        text = stringResource(R.string.backup_wrong_password),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PlainExportWarning(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_plain_warning_title)) },
        text = { Text(stringResource(R.string.backup_plain_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ConfirmRestoreDialog(
    summary: BackupSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.backup_confirm_summary, summary.createdOn.toDisplayText()),
                    style = MaterialTheme.typography.titleSmall,
                )
                CountRow(R.string.section_tasks, summary.tasks)
                CountRow(R.string.section_projects, summary.projects)
                CountRow(R.string.backup_blocks, summary.timeBlocks)
                CountRow(R.string.section_reviews, summary.reviews)
                CountRow(R.string.backup_goals, summary.learningGoals)
                Text(
                    text = stringResource(R.string.backup_confirm_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_confirm_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CountRow(@StringRes label: Int, count: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}
