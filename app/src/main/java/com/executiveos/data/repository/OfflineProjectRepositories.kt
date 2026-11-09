package com.executiveos.data.repository

import androidx.room.withTransaction
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.CapacitySettingsEntity
import com.executiveos.data.local.entity.ProjectEntity
import com.executiveos.data.local.entity.ProjectMilestoneEntity
import com.executiveos.data.local.entity.ProjectRiskEntity
import com.executiveos.data.local.entity.TimeBlockEntity
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.PlanningRules
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.projects.MilestoneKind
import com.executiveos.domain.projects.Project
import com.executiveos.domain.projects.ProjectMilestone
import com.executiveos.domain.projects.ProjectRisk
import com.executiveos.domain.projects.ProjectStatus
import com.executiveos.domain.repository.PlanningRepository
import com.executiveos.domain.repository.ProjectDetails
import com.executiveos.domain.repository.ProjectRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class OfflineProjectRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : ProjectRepository {
    private val projectDao = database.projectDao()
    private val trackingDao = database.projectTrackingDao()

    override fun observeProjects(): Flow<List<ProjectDetails>> = combine(
        projectDao.observeAll(),
        trackingDao.observeMilestones(),
        trackingDao.observeRisks(),
        trackingDao.observeOpenTaskCounts(),
    ) { projects, milestones, risks, counts ->
        val openByProject = counts.associate { it.projectId to it.count }
        projects.map { project ->
            ProjectDetails(
                project = project.toDomain(),
                milestones = milestones.filter { it.projectId == project.id }.map { it.toDomain() },
                risks = risks.filter { it.projectId == project.id }.map { it.toDomain() },
                openTasks = openByProject[project.id] ?: 0,
            )
        }
    }

    override fun observeProject(projectId: String): Flow<ProjectDetails?> =
        observeProjects().map { all -> all.firstOrNull { it.project.id == projectId } }

    override suspend fun create(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "El proyecto necesita un nombre" }
        val now = clock.instant()
        val id = UUID.randomUUID().toString()
        projectDao.insert(
            ProjectEntity(
                id = id,
                name = trimmed,
                description = null,
                objective = null,
                justification = null,
                status = ProjectStatus.PLANNED.name,
                sourceInboxItemId = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    override suspend fun save(project: Project) {
        require(project.name.isNotBlank()) { "El proyecto necesita un nombre" }
        database.withTransaction {
            val current = checkNotNull(projectDao.findById(project.id)) { "El proyecto ya no existe" }
            trackingDao.updateProject(
                current.copy(
                    name = project.name.trim(),
                    description = project.description,
                    objective = project.objective,
                    justification = project.justification,
                    owner = project.owner,
                    startDate = project.startDate,
                    targetDate = project.targetDate,
                    status = project.status.name,
                    priority = project.priority,
                    notes = project.notes,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun addMilestone(
        projectId: String,
        name: String,
        kind: MilestoneKind,
        weight: Int,
        dueDate: LocalDate?,
    ) {
        require(name.isNotBlank()) { "El hito necesita un nombre" }
        require(weight > 0) { "El peso debe ser positivo" }
        database.withTransaction {
            trackingDao.insertMilestone(
                ProjectMilestoneEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = name.trim(),
                    kind = kind,
                    weight = weight,
                    dueDate = dueDate,
                    completedAt = null,
                    sortOrder = trackingDao.nextMilestoneOrder(projectId),
                ),
            )
        }
    }

    override suspend fun setMilestoneCompleted(milestoneId: String, completed: Boolean) {
        database.withTransaction {
            val milestone = checkNotNull(trackingDao.findMilestone(milestoneId)) { "El hito ya no existe" }
            trackingDao.updateMilestone(
                milestone.copy(completedAt = clock.instant().takeIf { completed }),
            )
        }
    }

    override suspend fun deleteMilestone(milestoneId: String) = trackingDao.deleteMilestone(milestoneId)

    override suspend fun addRisk(
        projectId: String,
        description: String,
        mitigation: String?,
        severity: Int,
    ) {
        require(description.isNotBlank()) { "Describe el riesgo" }
        require(severity in 1..5) { "La severidad va de 1 a 5" }
        trackingDao.insertRisk(
            ProjectRiskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                description = description.trim(),
                mitigation = mitigation?.trim()?.ifEmpty { null },
                severity = severity,
                isOpen = true,
            ),
        )
    }

    override suspend fun setRiskOpen(riskId: String, isOpen: Boolean) =
        trackingDao.setRiskOpen(riskId, isOpen)
}

class OfflinePlanningRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : PlanningRepository {
    private val dao = database.planningDao()

    override fun observeBlocks(): Flow<List<TimeBlock>> =
        dao.observeBlocks().map { rows -> rows.map(TimeBlockEntity::toDomain) }

    override val capacity: Flow<CapacitySettings> = dao.observeCapacity().map { row ->
        row?.toDomain() ?: CapacitySettings()
    }

    override suspend fun save(block: TimeBlock) {
        require(PlanningRules.validate(block).isEmpty()) { "El bloque contiene datos no válidos" }
        database.withTransaction {
            val entity = block.toEntity(clock)
            if (dao.findBlock(block.id) == null) dao.insertBlock(entity) else dao.updateBlock(entity)
        }
    }

    override suspend fun delete(blockId: String) = dao.deleteBlock(blockId)

    override suspend fun saveCapacity(settings: CapacitySettings) {
        require(settings.availableMinutesPerDay in 0..1_440) { "Minutos por día no válidos" }
        require(settings.targetPercent in 1..100) { "Porcentaje no válido" }
        dao.saveCapacity(
            CapacitySettingsEntity(
                availableMinutesPerDay = settings.availableMinutesPerDay,
                targetPercent = settings.targetPercent,
                workDays = settings.workDays.joinToString(",") { it.name },
                updatedAt = clock.instant(),
            ),
        )
    }
}

private fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    description = description,
    objective = objective,
    justification = justification,
    owner = owner,
    startDate = startDate,
    targetDate = targetDate,
    status = ProjectStatus.entries.firstOrNull { it.name == status } ?: ProjectStatus.PLANNED,
    priority = priority,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ProjectMilestoneEntity.toDomain() =
    ProjectMilestone(id, projectId, name, kind, weight, dueDate, completedAt, sortOrder)

private fun ProjectRiskEntity.toDomain() =
    ProjectRisk(id, projectId, description, mitigation, severity, isOpen)

private fun TimeBlockEntity.toDomain() =
    TimeBlock(id, type, title, taskId, startAt, endAt, actualMinutes)

private fun TimeBlock.toEntity(clock: Clock) = TimeBlockEntity(
    id = id,
    type = type,
    title = title.trim(),
    taskId = taskId,
    startAt = startAt,
    endAt = endAt,
    actualMinutes = actualMinutes,
    updatedAt = clock.instant(),
)

private fun CapacitySettingsEntity.toDomain() = CapacitySettings(
    availableMinutesPerDay = availableMinutesPerDay,
    targetPercent = targetPercent,
    workDays = workDays.split(",")
        .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
        .toSet(),
)
