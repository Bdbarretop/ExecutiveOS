package com.executiveos.feature.projects

import androidx.lifecycle.SavedStateHandle
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectStatus
import com.executiveos.domain.repository.ProjectDetails
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val repository = mockk<ProjectRepository>(relaxUnitFun = true)
    private val tasks = mockk<TaskRepository>(relaxUnitFun = true) {
        every { observeTaskList() } returns flowOf(emptyList())
    }

    private fun details(
        id: String,
        status: ProjectStatus = ProjectStatus.ACTIVE,
        target: LocalDate? = null,
    ) = ProjectDetails(
        project = Project(
            id = id,
            name = id,
            status = status,
            targetDate = target,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        milestones = emptyList(),
        risks = emptyList(),
        openTasks = 0,
    )

    private fun editor(): ProjectEditorViewModel {
        every { repository.observeProject("p") } returns MutableStateFlow(details("p"))
        return ProjectEditorViewModel(
            SavedStateHandle(mapOf("projectId" to "p")),
            repository,
            tasks,
            clock,
        )
    }

    @Test
    fun delayedProjectsComeFirstAndFinishedOnesLast() = runTest {
        every { repository.observeProjects() } returns flowOf(
            listOf(
                details("terminado", ProjectStatus.COMPLETED),
                details("en-plazo", target = today.plusDays(5)),
                details("retrasado", target = today.minusDays(1)),
            ),
        )
        val viewModel = ProjectsViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.projects.collect {}
        }
        advanceUntilIdle()

        assertEquals(
            listOf("retrasado", "en-plazo", "terminado"),
            viewModel.projects.value!!.map { it.project.id },
        )
    }

    @Test
    fun creatingAProjectOpensIt() = runTest {
        every { repository.observeProjects() } returns flowOf(emptyList())
        coEvery { repository.create("ERP") } returns "nuevo"
        val viewModel = ProjectsViewModel(repository, clock)

        viewModel.create("ERP")

        assertEquals(ProjectsEvent.OpenProject("nuevo"), viewModel.events.first())
    }

    @Test
    fun editsAreSavedAutomatically() = runTest {
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.onEdit { copy(objective = "Unificar datos") }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.save(match { it.objective == "Unificar datos" }) }
    }

    @Test
    fun anInvalidProjectIsNotSaved() = runTest {
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.onEdit { copy(name = "  ") }
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun closingWithoutChangesDoesNotRewriteTheProject() = runTest {
        val viewModel = editor()
        advanceUntilIdle()
        var closed = false

        viewModel.flush { closed = true }
        advanceUntilIdle()

        assertTrue(closed)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun aTaskAddedFromTheProjectIsLinkedToIt() = runTest {
        coEvery { tasks.create(any(), any(), any()) } returns "t"
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.addTask("Informe")
        advanceUntilIdle()

        coVerify { tasks.create("Informe", null, "p") }
    }
}
