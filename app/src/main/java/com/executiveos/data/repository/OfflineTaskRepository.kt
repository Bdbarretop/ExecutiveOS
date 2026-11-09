package com.executiveos.data.repository

import androidx.room.withTransaction
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.CaptureTemplateEntity
import com.executiveos.data.local.entity.TagEntity
import com.executiveos.data.local.entity.TaskTagCrossRef
import com.executiveos.domain.capture.SimilarTask
import com.executiveos.domain.capture.TaskValidator
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.ProjectSummary
import com.executiveos.domain.model.Task
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.repository.TemplateRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class OfflineTaskRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : TaskRepository {
    private val taskDao = database.taskDao()
    private val tagDao = database.tagDao()

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

    override suspend fun save(task: Task) {
        require(TaskValidator.validate(task).isEmpty()) { "La ficha contiene datos no válidos" }
        database.withTransaction {
            taskDao.update(task.copy(title = task.title.trim(), updatedAt = clock.instant()).toEntity())
            replaceTags(task.id, task.tags)
        }
    }

    private suspend fun replaceTags(taskId: String, tags: List<String>) {
        tagDao.unlinkAll(taskId)
        tags.map(String::trim).filter(String::isNotEmpty).distinct().forEach { name ->
            tagDao.insertIfAbsent(TagEntity(UUID.randomUUID().toString(), name))
            val tag = checkNotNull(tagDao.findByName(name))
            tagDao.link(TaskTagCrossRef(taskId, tag.id))
        }
    }
}

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
