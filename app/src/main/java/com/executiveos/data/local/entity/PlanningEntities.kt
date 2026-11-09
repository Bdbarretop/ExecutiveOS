package com.executiveos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.projects.MilestoneKind
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "project_milestones",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("project_id")],
)
data class ProjectMilestoneEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val name: String,
    val kind: MilestoneKind,
    val weight: Int,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "project_risks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("project_id")],
)
data class ProjectRiskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val description: String,
    val mitigation: String?,
    val severity: Int,
    @ColumnInfo(name = "is_open") val isOpen: Boolean,
)

@Entity(
    tableName = "time_blocks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("task_id"), Index("start_at")],
)
data class TimeBlockEntity(
    @PrimaryKey val id: String,
    val type: BlockType,
    val title: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "start_at") val startAt: Instant,
    @ColumnInfo(name = "end_at") val endAt: Instant,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "capacity_settings")
data class CapacitySettingsEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "available_minutes_per_day") val availableMinutesPerDay: Int,
    @ColumnInfo(name = "target_percent") val targetPercent: Int,
    @ColumnInfo(name = "work_days") val workDays: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val SINGLETON_ID = "5e9c7d10-0000-4000-8000-000000000003"
    }
}

data class OpenTaskCountRow(
    @ColumnInfo(name = "project_id") val projectId: String,
    val count: Int,
)

@Dao
interface ProjectTrackingDao {
    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: String): Flow<ProjectEntity?>

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("SELECT * FROM project_milestones ORDER BY sort_order")
    fun observeMilestones(): Flow<List<ProjectMilestoneEntity>>

    @Query("SELECT * FROM project_milestones WHERE id = :id")
    suspend fun findMilestone(id: String): ProjectMilestoneEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM project_milestones WHERE project_id = :projectId")
    suspend fun nextMilestoneOrder(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMilestone(milestone: ProjectMilestoneEntity)

    @Update
    suspend fun updateMilestone(milestone: ProjectMilestoneEntity)

    @Query("DELETE FROM project_milestones WHERE id = :id")
    suspend fun deleteMilestone(id: String)

    @Query("SELECT * FROM project_risks")
    fun observeRisks(): Flow<List<ProjectRiskEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRisk(risk: ProjectRiskEntity)

    @Query("UPDATE project_risks SET is_open = :isOpen WHERE id = :id")
    suspend fun setRiskOpen(id: String, isOpen: Boolean)

    @Query(
        "SELECT project_id, COUNT(*) AS count FROM tasks WHERE project_id IS NOT NULL " +
            "AND status IN ('PENDING', 'IN_PROGRESS', 'WAITING', 'BLOCKED') GROUP BY project_id",
    )
    fun observeOpenTaskCounts(): Flow<List<OpenTaskCountRow>>
}

@Dao
interface PlanningDao {
    @Query("SELECT * FROM time_blocks ORDER BY start_at")
    fun observeBlocks(): Flow<List<TimeBlockEntity>>

    @Query("SELECT * FROM time_blocks WHERE id = :id")
    suspend fun findBlock(id: String): TimeBlockEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBlock(block: TimeBlockEntity)

    @Update
    suspend fun updateBlock(block: TimeBlockEntity)

    @Query("DELETE FROM time_blocks WHERE id = :id")
    suspend fun deleteBlock(id: String)

    @Query("SELECT * FROM capacity_settings WHERE id = :id")
    fun observeCapacity(id: String = CapacitySettingsEntity.SINGLETON_ID): Flow<CapacitySettingsEntity?>

    @Upsert
    suspend fun saveCapacity(settings: CapacitySettingsEntity)
}
