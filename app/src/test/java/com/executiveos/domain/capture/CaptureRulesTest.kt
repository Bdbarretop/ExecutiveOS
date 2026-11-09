package com.executiveos.domain.capture

import com.executiveos.domain.model.DateOption
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRulesTest {
    private val wednesday = LocalDate.of(2026, 11, 11)
    private val sunday = LocalDate.of(2026, 11, 15)

    private fun task(title: String = "Preparar informe") = Task(
        id = "t",
        status = TaskStatus.INBOX,
        sourceInboxItemId = null,
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun dateOptionsResolveAgainstToday() {
        assertEquals(wednesday, CaptureDates.resolve(DateOption.TODAY, wednesday, null))
        assertEquals(wednesday.plusDays(1), CaptureDates.resolve(DateOption.TOMORROW, wednesday, null))
        assertEquals(sunday, CaptureDates.resolve(DateOption.CUSTOM, wednesday, sunday))
    }

    @Test
    fun noDefinedDateNeverInventsOne() {
        assertNull(CaptureDates.resolve(DateOption.NONE, wednesday, sunday))
        assertNull(CaptureDates.resolve(DateOption.CUSTOM, wednesday, null))
    }

    @Test
    fun thisWeekEndsOnSundayIncludingWhenTodayIsSunday() {
        assertEquals(sunday, CaptureDates.resolve(DateOption.THIS_WEEK, wednesday, null))
        assertEquals(sunday, CaptureDates.resolve(DateOption.THIS_WEEK, sunday, null))
    }

    @Test
    fun duplicatesIgnoreCaseAccentsAndFillerWords() {
        val candidates = listOf(
            SimilarTask("1", "Preparar INFORME de Riesgos"),
            SimilarTask("2", "Comprar café"),
        )

        val similar = DuplicateDetector.findSimilar("preparar informe riésgos", candidates)

        assertEquals(listOf("1"), similar.map { it.id })
    }

    @Test
    fun unrelatedOrEmptyTitlesProduceNoSuggestions() {
        val candidates = listOf(SimilarTask("1", "Revisar propuesta del proveedor"))

        assertTrue(DuplicateDetector.findSimilar("Estudiar riesgo de mercado", candidates).isEmpty())
        assertTrue(DuplicateDetector.findSimilar("   ", candidates).isEmpty())
    }

    @Test
    fun emptyTaskReportsEveryKeyFieldAsMissing() {
        assertEquals(MissingField.entries.toList(), TaskCompleteness.missing(task()))
    }

    @Test
    fun priorityIsMissingUntilAllThreeScalesAreSet() {
        val partial = task().copy(impact = 5, urgency = 3)
        val complete = partial.copy(delayRisk = 1)

        assertTrue(MissingField.PRIORITY in TaskCompleteness.missing(partial))
        assertFalse(MissingField.PRIORITY in TaskCompleteness.missing(complete))
    }

    @Test
    fun onlyLargeEstimatesSuggestSplitting() {
        assertFalse(TaskCompleteness.shouldSuggestSplitting(task().copy(estimatedMinutes = 240)))
        assertTrue(TaskCompleteness.shouldSuggestSplitting(task().copy(estimatedMinutes = 241)))
        assertFalse(TaskCompleteness.shouldSuggestSplitting(task()))
    }

    @Test
    fun validationRejectsBlankTitleAndNegativeTime() {
        val errors = TaskValidator.validate(task(title = "  ").copy(actualMinutes = -1))

        assertEquals(
            listOf(TaskValidationError.BLANK_TITLE, TaskValidationError.NEGATIVE_TIME),
            errors,
        )
    }

    @Test
    fun validationEnforcesDateOrder() {
        val invalid = task().copy(
            startDate = sunday,
            targetDate = wednesday,
            deadlineDate = wednesday.minusDays(1),
        )

        assertEquals(
            listOf(
                TaskValidationError.TARGET_BEFORE_START,
                TaskValidationError.DEADLINE_BEFORE_START,
                TaskValidationError.TARGET_AFTER_DEADLINE,
            ),
            TaskValidator.validate(invalid),
        )
    }

    @Test
    fun taskWithOnlyATitleIsValid() {
        assertTrue(TaskValidator.validate(task()).isEmpty())
    }
}
