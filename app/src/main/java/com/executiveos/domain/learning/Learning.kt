package com.executiveos.domain.learning

import com.executiveos.domain.planning.DateSpan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class LearningGoalStatus { ACTIVE, PAUSED, COMPLETED }

data class LearningGoal(
    val id: String,
    val title: String,
    /** Texto libre: el catálogo inicial se puede ampliar escribiendo un tema nuevo. */
    val topic: String,
    val course: String? = null,
    val materials: String? = null,
    val evidence: String? = null,
    /** De 1 a 5; `null` mientras la persona no se haya evaluado. */
    val masteryLevel: Int? = null,
    val targetDate: LocalDate? = null,
    val status: LearningGoalStatus = LearningGoalStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LearningSession(
    val id: String,
    val goalId: String,
    val date: LocalDate,
    val minutes: Int,
    val notes: String? = null,
    val evaluation: String? = null,
)

data class TopicMinutes(val topic: String, val minutes: Int)

data class WeekMinutes(val weekStart: LocalDate, val minutes: Int)

enum class LearningValidationError { BLANK_TITLE, BLANK_TOPIC, MASTERY_OUT_OF_RANGE, MINUTES_NOT_POSITIVE }

object LearningCatalog {
    val INITIAL_TOPICS = listOf(
        "Riesgo de crédito",
        "Riesgo de mercado",
        "Riesgo operacional",
        "Riesgo de liquidez",
        "Riesgo de modelo",
        "Riesgo de tasa de interés",
        "Gobierno corporativo",
        "Regulación",
        "Gestión integral de riesgos",
        "Liderazgo",
        "Comunicación ejecutiva",
        "Analítica y tecnología",
    )
}

object LearningStats {
    private const val MAX_MASTERY = 5

    fun validate(goal: LearningGoal): List<LearningValidationError> = buildList {
        if (goal.title.isBlank()) add(LearningValidationError.BLANK_TITLE)
        if (goal.topic.isBlank()) add(LearningValidationError.BLANK_TOPIC)
        if (goal.masteryLevel != null && goal.masteryLevel !in 1..MAX_MASTERY) {
            add(LearningValidationError.MASTERY_OUT_OF_RANGE)
        }
    }

    fun validate(session: LearningSession): List<LearningValidationError> =
        if (session.minutes > 0) emptyList() else listOf(LearningValidationError.MINUTES_NOT_POSITIVE)

    fun minutesIn(sessions: List<LearningSession>, span: DateSpan): Int =
        sessions.filter { it.isWithin(span) }.sumOf { it.minutes }

    /** De mayor a menor dedicación; solo aparecen los temas con tiempo en el periodo. */
    fun byTopic(
        goals: List<LearningGoal>,
        sessions: List<LearningSession>,
        span: DateSpan,
    ): List<TopicMinutes> {
        val topicOf = goals.associate { it.id to it.topic }
        return sessions
            .filter { it.isWithin(span) && it.goalId in topicOf }
            .groupBy { topicOf.getValue(it.goalId) }
            .map { (topic, entries) -> TopicMinutes(topic, entries.sumOf { it.minutes }) }
            .sortedWith(compareBy({ -it.minutes }, { it.topic }))
    }

    fun byWeek(sessions: List<LearningSession>, span: DateSpan): List<WeekMinutes> = sessions
        .filter { it.isWithin(span) }
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (monday, entries) -> WeekMinutes(monday, entries.sumOf { it.minutes }) }
        .sortedBy { it.weekStart }

    private fun LearningSession.isWithin(span: DateSpan): Boolean =
        !date.isBefore(span.from) && !date.isAfter(span.to)
}
