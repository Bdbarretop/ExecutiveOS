package com.executiveos.domain

import com.executiveos.domain.dashboard.DashboardCalculator
import com.executiveos.domain.metrics.MetricPeriod
import com.executiveos.domain.metrics.MetricsCalculator
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.domain.tasks.TaskQueries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El límite es holgado: detecta un coste descontrolado, no mide milisegundos. */
class LargeDataTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun items(count: Int) = (1..count).map { index ->
        val status = if (index % 10 == 0) TaskStatus.COMPLETED else TaskStatus.PENDING
        TaskListItem(
            task = Task(
                id = "t$index",
                status = status,
                sourceInboxItemId = null,
                title = "Tarea $index",
                impact = index % 5 + 1,
                urgency = index % 3 + 1,
                delayRisk = index % 4 + 1,
                targetDate = today.plusDays((index % 30 - 15).toLong()),
                estimatedMinutes = index % 90,
                completedAt = Instant.parse("2026-11-10T12:00:00Z").takeIf { status == TaskStatus.COMPLETED },
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
            categoryName = "Trabajo",
            projectName = null,
            parentTitle = null,
            openDependencies = 0,
            openSubtasks = 0,
        )
    }

    private fun assertHandles(count: Int) {
        val all = items(count)
        var open = 0
        val elapsed = measureTimeMillis {
            val ranked = TaskQueries.rank(all, PrioritySettings(), today)
            open = DashboardCalculator.build(ranked, null, today, ZoneOffset.UTC).indicators.pending
            MetricsCalculator.calculate(
                ranked,
                emptyList(),
                emptyList(),
                emptyList(),
                MetricsCalculator.span(MetricPeriod.QUARTER, today),
                null,
                today,
                ZoneOffset.UTC,
            )
        }

        assertEquals(count - count / 10, open)
        assertTrue("Tardó $elapsed ms con $count tareas", elapsed < MAX_MILLIS)
    }

    @Test
    fun fiveHundredTasksAreProcessedQuickly() = assertHandles(500)

    @Test
    fun aThousandTasksAreProcessedQuickly() = assertHandles(1_000)

    private companion object {
        const val MAX_MILLIS = 3_000L
    }
}
