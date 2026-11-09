package com.executiveos.domain.repository

import com.executiveos.domain.capture.SimilarTask
import com.executiveos.domain.dashboard.DailyFocus
import com.executiveos.domain.model.AddDependencyResult
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.ProjectSummary
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskHistoryEntry
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.projects.MilestoneKind
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectMilestone
import com.executiveos.domain.projects.ProjectRisk
import com.executiveos.domain.reminders.ReminderSettings
import com.executiveos.domain.reminders.ReminderType
import com.executiveos.domain.reminders.TaskReminder
import com.executiveos.domain.tasks.TaskListItem
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface InboxRepository {
    fun observeEntries(): Flow<List<InboxEntry>>

    /** Guarda la captura y devuelve el id de su tarea. Repetir el mismo borrador no duplica. */
    suspend fun capture(draft: CaptureDraft): String

    suspend fun convertToTasks(itemIds: Collection<String>)

    suspend fun convertToProject(itemId: String)

    suspend fun convertToNote(itemId: String)

    suspend fun delegate(itemId: String, person: String)

    suspend fun postpone(itemIds: Collection<String>, until: LocalDate)

    suspend fun archive(itemIds: Collection<String>)

    suspend fun delete(itemIds: Collection<String>)
}

interface TaskRepository {
    fun observeTask(taskId: String): Flow<Task?>

    fun observeOpenTaskTitles(): Flow<List<SimilarTask>>

    fun observeProjects(): Flow<List<ProjectSummary>>

    fun observeTaskList(): Flow<List<TaskListItem>>

    fun observeSubtasks(taskId: String): Flow<List<TaskReference>>

    fun observeDependencies(taskId: String): Flow<List<TaskReference>>

    fun observeHistory(taskId: String): Flow<List<TaskHistoryEntry>>

    /** Guarda el contenido de la ficha. El estado solo cambia con [setStatus]. */
    suspend fun save(task: Task)

    suspend fun create(
        title: String,
        parentTaskId: String? = null,
        projectId: String? = null,
    ): String

    suspend fun setStatus(taskId: String, status: TaskStatus)

    suspend fun duplicate(taskId: String): String

    suspend fun delegate(taskId: String, person: String)

    /** Mueve la fecha objetivo. Una fecha límite es un compromiso y no se pospone sola. */
    suspend fun postpone(taskId: String, until: LocalDate)

    suspend fun addDependency(taskId: String, dependsOnId: String): AddDependencyResult

    suspend fun removeDependency(taskId: String, dependsOnId: String)
}

interface DailyFocusRepository {
    val focus: Flow<DailyFocus?>

    suspend fun pin(taskId: String, today: LocalDate)

    suspend fun dismiss(taskId: String, today: LocalDate)

    suspend fun reset(today: LocalDate)
}

interface ReminderRepository {
    val settings: Flow<ReminderSettings>

    suspend fun saveSettings(settings: ReminderSettings)

    fun observeTaskReminder(taskId: String): Flow<TaskReminder?>

    /** Una tarea tiene un solo recordatorio activo: el nuevo sustituye al anterior. */
    suspend fun setTaskReminder(taskId: String, at: Instant): String

    /** Devuelve el identificador cancelado, si había alguno. */
    suspend fun clearTaskReminder(taskId: String): String?

    suspend fun findScheduled(reminderId: String): TaskReminder?

    suspend fun markDelivered(reminderId: String)

    /** Devuelve `false` si esa clave ya se notificó: así no hay avisos duplicados. */
    suspend fun registerOnce(key: String, type: ReminderType): Boolean

    suspend fun deleteDeliveredBefore(instant: Instant)
}

data class ProjectDetails(
    val project: Project,
    val milestones: List<ProjectMilestone>,
    val risks: List<ProjectRisk>,
    val openTasks: Int,
)

interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectDetails>>

    fun observeProject(projectId: String): Flow<ProjectDetails?>

    suspend fun create(name: String): String

    suspend fun save(project: Project)

    suspend fun addMilestone(
        projectId: String,
        name: String,
        kind: MilestoneKind,
        weight: Int,
        dueDate: LocalDate?,
    )

    suspend fun setMilestoneCompleted(milestoneId: String, completed: Boolean)

    suspend fun deleteMilestone(milestoneId: String)

    suspend fun addRisk(projectId: String, description: String, mitigation: String?, severity: Int)

    suspend fun setRiskOpen(riskId: String, isOpen: Boolean)
}

interface PlanningRepository {
    fun observeBlocks(): Flow<List<TimeBlock>>

    val capacity: Flow<CapacitySettings>

    /** Crea el bloque o actualiza el existente con el mismo identificador. */
    suspend fun save(block: TimeBlock)

    suspend fun delete(blockId: String)

    suspend fun saveCapacity(settings: CapacitySettings)
}

interface PrioritySettingsRepository {
    val settings: Flow<PrioritySettings>

    suspend fun save(settings: PrioritySettings)
}

interface CaptureDraftRepository {
    /** Borrador pendiente, o uno vacío con un identificador nuevo si no existe. */
    suspend fun loadDraft(): CaptureDraft

    suspend fun saveDraft(draft: CaptureDraft)

    suspend fun rememberCategory(categoryId: String?)

    val lastProjectId: Flow<String?>

    suspend fun rememberProject(projectId: String?)
}

interface TemplateRepository {
    fun observeTemplates(): Flow<List<CaptureTemplate>>

    fun observeTemplate(templateId: String): Flow<CaptureTemplate?>

    suspend fun save(template: CaptureTemplate)
}
