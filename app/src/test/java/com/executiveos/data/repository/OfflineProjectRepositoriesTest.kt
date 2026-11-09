package com.executiveos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.planning.BlockType
import com.executiveos.domain.planning.CapacitySettings
import com.executiveos.domain.planning.TimeBlock
import com.executiveos.domain.projects.MilestoneKind
import com.executiveos.domain.projects.ProjectStatus
import com.executiveos.domain.repository.ProjectDetails
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineProjectRepositoriesTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val target = LocalDate.of(2026, 12, 1)
    private lateinit var database: AppDatabase
    private lateinit var projects: OfflineProjectRepository
    private lateinit var planning: OfflinePlanningRepository
    private lateinit var tasks: OfflineTaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()
        projects = OfflineProjectRepository(database, clock)
        planning = OfflinePlanningRepository(database, clock)
        tasks = OfflineTaskRepository(database, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun details(id: String): ProjectDetails = projects.observeProject(id).first()!!

    private fun block(id: String = "b", taskId: String? = null) = TimeBlock(
        id = id,
        type = BlockType.DEEP_WORK,
        title = "Informe",
        taskId = taskId,
        startAt = Instant.parse("2026-11-11T09:00:00Z"),
        endAt = Instant.parse("2026-11-11T10:00:00Z"),
    )

    @Test
    fun aProjectKeepsTheDataOfItsRecord() = runBlocking {
        val id = projects.create("  Implantar ERP ")

        val saved = details(id).project.copy(
            objective = "Unificar datos",
            owner = "Dirección",
            targetDate = target,
            status = ProjectStatus.ACTIVE,
        )
        projects.save(saved)

        val project = details(id).project
        assertEquals("Implantar ERP", project.name)
        assertEquals("Unificar datos", project.objective)
        assertEquals("Dirección", project.owner)
        assertEquals(target, project.targetDate)
        assertEquals(ProjectStatus.ACTIVE, project.status)
    }

    @Test
    fun aProjectConvertedFromTheInboxIsReadAsPlanned() = runBlocking {
        val inbox = OfflineInboxRepository(database, clock)
        inbox.capture(CaptureDraft(id = "c1", title = "Desde la bandeja"))

        inbox.convertToProject("c1")

        val converted = projects.observeProjects().first().single().project
        assertEquals("Desde la bandeja", converted.name)
        assertEquals(ProjectStatus.PLANNED, converted.status)
    }

    @Test
    fun aProjectWithoutANameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { projects.create("   ") }
        }
    }

    @Test
    fun milestonesKeepTheirOrderAndCanBeCompletedAndReopened() = runBlocking {
        val id = projects.create("ERP")
        projects.addMilestone(id, "Diseño", MilestoneKind.MILESTONE, 2, target)
        projects.addMilestone(id, "Manual", MilestoneKind.DELIVERABLE, 3, null)

        val added = details(id).milestones
        assertEquals(listOf("Diseño", "Manual"), added.map { it.name })
        assertEquals(listOf(0, 1), added.map { it.sortOrder })

        projects.setMilestoneCompleted(added.first().id, true)
        assertEquals(clock.instant(), details(id).milestones.first().completedAt)

        projects.setMilestoneCompleted(added.first().id, false)
        assertNull(details(id).milestones.first().completedAt)
    }

    @Test
    fun aMilestoneNeedsAPositiveWeight() = runBlocking {
        val id = projects.create("ERP")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { projects.addMilestone(id, "Diseño", MilestoneKind.MILESTONE, 0, null) }
        }
        projects.addMilestone(id, "Diseño", MilestoneKind.MILESTONE, 1, null)
        projects.deleteMilestone(details(id).milestones.single().id)

        assertTrue(details(id).milestones.isEmpty())
    }

    @Test
    fun onlyOpenTasksOfTheProjectAreCounted() = runBlocking {
        val id = projects.create("ERP")
        val other = projects.create("Otro")
        tasks.create("Abierta", projectId = id)
        tasks.create("De otro", projectId = other)
        tasks.setStatus(tasks.create("Hecha", projectId = id), TaskStatus.COMPLETED)

        assertEquals(1, details(id).openTasks)
        assertEquals(1, details(other).openTasks)
    }

    @Test
    fun aSubtaskStillInheritsTheProjectOfItsParent() = runBlocking {
        val id = projects.create("ERP")
        val parent = tasks.create("Padre", projectId = id)

        tasks.create("Hija", parentTaskId = parent)

        assertEquals(2, details(id).openTasks)
    }

    @Test
    fun risksCanBeClosedAndReopened() = runBlocking {
        val id = projects.create("ERP")
        projects.addRisk(id, "Retraso del proveedor", "  ", 4)

        val risk = details(id).risks.single()
        assertNull(risk.mitigation)
        assertTrue(risk.isOpen)

        projects.setRiskOpen(risk.id, false)
        assertFalse(details(id).risks.single().isOpen)
    }

    @Test
    fun aRiskSeverityMustBeBetweenOneAndFive() = runBlocking {
        val id = projects.create("ERP")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { projects.addRisk(id, "Riesgo", null, 6) }
        }
        Unit
    }

    @Test
    fun savingABlockCreatesItAndThenUpdatesTheSameOne() = runBlocking {
        planning.save(block())
        planning.save(block().copy(title = "Informe final", actualMinutes = 45))

        val stored = planning.observeBlocks().first().single()
        assertEquals("Informe final", stored.title)
        assertEquals(45, stored.actualMinutes)
    }

    @Test
    fun anInvalidBlockIsNotStored() = runBlocking {
        val invalid = block().copy(endAt = block().startAt)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { planning.save(invalid) }
        }
        assertTrue(planning.observeBlocks().first().isEmpty())
    }

    @Test
    fun deletingALinkedTaskKeepsTheBlockWithoutTheLink() = runBlocking {
        val taskId = tasks.create("Informe")
        planning.save(block(taskId = taskId))

        database.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(taskId))

        assertNull(planning.observeBlocks().first().single().taskId)
    }

    @Test
    fun capacityUsesDefaultsUntilItIsSavedAndThenKeepsTheLastValue() = runBlocking {
        assertEquals(CapacitySettings(), planning.capacity.first())

        planning.saveCapacity(CapacitySettings(360, 60, setOf(DayOfWeek.MONDAY)))
        planning.saveCapacity(CapacitySettings(300, 70, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))

        assertEquals(
            CapacitySettings(300, 70, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            planning.capacity.first(),
        )
    }

    @Test
    fun capacityOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { planning.saveCapacity(CapacitySettings(targetPercent = 0)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { planning.saveCapacity(CapacitySettings(availableMinutesPerDay = 1_441)) }
        }
    }
}
