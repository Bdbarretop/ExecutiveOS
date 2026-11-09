package com.executiveos.domain.reviews

import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.LearningStats
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.model.effectiveDate
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.planning.PlanningRules
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectProgress
import com.executiveos.domain.projects.ProjectTracking
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskView
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ReviewType { DAILY, CLOSING, WEEKLY }

enum class ReviewQuestion(val type: ReviewType) {
    TASKS_TODAY(ReviewType.DAILY),
    TOP_PRIORITIES(ReviewType.DAILY),
    DUE_COMMITMENTS(ReviewType.DAILY),
    BLOCKED_ITEMS(ReviewType.DAILY),
    CAN_DELEGATE(ReviewType.DAILY),
    CONSCIOUSLY_POSTPONE(ReviewType.DAILY),
    PROTECT_TIME(ReviewType.DAILY),
    FINISHED(ReviewType.CLOSING),
    STILL_PENDING(ReviewType.CLOSING),
    WHY_PENDING(ReviewType.CLOSING),
    WHAT_CHANGED(ReviewType.CLOSING),
    PREPARE_TOMORROW(ReviewType.CLOSING),
    WEEKLY_NOTES(ReviewType.WEEKLY),
}

/** Hay una por tipo y periodo: volver a ella la edita, no la duplica. */
data class Review(
    val id: String,
    val type: ReviewType,
    val periodDate: LocalDate,
    val answers: Map<ReviewQuestion, String> = emptyMap(),
    val updatedAt: Instant,
)

/** Sugerencias heurísticas de la app a partir de los datos; no son hechos ni diagnósticos. */
enum class WeeklyRecommendation {
    CLEAR_OVERDUE,
    REVIEW_DELAYED_PROJECTS,
    REDUCE_OVERLOAD,
    PROTECT_DEEP_WORK,
    SCHEDULE_LEARNING,
    PREPARE_COMMITMENTS,
}

data class WeeklySummary(
    val completedTasks: Int,
    val pendingTasks: Int,
    val overdueTasks: Int,
    val activeProjects: Int,
    val delayedProjects: Int,
    val learningMinutes: Int,
    val deepWorkMinutes: Int,
    val overloadedDays: Int,
    val nextWeekCommitments: Int,
    val recommendations: List<WeeklyRecommendation>,
)

object ReviewRules {
    private const val DAYS_PER_WEEK = 7L

    fun questions(type: ReviewType): List<ReviewQuestion> =
        ReviewQuestion.entries.filter { it.type == type }

    /** La revisión semanal se identifica por el lunes de su semana. */
    fun periodDate(type: ReviewType, date: LocalDate): LocalDate =
        if (type == ReviewType.WEEKLY) date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else date

    fun summarize(
        tasks: List<RankedTask>,
        projects: List<Pair<Project, ProjectProgress>>,
        blocks: List<TimeBlock>,
        capacity: CapacitySettings,
        sessions: List<LearningSession>,
        hasActiveGoals: Boolean,
        week: DateSpan,
        today: LocalDate,
        zone: ZoneId,
    ): WeeklySummary {
        val open = tasks.filter { TaskQueries.isOpen(it.item.task.status) }
        val next = DateSpan(week.to.plusDays(1), week.to.plusDays(DAYS_PER_WEEK))
        val active = projects.filterNot { (project, _) -> ProjectTracking.isFinished(project.status) }
        val deepWork = PlanningRules.execution(blocks, week, zone)
            .firstOrNull { it.type == BlockType.DEEP_WORK }
            ?.actualMinutes ?: 0
        val learning = LearningStats.minutesIn(sessions, week)
        val overdue = tasks.count { TaskQueries.matchesView(it, TaskView.OVERDUE, today) }
        val delayed = active.count { (_, progress) -> progress.isDelayed }
        val overloaded = PlanningRules.overloadedDays(blocks, week, capacity, zone)
        val commitments = open.count { ranked ->
            val task = ranked.item.task
            (task.deadlineDate != null || ranked.priority.obligation != null) &&
                task.effectiveDate.isWithin(next)
        }
        return WeeklySummary(
            completedTasks = tasks.count { ranked ->
                val task = ranked.item.task
                task.status == TaskStatus.COMPLETED &&
                    task.completedAt?.atZone(zone)?.toLocalDate().isWithin(week)
            },
            pendingTasks = open.size,
            overdueTasks = overdue,
            activeProjects = active.size,
            delayedProjects = delayed,
            learningMinutes = learning,
            deepWorkMinutes = deepWork,
            overloadedDays = overloaded,
            nextWeekCommitments = commitments,
            recommendations = buildList {
                if (overdue > 0) add(WeeklyRecommendation.CLEAR_OVERDUE)
                if (delayed > 0) add(WeeklyRecommendation.REVIEW_DELAYED_PROJECTS)
                if (overloaded > 0) add(WeeklyRecommendation.REDUCE_OVERLOAD)
                if (deepWork == 0) add(WeeklyRecommendation.PROTECT_DEEP_WORK)
                if (hasActiveGoals && learning == 0) add(WeeklyRecommendation.SCHEDULE_LEARNING)
                if (commitments > 0) add(WeeklyRecommendation.PREPARE_COMMITMENTS)
            },
        )
    }

    private fun LocalDate?.isWithin(span: DateSpan): Boolean =
        this != null && !isBefore(span.from) && !isAfter(span.to)
}
