package com.executiveos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.CriticalObligation
import com.executiveos.domain.priority.PriorityLevel
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
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("category_id"),
        Index("project_id"),
        Index("source_inbox_item_id"),
        Index("status"),
        Index("parent_task_id"),
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
    @ColumnInfo(name = "parent_task_id") val parentTaskId: String? = null,
    @ColumnInfo(name = "manual_priority") val manualPriority: PriorityLevel? = null,
    @ColumnInfo(name = "manual_priority_reason") val manualPriorityReason: String? = null,
    val obligation: CriticalObligation? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
)

data class TaskWithRelations(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "task_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity?,
    @Relation(parentColumn = "project_id", entityColumn = "id")
    val project: ProjectEntity?,
)

@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["task_id", "depends_on_task_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["depends_on_task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("depends_on_task_id")],
)
data class TaskDependencyEntity(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "depends_on_task_id") val dependsOnTaskId: String,
)

/** Configuración que debe viajar en las copias de seguridad; una sola fila. */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "impact_weight") val impactWeight: Int,
    @ColumnInfo(name = "urgency_weight") val urgencyWeight: Int,
    @ColumnInfo(name = "delay_risk_weight") val delayRiskWeight: Int,
    @ColumnInfo(name = "critical_threshold") val criticalThreshold: Int,
    @ColumnInfo(name = "high_threshold") val highThreshold: Int,
    @ColumnInfo(name = "medium_threshold") val mediumThreshold: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val SINGLETON_ID = "5e9c7d10-0000-4000-8000-000000000001"
    }
}

/** Solo lo necesario para detectar posibles duplicados. */
data class TaskTitleRow(val id: String, val title: String)

data class TaskReferenceRow(val id: String, val title: String, val status: TaskStatus)
