package com.executiveos.domain.capture

import com.executiveos.domain.model.DateOption
import com.executiveos.domain.model.Task
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object CaptureDates {
    /** «Esta semana» termina el domingo de la semana en curso. */
    fun resolve(option: DateOption, today: LocalDate, customDate: LocalDate?): LocalDate? =
        when (option) {
            DateOption.TODAY -> today
            DateOption.TOMORROW -> today.plusDays(1)
            DateOption.THIS_WEEK -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            DateOption.CUSTOM -> customDate
            DateOption.NONE -> null
        }
}

data class SimilarTask(val id: String, val title: String)

/** Compara palabras significativas, sin distinguir mayúsculas ni tildes. Solo sugiere. */
object DuplicateDetector {
    private const val SIMILARITY_THRESHOLD = 0.6
    private const val MIN_TOKEN_LENGTH = 3
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
    private val STOP_WORDS = setOf("del", "los", "las", "para", "con", "por", "una", "que")

    fun findSimilar(
        title: String,
        candidates: List<SimilarTask>,
        limit: Int = 3,
    ): List<SimilarTask> {
        val tokens = tokens(title)
        if (tokens.isEmpty()) return emptyList()
        return candidates
            .map { candidate -> candidate to similarity(tokens, tokens(candidate.title)) }
            .filter { (_, score) -> score >= SIMILARITY_THRESHOLD }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (candidate, _) -> candidate }
    }

    private fun tokens(text: String): Set<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .split(SEPARATORS)
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOP_WORDS }
            .toSet()

    private fun similarity(first: Set<String>, second: Set<String>): Double {
        if (second.isEmpty()) return 0.0
        return first.intersect(second).size.toDouble() / first.union(second).size
    }
}

enum class MissingField { WHAT_TO_DO, IMPORTANCE, EXPECTED_OUTCOME, NEXT_ACTION, PRIORITY }

object TaskCompleteness {
    private const val LARGE_TASK_MINUTES = 240

    fun missing(task: Task): List<MissingField> = buildList {
        if (task.whatToDo.isNullOrBlank()) add(MissingField.WHAT_TO_DO)
        if (task.importanceIsEmpty()) add(MissingField.IMPORTANCE)
        if (task.expectedOutcome.isNullOrBlank()) add(MissingField.EXPECTED_OUTCOME)
        if (task.nextAction.isNullOrBlank()) add(MissingField.NEXT_ACTION)
        if (task.impact == null || task.urgency == null || task.delayRisk == null) {
            add(MissingField.PRIORITY)
        }
    }

    fun shouldSuggestSplitting(task: Task): Boolean =
        (task.estimatedMinutes ?: 0) > LARGE_TASK_MINUTES

    private fun Task.importanceIsEmpty(): Boolean = listOf(
        expectedImpact,
        consequenceIfNotDone,
        consequenceIfLate,
        affectedParties,
        relatedRisk,
    ).all { it.isNullOrBlank() }
}

enum class TaskValidationError {
    BLANK_TITLE,
    NEGATIVE_TIME,
    TARGET_BEFORE_START,
    DEADLINE_BEFORE_START,
    TARGET_AFTER_DEADLINE,
}

object TaskValidator {
    fun validate(task: Task): List<TaskValidationError> = buildList {
        if (task.title.isBlank()) add(TaskValidationError.BLANK_TITLE)
        if ((task.estimatedMinutes ?: 0) < 0 || (task.actualMinutes ?: 0) < 0) {
            add(TaskValidationError.NEGATIVE_TIME)
        }
        if (isBefore(task.targetDate, task.startDate)) {
            add(TaskValidationError.TARGET_BEFORE_START)
        }
        if (isBefore(task.deadlineDate, task.startDate)) {
            add(TaskValidationError.DEADLINE_BEFORE_START)
        }
        if (isBefore(task.deadlineDate, task.targetDate)) {
            add(TaskValidationError.TARGET_AFTER_DEADLINE)
        }
    }

    private fun isBefore(date: LocalDate?, reference: LocalDate?): Boolean =
        date != null && reference != null && date.isBefore(reference)
}
