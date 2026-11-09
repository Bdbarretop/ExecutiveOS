package com.executiveos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = InboxItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_inbox_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("category_id"),
        Index("project_id"),
        Index("source_inbox_item_id"),
        Index("status"),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val status: TaskStatus,
    @ColumnInfo(name = "source_inbox_item_id") val sourceInboxItemId: String?,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "what_to_do") val whatToDo: String?,
    @ColumnInfo(name = "why_needed") val whyNeeded: String?,
    @ColumnInfo(name = "requested_by") val requestedBy: String?,
    val origin: String?,
    @ColumnInfo(name = "context_to_remember") val contextToRemember: String?,
    @ColumnInfo(name = "required_documents") val requiredDocuments: String?,
    @ColumnInfo(name = "expected_impact") val expectedImpact: String?,
    @ColumnInfo(name = "consequence_if_not_done") val consequenceIfNotDone: String?,
    @ColumnInfo(name = "consequence_if_late") val consequenceIfLate: String?,
    @ColumnInfo(name = "affected_parties") val affectedParties: String?,
    @ColumnInfo(name = "related_risk") val relatedRisk: String?,
    @ColumnInfo(name = "expected_outcome") val expectedOutcome: String?,
    @ColumnInfo(name = "next_action") val nextAction: String?,
    val impact: Int?,
    val urgency: Int?,
    @ColumnInfo(name = "delay_risk") val delayRisk: Int?,
    @ColumnInfo(name = "start_date") val startDate: LocalDate?,
    @ColumnInfo(name = "target_date") val targetDate: LocalDate?,
    @ColumnInfo(name = "deadline_date") val deadlineDate: LocalDate?,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int?,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int?,
    val owner: String?,
    @ColumnInfo(name = "involved_people") val involvedPeople: String?,
    @ColumnInfo(name = "dependencies_note") val dependenciesNote: String?,
    @ColumnInfo(name = "delegation_status") val delegationStatus: DelegationStatus,
    val notes: String?,
    val links: String?,
    val references: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** Solo lo necesario para detectar posibles duplicados. */
data class TaskTitleRow(val id: String, val title: String)
