package com.executiveos.feature.settings

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrioritySettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val settingsRepository = mockk<PrioritySettingsRepository>(relaxUnitFun = true) {
        every { settings } returns flowOf(PrioritySettings())
    }
    private val tasks = mockk<TaskRepository>()

    private fun task(score: Int, manual: PriorityLevel? = null) = TaskListItem(
        task = Task(
            id = "t$score",
            status = TaskStatus.PENDING,
            sourceInboxItemId = null,
            title = "Tarea",
            impact = score,
            urgency = score,
            delayRisk = score,
            manualPriority = manual,
            manualPriorityReason = manual?.let { "Motivo" },
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        categoryName = null,
        projectName = null,
        parentTitle = null,
        openDependencies = 0,
        openSubtasks = 0,
    )

    private fun viewModelWith(vararg items: TaskListItem): PrioritySettingsViewModel {
        every { tasks.observeTaskList() } returns flowOf(items.toList())
        return PrioritySettingsViewModel(settingsRepository, tasks, clock)
    }

    @Test
    fun formAcceptsCommaOrPointAsDecimalSeparator() {
        val base = PrioritySettings().toForm()

        assertEquals(350, base.copy(high = "3,50").toSettings()?.highThreshold)
        assertEquals(350, base.copy(high = "3.5").toSettings()?.highThreshold)
    }

    @Test
    fun formIsInvalidWhenWeightsDoNotAddUpOrTextIsNotANumber() {
        val base = PrioritySettings().toForm()

        assertNull(base.copy(impact = "60").toSettings())
        assertNull(base.copy(critical = "alto").toSettings())
    }

    @Test
    fun savingWithoutAffectedTasksPersistsDirectly() = runTest {
        val viewModel = viewModelWith()
        advanceUntilIdle()

        viewModel.requestSave()
        advanceUntilIdle()

        assertNull(viewModel.pendingConfirmation.value)
        coVerify(exactly = 1) { settingsRepository.save(PrioritySettings()) }
    }

    @Test
    fun changesThatMoveTasksRequireConfirmation() = runTest {
        val viewModel = viewModelWith(task(3))
        advanceUntilIdle()
        viewModel.onFormChange { copy(high = "3,50") }

        viewModel.requestSave()
        advanceUntilIdle()

        assertEquals(1, viewModel.pendingConfirmation.value)
        coVerify(exactly = 0) { settingsRepository.save(any()) }

        viewModel.confirmSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.save(any()) }
        assertNull(viewModel.pendingConfirmation.value)
    }

    @Test
    fun manualPrioritiesAreNotCountedAsAffected() = runTest {
        val viewModel = viewModelWith(task(3, manual = PriorityLevel.CRITICAL))
        advanceUntilIdle()
        viewModel.onFormChange { copy(high = "3,50") }

        viewModel.requestSave()
        advanceUntilIdle()

        assertNull(viewModel.pendingConfirmation.value)
        coVerify(exactly = 1) { settingsRepository.save(any()) }
    }

    @Test
    fun resetRestoresTheBriefValues() = runTest {
        val viewModel = viewModelWith()
        advanceUntilIdle()
        viewModel.onFormChange { copy(impact = "10") }

        viewModel.resetToDefaults()

        assertEquals(PrioritySettings().toForm(), viewModel.form.value)
    }
}
