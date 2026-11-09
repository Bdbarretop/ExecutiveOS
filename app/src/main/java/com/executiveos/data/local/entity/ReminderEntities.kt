package com.executiveos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.executiveos.domain.reminders.ReminderType
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class ReminderStatus { SCHEDULED, DELIVERED, CANCELLED }

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("task_id"), Index(value = ["dedupe_key"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    val type: ReminderType,
    @ColumnInfo(name = "trigger_at") val triggerAt: Instant,
    val status: ReminderStatus,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "reminder_settings")
data class ReminderSettingsEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "deadline_enabled") val deadlineEnabled: Boolean,
    @ColumnInfo(name = "deadline_days_before") val deadlineDaysBefore: Int,
    @ColumnInfo(name = "critical_enabled") val criticalEnabled: Boolean,
    @ColumnInfo(name = "morning_minute") val morningMinute: Int,
    @ColumnInfo(name = "daily_review_enabled") val dailyReviewEnabled: Boolean,
    @ColumnInfo(name = "daily_review_minute") val dailyReviewMinute: Int,
    @ColumnInfo(name = "weekly_review_enabled") val weeklyReviewEnabled: Boolean,
    @ColumnInfo(name = "weekly_review_day") val weeklyReviewDay: DayOfWeek,
    @ColumnInfo(name = "weekly_review_minute") val weeklyReviewMinute: Int,
    @ColumnInfo(name = "quiet_enabled") val quietEnabled: Boolean,
    @ColumnInfo(name = "quiet_start_minute") val quietStartMinute: Int,
    @ColumnInfo(name = "quiet_end_minute") val quietEndMinute: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val SINGLETON_ID = "5e9c7d10-0000-4000-8000-000000000002"
    }
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(reminder: ReminderEntity): Long

    @Query(
        "SELECT * FROM reminders WHERE task_id = :taskId AND type = 'TASK' " +
            "AND status = 'SCHEDULED' LIMIT 1",
    )
    fun observeActive(taskId: String): Flow<ReminderEntity?>

    @Query(
        "SELECT * FROM reminders WHERE task_id = :taskId AND type = 'TASK' " +
            "AND status = 'SCHEDULED' LIMIT 1",
    )
    suspend fun findActive(taskId: String): ReminderEntity?

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id AND status = 'SCHEDULED'")
    suspend fun findScheduled(id: String): ReminderEntity?

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReminderStatus)

    @Query("DELETE FROM reminders WHERE status != 'SCHEDULED' AND trigger_at < :before")
    suspend fun deleteFinishedBefore(before: Instant)

    @Query("SELECT * FROM reminder_settings WHERE id = :id")
    fun observeSettings(id: String = ReminderSettingsEntity.SINGLETON_ID): Flow<ReminderSettingsEntity?>

    @Upsert
    suspend fun saveSettings(settings: ReminderSettingsEntity)
}
