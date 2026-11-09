package com.executiveos.feature.reviews

import androidx.lifecycle.SavedStateHandle
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.repository.LearningRepository
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ProjectRepository
import com.executiveos.domain.repository.ReviewRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.reviews.Review
import com.executiveos.domain.reviews.ReviewQuestion
import com.executiveos.domain.reviews.ReviewType
import com.executiveos.domain.reviews.WeeklyRecommendation
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val reviews = mockk<ReviewRepository>(relaxUnitFun = true)
    private val tasks = mockk<TaskRepository> {
        every { observeTaskList() } returns flowOf(emptyList())
    }
    private val priority = mockk<PrioritySettingsRepository> {
        every { settings } returns flowOf(PrioritySettings())
    }
    private val projects = mockk<ProjectRepository> {
        every { observeProjects() } returns flowOf(emptyList())
    }
    private val planning = mockk<PlanningRepository> {
        every { observeBlocks() } returns flowOf(emptyList())
        every { capacity } returns flowOf(CapacitySettings())
    }
    private val learning = mockk<LearningRepository> {
        every { observeSessions() } returns flowOf(emptyList())
        every { observeGoals() } returns flowOf(emptyList())
    }

    private fun review(
        type: ReviewType,
        date: LocalDate,
        answers: Map<ReviewQuestion, String>,
    ) = Review("r-$type-$date", type, date, answers, Instant.EPOCH)

    private fun editor(type: ReviewType, existing: Review? = null): ReviewEditorViewModel {
        every { reviews.observeReview(type, today) } returns flowOf(existing)
        return ReviewEditorViewModel(
            SavedStateHandle(mapOf("type" to type.name)),
            reviews,
            tasks,
            priority,
            projects,
            planning,
            learning,
            clock,
        )
    }

    @Test
    fun theHubOnlyMarksAnsweredReviewsOfTheCurrentPeriod() = runTest {
        every { reviews.observeHistory() } returns flowOf(
            listOf(
                review(ReviewType.DAILY, today, mapOf(ReviewQuestion.TOP_PRIORITIES to "Comité")),
                review(ReviewType.CLOSING, today, emptyMap()),
                review(ReviewType.WEEKLY, today.minusWeeks(2), mapOf(ReviewQuestion.WEEKLY_NOTES to "x")),
            ),
        )
        val viewModel = ReviewsViewModel(reviews, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value!!

        assertEquals(setOf(ReviewType.CLOSING, ReviewType.WEEKLY), state.pendingTypes)
        assertEquals(2, state.history.size)
    }

    @Test
    fun existingAnswersAreLoaded() = runTest {
        val answers = mapOf(ReviewQuestion.CAN_DELEGATE to "Informe")
        val viewModel = editor(ReviewType.DAILY, review(ReviewType.DAILY, today, answers))
        advanceUntilIdle()

        assertEquals(answers, viewModel.answers.value)
    }

    @Test
    fun answersAreSavedAutomatically() = runTest {
        val viewModel = editor(ReviewType.CLOSING)
        advanceUntilIdle()

        viewModel.onAnswerChange(ReviewQuestion.FINISHED, "Informe enviado")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            reviews.saveAnswers(
                ReviewType.CLOSING,
                today,
                mapOf(ReviewQuestion.FINISHED to "Informe enviado"),
            )
        }
    }

    @Test
    fun anUntouchedReviewIsNotCreated() = runTest {
        val viewModel = editor(ReviewType.DAILY)
        advanceUntilIdle()
        var closed = false

        viewModel.flush { closed = true }
        advanceUntilIdle()

        assertTrue(closed)
        coVerify(exactly = 0) { reviews.saveAnswers(any(), any(), any()) }
    }

    @Test
    fun erasingAnAnswerRemovesIt() = runTest {
        val viewModel = editor(
            ReviewType.DAILY,
            review(ReviewType.DAILY, today, mapOf(ReviewQuestion.PROTECT_TIME to "Mañana")),
        )
        advanceUntilIdle()

        viewModel.onAnswerChange(ReviewQuestion.PROTECT_TIME, "")
        advanceUntilIdle()

        coVerify { reviews.saveAnswers(ReviewType.DAILY, today, emptyMap()) }
    }

    @Test
    fun theWeeklyReviewCarriesASummaryAndADailyOneDoesNot() = runTest {
        val weekly = editor(ReviewType.WEEKLY)
        val daily = editor(ReviewType.DAILY)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { weekly.summary.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { daily.summary.collect {} }
        advanceUntilIdle()

        assertTrue(WeeklyRecommendation.PROTECT_DEEP_WORK in weekly.summary.value!!.recommendations)
        assertNull(daily.summary.value)
    }

    @Test
    fun aNotificationReviewOpensTheCurrentMonday() = runTest {
        val viewModel = editor(ReviewType.WEEKLY)

        assertEquals(LocalDate.of(2026, 11, 9), viewModel.periodDate)
        assertEquals(listOf(ReviewQuestion.WEEKLY_NOTES), viewModel.questions)
    }
}
