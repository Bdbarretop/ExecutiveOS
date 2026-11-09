package com.executiveos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DEFAULT_TEMPLATE_COUNT
import com.executiveos.core.database.DefaultCategories
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.DateOption
import com.executiveos.domain.model.DelegationStatus
import com.executiveos.domain.model.InboxEntry
import com.executiveos.domain.model.InboxState
import com.executiveos.domain.model.ProcessedAs
import com.executiveos.domain.model.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineInboxRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 11)
    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineInboxRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()
        repository = OfflineInboxRepository(database, clock)
    }

    @After
    fun tearDown() = database.close()

    private fun draft(id: String = "c1", title: String = "Preparar informe") =
        CaptureDraft(id = id, title = title, categoryId = DefaultCategories.WORK.id)

    private suspend fun entry(id: String = "c1"): InboxEntry =
        repository.observeEntries().first().single { it.item.id == id }

    private suspend fun taskOf(id: String = "c1") = database.taskDao().findByInboxItem(id)!!

    @Test
    fun seedIncludesTheTenTemplates() = runBlocking {
        assertEquals(DEFAULT_TEMPLATE_COUNT, database.templateDao().observeAll().first().size)
    }

    @Test
    fun captureEntersTheInboxWithOnlyATitle() = runBlocking {
        repository.capture(CaptureDraft(id = "c1", title = "  Idea rápida  "))

        val entry = entry()
        assertEquals("Idea rápida", entry.title)
        assertEquals(InboxState.PENDING, entry.item.state)
        assertEquals(TaskStatus.INBOX, taskOf().status)
        assertNull(taskOf().impact)
        assertNull(taskOf().targetDate)
    }

    @Test
    fun savingTheSameDraftTwiceDoesNotDuplicate() = runBlocking {
        val first = repository.capture(draft())
        val second = repository.capture(draft())

        assertEquals(first, second)
        assertEquals(1, repository.observeEntries().first().size)
    }

    @Test
    fun dateIsOnlyADeadlineWhenTheUserConfirmsIt() = runBlocking {
        repository.capture(draft("objetivo").copy(dateOption = DateOption.TODAY))
        repository.capture(
            draft("limite", "Otra").copy(dateOption = DateOption.TODAY, dateIsDeadline = true),
        )

        assertEquals(today, taskOf("objetivo").targetDate)
        assertNull(taskOf("objetivo").deadlineDate)
        assertEquals(today, taskOf("limite").deadlineDate)
        assertNull(taskOf("limite").targetDate)
    }

    @Test
    fun templateCompletesContextAndLeavesTitleUntouched() = runBlocking {
        val template = database.templateDao().observeAll().first().first()

        repository.capture(draft().copy(templateId = template.id))

        val task = taskOf()
        assertEquals("Preparar informe", task.title)
        assertEquals(template.nextAction, task.nextAction)
        assertEquals(template.expectedOutcome, task.expectedOutcome)
    }

    @Test
    fun convertingToATaskKeepsTheOriginalCapture() = runBlocking {
        repository.capture(draft())
        database.taskDao().update(taskOf().copy(title = "Título editado"))

        repository.convertToTasks(listOf("c1"))

        val entry = entry()
        assertEquals("Preparar informe", entry.item.originalTitle)
        assertEquals("Título editado", entry.title)
        assertEquals(InboxState.PROCESSED, entry.item.state)
        assertEquals(ProcessedAs.TASK, entry.item.processedAs)
        assertEquals(TaskStatus.PENDING, taskOf().status)
    }

    @Test
    fun convertingToAProjectKeepsAReferenceToTheCapture() = runBlocking {
        repository.capture(draft())

        repository.convertToProject("c1")

        val entry = entry()
        val project = database.projectDao().findById(entry.item.processedTargetId!!)
        assertEquals("c1", project!!.sourceInboxItemId)
        assertEquals("Preparar informe", project.name)
        assertEquals(TaskStatus.ARCHIVED, taskOf().status)
    }

    @Test
    fun convertingToANoteKeepsTheContent() = runBlocking {
        repository.capture(draft().copy(details = "Revisar cifras de octubre"))

        repository.convertToNote("c1")

        val note = database.noteDao().findById(entry().item.processedTargetId!!)
        assertEquals("Preparar informe", note!!.title)
        assertEquals("Revisar cifras de octubre", note.body)
        assertEquals(ProcessedAs.NOTE, entry().item.processedAs)
    }

    @Test
    fun delegatingAssignsAndWaits() = runBlocking {
        repository.capture(draft())

        repository.delegate("c1", "  Laura  ")

        val task = taskOf()
        assertEquals("Laura", task.owner)
        assertEquals(TaskStatus.WAITING, task.status)
        assertEquals(DelegationStatus.DELEGATED, task.delegationStatus)
    }

    @Test
    fun postponingKeepsTheItemAndRecordsTheDate() = runBlocking {
        repository.capture(draft())

        repository.postpone(listOf("c1"), today.plusDays(3))

        assertEquals(InboxState.POSTPONED, entry().item.state)
        assertEquals(today.plusDays(3), entry().item.postponedUntil)
        assertEquals(TaskStatus.INBOX, taskOf().status)
    }

    @Test
    fun archivingKeepsTheContent() = runBlocking {
        repository.capture(draft())

        repository.archive(listOf("c1"))

        assertEquals(InboxState.ARCHIVED, entry().item.state)
        assertEquals(TaskStatus.ARCHIVED, taskOf().status)
    }

    @Test
    fun deletingRemovesTheCaptureButLeavesItsHistory() = runBlocking {
        repository.capture(draft())

        repository.delete(listOf("c1"))

        assertTrue(repository.observeEntries().first().isEmpty())
        assertNull(database.taskDao().findByInboxItem("c1"))
        val actions = database.auditLogDao().findFor("c1").map { it.action }
        assertEquals(listOf("CAPTURED", "DELETED"), actions)
    }

    @Test
    fun batchProcessingConvertsEverySelectedCapture() = runBlocking {
        repository.capture(draft("a", "Primera"))
        repository.capture(draft("b", "Segunda"))
        repository.capture(draft("c", "Tercera"))

        repository.convertToTasks(listOf("a", "b"))

        assertEquals(InboxState.PROCESSED, entry("a").item.state)
        assertEquals(InboxState.PROCESSED, entry("b").item.state)
        assertEquals(InboxState.PENDING, entry("c").item.state)
        assertNotNull(entry("a").taskId)
    }
}
