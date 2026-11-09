package com.executiveos.domain.priority

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityEngineTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val settings = PrioritySettings()

    private fun task(impact: Int? = null, urgency: Int? = null, delay: Int? = null) = Task(
        id = "t",
        status = TaskStatus.PENDING,
        sourceInboxItemId = null,
        title = "Tarea",
        impact = impact,
        urgency = urgency,
        delayRisk = delay,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun assess(task: Task, openDependencies: Int = 0) =
        PriorityEngine.assess(task, settings, today, openDependencies)

    @Test
    fun appliesTheBriefFormula() {
        // 5 x 0,40 + 3 x 0,25 + 4 x 0,35 = 4,15
        assertEquals(415, assess(task(5, 3, 4)).score)
    }

    @Test
    fun exactBoundariesFallInTheHigherBand() {
        assertEquals(PriorityLevel.CRITICAL, assess(task(4, 4, 4)).calculatedLevel)
        assertEquals(PriorityLevel.HIGH, assess(task(3, 3, 3)).calculatedLevel)
        assertEquals(PriorityLevel.MEDIUM, assess(task(2, 2, 2)).calculatedLevel)
        assertEquals(PriorityLevel.LOW, assess(task(1, 1, 1)).calculatedLevel)
    }

    @Test
    fun justBelowABoundaryStaysInTheLowerBand() {
        // 4 x 0,40 + 3 x 0,25 + 4 x 0,35 = 3,75
        assertEquals(375, assess(task(4, 3, 4)).score)
        assertEquals(PriorityLevel.HIGH, assess(task(4, 3, 4)).calculatedLevel)
    }

    @Test
    fun missingScalesAreNeverAssumedAndLeaveThePriorityPending() {
        val result = assess(task(impact = 5))

        assertNull(result.score)
        assertNull(result.effectiveLevel)
        assertEquals(listOf(PriorityScale.URGENCY, PriorityScale.DELAY_RISK), result.missingScales)
    }

    @Test
    fun contributionsExplainHowTheScoreWasObtained() {
        val contributions = assess(task(5, 3, 4)).contributions

        assertEquals(listOf(200, 75, 140), contributions.map { it.points })
    }

    @Test
    fun weightsAndThresholdsAreConfigurable() {
        val custom = PrioritySettings(
            impactWeight = 100,
            urgencyWeight = 0,
            delayRiskWeight = 0,
            criticalThreshold = 450,
            highThreshold = 350,
            mediumThreshold = 250,
        )

        val result = PriorityEngine.assess(task(4, 1, 1), custom, today)

        assertEquals(400, result.score)
        assertEquals(PriorityLevel.HIGH, result.calculatedLevel)
    }

    @Test
    fun manualPriorityOverridesButKeepsTheCalculatedLevelVisible() {
        val manual = task(1, 1, 1).copy(
            manualPriority = PriorityLevel.CRITICAL,
            manualPriorityReason = "Lo pidió la junta",
        )

        val result = assess(manual)

        assertEquals(PriorityLevel.CRITICAL, result.effectiveLevel)
        assertEquals(PriorityLevel.LOW, result.calculatedLevel)
        assertEquals("Lo pidió la junta", result.manualReason)
    }

    @Test
    fun obligationIsFlaggedWithoutAlteringTheScore() {
        val result = assess(task(1, 1, 1).copy(obligation = CriticalObligation.REGULATORY))

        assertEquals(100, result.score)
        assertEquals(PriorityLevel.LOW, result.calculatedLevel)
        assertTrue(PriorityReason.CRITICAL_OBLIGATION in result.reasons)
    }

    @Test
    fun aNearDateExplainsButNeverRaisesTheScore() {
        val relaxed = assess(task(2, 2, 2))
        val overdue = assess(task(2, 2, 2).copy(deadlineDate = today.minusDays(5)))

        assertEquals(relaxed.score, overdue.score)
        assertTrue(PriorityReason.OVERDUE in overdue.reasons)
    }

    @Test
    fun deadlineTakesPrecedenceOverTargetDate() {
        val task = task().copy(targetDate = today.plusDays(30), deadlineDate = today)

        assertEquals(DateSignal.DUE_TODAY, PriorityEngine.dateSignal(task, today))
    }

    @Test
    fun dateSignalsCoverEveryCase() {
        assertEquals(DateSignal.NONE, PriorityEngine.dateSignal(task(), today))
        assertEquals(DateSignal.OVERDUE, signalIn(-1))
        assertEquals(DateSignal.DUE_TODAY, signalIn(0))
        assertEquals(DateSignal.DUE_SOON, signalIn(7))
        assertEquals(DateSignal.FUTURE, signalIn(8))
    }

    private fun signalIn(days: Long) =
        PriorityEngine.dateSignal(task().copy(targetDate = today.plusDays(days)), today)

    @Test
    fun pendingDependenciesAreExplained() {
        assertTrue(PriorityReason.PENDING_DEPENDENCIES in assess(task(), openDependencies = 2).reasons)
        assertFalse(PriorityReason.PENDING_DEPENDENCIES in assess(task()).reasons)
    }

    @Test
    fun settingsRejectWeightsThatDoNotAddUpOrUnorderedThresholds() {
        assertTrue(PrioritySettings().isValid)
        assertFalse(PrioritySettings(impactWeight = 50).isValid)
        assertFalse(PrioritySettings(highThreshold = 450).isValid)
        assertFalse(PrioritySettings(mediumThreshold = 50).isValid)
    }
}
