package com.executiveos.feature.learning

import androidx.lifecycle.SavedStateHandle
import com.executiveos.domain.learning.LearningGoal
import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.TopicMinutes
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectStatus
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.LearningRepository
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ProjectDetails
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.feature.metrics.MetricsViewModel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningAndMetricsViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val learning = mockk<LearningRepository>(relaxUnitFun = true)

    private fun goal(id: String, topic: String = "Regulación") =
        LearningGoal(id, id, topic, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun session(id: String, goalId: String, minutes: Int, date: LocalDate = today) =
        LearningSession(id, goalId, date, minutes)

    private fun givenLearning(goals: List<LearningGoal>, sessions: List<LearningSession>) {
        every { learning.observeGoals() } returns flowOf(goals)
        every { learning.observeSessions() } returns flowOf(sessions)
    }

    private fun goalEditor() = LearningGoalViewModel(
        SavedStateHandle(mapOf("goalId" to "g")),
        learning,
        clock,
    )

    private fun TestScope.metrics(
        tasks: List<Task> = emptyList(),
        projects: List<ProjectDetails> = emptyList(),
    ): MetricsViewModel {
        givenLearning(emptyList(), emptyList())
        val viewModel = MetricsViewModel(
            mockk<TaskRepository> {
                every { observeTaskList() } returns flowOf(
                    tasks.map { TaskListItem(it, null, null, null, 0, 0) },
                )
            },
            mockk<PrioritySettingsRepository> {
                every { settings } returns flowOf(PrioritySettings())
            },
            mockk<PlanningRepository> { every { observeBlocks() } returns flowOf(emptyList()) },
            learning,
            mockk<ProjectRepository> { every { observeProjects() } returns flowOf(projects) },
            mockk<CategoryRepository> { every { observeCategories() } returns flowOf(emptyList()) },
            clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        return viewModel
    }

    private fun task(id: String, categoryId: String) = Task(
        id = id,
        status = TaskStatus.PENDING,
        sourceInboxItemId = null,
        title = id,
        categoryId = categoryId,
        deadlineDate = today.minusDays(1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun project(id: String, status: ProjectStatus) = ProjectDetails(
        Project(id = id, name = id, status = status, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        emptyList(),
        emptyList(),
        0,
    )

    @Test
    fun theHubGroupsTimeOfThePeriodAndTotalsEachGoal() = runTest {
        givenLearning(
            listOf(goal("a", "Liderazgo"), goal("b", "Regulación")),
            listOf(session("1", "a", 30), session("2", "b", 60), session("3", "a", 500, today.minusMonths(2))),
        )
        val viewModel = LearningViewModel(learning, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value!!

        assertEquals(90, state.totalMinutes)
        assertEquals(listOf(TopicMinutes("Regulación", 60), TopicMinutes("Liderazgo", 30)), state.byTopic)
        assertEquals(530, state.goals.first { it.goal.id == "a" }.totalMinutes)
    }

    @Test
    fun creatingAGoalOpensIt() = runTest {
        givenLearning(emptyList(), emptyList())
        coEvery { learning.createGoal("Basilea", "Regulación") } returns "nuevo"
        val viewModel = LearningViewModel(learning, clock)

        viewModel.createGoal("Basilea", "Regulación")

        assertEquals(LearningEvent.OpenGoal("nuevo"), viewModel.events.first())
    }

    @Test
    fun editsToAGoalAreSavedAutomatically() = runTest {
        givenLearning(listOf(goal("g")), emptyList())
        val viewModel = goalEditor()
        advanceUntilIdle()

        viewModel.onEdit { copy(course = "CFA") }
        advanceUntilIdle()

        coVerify(exactly = 1) { learning.saveGoal(match { it.course == "CFA" }) }
    }

    @Test
    fun anInvalidGoalIsNotSaved() = runTest {
        givenLearning(listOf(goal("g")), emptyList())
        val viewModel = goalEditor()
        advanceUntilIdle()

        viewModel.onEdit { copy(topic = " ") }
        advanceUntilIdle()

        coVerify(exactly = 0) { learning.saveGoal(any()) }
    }

    @Test
    fun theGoalOnlyShowsItsOwnSessions() = runTest {
        givenLearning(listOf(goal("g")), listOf(session("1", "g", 30), session("2", "otro", 90)))
        val viewModel = goalEditor()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals(30, viewModel.uiState.value.totalMinutes)
    }

    @Test
    fun aSessionIsAddedToTheGoal() = runTest {
        givenLearning(listOf(goal("g")), emptyList())
        val viewModel = goalEditor()
        advanceUntilIdle()

        viewModel.addSession(today, 30, null, "Examen")
        advanceUntilIdle()

        coVerify { learning.addSession("g", today, 30, null, "Examen") }
    }

    @Test
    fun metricsStartOnThisWeekAndMoveByThePeriod() = runTest {
        val viewModel = metrics()
        assertEquals(
            DateSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)),
            viewModel.uiState.value!!.metrics.span,
        )

        viewModel.selectPeriod(MetricPeriod.QUARTER)
        viewModel.next()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2027, 1, 1), viewModel.uiState.value!!.metrics.span.from)
    }

    @Test
    fun theCategoryFilterNarrowsTaskIndicators() = runTest {
        val viewModel = metrics(tasks = listOf(task("a", "c1"), task("b", "c2")))
        assertEquals(2, viewModel.uiState.value!!.metrics.overdueTasks)

        viewModel.selectCategory("c1")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value!!.metrics.overdueTasks)
    }

    @Test
    fun finishedProjectsDoNotAppearInProgress() = runTest {
        val viewModel = metrics(
            projects = listOf(
                project("activo", ProjectStatus.ACTIVE),
                project("cerrado", ProjectStatus.COMPLETED),
            ),
        )

        assertEquals(listOf("activo"), viewModel.uiState.value!!.metrics.projects.map { it.name })
    }
}
