package com.executiveos.domain.planning

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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

    /** Un día no laborable no tiene capacidad que superar. */
    val isOverloaded: Boolean get() = availableMinutes > 0 && plannedMinutes > targetMinutes
}

enum class BlockValidationError { BLANK_TITLE, END_NOT_AFTER_START, NEGATIVE_ACTUAL }

enum class PlanningPeriod { DAY, WEEK, MONTH }

data class DateSpan(val from: LocalDate, val to: LocalDate) {
    val days: List<LocalDate>
        get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
}

data class ExecutionByType(
    val type: BlockType,
    val plannedMinutes: Int,
    /** Solo los bloques con tiempo real: lo que aún no se ha registrado no es una desviación. */
    val comparablePlannedMinutes: Int,
    val actualMinutes: Int,
)

object PlanningRules {
    fun span(period: PlanningPeriod, anchor: LocalDate): DateSpan = when (period) {
        PlanningPeriod.DAY -> DateSpan(anchor, anchor)
        PlanningPeriod.WEEK -> DateSpan(
            anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
        )
        PlanningPeriod.MONTH -> DateSpan(
            anchor.with(TemporalAdjusters.firstDayOfMonth()),
            anchor.with(TemporalAdjusters.lastDayOfMonth()),
        )
    }

    fun shift(period: PlanningPeriod, anchor: LocalDate, steps: Long): LocalDate = when (period) {
        PlanningPeriod.DAY -> anchor.plusDays(steps)
        PlanningPeriod.WEEK -> anchor.plusWeeks(steps)
        PlanningPeriod.MONTH -> anchor.plusMonths(steps)
    }

    /** Otros bloques que coinciden con [block]; dos consecutivos que solo se tocan no chocan. */
    fun conflicts(block: TimeBlock, others: List<TimeBlock>): List<TimeBlock> = others
        .filter { it.id != block.id && it.startAt.isBefore(block.endAt) && block.startAt.isBefore(it.endAt) }
        .sortedBy { it.startAt }

    fun onDay(blocks: List<TimeBlock>, day: LocalDate, zone: ZoneId): List<TimeBlock> =
        blocks.filter { it.startAt.atZone(zone).toLocalDate() == day }.sortedBy { it.startAt }

    /** Conserva la duración y, al cambiar de día, también la hora local. */
    fun move(block: TimeBlock, days: Long, minutes: Long, zone: ZoneId): TimeBlock {
        val start = block.startAt.atZone(zone).plusDays(days).plusMinutes(minutes).toInstant()
        return block.copy(
            startAt = start,
            endAt = start.plus(Duration.between(block.startAt, block.endAt)),
        )
    }

    fun execution(blocks: List<TimeBlock>, span: DateSpan, zone: ZoneId): List<ExecutionByType> {
        val inSpan = blocks.filter { it.startsWithin(span.from, span.to, zone) }
        return BlockType.entries.mapNotNull { type ->
            val ofType = inSpan.filter { it.type == type }
            val recorded = ofType.filter { it.actualMinutes != null }
            ExecutionByType(
                type = type,
                plannedMinutes = ofType.sumOf { it.plannedMinutes },
                comparablePlannedMinutes = recorded.sumOf { it.plannedMinutes },
                actualMinutes = recorded.sumOf { it.actualMinutes ?: 0 },
            ).takeIf { ofType.isNotEmpty() }
        }
    }

    fun overloadedDays(
        blocks: List<TimeBlock>,
        span: DateSpan,
        settings: CapacitySettings,
        zone: ZoneId,
    ): Int = span.days.count { day -> capacity(blocks, day, day, settings, zone).isOverloaded }

    private fun TimeBlock.startsWithin(from: LocalDate, to: LocalDate, zone: ZoneId): Boolean {
        val day = startAt.atZone(zone).toLocalDate()
        return !day.isBefore(from) && !day.isAfter(to)
    }

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
            block.type != BlockType.PERSONAL && block.startsWithin(from, to, zone)
        }
        return CapacitySummary(
            availableMinutes = available,
            targetMinutes = available * settings.targetPercent / 100,
            plannedMinutes = productive.sumOf { it.plannedMinutes },
            actualMinutes = productive.sumOf { it.actualMinutes ?: 0 },
        )
    }
}
