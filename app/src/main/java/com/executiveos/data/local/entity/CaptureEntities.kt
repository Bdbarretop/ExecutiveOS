package com.executiveos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.executiveos.domain.model.DateOption
import com.executiveos.domain.model.InboxState
import com.executiveos.domain.model.ProcessedAs
import com.executiveos.domain.priority.PriorityLevel
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "inbox_items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("category_id"), Index("state")],
)
data class InboxItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "original_title") val originalTitle: String,
    @ColumnInfo(name = "original_details") val originalDetails: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "date_option") val dateOption: DateOption,
    @ColumnInfo(name = "captured_date") val capturedDate: LocalDate?,
    @ColumnInfo(name = "date_is_deadline") val dateIsDeadline: Boolean,
    val state: InboxState,
    @ColumnInfo(name = "postponed_until") val postponedUntil: LocalDate?,
    @ColumnInfo(name = "processed_as") val processedAs: ProcessedAs?,
    @ColumnInfo(name = "processed_target_id") val processedTargetId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

data class InboxItemWithDetails(
    @Embedded val item: InboxItemEntity,
    @Relation(parentColumn = "id", entityColumn = "source_inbox_item_id")
    val tasks: List<TaskEntity>,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity?,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val objective: String?,
    val justification: String?,
    val status: String,
    @ColumnInfo(name = "source_inbox_item_id") val sourceInboxItemId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val owner: String? = null,
    @ColumnInfo(name = "start_date") val startDate: LocalDate? = null,
    @ColumnInfo(name = "target_date") val targetDate: LocalDate? = null,
    val priority: PriorityLevel? = null,
    val notes: String? = null,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String?,
    @ColumnInfo(name = "source_inbox_item_id") val sourceInboxItemId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "capture_templates",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("category_id")],
)
data class CaptureTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "title_prefix") val titlePrefix: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "what_to_do") val whatToDo: String?,
    @ColumnInfo(name = "expected_outcome") val expectedOutcome: String?,
    @ColumnInfo(name = "next_action") val nextAction: String?,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** Sin claves foráneas: el historial debe sobrevivir a lo que registra. */
@Entity(tableName = "audit_log", indices = [Index("entity_id")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val action: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["task_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class TaskTagCrossRef(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)
