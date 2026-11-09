package com.executiveos.data.repository

import androidx.room.withTransaction
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.AuditLogEntity
import com.executiveos.data.local.entity.CaptureTemplateEntity
import com.executiveos.data.local.entity.TagEntity
import com.executiveos.data.local.entity.TaskDependencyEntity
import com.executiveos.data.local.entity.TaskEntity
import com.executiveos.data.local.entity.TaskReferenceRow
import com.executiveos.data.local.entity.TaskTagCrossRef
import com.executiveos.data.local.entity.TaskWithRelations
import com.executiveos.data.local.entity.UserSettingsEntity
import com.executiveos.domain.capture.SimilarTask
import com.executiveos.domain.capture.TaskValidator
import com.executiveos.domain.model.AddDependencyResult
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.ProjectSummary
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskHistoryAction
import com.executiveos.domain.model.TaskHistoryEntry
import com.executiveos.domain.model.TaskReference
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.repository.TemplateRepository
import com.executiveos.domain.tasks.DependencyRules
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskStatusRules
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val ENTITY_TASK = "TASK"

class OfflineTaskRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : TaskRepository {
    private val taskDao = database.taskDao()
    private val tagDao = database.tagDao()
    private val auditDao = database.auditLogDao()

    override fun observeTask(taskId: String): Flow<Task?> =
        combine(taskDao.observeById(taskId), tagDao.observeNames(taskId)) { task, tags ->
            task?.toDomain(tags)
        }

    override fun observeOpenTaskTitles(): Flow<List<SimilarTask>> =
        taskDao.observeOpenTitles().map { rows -> rows.map { SimilarTask(it.id, it.title) } }

    override fun observeProjects(): Flow<List<ProjectSummary>> =
        database.projectDao().observeAll().map { rows ->
            rows.map { ProjectSummary(it.id, it.name) }
        }

    override fun observeTaskList(): Flow<List<TaskListItem>> = combine(
        taskDao.observeListRows(),
        taskDao.observeAllDependencies(),
    ) { rows, dependencies ->
        val statusById = rows.associate { it.task.id to it.task.status }
        val titleById = rows.associate { it.task.id to it.task.title }
        val openDependencies = dependencies
            .filter { dependency -> statusById[dependency.dependsOnTaskId].isOpenOrUnknown() }
            .groupingBy { it.taskId }
            .eachCount()
        val openSubtasks = rows
            .filter { it.task.parentTaskId != null && TaskQueries.isOpen(it.task.status) }
            .groupingBy { checkNotNull(it.task.parentTaskId) }
            .eachCount()
        rows.map { row ->
            TaskListItem(
                task = row.toTask(),
                categoryName = row.category?.name,
                projectName = row.project?.name,
                parentTitle = row.task.parentTaskId?.let(titleById::get),
                openDependencies = openDependencies[row.task.id] ?: 0,
                openSubtasks = openSubtasks[row.task.id] ?: 0,
            )
        }
    }

    override fun observeSubtasks(taskId: String): Flow<List<TaskReference>> =
        taskDao.observeSubtasks(taskId).map { rows -> rows.map(TaskReferenceRow::toDomain) }

    override fun observeDependencies(taskId: String): Flow<List<TaskReference>> =
        taskDao.observeDependencies(taskId).map { rows -> rows.map(TaskReferenceRow::toDomain) }

