package com.executiveos.domain.reminders

import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.ReminderRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.tasks.RankedTask
import com.executiveos.domain.tasks.TaskQueries
import com.executiveos.domain.tasks.TaskView
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.first

enum class ReminderType { TASK, MORNING_NOTICE, DAILY_REVIEW, WEEKLY_REVIEW }

enum class ReviewKind { DAILY, WEEKLY }

data class TaskReminder(val id: String, val taskId: String, val triggerAt: Instant)

/** Horas expresadas en minutos desde la medianoche. */
data class ReminderSettings(
    val deadlineRemindersEnabled: Boolean = true,
    val deadlineDaysBefore: Int = 1,
    val criticalAlertsEnabled: Boolean = true,
    val morningNoticeMinute: Int = 8 * 60,
    val dailyReviewEnabled: Boolean = false,
    val dailyReviewMinute: Int = 8 * 60,
    val weeklyReviewEnabled: Boolean = false,
    val weeklyReviewDay: DayOfWeek = DayOfWeek.FRIDAY,
    val weeklyReviewMinute: Int = 16 * 60,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
)

/** Resumen único: varios compromisos no deben generar una notificación por cada uno. */
data class CommitmentNotice(
    val dueCount: Int,
    val overdueCount: Int,
    val criticalCount: Int,
    val singleTaskId: String?,
)

object ReminderRules {
    fun isQuiet(time: LocalTime, settings: ReminderSettings): Boolean {
        if (!settings.quietHoursEnabled) return false
        val minute = time.hour * 60 + time.minute
        val start = settings.quietStartMinute
        val end = settings.quietEndMinute
        return when {
            start == end -> false
            start < end -> minute in start until end
            else -> minute >= start || minute < end
        }
    }

    /** Un aviso que caería en horario silencioso se retrasa hasta que termine. */
    fun applyQuietHours(at: LocalDateTime, settings: ReminderSettings): LocalDateTime {
        if (!isQuiet(at.toLocalTime(), settings)) return at
        val end = LocalTime.of(settings.quietEndMinute / 60, settings.quietEndMinute % 60)
        return if (at.toLocalTime().isBefore(end)) {
            at.toLocalDate().atTime(end)
        } else {
            at.toLocalDate().plusDays(1).atTime(end)
        }
    }

    fun nextDaily(now: LocalDateTime, minuteOfDay: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(timeOf(minuteOfDay))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun nextWeekly(now: LocalDateTime, day: DayOfWeek, minuteOfDay: Int): LocalDateTime {
        val candidate = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(day))
            .atTime(timeOf(minuteOfDay))
        return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
    }

    private fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    fun commitmentNotice(
        ranked: List<RankedTask>,
        today: LocalDate,
        settings: ReminderSettings,
    ): CommitmentNotice? {
        val open = ranked.filter { TaskQueries.isOpen(it.item.task.status) }
        val horizon = today.plusDays(settings.deadlineDaysBefore.toLong())
        val withDeadline = if (settings.deadlineRemindersEnabled) {
            open.filter { it.item.task.deadlineDate != null }
        } else {
            emptyList()
        }
        val overdue = withDeadline.filter { checkNotNull(it.item.task.deadlineDate).isBefore(today) }
        val due = withDeadline.filter {
            val deadline = checkNotNull(it.item.task.deadlineDate)
            !deadline.isBefore(today) && !deadline.isAfter(horizon)
        }
        val critical = if (settings.criticalAlertsEnabled) {
            open.filter { TaskQueries.matchesView(it, TaskView.CRITICAL, today) }
        } else {
            emptyList()
        }
        val relevant = (overdue + due + critical).distinctBy { it.item.task.id }
        if (relevant.isEmpty()) return null
        return CommitmentNotice(
            dueCount = due.size,
            overdueCount = overdue.size,
            criticalCount = critical.size,
            singleTaskId = relevant.singleOrNull()?.item?.task?.id,
        )
    }
}

interface NotificationPublisher {
    fun showTaskReminder(taskId: String, title: String, nextAction: String?)

    fun showCommitmentNotice(notice: CommitmentNotice)

    fun showReviewReminder(kind: ReviewKind)

    fun cancelTaskReminder(taskId: String)
}

interface ReminderScheduler {
    fun scheduleTaskReminder(reminderId: String, at: Instant)

    fun cancelTaskReminder(reminderId: String)

    fun scheduleRecurring(settings: ReminderSettings, replaceExisting: Boolean)
}

sealed interface DeliveryResult {
    data object Delivered : DeliveryResult
    data object Skipped : DeliveryResult
    data class Deferred(val until: Instant) : DeliveryResult
}

/** Decide qué se notifica. Los workers solo lo invocan y Android queda fuera de estas reglas. */
class ReminderProcessor @Inject constructor(
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
    private val prioritySettings: PrioritySettingsRepository,
    private val publisher: NotificationPublisher,
    private val clock: Clock,
) {
    private val finishedStatuses =
        setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.ARCHIVED)

    suspend fun deliverTaskReminder(reminderId: String): DeliveryResult {
        val reminder = reminders.findScheduled(reminderId) ?: return DeliveryResult.Skipped
        val task = tasks.observeTask(reminder.taskId).first()
        if (task == null || task.status in finishedStatuses) {
            reminders.markDelivered(reminderId)
            return DeliveryResult.Skipped
        }
        val settings = reminders.settings.first()
        val now = LocalDateTime.now(clock)
        val allowedAt = ReminderRules.applyQuietHours(now, settings)
        if (allowedAt != now) {
            return DeliveryResult.Deferred(allowedAt.atZone(clock.zone).toInstant())
        }
        publisher.showTaskReminder(task.id, task.title, task.nextAction)
        reminders.markDelivered(reminderId)
        return DeliveryResult.Delivered
    }

    suspend fun runMorningNotice() {
        val settings = reminders.settings.first()
        val today = LocalDate.now(clock)
        val items = tasks.observeTaskList().first()
        val ranked = TaskQueries.rank(items, prioritySettings.settings.first(), today)
        val notice = ReminderRules.commitmentNotice(ranked, today, settings) ?: return
        if (reminders.registerOnce("MORNING:$today", ReminderType.MORNING_NOTICE)) {
            publisher.showCommitmentNotice(notice)
        }
        reminders.deleteDeliveredBefore(clock.instant().minusSeconds(RETENTION_SECONDS))
    }

    suspend fun runReviewReminder(kind: ReviewKind) {
        val today = LocalDate.now(clock)
        val type = if (kind == ReviewKind.DAILY) ReminderType.DAILY_REVIEW else ReminderType.WEEKLY_REVIEW
        if (reminders.registerOnce("$kind:$today", type)) publisher.showReviewReminder(kind)
    }

    private companion object {
        const val RETENTION_SECONDS = 30L * 24 * 60 * 60
    }
}
