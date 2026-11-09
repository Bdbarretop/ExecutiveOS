package com.executiveos.domain.model

import java.time.Instant
import java.time.LocalDate

enum class TaskStatus {
    INBOX,
    PENDING,
    IN_PROGRESS,
    WAITING,
    BLOCKED,
    COMPLETED,
    CANCELLED,
    ARCHIVED,
}

enum class DelegationStatus { NOT_DELEGATED, DELEGATED, FOLLOWING_UP }

/** Ficha completa. Salvo el título, ningún campo es obligatorio y nunca se inventan valores. */
data class Task(
    val id: String,
    val status: TaskStatus,
    val sourceInboxItemId: String?,
    val title: String,
    val description: String? = null,
    val categoryId: String? = null,
    val projectId: String? = null,
    val tags: List<String> = emptyList(),
    val whatToDo: String? = null,
    val whyNeeded: String? = null,
    val requestedBy: String? = null,
    val origin: String? = null,
    val contextToRemember: String? = null,
    val requiredDocuments: String? = null,
    val expectedImpact: String? = null,
    val consequenceIfNotDone: String? = null,
    val consequenceIfLate: String? = null,
    val affectedParties: String? = null,
    val relatedRisk: String? = null,
    val expectedOutcome: String? = null,
    val nextAction: String? = null,
    val impact: Int? = null,
    val urgency: Int? = null,
    val delayRisk: Int? = null,
    val startDate: LocalDate? = null,
    val targetDate: LocalDate? = null,
    val deadlineDate: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val owner: String? = null,
    val involvedPeople: String? = null,
    val dependenciesNote: String? = null,
    val delegationStatus: DelegationStatus = DelegationStatus.NOT_DELEGATED,
    val notes: String? = null,
    val links: String? = null,
    val references: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