    /** Incluye lo ocurrido a la captura de origen, para ver la historia completa. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHistory(taskId: String): Flow<List<TaskHistoryEntry>> =
        taskDao.observeById(taskId).flatMapLatest { task ->
            if (task == null) {
                flowOf(emptyList())
            } else {
                auditDao.observeFor(listOfNotNull(task.id, task.sourceInboxItemId)).map { rows ->
                    rows.mapNotNull(AuditLogEntity::toHistoryEntry)
                }
            }
        }

    override suspend fun save(task: Task) {
        require(TaskValidator.validate(task).isEmpty()) { "La ficha contiene datos no válidos" }
        database.withTransaction {
            val previous = checkNotNull(taskDao.findById(task.id)) { "La tarea ya no existe" }
            val now = clock.instant()
            taskDao.update(
                task.copy(
                    title = task.title.trim(),
                    status = previous.status,
                    completedAt = previous.completedAt,
                    parentTaskId = previous.parentTaskId,
                    updatedAt = now,
                ).toEntity(),
            )
            replaceTags(task.id, task.tags)
            recordChanges(previous, task, now)
        }
    }

    /** Las prioridades no cambian en silencio: cada cambio queda en el historial. */
    private suspend fun recordChanges(previous: TaskEntity, task: Task, now: Instant) {
        val scalesChanged = previous.impact != task.impact ||
            previous.urgency != task.urgency ||
            previous.delayRisk != task.delayRisk
        if (scalesChanged) record(task.id, TaskHistoryAction.PRIORITY_CHANGED, now)
        if (previous.manualPriority != task.manualPriority) {
            record(task.id, TaskHistoryAction.MANUAL_PRIORITY_CHANGED, now)
        }
        if (previous.obligation != task.obligation) {
            record(task.id, TaskHistoryAction.OBLIGATION_CHANGED, now)
        }
        if (previous.targetDate != task.targetDate || previous.deadlineDate != task.deadlineDate) {
            record(task.id, TaskHistoryAction.DATE_CHANGED, now)
        }
    }

    override suspend fun create(title: String, parentTaskId: String?, projectId: String?): String =
        database.withTransaction {
            val trimmed = title.trim()
            require(trimmed.isNotEmpty()) { "La tarea necesita un título" }
            val now = clock.instant()
            val parent = parentTaskId?.let { requireTask(it) }
            val task = Task(
                id = UUID.randomUUID().toString(),
                status = TaskStatus.PENDING,
                sourceInboxItemId = null,
                title = trimmed,
                categoryId = parent?.categoryId,
                projectId = projectId ?: parent?.projectId,
                parentTaskId = parentTaskId,
                createdAt = now,
                updatedAt = now,
            )
            taskDao.insert(task.toEntity())
            record(task.id, TaskHistoryAction.CREATED, now)
            if (parentTaskId != null) record(parentTaskId, TaskHistoryAction.SUBTASK_ADDED, now)
            task.id
        }

    override suspend fun setStatus(taskId: String, status: TaskStatus) {
        database.withTransaction {
            val task = requireTask(taskId)
            if (task.status == status) return@withTransaction
            require(TaskStatusRules.canTransition(task.status, status)) {
                "Cambio de estado no permitido"
            }
            val now = clock.instant()
            taskDao.update(
                task.copy(
                    status = status,
                    completedAt = now.takeIf { status == TaskStatus.COMPLETED },
                    updatedAt = now,
                ),
            )
            val action = if (status == TaskStatus.ARCHIVED) {
                TaskHistoryAction.ARCHIVED
            } else {
                TaskHistoryAction.STATUS_CHANGED
            }
            record(taskId, action, now)
        }
    }

