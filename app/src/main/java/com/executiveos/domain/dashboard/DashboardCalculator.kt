package com.executiveos.domain.dashboard

import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.model.effectiveDate
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.CapacitySummary
import com.executiveos.domain.planning.DateSpan
import com.executiveos.domain.planning.PlanningRules
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.priority.DateSignal
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskView
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class DashboardIndicators(
    val pending: Int,
    val overdue: Int,
    val critical: Int,
    val high: Int,
    val inProgress: Int,
    val blocked: Int,
    val completedThisWeek: Int,
    val estimatedMinutesThisWeek: Int,
)

enum class AlertType {
    OVERDUE_COMMITMENTS,
    UPCOMING_COMMITMENTS,
    BLOCKED_TASKS,
    PENDING_DEPENDENCIES,
    CRITICAL_WITHOUT_NEXT_ACTION,
    OVERLOADED_DAYS,
    DELAYED_PROJECTS,
}

/** Lo que Inicio necesita saber de proyectos y planificación. */
data class PlanningOverview(
    val blocks: List<TimeBlock> = emptyList(),
    val capacity: CapacitySettings = CapacitySettings(),
    val delayedProjects: Int = 0,
)

data class DashboardAlert(val type: AlertType, val count: Int)

/** Ajustes del usuario a la recomendación. Solo valen para el día en que se hicieron. */
data class DailyFocus(
    val date: LocalDate,
    val pinnedIds: Set<String> = emptySet(),
    val dismissedIds: Set<String> = emptySet(),
)

data class FocusTask(
    val ranked: RankedTask,
    val isPinned: Boolean,
    val isEmergency: Boolean,
)

data class Dashboard(
    val indicators: DashboardIndicators,
    val focus: List<FocusTask>,
    val isFocusAdjusted: Boolean,
    val alerts: List<DashboardAlert>,
    val next24Hours: List<RankedTask>,
    val focusCandidates: List<RankedTask>,
    /** De hoy al domingo: los días ya pasados no aportan tiempo disponible. */
    val capacityThisWeek: CapacitySummary,
    val nextBlocks: List<TimeBlock>,
)

object DashboardCalculator {
    private const val RECOMMENDED_TASKS = 3
    private const val COMMITMENT_HORIZON_DAYS = 7L
    private const val NEXT_BLOCKS_HOURS = 24L

    /** Las emergencias van primero; después pesa la prioridad, y la fecha solo desempata. */
    private val FOCUS_ORDER = compareBy<RankedTask>(
        { !isEmergency(it) },
        { it.priority.effectiveLevel?.ordinal ?: Int.MAX_VALUE },
        { it.item.task.effectiveDate ?: LocalDate.MAX },
        { it.item.task.title.lowercase() },
    )

    private val AGENDA_ORDER = compareBy<RankedTask>(
        { it.item.task.effectiveDate ?: LocalDate.MAX },
        { it.priority.effectiveLevel?.ordinal ?: Int.MAX_VALUE },
        { it.item.task.title.lowercase() },
    )

