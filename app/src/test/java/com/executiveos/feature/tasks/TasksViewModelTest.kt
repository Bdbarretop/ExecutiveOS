package com.executiveos.feature.tasks

import com.executiveos.R
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.domain.tasks.TaskView
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val repository = mockk<TaskRepository>(relaxUnitFun = true)
    private val settings = mockk<PrioritySettingsRepository> {
        every { this@mockk.settings } returns MutableStateFlow(PrioritySettings())
    }

    private fun item(id: String, status: TaskStatus = TaskStatus.PENDING, configure: Task.() -> Task = { this }) =
        TaskListItem(
            task = Task(
                id = id,
                status = status,
                sourceInboxItemId = null,
                title = id,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ).configure(),
            categoryName = null,
            projectName = null,
            parentTitle = null,
            openDependencies = 0,
            openSubtasks = 0,
        )

    private fun viewModelWith(vararg items: TaskListItem): TasksViewModel {
        every { repository.observeTaskList() } returns flowOf(items.toList())
        return TasksViewModel(repository, settings, clock)
    }

    private fun TestScope.collectState(viewModel: TasksViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
    }

    private fun TasksViewModel.ids() = uiState.value.tasks.map { it.item.task.id }

    @Test
    fun selectingAViewNarrowsTheList() = runTest {
        val viewModel = viewModelWith(
            item("hoy") { copy(targetDate = today) },
            item("sinfecha"),
        )
        collectState(viewModel)

        viewModel.onViewSelected(TaskView.TODAY)
        advanceUntilIdle()

        assertEquals(listOf("hoy"), viewModel.ids())
    }

    @Test
    fun searchAppliesAfterAShortPause() = runTest {
        val viewModel = viewModelWith(item("informe"), item("compras"))
        collectState(viewModel)

        viewModel.onQueryChange("INFORME")
        advanceUntilIdle()

        assertEquals(listOf("informe"), viewModel.ids())
        assertEquals("INFORME", viewModel.uiState.value.query)
    }

    @Test
    fun filtersCanBeAppliedAndCleared() = runTest {
        val viewModel = viewModelWith(
            item("alta") { copy(impact = 3, urgency = 3, delayRisk = 3) },
            item("sinevaluar"),
        )
        collectState(viewModel)

        viewModel.onFiltersChange { copy(levels = setOf(PriorityLevel.HIGH)) }
        advanceUntilIdle()
        assertEquals(listOf("alta"), viewModel.ids())

        viewModel.clearFilters()
        advanceUntilIdle()
        assertEquals(2, viewModel.ids().size)
    }

    @Test
    fun filterOptionsComeFromExistingTasks() = runTest {
        val viewModel = viewModelWith(
            item("a") { copy(owner = "Ana", tags = listOf("comité")) },
            item("b") { copy(owner = "Ana") },
        )

        collectState(viewModel)

        assertEquals(listOf("Ana"), viewModel.uiState.value.options.owners)
        assertEquals(listOf("comité"), viewModel.uiState.value.options.tags)
    }

    @Test
    fun creatingATaskOpensItsRecord() = runTest {
        coEvery { repository.create("Nueva", null) } returns "nueva-id"
        val viewModel = viewModelWith()

        viewModel.create("Nueva")
        advanceUntilIdle()

        assertEquals(TasksEvent.OpenTask("nueva-id"), viewModel.events.first())
    }

    @Test
    fun completingReportsSuccess() = runTest {
        val viewModel = viewModelWith(item("a"))

        viewModel.complete("a")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setStatus("a", TaskStatus.COMPLETED) }
        assertEquals(TasksEvent.Message(R.string.tasks_done_completed), viewModel.events.first())
    }

    @Test
    fun failedActionsAreReportedInsteadOfCrashing() = runTest {
        coEvery { repository.duplicate("a") } throws IOException("sin espacio")
        val viewModel = viewModelWith(item("a"))

        viewModel.duplicate("a")
        advanceUntilIdle()

        assertEquals(TasksEvent.Message(R.string.tasks_action_failed), viewModel.events.first())
    }

    @Test
    fun viewModelStartsLoadingThenShowsTasks() = runTest {
        val viewModel = viewModelWith(item("a"))
        assertTrue(viewModel.uiState.value.isLoading)

        collectState(viewModel)

        assertFalse(viewModel.uiState.value.isLoading)
    }
}
