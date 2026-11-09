package com.executiveos.domain.dashboard

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.CriticalObligation
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.domain.tasks.TaskQueries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCalculatorTest {
    /** Miércoles: la semana va del lunes 9 al domingo 15. */
    private val today = LocalDate.of(2026, 11, 11)

    private fun task(id: String, status: TaskStatus = TaskStatus.PENDING) = Task(
        id = id,
        status = status,
        sourceInboxItemId = null,
        title = id,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun critical(id: String) = task(id).copy(impact = 5, urgency = 5, delayRisk = 5)

    private fun high(id: String) = task(id).copy(impact = 3, urgency = 3, delayRisk = 3)

    private fun low(id: String) = task(id).copy(impact = 1, urgency = 1, delayRisk = 1)

    private fun build(
        tasks: List<Task>,
        focus: DailyFocus? = null,
        dependencies: Map<String, Int> = emptyMap(),
    ): Dashboard {
        val items = tasks.map { task ->
            TaskListItem(task, null, null, null, dependencies[task.id] ?: 0, 0)
        }
        val ranked = TaskQueries.rank(items, PrioritySettings(), today)
        return DashboardCalculator.build(ranked, focus, today, ZoneOffset.UTC)
    }

    private fun Dashboard.focusIds() = focus.map { it.ranked.item.task.id }

    @Test
    fun recommendsThreeByPriorityNotByCreationOrder() {
        val dashboard = build(listOf(low("baja"), high("alta"), critical("critica"), low("otra")))

        assertEquals(listOf("critica", "alta", "baja"), dashboard.focusIds())
    }

    @Test
    fun doesNotForceThreeWhenThereAreFewerTasks() {
        assertEquals(listOf("unica"), build(listOf(high("unica"))).focusIds())
        assertTrue(build(emptyList()).focus.isEmpty())
    }

    @Test
    fun anEmergencyIsAddedEvenBeyondThree() {
        val tasks = listOf(
            critical("a").copy(deadlineDate = today),
            critical("b").copy(deadlineDate = today),
            critical("c").copy(deadlineDate = today),
            critical("d").copy(deadlineDate = today),
            high("normal"),
        )

        val dashboard = build(tasks)

        assertEquals(listOf("a", "b", "c", "d"), dashboard.focusIds())
        assertTrue(dashboard.focus.all { it.isEmergency })
    }

    @Test
    fun aNearDateDoesNotBeatHigherImportance() {
        val dashboard = build(
            listOf(low("hoy").copy(targetDate = today), critical("sinfecha"), high("alta"), low("z")),
        )

        assertEquals("sinfecha", dashboard.focusIds().first())
        assertEquals("alta", dashboard.focusIds()[1])
    }

    @Test
    fun blockedWaitingAndDependentTasksAreNotRecommended() {
        val dashboard = build(
            listOf(
                critical("bloqueada").copy(status = TaskStatus.BLOCKED),
                critical("espera").copy(status = TaskStatus.WAITING),
                critical("depende"),
                low("libre"),
            ),
            dependencies = mapOf("depende" to 1),
        )

        assertEquals(listOf("libre"), dashboard.focusIds())
    }

    @Test
    fun anObligationIsRecommendedEvenIfBlocked() {
        val dashboard = build(
            listOf(
                low("regulatoria").copy(
                    status = TaskStatus.BLOCKED,
                    obligation = CriticalObligation.REGULATORY,
                    deadlineDate = today.plusDays(2),
                ),
            ),
        )

        assertEquals(listOf("regulatoria"), dashboard.focusIds())
        assertTrue(dashboard.focus.single().isEmergency)
    }

    @Test
    fun theUserCanPinAndRemoveTasksForToday() {
        val tasks = listOf(critical("critica"), high("alta"), low("baja"), low("elegida"))

        val dashboard = build(tasks, DailyFocus(today, setOf("elegida"), setOf("critica")))

        assertEquals("elegida", dashboard.focusIds().first())
        assertFalse("critica" in dashboard.focusIds())
        assertTrue(dashboard.focus.first().isPinned)
        assertTrue(dashboard.isFocusAdjusted)
    }

    @Test
    fun adjustmentsFromAnotherDayAreIgnored() {
        val dashboard = build(
            listOf(critical("critica")),
            DailyFocus(today.minusDays(1), dismissedIds = setOf("critica")),
        )

        assertEquals(listOf("critica"), dashboard.focusIds())
        assertFalse(dashboard.isFocusAdjusted)
    }

    @Test
    fun candidatesExcludeWhatIsAlreadySelected() {
        val dashboard = build(listOf(critical("a"), critical("b"), critical("c"), low("d")))

        assertEquals(listOf("d"), dashboard.focusCandidates.map { it.item.task.id })
    }

    @Test
    fun indicatorsCountRealTasks() {
        val indicators = build(
            listOf(
                task("vencida").copy(deadlineDate = today.minusDays(1)),
                critical("critica"),
                high("alta"),
                task("progreso", TaskStatus.IN_PROGRESS),
                task("bloqueada", TaskStatus.BLOCKED),
                task("hecha", TaskStatus.COMPLETED),
            ),
        ).indicators

        assertEquals(5, indicators.pending)
        assertEquals(1, indicators.overdue)
        assertEquals(1, indicators.critical)
        assertEquals(1, indicators.high)
        assertEquals(1, indicators.inProgress)
        assertEquals(1, indicators.blocked)
    }

    @Test
    fun completedCountsOnlyTheCurrentMondayToSundayWeek() {
        fun doneOn(id: String, date: LocalDate) = task(id, TaskStatus.COMPLETED)
            .copy(completedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC))

        val indicators = build(
            listOf(
                doneOn("lunes", LocalDate.of(2026, 11, 9)),
                doneOn("domingo", LocalDate.of(2026, 11, 15)),
                doneOn("anterior", LocalDate.of(2026, 11, 8)),
            ),
        ).indicators

        assertEquals(2, indicators.completedThisWeek)
    }

    @Test
    fun estimatedTimeSumsOpenTasksDatedUntilSunday() {
        val indicators = build(
            listOf(
                task("hoy").copy(targetDate = today, estimatedMinutes = 60),
                task("domingo").copy(deadlineDate = LocalDate.of(2026, 11, 15), estimatedMinutes = 30),
                task("siguiente").copy(targetDate = LocalDate.of(2026, 11, 16), estimatedMinutes = 500),
                task("sinfecha").copy(estimatedMinutes = 500),
                task("hecha", TaskStatus.COMPLETED).copy(targetDate = today, estimatedMinutes = 500),
            ),
        ).indicators

        assertEquals(90, indicators.estimatedMinutesThisWeek)
    }

    @Test
    fun noAlertsAreGeneratedWithoutCases() {
        assertTrue(build(listOf(task("tranquila"))).alerts.isEmpty())
    }

    @Test
    fun aMissedTargetDateIsNotACommitmentAlert() {
        val alerts = build(listOf(task("objetivo").copy(targetDate = today.minusDays(2)))).alerts

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun alertsReportEachKindOfProblem() {
        val alerts = build(
            listOf(
                task("vencida").copy(deadlineDate = today.minusDays(1), nextAction = "Llamar"),
                task("proxima").copy(deadlineDate = today.plusDays(7), nextAction = "Llamar"),
                task("bloqueada", TaskStatus.BLOCKED),
                task("depende"),
                critical("sinaccion"),
            ),
            dependencies = mapOf("depende" to 2),
        ).alerts.associate { it.type to it.count }

        assertEquals(1, alerts[AlertType.OVERDUE_COMMITMENTS])
        assertEquals(1, alerts[AlertType.UPCOMING_COMMITMENTS])
        assertEquals(1, alerts[AlertType.BLOCKED_TASKS])
        assertEquals(1, alerts[AlertType.PENDING_DEPENDENCIES])
        assertEquals(1, alerts[AlertType.CRITICAL_WITHOUT_NEXT_ACTION])
    }

    @Test
    fun nextHoursCoverTodayAndTomorrowInOrder() {
        val dashboard = build(
            listOf(
                task("manana").copy(targetDate = today.plusDays(1)),
                task("hoy").copy(deadlineDate = today),
                task("pasado").copy(targetDate = today.plusDays(2)),
                task("hecha", TaskStatus.COMPLETED).copy(targetDate = today),
            ),
        )

        assertEquals(listOf("hoy", "manana"), dashboard.next24Hours.map { it.item.task.id })
    }
}
