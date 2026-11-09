package com.executiveos.data.repository

import androidx.room.withTransaction
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.AuditLogEntity
import com.executiveos.data.local.entity.CaptureTemplateEntity
import com.executiveos.data.local.entity.InboxItemEntity
import com.executiveos.data.local.entity.InboxItemWithDetails
import com.executiveos.data.local.entity.NoteEntity
import com.executiveos.data.local.entity.ProjectEntity
import com.executiveos.data.local.entity.TaskEntity
import com.executiveos.domain.capture.CaptureDates
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.InboxState
import com.executiveos.domain.model.ProcessedAs
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.repository.InboxRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ENTITY_INBOX_ITEM = "INBOX_ITEM"

class OfflineInboxRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : InboxRepository {
    private val inboxDao = database.inboxDao()
    private val taskDao = database.taskDao()
    private val auditDao = database.auditLogDao()

    override fun observeEntries(): Flow<List<InboxEntry>> =
        inboxDao.observeAll().map { rows -> rows.map(InboxItemWithDetails::toEntry) }

    override suspend fun capture(draft: CaptureDraft): String = database.withTransaction {
        val now = clock.instant()
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "La captura necesita un título" }
        val date = CaptureDates.resolve(draft.dateOption, LocalDate.now(clock), draft.customDate)
        val details = draft.details.trim().ifEmpty { null }
        val inserted = inboxDao.insertIfAbsent(draft.toItem(title, details, date, now))
        if (inserted == -1L) {
            // El mismo borrador ya se guardó: una doble pulsación no crea otra tarea.
            return@withTransaction taskDao.findByInboxItem(draft.id)?.id ?: draft.id
        }
        val template = draft.templateId?.let { database.templateDao().findById(it) }
        val task = draft.toTask(title, details, date, template, now)
        taskDao.insert(task)
        recordAudit(draft.id, "CAPTURED", now)
        task.id
    }

    override suspend fun convertToTasks(itemIds: Collection<String>) = forEachItem(itemIds) { item, now ->
        val task = requireTask(item.id)
        taskDao.update(task.copy(status = TaskStatus.PENDING, updatedAt = now))
        inboxDao.update(item.processed(ProcessedAs.TASK, task.id, now))
        recordAudit(item.id, "CONVERTED_TO_TASK", now)
    }

    override suspend fun convertToProject(itemId: String) = forEachItem(listOf(itemId)) { item, now ->
        val task = requireTask(item.id)
        val projectId = UUID.randomUUID().toString()
        database.projectDao().insert(
            ProjectEntity(
                id = projectId,
                name = task.title,
                description = task.description,
                objective = task.expectedOutcome,
                justification = task.whyNeeded,
                status = "PLANNED",
                sourceInboxItemId = item.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        taskDao.update(task.copy(status = TaskStatus.ARCHIVED, updatedAt = now))
        inboxDao.update(item.processed(ProcessedAs.PROJECT, projectId, now))
        recordAudit(item.id, "CONVERTED_TO_PROJECT", now)
    }

    override suspend fun convertToNote(itemId: String) = forEachItem(listOf(itemId)) { item, now ->
        val task = requireTask(item.id)
        val noteId = UUID.randomUUID().toString()
        val body = listOfNotNull(task.description, task.contextToRemember, task.notes)
            .joinToString("\n\n")
            .ifEmpty { item.originalDetails }
        database.noteDao().insert(
            NoteEntity(
                id = noteId,
                title = task.title,
                body = body,
                sourceInboxItemId = item.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        taskDao.update(task.copy(status = TaskStatus.ARCHIVED, updatedAt = now))
        inboxDao.update(item.processed(ProcessedAs.NOTE, noteId, now))
        recordAudit(item.id, "CONVERTED_TO_NOTE", now)
    }

    override suspend fun delegate(itemId: String, person: String) = forEachItem(listOf(itemId)) { item, now ->
        val task = requireTask(item.id)
        taskDao.update(
            task.copy(
                status = TaskStatus.WAITING,
                owner = person.trim(),
                delegationStatus = DelegationStatus.DELEGATED,
                updatedAt = now,
            ),
        )
        inboxDao.update(item.processed(ProcessedAs.TASK, task.id, now))
        recordAudit(item.id, "DELEGATED", now)
    }

    override suspend fun postpone(itemIds: Collection<String>, until: LocalDate) =
        forEachItem(itemIds) { item, now ->
            inboxDao.update(
                item.copy(state = InboxState.POSTPONED, postponedUntil = until, updatedAt = now),
            )
            recordAudit(item.id, "POSTPONED", now)
        }

    override suspend fun archive(itemIds: Collection<String>) = forEachItem(itemIds) { item, now ->
        taskDao.findByInboxItem(item.id)?.let { task ->
            taskDao.update(task.copy(status = TaskStatus.ARCHIVED, updatedAt = now))
        }
        inboxDao.update(item.copy(state = InboxState.ARCHIVED, updatedAt = now))
        recordAudit(item.id, "ARCHIVED", now)
    }

    override suspend fun delete(itemIds: Collection<String>) = forEachItem(itemIds) { item, now ->
        taskDao.findByInboxItem(item.id)?.let { task -> taskDao.delete(task.id) }
        inboxDao.delete(item.id)
        recordAudit(item.id, "DELETED", now)
    }

    /** Todo el lote se aplica en una transacción: o se procesan todas o ninguna. */
    private suspend fun forEachItem(
        itemIds: Collection<String>,
        action: suspend (InboxItemEntity, Instant) -> Unit,
    ) {
        database.withTransaction {
            val now = clock.instant()
            itemIds.forEach { id ->
                inboxDao.findById(id)?.let { item -> action(item, now) }
            }
        }
    }

    private suspend fun requireTask(itemId: String): TaskEntity =
        checkNotNull(taskDao.findByInboxItem(itemId)) { "Captura sin tarea asociada" }

    private suspend fun recordAudit(entityId: String, action: String, now: Instant) {
        auditDao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = ENTITY_INBOX_ITEM,
                entityId = entityId,
                action = action,
                createdAt = now,
            ),
        )
    }
}

private fun InboxItemWithDetails.toEntry(): InboxEntry {
    val task = tasks.firstOrNull()
    return InboxEntry(
        item = item.toDomain(),
        taskId = task?.id,
        title = task?.title ?: item.originalTitle,
        categoryName = category?.name,
    )
}

private fun InboxItemEntity.processed(kind: ProcessedAs, targetId: String, now: Instant) = copy(
    state = InboxState.PROCESSED,
    processedAs = kind,
    processedTargetId = targetId,
    updatedAt = now,
)

private fun CaptureDraft.toItem(
    title: String,
    details: String?,
    date: LocalDate?,
    now: Instant,
) = InboxItemEntity(
    id = id,
    originalTitle = title,
    originalDetails = details,
    categoryId = categoryId,
    dateOption = dateOption,
    capturedDate = date,
    dateIsDeadline = dateIsDeadline && date != null,
    state = InboxState.PENDING,
    postponedUntil = null,
    processedAs = null,
    processedTargetId = null,
    createdAt = now,
    updatedAt = now,
)

/** La fecha solo es vencimiento si el usuario lo confirmó; si no, es una fecha objetivo. */
private fun CaptureDraft.toTask(
    title: String,
    details: String?,
    date: LocalDate?,
    template: CaptureTemplateEntity?,
    now: Instant,
) = TaskEntity(
    id = UUID.randomUUID().toString(),
    status = TaskStatus.INBOX,
    sourceInboxItemId = id,
    title = title,
    description = details,
    categoryId = categoryId ?: template?.categoryId,
    projectId = null,
    whatToDo = template?.whatToDo,
    whyNeeded = null,
    requestedBy = null,
    origin = null,
    contextToRemember = null,
    requiredDocuments = null,
    expectedImpact = null,
    consequenceIfNotDone = null,
    consequenceIfLate = null,
    affectedParties = null,
    relatedRisk = null,
    expectedOutcome = template?.expectedOutcome,
    nextAction = template?.nextAction,
    impact = null,
    urgency = null,
    delayRisk = null,
    startDate = null,
    targetDate = date.takeUnless { dateIsDeadline },
    deadlineDate = date.takeIf { dateIsDeadline },
    estimatedMinutes = template?.estimatedMinutes,
    actualMinutes = null,
    owner = null,
    involvedPeople = null,
    dependenciesNote = null,
    delegationStatus = DelegationStatus.NOT_DELEGATED,
    notes = null,
    links = null,
    references = null,
    createdAt = now,
    updatedAt = now,
)
