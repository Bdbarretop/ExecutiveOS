package com.executiveos.domain.tasks

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.model.effectiveDate
import com.executiveos.domain.priority.PriorityAssessment
import com.executiveos.domain.priority.PriorityEngine
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.priority.PrioritySettings
import java.text.Normalizer
import java.time.LocalDate

enum class TaskView {
    ALL,
    TODAY,
    UPCOMING,
    OVERDUE,
    CRITICAL,
    HIGH,
    IN_PROGRESS,
    WAITING,
    BLOCKED,
    COMPLETED,
    NO_DATE,
}

enum class EstimateFilter { UP_TO_30, UP_TO_60, UP_TO_120, OVER_120 }

data class TaskFilters(
    val categoryIds: Set<String> = emptySet(),
    val projectIds: Set<String> = emptySet(),
    val levels: Set<PriorityLevel> = emptySet(),
    val statuses: Set<TaskStatus> = emptySet(),
    val owners: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val estimate: EstimateFilter? = null,
) {
    val activeCount: Int
        get() = listOf(
            categoryIds.isNotEmpty(),
            projectIds.isNotEmpty(),
            levels.isNotEmpty(),
            statuses.isNotEmpty(),
            owners.isNotEmpty(),
            tags.isNotEmpty(),
            dateFrom != null || dateTo != null,
            estimate != null,
        ).count { it }
}

data class TaskListItem(
    val task: Task,
    val categoryName: String?,
    val projectName: String?,
    val parentTitle: String?,
    val openDependencies: Int,
    val openSubtasks: Int,
)

data class RankedTask(
    val item: TaskListItem,
    val priority: PriorityAssessment,
)

object TaskQueries {
    private const val UPCOMING_DAYS = 7L
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val OPEN_STATUSES = setOf(
        TaskStatus.PENDING,
        TaskStatus.IN_PROGRESS,
        TaskStatus.WAITING,
        TaskStatus.BLOCKED,
    )

    /** Las obligaciones críticas van primero: la puntuación no puede ocultarlas. */
    private val PRIORITY_ORDER = compareBy<RankedTask>(
        { it.priority.obligation == null },
        { it.priority.effectiveLevel?.ordinal ?: Int.MAX_VALUE },
        { it.item.task.effectiveDate ?: LocalDate.MAX },
        { it.item.task.title.lowercase() },
    )

    fun isOpen(status: TaskStatus): Boolean = status in OPEN_STATUSES

    fun rank(
        items: List<TaskListItem>,
        settings: PrioritySettings,
        today: LocalDate,
    ): List<RankedTask> = items.map { item ->
        RankedTask(item, PriorityEngine.assess(item.task, settings, today, item.openDependencies))
    }

    fun select(
        ranked: List<RankedTask>,
        view: TaskView,
        filters: TaskFilters,
        query: String,
        today: LocalDate,
    ): List<RankedTask> = ranked
        .filter { matchesView(it, view, today) && matchesFilters(it, filters) && matchesSearch(it.item, query) }
        .sortedWith(PRIORITY_ORDER)

    fun matchesView(ranked: RankedTask, view: TaskView, today: LocalDate): Boolean {
        val task = ranked.item.task
        val open = isOpen(task.status)
        val date = task.effectiveDate
        return when (view) {
            TaskView.ALL -> task.status != TaskStatus.ARCHIVED
            TaskView.TODAY -> open && date == today
            TaskView.UPCOMING ->
                open && date != null && date.isAfter(today) &&
                    !date.isAfter(today.plusDays(UPCOMING_DAYS))
            TaskView.OVERDUE -> open && date != null && date.isBefore(today)
            TaskView.CRITICAL -> open && isCritical(ranked.priority)
            TaskView.HIGH ->
                open && !isCritical(ranked.priority) &&
                    ranked.priority.effectiveLevel == PriorityLevel.HIGH
            TaskView.IN_PROGRESS -> task.status == TaskStatus.IN_PROGRESS
            TaskView.WAITING -> task.status == TaskStatus.WAITING
            TaskView.BLOCKED -> task.status == TaskStatus.BLOCKED
            TaskView.COMPLETED -> task.status == TaskStatus.COMPLETED
            TaskView.NO_DATE -> open && date == null
        }
    }

    private fun isCritical(priority: PriorityAssessment): Boolean =
        priority.obligation != null || priority.effectiveLevel == PriorityLevel.CRITICAL

    fun matchesFilters(ranked: RankedTask, filters: TaskFilters): Boolean {
        val task = ranked.item.task
        return matchesSet(filters.categoryIds, task.categoryId) &&
            matchesSet(filters.projectIds, task.projectId) &&
            matchesSet(filters.levels, ranked.priority.effectiveLevel) &&
            matchesSet(filters.statuses, task.status) &&
            matchesSet(filters.owners, task.owner) &&
            (filters.tags.isEmpty() || task.tags.any { it in filters.tags }) &&
            matchesDate(task.effectiveDate, filters) &&
            matchesEstimate(task.estimatedMinutes, filters.estimate)
    }

    private fun <T> matchesSet(allowed: Set<T>, value: T?): Boolean =
        allowed.isEmpty() || value in allowed

    private fun matchesDate(date: LocalDate?, filters: TaskFilters): Boolean {
        if (filters.dateFrom == null && filters.dateTo == null) return true
        if (date == null) return false
        return (filters.dateFrom == null || !date.isBefore(filters.dateFrom)) &&
            (filters.dateTo == null || !date.isAfter(filters.dateTo))
    }

    private fun matchesEstimate(minutes: Int?, filter: EstimateFilter?): Boolean {
        if (filter == null) return true
        if (minutes == null) return false
        return when (filter) {
            EstimateFilter.UP_TO_30 -> minutes <= 30
            EstimateFilter.UP_TO_60 -> minutes <= 60
            EstimateFilter.UP_TO_120 -> minutes <= 120
            EstimateFilter.OVER_120 -> minutes > 120
        }
    }

    /** Cada palabra buscada debe aparecer; sin distinguir mayúsculas ni tildes. */
    fun matchesSearch(item: TaskListItem, query: String): Boolean {
        val tokens = normalize(query).split(' ').filter(String::isNotEmpty)
        if (tokens.isEmpty()) return true
        val haystack = normalize(searchableText(item))
        return tokens.all(haystack::contains)
    }

    private fun searchableText(item: TaskListItem): String {
        val task = item.task
        return (
            listOf(
                task.title,
                task.description,
                task.whatToDo,
                task.whyNeeded,
                task.contextToRemember,
                task.origin,
                task.requestedBy,
                task.notes,
                item.projectName,
            ) + task.tags
            ).filterNotNull().joinToString(" ")
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
}
