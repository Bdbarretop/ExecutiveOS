package com.executiveos.domain.planning

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class BlockType { DEEP_WORK, COORDINATION, STUDY, PERSONAL }

data class TimeBlock(
    val id: String,
    val type: BlockType,
    val title: String,
    val taskId: String? = null,
    val startAt: Instant,
    val endAt: Instant,
    val actualMinutes: Int? = null,
) {
    val plannedMinutes: Int get() = Duration.between(startAt, endAt).toMinutes().toInt()
}

/** Por defecto solo se planifica el 75 % de lo disponible: siempre hay imprevistos. */
data class CapacitySettings(
    val availableMinutesPerDay: Int = 8 * 60,
    val targetPercent: Int = 75,
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
)

data class CapacitySummary(
    val availableMinutes: Int,
    val targetMinutes: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
) {
    val remainingMinutes: Int get() = (targetMinutes - plannedMinutes).coerceAtLeast(0)
    val isOverloaded: Boolean get() = plannedMinutes > targetMinutes
}

enum class BlockValidationError { BLANK_TITLE, END_NOT_AFTER_START, NEGATIVE_ACTUAL }

object PlanningRules {
    fun validate(block: TimeBlock): List<BlockValidationError> = buildList {
        if (block.title.isBlank()) add(BlockValidationError.BLANK_TITLE)
        if (!block.endAt.isAfter(block.startAt)) add(BlockValidationError.END_NOT_AFTER_START)
        if ((block.actualMinutes ?: 0) < 0) add(BlockValidationError.NEGATIVE_ACTUAL)
    }

    /** Bloques que coinciden con otro; dos consecutivos que solo se tocan no se solapan. */
    fun overlappingIds(blocks: List<TimeBlock>): Set<String> {
        val sorted = blocks.sortedBy { it.startAt }
        val overlapping = mutableSetOf<String>()
        sorted.forEachIndexed { index, block ->
            for (other in sorted.drop(index + 1)) {
                if (!other.startAt.isBefore(block.endAt)) break
                overlapping += block.id
                overlapping += other.id
            }
        }
        return overlapping
    }

    /** El tiempo personal y de recuperación no cuenta como capacidad productiva. */
    fun capacity(
        blocks: List<TimeBlock>,
        from: LocalDate,
        to: LocalDate,
        settings: CapacitySettings,
        zone: ZoneId,
    ): CapacitySummary {
        val workDays = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .count { it.dayOfWeek in settings.workDays }
        val available = workDays * settings.availableMinutesPerDay
        val productive = blocks.filter { block ->
            val day = block.startAt.atZone(zone).toLocalDate()
            block.type != BlockType.PERSONAL && !day.isBefore(from) && !day.isAfter(to)
        }
        return CapacitySummary(
            availableMinutes = available,
            targetMinutes = available * settings.targetPercent / 100,
            plannedMinutes = productive.sumOf { it.plannedMinutes },
            actualMinutes = productive.sumOf { it.actualMinutes ?: 0 },
        )
    }
}
