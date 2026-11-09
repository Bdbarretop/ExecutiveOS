package com.executiveos.domain.planning

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningRulesTest {
    /** Lunes. */
    private val monday = LocalDate.of(2026, 11, 9)

    private fun at(hour: Int, day: LocalDate = monday): Instant =
        day.atTime(hour, 0).toInstant(ZoneOffset.UTC)

    private fun block(
        id: String,
        start: Int,
        end: Int,
        type: BlockType = BlockType.DEEP_WORK,
        day: LocalDate = monday,
        actual: Int? = null,
    ) = TimeBlock(id, type, id, startAt = at(start, day), endAt = at(end, day), actualMinutes = actual)

    @Test
    fun aBlockMustEndAfterItStartsAndHaveATitle() {
        val invalid = block("x", 10, 10).copy(title = " ", actualMinutes = -1)

        assertEquals(
            listOf(
                BlockValidationError.BLANK_TITLE,
                BlockValidationError.END_NOT_AFTER_START,
                BlockValidationError.NEGATIVE_ACTUAL,
            ),
            PlanningRules.validate(invalid),
        )
        assertTrue(PlanningRules.validate(block("ok", 9, 10)).isEmpty())
    }

    @Test
    fun overlappingBlocksAreDetected() {
        val ids = PlanningRules.overlappingIds(
            listOf(block("a", 9, 11), block("b", 10, 12), block("c", 14, 15)),
        )

        assertEquals(setOf("a", "b"), ids)
    }

    @Test
    fun consecutiveBlocksDoNotOverlap() {
        assertTrue(PlanningRules.overlappingIds(listOf(block("a", 9, 10), block("b", 10, 11))).isEmpty())
    }

    @Test
    fun aLongBlockOverlapsEveryBlockInsideIt() {
        val ids = PlanningRules.overlappingIds(
            listOf(block("largo", 9, 17), block("a", 10, 11), block("b", 15, 16)),
        )

        assertEquals(setOf("largo", "a", "b"), ids)
    }

    private fun capacity(vararg blocks: TimeBlock, to: LocalDate = monday.plusDays(6)) =
        PlanningRules.capacity(blocks.toList(), monday, to, CapacitySettings(), ZoneOffset.UTC)

    @Test
    fun onlySeventyFivePercentOfWorkingTimeIsPlannable() {
        val summary = capacity()

        assertEquals(5 * 480, summary.availableMinutes)
        assertEquals(1_800, summary.targetMinutes)
    }

    @Test
    fun personalTimeIsNotProductiveCapacity() {
        val summary = capacity(block("gym", 7, 8, BlockType.PERSONAL), block("foco", 9, 11))

        assertEquals(120, summary.plannedMinutes)
    }

    @Test
    fun exceedingTheTargetFlagsOverload() {
        val day = capacity(block("largo", 8, 15), to = monday)

        assertEquals(360, day.targetMinutes)
        assertEquals(420, day.plannedMinutes)
        assertTrue(day.isOverloaded)
        assertEquals(0, day.remainingMinutes)
        assertFalse(capacity(block("corto", 9, 10), to = monday).isOverloaded)
    }

    @Test
    fun blocksOutsideTheRangeAreIgnoredAndActualTimeIsAdded() {
        val summary = capacity(
            block("dentro", 9, 10, actual = 75),
            block("fuera", 9, 10, day = monday.plusDays(7), actual = 500),
        )

        assertEquals(60, summary.plannedMinutes)
        assertEquals(75, summary.actualMinutes)
    }
}
