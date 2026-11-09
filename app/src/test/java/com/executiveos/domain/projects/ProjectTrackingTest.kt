package com.executiveos.domain.projects

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTrackingTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun project(target: LocalDate? = null, status: ProjectStatus = ProjectStatus.ACTIVE) =
        Project(
            id = "p",
            name = "Proyecto",
            targetDate = target,
            status = status,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun milestone(id: String, weight: Int, due: LocalDate? = null, done: Boolean = false) =
        ProjectMilestone(
            id = id,
            projectId = "p",
            name = id,
            kind = MilestoneKind.MILESTONE,
            weight = weight,
            dueDate = due,
            completedAt = Instant.EPOCH.takeIf { done },
        )

    private fun progress(
        project: Project = project(),
        milestones: List<ProjectMilestone> = emptyList(),
        risks: List<ProjectRisk> = emptyList(),
        openTasks: Int = 0,
    ) = ProjectTracking.progress(project, milestones, risks, openTasks, today)

    @Test
    fun progressIsWeightedNotCounted() {
        val result = progress(
            milestones = listOf(milestone("grande", 80, done = true), milestone("chico", 20)),
        )

        assertEquals(80, result.percent)
        assertEquals(1, result.completedMilestones)
    }

    @Test
    fun withoutMilestonesProgressIsUnknownEvenWithManyTasks() {
        assertNull(progress(openTasks = 50).percent)
    }

    @Test
    fun nextMilestoneIsTheEarliestOpenOne() {
        val result = progress(
            milestones = listOf(
                milestone("hecho", 10, today.minusDays(9), done = true),
                milestone("luego", 10, today.plusDays(9)),
                milestone("pronto", 10, today.plusDays(1)),
            ),
        )

        assertEquals("pronto", result.nextMilestone?.id)
    }

    @Test
    fun anOverdueMilestoneMarksTheProjectAsDelayed() {
        val result = progress(milestones = listOf(milestone("tarde", 10, today.minusDays(1))))

        assertEquals(1, result.overdueMilestones)
        assertTrue(result.isDelayed)
    }

    @Test
    fun forecastBeyondTheTargetReportsTheDeviation() {
        val result = progress(
            project = project(target = today.plusDays(5)),
            milestones = listOf(milestone("final", 10, today.plusDays(12))),
        )

        assertEquals(today.plusDays(12), result.forecastCompletion)
        assertEquals(7L, result.deviationDays)
        assertTrue(result.isDelayed)
    }

    @Test
    fun finishedProjectsAreNeverDelayed() {
        val result = progress(project = project(today.minusDays(5), ProjectStatus.COMPLETED))

        assertFalse(result.isDelayed)
    }

    @Test
    fun onlyOpenRisksAreCounted() {
        val risks = listOf(
            ProjectRisk("a", "p", "Abierto", severity = 3),
            ProjectRisk("b", "p", "Cerrado", severity = 3, isOpen = false),
        )

        assertEquals(1, progress(risks = risks).openRisks)
    }
}
