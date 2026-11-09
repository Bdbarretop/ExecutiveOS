package com.executiveos.data.repository

import androidx.room.withTransaction
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.LearningGoalEntity
import com.executiveos.data.local.entity.LearningSessionEntity
import com.executiveos.data.local.entity.ReviewEntity
import com.executiveos.domain.learning.LearningGoal
import com.executiveos.domain.learning.LearningGoalStatus
import com.executiveos.domain.learning.LearningSession
import com.executiveos.domain.learning.LearningStats
import com.executiveos.domain.repository.LearningRepository
import com.executiveos.domain.repository.ReviewRepository
import com.executiveos.domain.reviews.Review
import com.executiveos.domain.reviews.ReviewQuestion
import com.executiveos.domain.reviews.ReviewRules
import com.executiveos.domain.reviews.ReviewType
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val ANSWERS_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

class OfflineReviewRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : ReviewRepository {
    private val dao = database.reviewDao()

    override fun observeReview(type: ReviewType, date: LocalDate): Flow<Review?> =
        dao.observe(type, ReviewRules.periodDate(type, date)).map { it?.toDomain() }

    override fun observeHistory(): Flow<List<Review>> =
        dao.observeAll().map { rows -> rows.map(ReviewEntity::toDomain) }

    override suspend fun saveAnswers(
        type: ReviewType,
        date: LocalDate,
        answers: Map<ReviewQuestion, String>,
    ) {
        require(answers.keys.all { it.type == type }) { "Pregunta de otro tipo de revisión" }
        val encoded = Json.encodeToString(
            ANSWERS_SERIALIZER,
            answers.filterValues { it.isNotBlank() }.mapKeys { (question, _) -> question.name },
        )
        val period = ReviewRules.periodDate(type, date)
        database.withTransaction {
            val existing = dao.find(type, period)
            val now = clock.instant()
            if (existing == null) {
                dao.insert(ReviewEntity(UUID.randomUUID().toString(), type, period, encoded, now))
            } else {
                dao.update(existing.copy(answers = encoded, updatedAt = now))
            }
        }
    }
}

class OfflineLearningRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : LearningRepository {
    private val dao = database.learningDao()

    override fun observeGoals(): Flow<List<LearningGoal>> =
        dao.observeGoals().map { rows -> rows.map(LearningGoalEntity::toDomain) }

    override fun observeSessions(): Flow<List<LearningSession>> =
        dao.observeSessions().map { rows -> rows.map(LearningSessionEntity::toDomain) }

    override suspend fun createGoal(title: String, topic: String): String {
        val now = clock.instant()
        val goal = LearningGoal(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            topic = topic.trim(),
            status = LearningGoalStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        require(LearningStats.validate(goal).isEmpty()) { "El objetivo contiene datos no válidos" }
        dao.insertGoal(goal.toEntity())
        return goal.id
    }

    override suspend fun saveGoal(goal: LearningGoal) {
        require(LearningStats.validate(goal).isEmpty()) { "El objetivo contiene datos no válidos" }
        database.withTransaction {
            val current = checkNotNull(dao.findGoal(goal.id)) { "El objetivo ya no existe" }
            dao.updateGoal(
                goal.toEntity().copy(
                    title = goal.title.trim(),
                    topic = goal.topic.trim(),
                    createdAt = current.createdAt,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun addSession(
        goalId: String,
        date: LocalDate,
        minutes: Int,
        notes: String?,
        evaluation: String?,
    ) {
        val session = LearningSession(
            id = UUID.randomUUID().toString(),
            goalId = goalId,
            date = date,
            minutes = minutes,
            notes = notes?.trim()?.ifEmpty { null },
            evaluation = evaluation?.trim()?.ifEmpty { null },
        )
        require(LearningStats.validate(session).isEmpty()) { "La sesión necesita minutos" }
        dao.insertSession(session.toEntity())
    }

    override suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)
}

private fun ReviewEntity.toDomain() = Review(
    id = id,
    type = type,
    periodDate = periodDate,
    answers = Json.decodeFromString(ANSWERS_SERIALIZER, answers)
        .mapNotNull { (name, text) ->
            ReviewQuestion.entries.firstOrNull { it.name == name }?.let { it to text }
        }
        .toMap(),
    updatedAt = updatedAt,
)

private fun LearningGoalEntity.toDomain() = LearningGoal(
    id, title, topic, course, materials, evidence, masteryLevel, targetDate, status, createdAt, updatedAt,
)

private fun LearningGoal.toEntity() = LearningGoalEntity(
    id, title, topic, course, materials, evidence, masteryLevel, targetDate, status, createdAt, updatedAt,
)

private fun LearningSessionEntity.toDomain() =
    LearningSession(id, goalId, date, minutes, notes, evaluation)

private fun LearningSession.toEntity() =
    LearningSessionEntity(id, goalId, date, minutes, notes, evaluation)
