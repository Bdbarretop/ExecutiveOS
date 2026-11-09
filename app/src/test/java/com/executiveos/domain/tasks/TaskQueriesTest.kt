package com.executiveos.domain.tasks

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.CriticalObligation
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.priority.PrioritySettings
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskQueriesTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun task(id: String, status: TaskStatus = TaskStatus.PENDING) = Task(
        id = id,
        status = status,
        sourceInboxItemId = null,
        title = id,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun item(task: Task, project: String? = null) =
        TaskListItem(task, categoryName = null, projectName = project, parentTitle = null, 0, 0)

    private fun ranked(vararg tasks: Task): List<RankedTask> =
        TaskQueries.rank(tasks.map(::item), PrioritySettings(), today)

    private fun viewIds(view: TaskView, vararg tasks: Task): List<String> =
        TaskQueries.select(ranked(*tasks), view, TaskFilters(), "", today).map { it.item.task.id }

    @Test
    fun allExcludesArchivedOnly() {
        val ids = viewIds(
            TaskView.ALL,
            task("abierta"),
            task("hecha", TaskStatus.COMPLETED),
            task("archivada", TaskStatus.ARCHIVED),
        )

        assertEquals(setOf("abierta", "hecha"), ids.toSet())
    }

    @Test
    fun dateViewsSplitTodayUpcomingOverdueAndNoDate() {
        val tasks = arrayOf(
            task("hoy").copy(targetDate = today),
            task("semana").copy(targetDate = today.plusDays(7)),
            task("lejos").copy(targetDate = today.plusDays(8)),
            task("vencida").copy(deadlineDate = today.minusDays(1)),
            task("sinfecha"),
        )

        assertEquals(listOf("hoy"), viewIds(TaskView.TODAY, *tasks))
        assertEquals(listOf("semana"), viewIds(TaskView.UPCOMING, *tasks))
        assertEquals(listOf("vencida"), viewIds(TaskView.OVERDUE, *tasks))
        assertEquals(listOf("sinfecha"), viewIds(TaskView.NO_DATE, *tasks))
    }

    @Test
    fun finishedTasksDoNotAppearAsOverdue() {
        val done = task("hecha", TaskStatus.COMPLETED).copy(deadlineDate = today.minusDays(3))

        assertTrue(viewIds(TaskView.OVERDUE, done).isEmpty())
    }

    @Test
    fun statusViewsMatchTheirStatus() {
        val tasks = arrayOf(
            task("progreso", TaskStatus.IN_PROGRESS),
            task("espera", TaskStatus.WAITING),
            task("bloqueada", TaskStatus.BLOCKED),
            task("hecha", TaskStatus.COMPLETED),
        )

        assertEquals(listOf("progreso"), viewIds(TaskView.IN_PROGRESS, *tasks))
        assertEquals(listOf("espera"), viewIds(TaskView.WAITING, *tasks))
        assertEquals(listOf("bloqueada"), viewIds(TaskView.BLOCKED, *tasks))
        assertEquals(listOf("hecha"), viewIds(TaskView.COMPLETED, *tasks))
    }

    @Test
    fun priorityViewsSeparateCriticalFromHigh() {
        val tasks = arrayOf(
            task("critica").copy(impact = 5, urgency = 5, delayRisk = 5),
            task("alta").copy(impact = 3, urgency = 3, delayRisk = 3),
            task("baja").copy(impact = 1, urgency = 1, delayRisk = 1),
        )

        assertEquals(listOf("critica"), viewIds(TaskView.CRITICAL, *tasks))
        assertEquals(listOf("alta"), viewIds(TaskView.HIGH, *tasks))
    }

    @Test
    fun anObligationIsNeverHiddenByALowScore() {
        val regulated = task("regulatoria").copy(
            impact = 1,
            urgency = 1,
            delayRisk = 1,
            obligation = CriticalObligation.REGULATORY,
        )

        assertEquals(listOf("regulatoria"), viewIds(TaskView.CRITICAL, regulated))
        assertTrue(viewIds(TaskView.HIGH, regulated).isEmpty())
    }

    @Test
    fun obligationsSortFirstThenLevelThenDate() {
        val ids = viewIds(
            TaskView.ALL,
            task("pendiente"),
            task("alta").copy(impact = 3, urgency = 3, delayRisk = 3),
            task("critica").copy(impact = 5, urgency = 5, delayRisk = 5),
            task("obligacion").copy(obligation = CriticalObligation.LEGAL),
        )

        assertEquals(listOf("obligacion", "critica", "alta", "pendiente"), ids)
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndCoversContextProjectAndTags() {
        val task = task("t").copy(
            title = "Preparar informe",
            contextToRemember = "Reunión con auditoría",
            tags = listOf("Comité"),
        )
        val item = item(task, project = "Modelo de Crédito")

        assertTrue(TaskQueries.matchesSearch(item, "INFORME"))
        assertTrue(TaskQueries.matchesSearch(item, "reunion"))
        assertTrue(TaskQueries.matchesSearch(item, "credito"))
        assertTrue(TaskQueries.matchesSearch(item, "comite"))
        assertTrue(TaskQueries.matchesSearch(item, "informe auditoria"))
        assertFalse(TaskQueries.matchesSearch(item, "informe presupuesto"))
    }

    @Test
    fun emptySearchMatchesEverything() {
        assertTrue(TaskQueries.matchesSearch(item(task("t")), "   "))
    }

    private fun filtered(filters: TaskFilters, vararg tasks: Task): List<String> =
        TaskQueries.select(ranked(*tasks), TaskView.ALL, filters, "", today).map { it.item.task.id }

    @Test
    fun filtersByCategoryOwnerAndTag() {
        val tasks = arrayOf(
            task("a").copy(categoryId = "trabajo", owner = "Ana", tags = listOf("urgente")),
            task("b").copy(categoryId = "salud", owner = "Luis"),
        )

        assertEquals(listOf("a"), filtered(TaskFilters(categoryIds = setOf("trabajo")), *tasks))
        assertEquals(listOf("b"), filtered(TaskFilters(owners = setOf("Luis")), *tasks))
        assertEquals(listOf("a"), filtered(TaskFilters(tags = setOf("urgente")), *tasks))
    }

    @Test
    fun filtersByPriorityLevelAndStatus() {
        val tasks = arrayOf(
            task("alta").copy(impact = 3, urgency = 3, delayRisk = 3),
            task("hecha", TaskStatus.COMPLETED),
        )

        assertEquals(listOf("alta"), filtered(TaskFilters(levels = setOf(PriorityLevel.HIGH)), *tasks))
        assertEquals(
            listOf("hecha"),
            filtered(TaskFilters(statuses = setOf(TaskStatus.COMPLETED)), *tasks),
        )
    }

    @Test
    fun dateRangeExcludesTasksWithoutADate() {
        val tasks = arrayOf(task("con").copy(targetDate = today), task("sin"))

        assertEquals(listOf("con"), filtered(TaskFilters(dateFrom = today, dateTo = today), *tasks))
    }

    @Test
    fun estimateFilterUsesEachRange() {
        val tasks = arrayOf(
            task("corta").copy(estimatedMinutes = 30),
            task("larga").copy(estimatedMinutes = 180),
            task("sin"),
        )

        assertEquals(listOf("corta"), filtered(TaskFilters(estimate = EstimateFilter.UP_TO_30), *tasks))
        assertEquals(listOf("larga"), filtered(TaskFilters(estimate = EstimateFilter.OVER_120), *tasks))
    }

    @Test
    fun activeCountCountsEachKindOfFilterOnce() {
        assertEquals(0, TaskFilters().activeCount)
        assertEquals(
            2,
            TaskFilters(owners = setOf("Ana", "Luis"), dateFrom = today, dateTo = today).activeCount,
        )
    }

    @Test
    fun selectingFromAThousandTasksRemainsFast() {
        val tasks = List(1_000) { index ->
            task("t$index").copy(title = "Tarea $index", impact = 3, urgency = 3, delayRisk = 3)
        }
        val ranked = TaskQueries.rank(tasks.map(::item), PrioritySettings(), today)

        val start = System.nanoTime()
        val result = TaskQueries.select(ranked, TaskView.ALL, TaskFilters(), "tarea 99", today)
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        // 99, 199 … 899 y 990 … 999 contienen «99».
        assertEquals(19, result.size)
        assertTrue("Tardó $elapsedMillis ms", elapsedMillis < 1_000)
    }
}
