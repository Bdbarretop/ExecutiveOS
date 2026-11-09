package com.executiveos.domain.planning

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

    @Test
    fun aNonWorkingDayHasNoCapacityToExceed() {
        val sunday = monday.minusDays(1)

        val summary = PlanningRules.capacity(
            listOf(block("domingo", 9, 12, day = sunday)),
            sunday,
            sunday,
            CapacitySettings(),
            ZoneOffset.UTC,
        )

        assertEquals(0, summary.availableMinutes)
        assertFalse(summary.isOverloaded)
    }

    @Test
    fun spansCoverTheDayTheWeekFromMondayAndTheWholeMonth() {
        val wednesday = monday.plusDays(2)

        assertEquals(DateSpan(wednesday, wednesday), PlanningRules.span(PlanningPeriod.DAY, wednesday))
        assertEquals(
            DateSpan(monday, monday.plusDays(6)),
            PlanningRules.span(PlanningPeriod.WEEK, wednesday),
        )
        assertEquals(
            DateSpan(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
            PlanningRules.span(PlanningPeriod.MONTH, wednesday),
        )
        assertEquals(7, PlanningRules.span(PlanningPeriod.WEEK, wednesday).days.size)
    }

    @Test
    fun shiftingAdvancesByTheSizeOfThePeriod() {
        assertEquals(monday.plusDays(1), PlanningRules.shift(PlanningPeriod.DAY, monday, 1))
        assertEquals(monday.minusWeeks(1), PlanningRules.shift(PlanningPeriod.WEEK, monday, -1))
        assertEquals(monday.plusMonths(1), PlanningRules.shift(PlanningPeriod.MONTH, monday, 1))
    }

    @Test
    fun movingKeepsTheDurationAndTheLocalTimeAcrossADaylightChange() {
        val zone = ZoneId.of("Europe/Madrid")
        val start = LocalDate.of(2026, 3, 28).atTime(9, 0).atZone(zone).toInstant()
        val original = TimeBlock(
            id = "m",
            type = BlockType.DEEP_WORK,
            title = "m",
            startAt = start,
            endAt = start.plus(Duration.ofMinutes(90)),
        )

        val moved = PlanningRules.move(original, days = 1, minutes = 0, zone = zone)

        assertEquals(LocalTime.of(9, 0), moved.startAt.atZone(zone).toLocalTime())
        assertEquals(LocalDate.of(2026, 3, 29), moved.startAt.atZone(zone).toLocalDate())
        assertEquals(90, moved.plannedMinutes)
    }

    @Test
    fun executionComparesOnlyTheBlocksWithRecordedTime() {
        val result = PlanningRules.execution(
            listOf(block("a", 9, 11, actual = 150), block("b", 14, 15)),
            DateSpan(monday, monday),
            ZoneOffset.UTC,
        ).single()

        assertEquals(BlockType.DEEP_WORK, result.type)
        assertEquals(180, result.plannedMinutes)
        assertEquals(120, result.comparablePlannedMinutes)
        assertEquals(150, result.actualMinutes)
    }

    @Test
    fun conflictsIgnoreTheBlockItselfAndConsecutiveBlocks() {
        val candidate = block("x", 10, 12)
        val others = listOf(block("x", 10, 12), block("antes", 9, 10), block("cruza", 11, 13))

        assertEquals(listOf("cruza"), PlanningRules.conflicts(candidate, others).map { it.id })
    }

    @Test
    fun onlyDaysOverTheirTargetCountAsOverloaded() {
        val blocks = listOf(block("a", 8, 15), block("b", 9, 10, day = monday.plusDays(1)))

        val days = PlanningRules.overloadedDays(
            blocks,
            DateSpan(monday, monday.plusDays(6)),
            CapacitySettings(),
            ZoneOffset.UTC,
        )

        assertEquals(1, days)
    }
}
