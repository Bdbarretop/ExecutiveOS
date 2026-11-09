package com.executiveos.domain.projects

import com.executiveos.domain.priority.PriorityLevel
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ProjectStatus { PLANNED, ACTIVE, ON_HOLD, AT_RISK, COMPLETED, CANCELLED }

enum class MilestoneKind { MILESTONE, DELIVERABLE }

data class Project(
    val id: String,
    val name: String,
    val description: String? = null,
    val objective: String? = null,
    val justification: String? = null,
    val owner: String? = null,
    val startDate: LocalDate? = null,
    val targetDate: LocalDate? = null,
    val status: ProjectStatus = ProjectStatus.PLANNED,
    val priority: PriorityLevel? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ProjectMilestone(
    val id: String,
    val projectId: String,
    val name: String,
    val kind: MilestoneKind,
    val weight: Int,
    val dueDate: LocalDate? = null,
    val completedAt: Instant? = null,
    val sortOrder: Int = 0,
) {
    val isCompleted: Boolean get() = completedAt != null
}

data class ProjectRisk(
    val id: String,
    val projectId: String,
    val description: String,
    val mitigation: String? = null,
    val severity: Int,
    val isOpen: Boolean = true,
)

data class ProjectProgress(
    /** `null` si no hay hitos: el avance no se deduce de contar tareas pequeñas. */
    val percent: Int?,
    val completedMilestones: Int,
    val totalMilestones: Int,
    val nextMilestone: ProjectMilestone?,
    val overdueMilestones: Int,
    val openTasks: Int,
    val openRisks: Int,
    val forecastCompletion: LocalDate?,
    val deviationDays: Long?,
    val isDelayed: Boolean,
)

enum class ProjectValidationError { BLANK_NAME, TARGET_BEFORE_START }

object ProjectTracking {
    private val FINISHED = setOf(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED)

    fun isFinished(status: ProjectStatus): Boolean = status in FINISHED

    fun validate(project: Project): List<ProjectValidationError> = buildList {
        if (project.name.isBlank()) add(ProjectValidationError.BLANK_NAME)
        if (project.startDate != null && project.targetDate?.isBefore(project.startDate) == true) {
            add(ProjectValidationError.TARGET_BEFORE_START)
        }
    }

    fun progress(
        project: Project,
        milestones: List<ProjectMilestone>,
        risks: List<ProjectRisk>,
        openTasks: Int,
        today: LocalDate,
    ): ProjectProgress {
        val totalWeight = milestones.sumOf { it.weight }
        val doneWeight = milestones.filter { it.isCompleted }.sumOf { it.weight }
        val open = milestones.filterNot { it.isCompleted }
        val overdue = open.count { it.dueDate?.isBefore(today) == true }
        val forecast = open.mapNotNull { it.dueDate }.maxOrNull()
            ?: project.targetDate.takeIf { open.isNotEmpty() || milestones.isEmpty() }
        val deviation = if (forecast != null && project.targetDate != null) {
            ChronoUnit.DAYS.between(project.targetDate, forecast)
        } else {
            null
        }
        val active = project.status !in FINISHED
        val targetMissed = project.targetDate?.isBefore(today) == true
        return ProjectProgress(
            percent = if (totalWeight > 0) doneWeight * 100 / totalWeight else null,
            completedMilestones = milestones.count { it.isCompleted },
            totalMilestones = milestones.size,
            nextMilestone = open.sortedWith(
                compareBy({ it.dueDate ?: LocalDate.MAX }, { it.sortOrder }),
            ).firstOrNull(),
            overdueMilestones = overdue,
            openTasks = openTasks,
            openRisks = risks.count { it.isOpen },
            forecastCompletion = forecast,
            deviationDays = deviation,
            isDelayed = active && (overdue > 0 || targetMissed || (deviation ?: 0) > 0),
        )
    }
}
