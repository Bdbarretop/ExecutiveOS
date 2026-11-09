package com.executiveos.domain.reviews

import com.executiveos.domain.learning.LearningCatalog
import com.executiveos.domain.learning.LearningGoal
import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.LearningStats
import com.executiveos.domain.learning.LearningValidationError
import com.executiveos.domain.learning.TopicMinutes
import com.executiveos.domain.learning.WeekMinutes
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.domain.metrics.Metrics
import com.executiveos.domain.metrics.MetricsCalculator
import com.executiveos.domain.metrics.Ratio
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.domain.tasks.TaskQueries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAndMetricsTest {
    /** Miércoles: la semana va del lunes 9 al domingo 15. */
    private val today = LocalDate.of(2026, 11, 11)
    private val week = DateSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))

    private fun task(id: String) = Task(
        id = id,
        status = TaskStatus.PENDING,
        sourceInboxItemId = null,
        title = id,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun Task.high() = copy(impact = 4, urgency = 4, delayRisk = 4)

    private fun Task.low() = copy(impact = 1, urgency = 1, delayRisk = 1)

    private fun Task.doneOn(day: LocalDate) = copy(
        status = TaskStatus.COMPLETED,
        completedAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
    )

    private fun ranked(vararg tasks: Task, category: String? = null) = TaskQueries.rank(
        tasks.map { TaskListItem(it, category, null, null, 0, 0) },
        PrioritySettings(),
        today,
    )

    private fun metrics(vararg tasks: Task, categoryId: String? = null): Metrics =
        MetricsCalculator.calculate(
            ranked(*tasks),
            emptyList(),
            emptyList(),
            emptyList(),
            week,
            categoryId,
            today,
            ZoneOffset.UTC,
        )

    private fun goal(id: String, topic: String) =
        LearningGoal(id, id, topic, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun session(goalId: String, date: LocalDate, minutes: Int) =
        LearningSession("$goalId-$date", goalId, date, minutes)

    private fun deepBlock(actual: Int?) = TimeBlock(
        id = "b",
        type = BlockType.DEEP_WORK,
        title = "b",
        startAt = today.atTime(9, 0).toInstant(ZoneOffset.UTC),
        endAt = today.atTime(11, 0).toInstant(ZoneOffset.UTC),
        actualMinutes = actual,
    )

    private fun summary(
        tasks: List<Task> = emptyList(),
        blocks: List<TimeBlock> = emptyList(),
        sessions: List<LearningSession> = emptyList(),
        hasActiveGoals: Boolean = false,
    ) = ReviewRules.summarize(
        ranked(*tasks.toTypedArray()),
        emptyList(),
        blocks,
        CapacitySettings(),
        sessions,
        hasActiveGoals,
        week,
        today,
        ZoneOffset.UTC,
    )

    @Test
    fun eachReviewHasTheQuestionsOfTheBrief() {
        assertEquals(7, ReviewRules.questions(ReviewType.DAILY).size)
        assertEquals(5, ReviewRules.questions(ReviewType.CLOSING).size)
    }

    @Test
    fun aWeeklyReviewIsIdentifiedByItsMonday() {
        assertEquals(week.from, ReviewRules.periodDate(ReviewType.WEEKLY, today))
        assertEquals(today, ReviewRules.periodDate(ReviewType.DAILY, today))
    }

    @Test
    fun theWeeklySummaryCountsRealData() {
        val result = summary(
            tasks = listOf(
                task("hecha").doneOn(today),
                task("anterior").doneOn(week.from.minusDays(1)),
                task("vencida").copy(deadlineDate = today.minusDays(2)),
                task("proxima").copy(deadlineDate = week.to.plusDays(3)),
            ),
            blocks = listOf(deepBlock(actual = 90)),
            sessions = listOf(session("g", today, 45), session("g", week.from.minusDays(1), 500)),
        )

        assertEquals(1, result.completedTasks)
        assertEquals(2, result.pendingTasks)
        assertEquals(1, result.overdueTasks)
        assertEquals(1, result.nextWeekCommitments)
        assertEquals(90, result.deepWorkMinutes)
        assertEquals(45, result.learningMinutes)
    }

    @Test
    fun recommendationsOnlyAppearWithDataThatSupportsThem() {
        val calm = summary(blocks = listOf(deepBlock(actual = 60)))
        assertTrue(calm.recommendations.isEmpty())

        val busy = summary(
            tasks = listOf(task("vencida").copy(deadlineDate = today.minusDays(1))),
            hasActiveGoals = true,
        )
        assertEquals(
            listOf(
                WeeklyRecommendation.CLEAR_OVERDUE,
                WeeklyRecommendation.PROTECT_DEEP_WORK,
                WeeklyRecommendation.SCHEDULE_LEARNING,
            ),
            busy.recommendations,
        )
    }

    @Test
    fun theLearningCatalogHasTheTwelveInitialTopics() {
        assertEquals(12, LearningCatalog.INITIAL_TOPICS.toSet().size)
    }

    @Test
    fun learningTimeIsGroupedByTopicAndWeek() {
        val goals = listOf(goal("a", "Liderazgo"), goal("b", "Regulación"))
        val sessions = listOf(
            session("a", today, 30),
            session("b", today, 90),
            session("a", week.from.plusDays(1), 15),
            session("a", week.from.plusDays(7), 200),
            session("borrado", today, 999),
        )
        val fortnight = DateSpan(week.from, week.to.plusDays(7))

        assertEquals(
            listOf(TopicMinutes("Regulación", 90), TopicMinutes("Liderazgo", 45)),
            LearningStats.byTopic(goals, sessions, week),
        )
        assertEquals(
            listOf(WeekMinutes(week.from, 1_134), WeekMinutes(week.from.plusDays(7), 200)),
            LearningStats.byWeek(sessions, fortnight),
        )
    }

    @Test
    fun learningDataIsValidated() {
        val invalid = goal(" ", " ").copy(masteryLevel = 6)

        assertEquals(
            listOf(
                LearningValidationError.BLANK_TITLE,
                LearningValidationError.BLANK_TOPIC,
                LearningValidationError.MASTERY_OUT_OF_RANGE,
            ),
            LearningStats.validate(invalid),
        )
        assertEquals(
            listOf(LearningValidationError.MINUTES_NOT_POSITIVE),
            LearningStats.validate(session("a", today, 0)),
        )
    }

    @Test
    fun aQuarterStartsOnTheFirstDayOfItsThreeMonths() {
        assertEquals(
            DateSpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)),
            MetricsCalculator.span(MetricPeriod.QUARTER, today),
        )
        assertEquals(
            DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
            MetricsCalculator.span(MetricPeriod.QUARTER, LocalDate.of(2026, 2, 28)),
        )
    }

    @Test
    fun withoutDataARatioHasNoPercentInsteadOfZero() {
        assertNull(Ratio(0, 0).percent)
        assertEquals(50, Ratio(1, 2).percent)
    }

    @Test
    fun priorityCompletionOnlyCountsHighAndCriticalTasks() {
        val result = metrics(
            task("alta").high().doneOn(today),
            task("alta-abierta").high().copy(targetDate = today),
            task("baja").low().doneOn(today),
        )

        assertEquals(Ratio(1, 2), result.priorityCompletion)
    }

    @Test
    fun manySmallTasksDoNotInflateCompliance() {
        val small = (1..10).map { task("mini$it").low().copy(targetDate = today).doneOn(today) }
        val subtasks = (1..5).map {
            task("sub$it").high().copy(targetDate = today, parentTaskId = "padre").doneOn(today)
        }
        val missed = task("importante").high().copy(deadlineDate = today.minusDays(1))

        val result = metrics(*(small + subtasks + missed).toTypedArray())

        assertEquals(Ratio(0, 1), result.compliance)
    }

    @Test
    fun complianceRequiresFinishingOnTimeAndIgnoresFutureDates() {
        val result = metrics(
            task("a-tiempo").high().copy(targetDate = today).doneOn(today),
            task("tarde").high().copy(targetDate = week.from).doneOn(today),
            task("futura").high().copy(targetDate = today.plusDays(2)),
        )

        assertEquals(Ratio(1, 2), result.compliance)
    }

    @Test
    fun estimatedAndActualCompareOnlyTasksWithBothValues() {
        val result = metrics(
            task("ambos").copy(estimatedMinutes = 60, actualMinutes = 90).doneOn(today),
            task("sin-real").copy(estimatedMinutes = 500).doneOn(today),
        )

        assertEquals(60, result.estimatedMinutes)
        assertEquals(90, result.actualMinutes)
    }

    @Test
    fun theCategoryFilterOnlyKeepsTasksOfThatCategory() {
        val result = metrics(
            task("trabajo").copy(categoryId = "c1", deadlineDate = today.minusDays(1)),
            task("otra").copy(categoryId = "c2", deadlineDate = today.minusDays(1)),
            categoryId = "c1",
        )

        assertEquals(1, result.overdueTasks)
        assertFalse(result.priorityCompletion.total > 1)
    }
}
