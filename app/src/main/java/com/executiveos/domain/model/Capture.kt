package com.executiveos.domain.model

import java.time.Instant
import java.time.LocalDate

enum class DateOption { TODAY, TOMORROW, THIS_WEEK, CUSTOM, NONE }

/** Formulario de captura rápida en curso. El identificador es estable para evitar duplicados. */
data class CaptureDraft(
    val id: String,
    val title: String = "",
    val details: String = "",
    val categoryId: String? = null,
    val dateOption: DateOption = DateOption.NONE,
    val customDate: LocalDate? = null,
    val dateIsDeadline: Boolean = false,
    val templateId: String? = null,
)

enum class InboxState { PENDING, POSTPONED, PROCESSED, ARCHIVED }

enum class ProcessedAs { TASK, PROJECT, NOTE }

/** Captura original. Su contenido no cambia aunque después se edite o convierta. */
data class InboxItem(
    val id: String,
    val originalTitle: String,
    val originalDetails: String?,
    val categoryId: String?,
    val dateOption: DateOption,
    val capturedDate: LocalDate?,
    val dateIsDeadline: Boolean,
    val state: InboxState,
    val postponedUntil: LocalDate?,
    val processedAs: ProcessedAs?,
    val processedTargetId: String?,
    val createdAt: Instant,
)

data class InboxEntry(
    val item: InboxItem,
    val taskId: String?,
    val title: String,
    val categoryName: String?,
)

data class CaptureTemplate(
    val id: String,
    val name: String,
    val titlePrefix: String,
    val categoryId: String?,
    val whatToDo: String?,
    val expectedOutcome: String?,
    val nextAction: String?,
    val estimatedMinutes: Int?,
    val sortOrder: Int,
)

data class ProjectSummary(val id: String, val name: String)
