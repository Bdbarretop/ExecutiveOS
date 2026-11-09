package com.executiveos.feature.settings

import com.executiveos.R
import com.executiveos.domain.backup.BackupCheck
import com.executiveos.domain.backup.BackupRepository
import com.executiveos.domain.backup.BackupSummary
import com.executiveos.domain.backup.ExportFormat
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val repository = mockk<BackupRepository>(relaxUnitFun = true)
    private val viewModel = BackupViewModel(repository, clock)
    private val summary = BackupSummary(LocalDate.of(2026, 11, 1), 3, 1, 2, 4, 1)

    private fun stage() = viewModel.uiState.value.restoreStage

    @Test
    fun eachFormatSuggestsADatedFileName() {
        assertEquals(
            "executive-os-2026-11-11.eosbackup",
            viewModel.prepareExport(ExportFormat.PROTECTED_JSON, "clave larga"),
        )
        assertEquals("executive-os-2026-11-11.json", viewModel.prepareExport(ExportFormat.PLAIN_JSON, null))
        assertEquals("executive-os-2026-11-11.zip", viewModel.prepareExport(ExportFormat.CSV_ZIP, null))
    }

    @Test
    fun exportingUsesThePreparedFormatAndPassword() = runTest {
        viewModel.prepareExport(ExportFormat.PROTECTED_JSON, "clave larga")

        viewModel.onExportDestination("content://copia")

        assertEquals(R.string.backup_export_done, viewModel.messages.first())
        coVerify { repository.export(ExportFormat.PROTECTED_JSON, "content://copia", "clave larga") }
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun cancellingTheFilePickerExportsNothing() = runTest {
        viewModel.prepareExport(ExportFormat.PLAIN_JSON, null)

        viewModel.onExportDestination(null)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.export(any(), any(), any()) }
    }

    @Test
    fun aFullDiskIsReportedWithoutCrashing() = runTest {
        coEvery { repository.export(any(), any(), any()) } throws IOException("sin espacio")
        viewModel.prepareExport(ExportFormat.PLAIN_JSON, null)

        viewModel.onExportDestination("content://copia")

        assertEquals(R.string.backup_export_failed, viewModel.messages.first())
    }

    @Test
    fun aProtectedCopyAsksForThePassword() = runTest {
        coEvery { repository.check("uri", null) } returns BackupCheck.NeedsPassword

        viewModel.inspect("uri")
        advanceUntilIdle()

        assertEquals(RestoreStage.AskPassword("uri", wasWrong = false), stage())
    }

    @Test
    fun aWrongPasswordAsksAgain() = runTest {
        coEvery { repository.check("uri", "mala") } returns BackupCheck.WrongPasswordOrDamaged

        viewModel.inspect("uri", "mala")
        advanceUntilIdle()

        assertEquals(RestoreStage.AskPassword("uri", wasWrong = true), stage())
    }

    @Test
    fun aValidCopyNeedsConfirmationBeforeRestoring() = runTest {
        coEvery { repository.check("uri", "buena") } returns BackupCheck.Valid(summary)

        viewModel.inspect("uri", "buena")
        advanceUntilIdle()

        assertEquals(RestoreStage.Confirm("uri", "buena", summary), stage())
        coVerify(exactly = 0) { repository.restore(any(), any()) }
    }

    @Test
    fun confirmingRestoresAndCancellingDoesNot() = runTest {
        coEvery { repository.check("uri", null) } returns BackupCheck.Valid(summary)
        viewModel.inspect("uri")
        advanceUntilIdle()

        viewModel.dismissRestore()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.restore(any(), any()) }

        viewModel.inspect("uri")
        advanceUntilIdle()
        viewModel.confirmRestore()

        assertEquals(R.string.backup_restored, viewModel.messages.first())
        coVerify(exactly = 1) { repository.restore("uri", null) }
        assertEquals(RestoreStage.Idle, stage())
    }

    @Test
    fun anInvalidOrNewerCopyExplainsWhy() = runTest {
        coEvery { repository.check("mala", null) } returns BackupCheck.Invalid
        coEvery { repository.check("nueva", null) } returns BackupCheck.NewerVersion

        viewModel.inspect("mala")
        assertEquals(R.string.backup_invalid, viewModel.messages.first())

        viewModel.inspect("nueva")
        assertEquals(R.string.backup_newer, viewModel.messages.first())
        assertEquals(RestoreStage.Idle, stage())
    }

    @Test
    fun aFailedRestoreSaysTheDataIsUnchanged() = runTest {
        coEvery { repository.check("uri", null) } returns BackupCheck.Valid(summary)
        coEvery { repository.restore("uri", null) } throws IOException("fallo")
        viewModel.inspect("uri")
        advanceUntilIdle()

        viewModel.confirmRestore()

        assertEquals(R.string.backup_restore_failed, viewModel.messages.first())
    }
}