    /** No copia las fechas: serían compromisos que nadie ha adquirido. */
    override suspend fun duplicate(taskId: String): String = database.withTransaction {
        val original = requireTask(taskId)
        val now = clock.instant()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            status = TaskStatus.PENDING,
            sourceInboxItemId = null,
            startDate = null,
            targetDate = null,
            deadlineDate = null,
            actualMinutes = null,
            completedAt = null,
            parentTaskId = null,
            createdAt = now,
            updatedAt = now,
        )
        taskDao.insert(copy)
        database.tagDao().namesOf(taskId).forEach { name -> linkTag(copy.id, name) }
        record(copy.id, TaskHistoryAction.DUPLICATED, now)
        copy.id
    }

    override suspend fun delegate(taskId: String, person: String) {
        require(person.isNotBlank()) { "Indica a quién se delega" }
        database.withTransaction {
            val now = clock.instant()
            taskDao.update(
                requireTask(taskId).copy(
                    owner = person.trim(),
                    delegationStatus = DelegationStatus.DELEGATED,
                    status = TaskStatus.WAITING,
                    updatedAt = now,
                ),
            )
            record(taskId, TaskHistoryAction.DELEGATED, now)
        }
    }

    override suspend fun postpone(taskId: String, until: LocalDate) {
        database.withTransaction {
            val now = clock.instant()
            taskDao.update(requireTask(taskId).copy(targetDate = until, updatedAt = now))
            record(taskId, TaskHistoryAction.POSTPONED, now)
        }
    }

    override suspend fun addDependency(
        taskId: String,
        dependsOnId: String,
    ): AddDependencyResult = database.withTransaction {
        val graph = taskDao.allDependencies()
            .groupBy({ it.taskId }, { it.dependsOnTaskId })
            .mapValues { (_, values) -> values.toSet() }
        when {
            dependsOnId in graph[taskId].orEmpty() -> AddDependencyResult.ALREADY_EXISTS
            DependencyRules.wouldCreateCycle(taskId, dependsOnId, graph) ->
                AddDependencyResult.WOULD_CREATE_CYCLE
            else -> {
                taskDao.addDependency(TaskDependencyEntity(taskId, dependsOnId))
                record(taskId, TaskHistoryAction.DEPENDENCY_ADDED, clock.instant())
                AddDependencyResult.ADDED
            }
        }
    }

    override suspend fun removeDependency(taskId: String, dependsOnId: String) {
        database.withTransaction {
            taskDao.removeDependency(taskId, dependsOnId)
            record(taskId, TaskHistoryAction.DEPENDENCY_REMOVED, clock.instant())
        }
    }

    private suspend fun requireTask(taskId: String): TaskEntity =
        checkNotNull(taskDao.findById(taskId)) { "La tarea ya no existe" }

    private suspend fun replaceTags(taskId: String, tags: List<String>) {
        tagDao.unlinkAll(taskId)
        tags.map(String::trim).filter(String::isNotEmpty).distinct().forEach { name ->
            linkTag(taskId, name)
        }
    }

    private suspend fun linkTag(taskId: String, name: String) {
        tagDao.insertIfAbsent(TagEntity(UUID.randomUUID().toString(), name))
        val tag = checkNotNull(tagDao.findByName(name))
        tagDao.link(TaskTagCrossRef(taskId, tag.id))
    }

    private suspend fun record(taskId: String, action: TaskHistoryAction, now: Instant) {
        auditDao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = ENTITY_TASK,
                entityId = taskId,
                action = action.name,
                createdAt = now,
            ),
        )
    }
}

/** Una dependencia cuya tarea sigue en la bandeja también está pendiente. */
private fun TaskStatus?.isOpenOrUnknown(): Boolean = this == null || TaskQueries.isOpen(this)

private fun TaskWithRelations.toTask(): Task = task.toDomain(tags.map { it.name }.sorted())

private fun TaskReferenceRow.toDomain() = TaskReference(id, title, status)

private fun AuditLogEntity.toHistoryEntry(): TaskHistoryEntry? =
    TaskHistoryAction.entries.firstOrNull { it.name == action }
        ?.let { TaskHistoryEntry(it, createdAt) }

class OfflinePrioritySettingsRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : PrioritySettingsRepository {
    override val settings: Flow<PrioritySettings> = database.userSettingsDao().observe().map { row ->
        row?.toDomain() ?: PrioritySettings()
    }

    override suspend fun save(settings: PrioritySettings) {
        require(settings.isValid) { "Pesos o umbrales no válidos" }
        database.userSettingsDao().save(
            UserSettingsEntity(
                impactWeight = settings.impactWeight,
                urgencyWeight = settings.urgencyWeight,
                delayRiskWeight = settings.delayRiskWeight,
                criticalThreshold = settings.criticalThreshold,
                highThreshold = settings.highThreshold,
                mediumThreshold = settings.mediumThreshold,
                updatedAt = clock.instant(),
            ),
        )
    }
}

private fun UserSettingsEntity.toDomain() = PrioritySettings(
    impactWeight = impactWeight,
    urgencyWeight = urgencyWeight,
    delayRiskWeight = delayRiskWeight,
    criticalThreshold = criticalThreshold,
    highThreshold = highThreshold,
    mediumThreshold = mediumThreshold,
)

class OfflineTemplateRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : TemplateRepository {
    private val templateDao = database.templateDao()

    override fun observeTemplates(): Flow<List<CaptureTemplate>> =
        templateDao.observeAll().map { rows -> rows.map(CaptureTemplateEntity::toDomain) }

    override fun observeTemplate(templateId: String): Flow<CaptureTemplate?> =
        templateDao.observeById(templateId).map { it?.toDomain() }

    override suspend fun save(template: CaptureTemplate) {
        require(template.name.isNotBlank()) { "La plantilla necesita un nombre" }
        templateDao.update(template.toEntity(clock.instant()))
    }
}
