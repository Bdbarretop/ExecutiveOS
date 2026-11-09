package com.executiveos.domain.reminders

import com.executiveos.domain.model.Task
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.priority.PrioritySettings
import com.executiveos.domain.tasks.TaskListItem
import com.executiveos.domain.tasks.TaskQueries
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {
    private val quiet = ReminderSettings(
        quietHoursEnabled = true,
        quietStartMinute = 22 * 60,
        quietEndMinute = 7 * 60,
    )

    /** Miércoles. */
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun quietHoursCrossingMidnightCoverBothSides() {
        assertTrue(ReminderRules.isQuiet(LocalTime.of(23, 0), quiet))
        assertTrue(ReminderRules.isQuiet(LocalTime.of(6, 59), quiet))
        assertFalse(ReminderRules.isQuiet(LocalTime.of(7, 0), quiet))
        assertFalse(ReminderRules.isQuiet(LocalTime.of(21, 59), quiet))
    }

    @Test
    fun quietHoursWithinTheSameDayWork() {
        val midday = quiet.copy(quietStartMinute = 12 * 60, quietEndMinute = 14 * 60)

        assertTrue(ReminderRules.isQuiet(LocalTime.of(13, 0), midday))
        assertFalse(ReminderRules.isQuiet(LocalTime.of(14, 0), midday))
    }

    @Test
    fun disabledQuietHoursNeverSilence() {
        assertFalse(ReminderRules.isQuiet(LocalTime.of(23, 0), quiet.copy(quietHoursEnabled = false)))
    }

    @Test
    fun aReminderInQuietHoursMovesToTheirEnd() {
        val night = today.atTime(23, 30)
        val dawn = today.atTime(5, 0)
        val afternoon = today.atTime(15, 0)

        assertEquals(today.plusDays(1).atTime(7, 0), ReminderRules.applyQuietHours(night, quiet))
        assertEquals(today.atTime(7, 0), ReminderRules.applyQuietHours(dawn, quiet))
        assertEquals(afternoon, ReminderRules.applyQuietHours(afternoon, quiet))
    }

    @Test
    fun dailyOccurrenceRollsToTomorrowOncePassed() {
        val morning = LocalDateTime.of(today, LocalTime.of(7, 0))
        val evening = LocalDateTime.of(today, LocalTime.of(20, 0))

        assertEquals(today.atTime(8, 0), ReminderRules.nextDaily(morning, 8 * 60))
        assertEquals(today.plusDays(1).atTime(8, 0), ReminderRules.nextDaily(evening, 8 * 60))
    }

    @Test
    fun weeklyOccurrenceFindsTheNextMatchingDay() {
        val wednesday = today.atTime(12, 0)

        assertEquals(
            today.plusDays(2).atTime(16, 0),
            ReminderRules.nextWeekly(wednesday, DayOfWeek.FRIDAY, 16 * 60),
        )
        assertEquals(
            today.plusWeeks(1).atTime(9, 0),
            ReminderRules.nextWeekly(wednesday, DayOfWeek.WEDNESDAY, 9 * 60),
        )
    }

    private fun task(id: String, status: TaskStatus = TaskStatus.PENDING) = Task(
        id = id,
        status = status,
        sourceInboxItemId = null,
        title = id,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun notice(settings: ReminderSettings, vararg tasks: Task): CommitmentNotice? {
        val items = tasks.map { TaskListItem(it, null, null, null, 0, 0) }
        return ReminderRules.commitmentNotice(
            TaskQueries.rank(items, PrioritySettings(), today),
            today,
            settings,
        )
    }

    @Test
    fun nothingToReportProducesNoNotice() {
        assertNull(notice(ReminderSettings(), task("tranquila")))
    }

    @Test
    fun noticeCountsDeadlinesWithinTheConfiguredWindow() {
        val result = notice(
            ReminderSettings(deadlineDaysBefore = 1),
            task("vencida").copy(deadlineDate = today.minusDays(1)),
            task("hoy").copy(deadlineDate = today),
            task("manana").copy(deadlineDate = today.plusDays(1)),
            task("lejos").copy(deadlineDate = today.plusDays(2)),
            task("objetivo").copy(targetDate = today),
        )!!

        assertEquals(1, result.overdueCount)
        assertEquals(2, result.dueCount)
        assertEquals(0, result.criticalCount)
        assertNull(result.singleTaskId)
    }

    @Test
    fun aSingleRelevantTaskCanBeOpenedDirectly() {
        val result = notice(ReminderSettings(), task("unica").copy(deadlineDate = today))!!

        assertEquals("unica", result.singleTaskId)
    }

    @Test
    fun finishedTasksAreNotReported() {
        assertNull(
            notice(ReminderSettings(), task("hecha", TaskStatus.COMPLETED).copy(deadlineDate = today)),
        )
    }

    @Test
    fun eachKindOfNoticeCanBeTurnedOff() {
        val tasks = arrayOf(
            task("limite").copy(deadlineDate = today),
            task("critica").copy(impact = 5, urgency = 5, delayRisk = 5),
        )

        val noDeadlines = notice(ReminderSettings(deadlineRemindersEnabled = false), *tasks)!!
        assertEquals(0, noDeadlines.dueCount)
        assertEquals(1, noDeadlines.criticalCount)
        assertNull(
            notice(
                ReminderSettings(deadlineRemindersEnabled = false, criticalAlertsEnabled = false),
                *tasks,
            ),
        )
    }
}