    fun build(
        ranked: List<RankedTask>,
        focus: DailyFocus?,
        today: LocalDate,
        zone: ZoneId,
        planning: PlanningOverview = PlanningOverview(),
        now: Instant = today.atStartOfDay(zone).toInstant(),
    ): Dashboard {
        val open = ranked.filter { TaskQueries.isOpen(it.item.task.status) }
        val todaysFocus = focus?.takeIf { it.date == today } ?: DailyFocus(today)
        val selected = selectFocus(open, todaysFocus)
        val selectedIds = selected.map { it.ranked.item.task.id }.toSet()
        val restOfWeek = DateSpan(today, today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)))
        val horizon = now.plus(Duration.ofHours(NEXT_BLOCKS_HOURS))
        return Dashboard(
            indicators = indicators(ranked, open, today, zone),
            focus = selected,
            isFocusAdjusted = todaysFocus.pinnedIds.isNotEmpty() || todaysFocus.dismissedIds.isNotEmpty(),
            alerts = alerts(
                open = open,
                today = today,
                overloadedDays = PlanningRules.overloadedDays(
                    planning.blocks,
                    restOfWeek,
                    planning.capacity,
                    zone,
                ),
                delayedProjects = planning.delayedProjects,
            ),
            next24Hours = open
                .filter { it.item.task.effectiveDate.isTodayOrTomorrow(today) }
                .sortedWith(AGENDA_ORDER),
            focusCandidates = open
                .filter { it.item.task.id !in selectedIds }
                .sortedBy { it.item.task.title.lowercase() },
            capacityThisWeek = PlanningRules.capacity(
                planning.blocks,
                restOfWeek.from,
                restOfWeek.to,
                planning.capacity,
                zone,
            ),
            nextBlocks = planning.blocks
                .filter { it.endAt.isAfter(now) && it.startAt.isBefore(horizon) }
                .sortedBy { it.startAt },
        )
    }

    /**
     * Hasta tres recomendaciones, sin obligar a llegar a tres. Una emergencia se añade aunque
     * supere ese número: el usuario debe verla.
     */
    private fun selectFocus(open: List<RankedTask>, focus: DailyFocus): List<FocusTask> {
        val pinned = open
            .filter { it.item.task.id in focus.pinnedIds }
            .sortedWith(FOCUS_ORDER)
        val pinnedIds = pinned.map { it.item.task.id }.toSet()
        val recommendable = open
            .filter { it.item.task.id !in pinnedIds && it.item.task.id !in focus.dismissedIds }
            .filter(::canAdvanceToday)
            .sortedWith(FOCUS_ORDER)
        val slots = (RECOMMENDED_TASKS - pinned.size).coerceAtLeast(0)
        val recommended = recommendable.take(slots)
        val extraEmergencies = recommendable.drop(recommended.size).filter(::isEmergency)
        return pinned.map { FocusTask(it, isPinned = true, isEmergency = isEmergency(it)) } +
            (recommended + extraEmergencies).map {
                FocusTask(it, isPinned = false, isEmergency = isEmergency(it))
            }
    }

    /** Lo bloqueado no se puede avanzar hoy, pero una obligación no se oculta por ello. */
    private fun canAdvanceToday(task: RankedTask): Boolean {
        if (task.priority.obligation != null) return true
        val status = task.item.task.status
        return status != TaskStatus.BLOCKED &&
            status != TaskStatus.WAITING &&
            task.item.openDependencies == 0
    }

    private fun isEmergency(task: RankedTask): Boolean {
        val signal = task.priority.dateSignal
        val isCloseObligation = task.priority.obligation != null &&
            (signal == DateSignal.OVERDUE || signal == DateSignal.DUE_TODAY || signal == DateSignal.DUE_SOON)
        val isCriticalNow = task.priority.effectiveLevel == PriorityLevel.CRITICAL &&
            (signal == DateSignal.OVERDUE || signal == DateSignal.DUE_TODAY)
        return isCloseObligation || isCriticalNow
    }

    private fun indicators(
        all: List<RankedTask>,
        open: List<RankedTask>,
        today: LocalDate,
        zone: ZoneId,
    ): DashboardIndicators {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        fun inView(view: TaskView) = all.count { TaskQueries.matchesView(it, view, today) }
        return DashboardIndicators(
            pending = open.size,
            overdue = inView(TaskView.OVERDUE),
            critical = inView(TaskView.CRITICAL),
            high = inView(TaskView.HIGH),
            inProgress = inView(TaskView.IN_PROGRESS),
            blocked = inView(TaskView.BLOCKED),
            completedThisWeek = all.count { ranked ->
                val task = ranked.item.task
                val completedOn = task.completedAt?.atZone(zone)?.toLocalDate()
                task.status == TaskStatus.COMPLETED && completedOn.isBetween(monday, sunday)
            },
            estimatedMinutesThisWeek = open
                .filter { it.item.task.effectiveDate.isBetween(today, sunday) }
                .sumOf { it.item.task.estimatedMinutes ?: 0 },
        )
    }

    /** Solo se generan las alertas que tienen casos: nada de avisos en cero. */
    private fun alerts(
        open: List<RankedTask>,
        today: LocalDate,
        overloadedDays: Int,
        delayedProjects: Int,
    ): List<DashboardAlert> {
        val horizon = today.plusDays(COMMITMENT_HORIZON_DAYS)
        val commitments = open.filter {
            it.item.task.deadlineDate != null || it.priority.obligation != null
        }
        val counts = listOf(
            AlertType.OVERDUE_COMMITMENTS to commitments.count {
                it.item.task.effectiveDate?.isBefore(today) == true
            },
            AlertType.UPCOMING_COMMITMENTS to commitments.count {
                it.item.task.effectiveDate.isBetween(today, horizon)
            },
            AlertType.BLOCKED_TASKS to open.count { it.item.task.status == TaskStatus.BLOCKED },
            AlertType.PENDING_DEPENDENCIES to open.count { it.item.openDependencies > 0 },
            AlertType.CRITICAL_WITHOUT_NEXT_ACTION to open.count {
                TaskQueries.matchesView(it, TaskView.CRITICAL, today) &&
                    it.item.task.nextAction.isNullOrBlank()
            },
            AlertType.OVERLOADED_DAYS to overloadedDays,
            AlertType.DELAYED_PROJECTS to delayedProjects,
        )
        return counts.filter { (_, count) -> count > 0 }.map { (type, count) -> DashboardAlert(type, count) }
    }

    private fun LocalDate?.isTodayOrTomorrow(today: LocalDate): Boolean =
        this == today || this == today.plusDays(1)

    private fun LocalDate?.isBetween(start: LocalDate, end: LocalDate): Boolean =
        this != null && !isBefore(start) && !isAfter(end)
}
