package com.executiveos.domain.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.data.repository.OfflinePrioritySettingsRepository
import com.executiveos.data.repository.OfflineReminderRepository
import com.executiveos.data.repository.OfflineTaskRepository
import com.executiveos.domain.model.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderProcessorTest {
    /** Miércoles a las 15:00. */
    private val clock = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC)
    private val later = clock.instant().plusSeconds(3_600)
    private val publisher = FakePublisher()
    private lateinit var database: AppDatabase
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var reminders: OfflineReminderRepository
    private lateinit var processor: ReminderProcessor

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()
        tasks = OfflineTaskRepository(database, clock)
        reminders = OfflineReminderRepository(database, clock)
        processor = ReminderProcessor(
            reminders,
            tasks,
            OfflinePrioritySettingsRepository(database, clock),
            publisher,
            clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun deliversAReminderOnlyOnce() = runBlocking {
        val taskId = tasks.create("Enviar acta")
        val reminderId = reminders.setTaskReminder(taskId, later)

        assertEquals(DeliveryResult.Delivered, processor.deliverTaskReminder(reminderId))
        assertEquals(DeliveryResult.Skipped, processor.deliverTaskReminder(reminderId))

        assertEquals(listOf("Enviar acta"), publisher.taskTitles)
        assertNull(reminders.observeTaskReminder(taskId).first())
    }

    @Test
    fun aFinishedTaskIsNotNotified() = runBlocking {
        val taskId = tasks.create("Hecha")
        val reminderId = reminders.setTaskReminder(taskId, later)
        tasks.setStatus(taskId, TaskStatus.COMPLETED)

        assertEquals(DeliveryResult.Skipped, processor.deliverTaskReminder(reminderId))

        assertTrue(publisher.taskTitles.isEmpty())
    }

    @Test
    fun aNewReminderReplacesThePreviousOne() = runBlocking {
        val taskId = tasks.create("Informe")
        val first = reminders.setTaskReminder(taskId, later)
        val second = reminders.setTaskReminder(taskId, later.plusSeconds(60))

        assertEquals(DeliveryResult.Skipped, processor.deliverTaskReminder(first))
        assertEquals(DeliveryResult.Delivered, processor.deliverTaskReminder(second))
        assertEquals(1, publisher.taskTitles.size)
    }

    @Test
    fun clearingCancelsTheReminderAndReturnsItsId() = runBlocking {
        val taskId = tasks.create("Informe")
        val id = reminders.setTaskReminder(taskId, later)

        assertEquals(id, reminders.clearTaskReminder(taskId))
        assertNull(reminders.clearTaskReminder(taskId))
        assertEquals(DeliveryResult.Skipped, processor.deliverTaskReminder(id))
    }

    @Test
    fun quietHoursDeferTheReminderInsteadOfLosingIt() = runBlocking {
        reminders.saveSettings(
            ReminderSettings(
                quietHoursEnabled = true,
                quietStartMinute = 14 * 60,
                quietEndMinute = 16 * 60,
            ),
        )
        val taskId = tasks.create("Silenciada")
        val reminderId = reminders.setTaskReminder(taskId, later)

        val result = processor.deliverTaskReminder(reminderId)

        assertEquals(DeliveryResult.Deferred(Instant.parse("2026-11-11T16:00:00Z")), result)
        assertTrue(publisher.taskTitles.isEmpty())
        assertEquals(reminderId, reminders.findScheduled(reminderId)?.id)
    }

    @Test
    fun theMorningNoticeIsSentOncePerDay() = runBlocking {
        val taskId = tasks.create("Con límite")
        tasks.save(tasks.observeTask(taskId).first()!!.copy(deadlineDate = java.time.LocalDate.of(2026, 11, 11)))

        processor.runMorningNotice()
        processor.runMorningNotice()

        assertEquals(1, publisher.notices.size)
        assertEquals(taskId, publisher.notices.single().singleTaskId)
    }

    @Test
    fun noNoticeIsSentWhenThereIsNothingToReport() = runBlocking {
        tasks.create("Tranquila")

        processor.runMorningNotice()

        assertTrue(publisher.notices.isEmpty())
    }

    @Test
    fun reviewRemindersAreNotRepeatedTheSameDay() = runBlocking {
        processor.runReviewReminder(ReviewKind.DAILY)
        processor.runReviewReminder(ReviewKind.DAILY)
        processor.runReviewReminder(ReviewKind.WEEKLY)

        assertEquals(listOf(ReviewKind.DAILY, ReviewKind.WEEKLY), publisher.reviews)
    }

    @Test
    fun settingsDefaultAndPersist() = runBlocking {
        assertEquals(ReminderSettings(), reminders.settings.first())

        val custom = ReminderSettings(quietHoursEnabled = true, deadlineDaysBefore = 3)
        reminders.saveSettings(custom)

        assertEquals(custom, reminders.settings.first())
    }

    private class FakePublisher : NotificationPublisher {
        val taskTitles = mutableListOf<String>()
        val notices = mutableListOf<CommitmentNotice>()
        val reviews = mutableListOf<ReviewKind>()

        override fun showTaskReminder(taskId: String, title: String, nextAction: String?) {
            taskTitles += title
        }

        override fun showCommitmentNotice(notice: CommitmentNotice) {
            notices += notice
        }

        override fun showReviewReminder(kind: ReviewKind) {
            reviews += kind
        }

        override fun cancelTaskReminder(taskId: String) = Unit
    }
}
