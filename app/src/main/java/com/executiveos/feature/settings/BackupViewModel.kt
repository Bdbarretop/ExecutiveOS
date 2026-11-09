package com.executiveos.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.R
import com.executiveos.domain.backup.BackupCheck
import com.executiveos.domain.backup.BackupRepository
import com.executiveos.domain.backup.BackupSummary
import com.executiveos.domain.backup.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MIN_BACKUP_PASSWORD_LENGTH = 8

sealed interface RestoreStage {
    data object Idle : RestoreStage
    data class AskPassword(val source: String, val wasWrong: Boolean) : RestoreStage
    data class Confirm(
        val source: String,
        val password: String?,
        val summary: BackupSummary,
    ) : RestoreStage
}

data class BackupUiState(
    val isWorking: Boolean = false,
    val restoreStage: RestoreStage = RestoreStage.Idle,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    private val messageChannel = Channel<Int>(Channel.BUFFERED)

    /** La contraseña solo vive en memoria mientras el selector de archivos está abierto. */
    private var pendingExport: Pair<ExportFormat, String?>? = null

    val uiState: StateFlow<BackupUiState> = mutableState.asStateFlow()
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    fun prepareExport(format: ExportFormat, password: String?): String {
        pendingExport = format to password
        val date = LocalDate.now(clock)
        return when (format) {
            ExportFormat.PROTECTED_JSON -> "executive-os-$date.eosbackup"
            ExportFormat.PLAIN_JSON -> "executive-os-$date.json"
            ExportFormat.CSV_ZIP -> "executive-os-$date.zip"
        }
    }

    fun onExportDestination(destination: String?) {
        val (format, password) = pendingExport ?: return
        pendingExport = null
        if (destination == null) return
        work(R.string.backup_export_failed) {
            repository.export(format, destination, password)
            messageChannel.send(R.string.backup_export_done)
        }
    }

    fun inspect(source: String?, password: String? = null) {
        if (source == null) return
        work(R.string.backup_invalid) {
            when (val result = repository.check(source, password)) {
                is BackupCheck.Valid ->
                    setStage(RestoreStage.Confirm(source, password, result.summary))
                BackupCheck.NeedsPassword -> setStage(RestoreStage.AskPassword(source, wasWrong = false))
                BackupCheck.WrongPasswordOrDamaged ->
                    setStage(RestoreStage.AskPassword(source, wasWrong = true))
                BackupCheck.NewerVersion -> rejectWith(R.string.backup_newer)
                BackupCheck.Invalid -> rejectWith(R.string.backup_invalid)
            }
        }
    }

    fun dismissRestore() = setStage(RestoreStage.Idle)

    fun confirmRestore() {
        val stage = mutableState.value.restoreStage as? RestoreStage.Confirm ?: return
        setStage(RestoreStage.Idle)
        work(R.string.backup_restore_failed) {
            repository.restore(stage.source, stage.password)
            messageChannel.send(R.string.backup_restored)
        }
    }

    private suspend fun rejectWith(@StringRes message: Int) {
        setStage(RestoreStage.Idle)
        messageChannel.send(message)
    }

    private fun setStage(stage: RestoreStage) {
        mutableState.update { it.copy(restoreStage = stage) }
    }

    private fun work(@StringRes failure: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true) }
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                messageChannel.send(failure)
            } finally {
                mutableState.update { it.copy(isWorking = false) }
            }
        }
    }
}
