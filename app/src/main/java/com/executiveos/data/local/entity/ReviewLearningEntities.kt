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
import com.executiveos.domain.learning.LearningGoalStatus
import com.executiveos.domain.reviews.ReviewType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Las respuestas van en JSON: se guardan juntas y una pregunta retirada no rompe las antiguas. */
@Entity(
    tableName = "reviews",
    indices = [Index(value = ["type", "period_date"], unique = true)],
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val type: ReviewType,
    @ColumnInfo(name = "period_date") val periodDate: LocalDate,
    val answers: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "learning_goals", indices = [Index("topic")])
data class LearningGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topic: String,
    val course: String?,
    val materials: String?,
    val evidence: String?,
    @ColumnInfo(name = "mastery_level") val masteryLevel: Int?,
    @ColumnInfo(name = "target_date") val targetDate: LocalDate?,
    val status: LearningGoalStatus,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "learning_sessions",
    foreignKeys = [
        ForeignKey(
            entity = LearningGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goal_id"), Index("date")],
)
data class LearningSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val date: LocalDate,
    val minutes: Int,
    val notes: String?,
    val evaluation: String?,
)

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE type = :type AND period_date = :periodDate")
    fun observe(type: ReviewType, periodDate: LocalDate): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews WHERE type = :type AND period_date = :periodDate")
    suspend fun find(type: ReviewType, periodDate: LocalDate): ReviewEntity?

    @Query("SELECT * FROM reviews ORDER BY period_date DESC, type")
    fun observeAll(): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: ReviewEntity)

    @Update
    suspend fun update(review: ReviewEntity)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM learning_goals ORDER BY status, target_date IS NULL, target_date, title")
    fun observeGoals(): Flow<List<LearningGoalEntity>>

    @Query("SELECT * FROM learning_sessions ORDER BY date DESC")
    fun observeSessions(): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_goals WHERE id = :id")
    suspend fun findGoal(id: String): LearningGoalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: LearningGoalEntity)

    @Update
    suspend fun updateGoal(goal: LearningGoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LearningSessionEntity)

    @Query("DELETE FROM learning_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
