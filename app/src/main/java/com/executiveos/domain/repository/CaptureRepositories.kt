package com.executiveos.domain.repository

import com.executiveos.domain.capture.SimilarTask
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.CaptureTemplate
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.ProjectSummary
import com.executiveos.domain.model.Task
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

    suspend fun save(task: Task)
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
