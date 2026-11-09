package com.executiveos.domain.metrics

import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.LearningStats
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.model.effectiveDate
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.planning.PlanningPeriod
import com.executiveos.domain.planning.PlanningRules
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskView
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class MetricPeriod { WEEK, MONTH, QUARTER }

/** Siempre se muestra con sus dos términos: un porcentaje solo puede engañar. */
data class Ratio(val done: Int, val total: Int) {
    val percent: Int? get() = if (total > 0) done * 100 / total else null
}

data class CategoryMinutes(val category: String?, val minutes: Int)

data class ProjectProgressLine(val name: String, val percent: Int?, val isDelayed: Boolean)

data class Metrics(
    val span: DateSpan,
    val priorityCompletion: Ratio,
    val compliance: Ratio,
    val overdueTasks: Int,
    val blockedTasks: Int,
    val estimatedMinutes: Int,
    val actualMinutes: Int,
    val deepWorkMinutes: Int,
    val learningMinutes: Int,
    val projects: List<ProjectProgressLine>,
    val timeByCategory: List<CategoryMinutes>,
)

object MetricsCalculator {
    private const val MONTHS_PER_QUARTER = 3

    private val PRIORITY_LEVELS = setOf(PriorityLevel.CRITICAL, PriorityLevel.HIGH)

    fun span(period: MetricPeriod, anchor: LocalDate): DateSpan = when (period) {
        MetricPeriod.WEEK -> PlanningRules.span(PlanningPeriod.WEEK, anchor)
        MetricPeriod.MONTH -> PlanningRules.span(PlanningPeriod.MONTH, anchor)
        MetricPeriod.QUARTER -> {
            val firstMonth = (anchor.monthValue - 1) / MONTHS_PER_QUARTER * MONTHS_PER_QUARTER + 1
            val start = anchor.withMonth(firstMonth).withDayOfMonth(1)
            DateSpan(
                start,
                start.plusMonths((MONTHS_PER_QUARTER - 1).toLong())
                    .with(TemporalAdjusters.lastDayOfMonth()),
            )
        }
    }

    fun shift(period: MetricPeriod, anchor: LocalDate, steps: Long): LocalDate = when (period) {
        MetricPeriod.WEEK -> anchor.plusWeeks(steps)
        MetricPeriod.MONTH -> anchor.plusMonths(steps)
        MetricPeriod.QUARTER -> anchor.plusMonths(steps * MONTHS_PER_QUARTER)
    }

    /**
     * El filtro de categoría solo afecta a los indicadores de tareas. Las subtareas y las tareas de
     * prioridad baja no cuentan para el cumplimiento: trocear trabajo no debe mejorar el resultado.
     */
    fun calculate(
        tasks: List<RankedTask>,
        blocks: List<TimeBlock>,
        sessions: List<LearningSession>,
        projects: List<ProjectProgressLine>,
        span: DateSpan,
        categoryId: String?,
        today: LocalDate,
        zone: ZoneId,
    ): Metrics {
        val scoped = tasks.filter { categoryId == null || it.item.task.categoryId == categoryId }
        val completedInSpan = scoped.filter { it.completedOn(zone).isWithin(span) }
        val comparable = completedInSpan.filter {
            it.item.task.estimatedMinutes != null && it.item.task.actualMinutes != null
        }
        return Metrics(
            span = span,
            priorityCompletion = priorityCompletion(scoped, span, zone),
            compliance = compliance(scoped, span, today, zone),
            overdueTasks = scoped.count { TaskQueries.matchesView(it, TaskView.OVERDUE, today) },
            blockedTasks = scoped.count { TaskQueries.matchesView(it, TaskView.BLOCKED, today) },
            estimatedMinutes = comparable.sumOf { it.item.task.estimatedMinutes ?: 0 },
            actualMinutes = comparable.sumOf { it.item.task.actualMinutes ?: 0 },
            deepWorkMinutes = PlanningRules.execution(blocks, span, zone)
                .firstOrNull { it.type == BlockType.DEEP_WORK }
                ?.actualMinutes ?: 0,
            learningMinutes = LearningStats.minutesIn(sessions, span),
            projects = projects,
            timeByCategory = completedInSpan
                .filter { it.item.task.actualMinutes != null }
                .groupBy { it.item.categoryName }
                .map { (name, entries) ->
                    CategoryMinutes(name, entries.sumOf { it.item.task.actualMinutes ?: 0 })
                }
                .sortedByDescending { it.minutes },
        )
    }

    private fun priorityCompletion(tasks: List<RankedTask>, span: DateSpan, zone: ZoneId): Ratio {
        val relevant = tasks.filter { ranked ->
            ranked.priority.effectiveLevel in PRIORITY_LEVELS &&
                ranked.isOpenOrCompleted() &&
                (ranked.item.task.effectiveDate.isWithin(span) || ranked.completedOn(zone).isWithin(span))
        }
        return Ratio(relevant.count { it.item.task.status == TaskStatus.COMPLETED }, relevant.size)
    }

    /** Solo cuentan las fechas ya alcanzadas: lo que vence mañana aún no es un incumplimiento. */
    private fun compliance(
        tasks: List<RankedTask>,
        span: DateSpan,
        today: LocalDate,
        zone: ZoneId,
    ): Ratio {
        val due = tasks.filter { ranked ->
            val date = ranked.item.task.effectiveDate
            ranked.isSubstantial() && ranked.isOpenOrCompleted() && date.isWithin(span) && !date!!.isAfter(today)
        }
        val onTime = due.count { ranked ->
            val completed = ranked.completedOn(zone)
            completed != null && !completed.isAfter(ranked.item.task.effectiveDate)
        }
        return Ratio(onTime, due.size)
    }

    private fun RankedTask.isSubstantial(): Boolean =
        item.task.parentTaskId == null && priority.effectiveLevel != PriorityLevel.LOW

    private fun RankedTask.isOpenOrCompleted(): Boolean =
        TaskQueries.isOpen(item.task.status) || item.task.status == TaskStatus.COMPLETED

    private fun RankedTask.completedOn(zone: ZoneId): LocalDate? =
        item.task.completedAt?.takeIf { item.task.status == TaskStatus.COMPLETED }
            ?.atZone(zone)?.toLocalDate()

    private fun LocalDate?.isWithin(span: DateSpan): Boolean =
        this != null && !isBefore(span.from) && !isAfter(span.to)
}
