package com.executiveos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.domain.model.AddDependencyResult
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskHistoryAction
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PriorityLevel
import com.executiveos.domain.priority.PrioritySettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineTaskRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 11)
    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineTaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()
        repository = OfflineTaskRepository(database, clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun task(id: String): Task = repository.observeTask(id).first()!!

    private suspend fun history(id: String): List<TaskHistoryAction> =
        repository.observeHistory(id).first().map { it.action }

    @Test
    fun taskListLeavesUnprocessedCapturesInTheInbox() = runBlocking {
        OfflineInboxRepository(database, clock).capture(CaptureDraft(id = "c", title = "Sin procesar"))
        repository.create("Procesada")

        val titles = repository.observeTaskList().first().map { it.task.title }

        assertEquals(listOf("Procesada"), titles)
    }

    @Test
    fun completingRecordsWhenAndReopeningClearsIt() = runBlocking {
        val id = repository.create("Informe")

        repository.setStatus(id, TaskStatus.COMPLETED)
        assertEquals(clock.instant(), task(id).completedAt)

        repository.setStatus(id, TaskStatus.PENDING)
        assertNull(task(id).completedAt)
    }

    @Test
    fun invalidStatusChangesAreRejected() = runBlocking {
        val id = repository.create("Informe")
        repository.setStatus(id, TaskStatus.COMPLETED)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setStatus(id, TaskStatus.IN_PROGRESS) }
        }
        assertEquals(TaskStatus.COMPLETED, task(id).status)
    }

    @Test
    fun savingTheRecordNeverOverwritesTheStatus() = runBlocking {
        val id = repository.create("Informe")
        val stale = task(id)
        repository.setStatus(id, TaskStatus.IN_PROGRESS)

        repository.save(stale.copy(notes = "Actualizada"))

        assertEquals(TaskStatus.IN_PROGRESS, task(id).status)
        assertEquals("Actualizada", task(id).notes)
    }

    @Test
    fun priorityChangesAreRecordedInTheHistory() = runBlocking {
        val id = repository.create("Informe")

        repository.save(task(id).copy(impact = 5))
        repository.save(
            task(id).copy(manualPriority = PriorityLevel.HIGH, manualPriorityReason = "Junta"),
        )

        val actions = history(id)
        assertTrue(TaskHistoryAction.PRIORITY_CHANGED in actions)
        assertTrue(TaskHistoryAction.MANUAL_PRIORITY_CHANGED in actions)
    }

    @Test
    fun manualPriorityWithoutJustificationIsRejected() = runBlocking {
        val id = repository.create("Informe")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(task(id).copy(manualPriority = PriorityLevel.HIGH)) }
        }
        assertNull(task(id).manualPriority)
    }

    @Test
    fun duplicatingCopiesContentAndTagsButNotCommitments() = runBlocking {
        val id = repository.create("Informe")
        repository.save(
            task(id).copy(
                nextAction = "Reunir datos",
                deadlineDate = today,
                tags = listOf("comité"),
            ),
        )
        repository.setStatus(id, TaskStatus.COMPLETED)

        val copyId = repository.duplicate(id)

        val copy = task(copyId)
        assertNotEquals(id, copyId)
        assertEquals("Reunir datos", copy.nextAction)
        assertEquals(listOf("comité"), copy.tags)
        assertNull(copy.deadlineDate)
        assertNull(copy.completedAt)
        assertEquals(TaskStatus.PENDING, copy.status)
    }

    @Test
    fun subtasksInheritContextAndAreCountedWhileOpen() = runBlocking {
        val parent = repository.create("Proyecto grande")
        repository.save(task(parent).copy(categoryId = database.firstCategory()))

        val child = repository.create("Primer paso", parentTaskId = parent)

        assertEquals(task(parent).categoryId, task(child).categoryId)
        assertEquals(listOf(child), repository.observeSubtasks(parent).first().map { it.id })
        assertEquals(1, listItem(parent).openSubtasks)

        repository.setStatus(child, TaskStatus.COMPLETED)
        assertEquals(0, listItem(parent).openSubtasks)
    }

    private suspend fun AppDatabase.firstCategory(): String =
        categoryDao().observeAll().first().first().id

    private suspend fun listItem(id: String) =
        repository.observeTaskList().first().single { it.task.id == id }

    @Test
    fun dependenciesCountOnlyWhileUnfinished() = runBlocking {
        val first = repository.create("Primera")
        val second = repository.create("Segunda")

        assertEquals(AddDependencyResult.ADDED, repository.addDependency(second, first))
        assertEquals(1, listItem(second).openDependencies)

        repository.setStatus(first, TaskStatus.COMPLETED)
        assertEquals(0, listItem(second).openDependencies)
    }

    @Test
    fun circularDependenciesAreRefused() = runBlocking {
        val a = repository.create("A")
        val b = repository.create("B")
        val c = repository.create("C")
        repository.addDependency(a, b)
        repository.addDependency(b, c)

        assertEquals(AddDependencyResult.WOULD_CREATE_CYCLE, repository.addDependency(c, a))
        assertEquals(AddDependencyResult.ALREADY_EXISTS, repository.addDependency(a, b))
        assertEquals(AddDependencyResult.WOULD_CREATE_CYCLE, repository.addDependency(a, a))
    }

    @Test
    fun removingADependencyFreesTheTask() = runBlocking {
        val first = repository.create("Primera")
        val second = repository.create("Segunda")
        repository.addDependency(second, first)

        repository.removeDependency(second, first)

        assertTrue(repository.observeDependencies(second).first().isEmpty())
    }

    @Test
    fun postponingMovesTheTargetButNeverTheDeadline() = runBlocking {
        val id = repository.create("Informe")
        repository.save(task(id).copy(deadlineDate = today.plusDays(30)))

        repository.postpone(id, today.plusDays(3))

        assertEquals(today.plusDays(3), task(id).targetDate)
        assertEquals(today.plusDays(30), task(id).deadlineDate)
    }

    @Test
    fun delegatingAssignsAndWaits() = runBlocking {
        val id = repository.create("Informe")

        repository.delegate(id, " Laura ")

        assertEquals("Laura", task(id).owner)
        assertEquals(DelegationStatus.DELEGATED, task(id).delegationStatus)
        assertEquals(TaskStatus.WAITING, task(id).status)
    }

    @Test
    fun historyIncludesWhatHappenedToTheOriginalCapture() = runBlocking {
        val taskId = OfflineInboxRepository(database, clock)
            .capture(CaptureDraft(id = "c", title = "Idea"))

        repository.setStatus(taskId, TaskStatus.PENDING)

        val actions = history(taskId)
        assertTrue(TaskHistoryAction.CAPTURED in actions)
        assertTrue(TaskHistoryAction.STATUS_CHANGED in actions)
    }

    @Test
    fun prioritySettingsDefaultToTheBriefAndPersist() = runBlocking {
        val settingsRepository = OfflinePrioritySettingsRepository(database, clock)
        assertEquals(PrioritySettings(), settingsRepository.settings.first())

        val custom = PrioritySettings(impactWeight = 50, urgencyWeight = 20, delayRiskWeight = 30)
        settingsRepository.save(custom)

        assertEquals(custom, settingsRepository.settings.first())
    }

    @Test
    fun invalidPrioritySettingsAreRejected() = runBlocking {
        val settingsRepository = OfflinePrioritySettingsRepository(database, clock)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { settingsRepository.save(PrioritySettings(impactWeight = 90)) }
        }
        assertNotNull(settingsRepository.settings.first())
    }
}
