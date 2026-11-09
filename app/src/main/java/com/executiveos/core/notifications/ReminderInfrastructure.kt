package com.executiveos.core.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.executiveos.MainActivity
import com.executiveos.R
import com.executiveos.domain.model.TaskStatus
import com.executiveos.domain.reminders.CommitmentNotice
import com.executiveos.domain.reminders.DeliveryResult
import com.executiveos.domain.reminders.NotificationPublisher
import com.executiveos.domain.reminders.ReminderProcessor
import com.executiveos.domain.reminders.ReminderRules
import com.executiveos.domain.reminders.ReminderScheduler
import com.executiveos.domain.reminders.ReminderSettings
import com.executiveos.domain.reminders.ReviewKind
import com.executiveos.domain.repository.ReminderRepository
import com.executiveos.domain.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val CHANNEL_TASKS = "task_reminders"
private const val CHANNEL_COMMITMENTS = "commitment_notices"
private const val NOTICE_NOTIFICATION_ID = 1001
private const val DAILY_REVIEW_NOTIFICATION_ID = 1002
private const val WEEKLY_REVIEW_NOTIFICATION_ID = 1003
private const val SNOOZE_MINUTES = 60L

/** El identificador deriva de la tarea: un segundo aviso actualiza el anterior, no lo duplica. */
private fun notificationIdFor(taskId: String): Int = taskId.hashCode()

@Singleton
class AndroidNotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPublisher {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASKS,
                context.getString(R.string.channel_tasks),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMMITMENTS,
                context.getString(R.string.channel_commitments),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    override fun showTaskReminder(taskId: String, title: String, nextAction: String?) {
        val builder = baseBuilder(CHANNEL_TASKS, openIntent(taskId))
            .setContentTitle(title)
            .addAction(0, context.getString(R.string.notification_complete), actionIntent(ACTION_COMPLETE, taskId))
            .addAction(0, context.getString(R.string.notification_snooze), actionIntent(ACTION_SNOOZE, taskId))
        nextAction?.let { builder.setContentText(context.getString(R.string.tasks_next_action, it)) }
        post(notificationIdFor(taskId), builder)
    }

    override fun showCommitmentNotice(notice: CommitmentNotice) {
        val resources = context.resources
        val parts = buildList {
            if (notice.overdueCount > 0) {
                add(resources.getQuantityString(R.plurals.alert_overdue, notice.overdueCount, notice.overdueCount))
            }
            if (notice.dueCount > 0) {
                add(resources.getQuantityString(R.plurals.notice_due, notice.dueCount, notice.dueCount))
            }
            if (notice.criticalCount > 0) {
                add(resources.getQuantityString(R.plurals.notice_critical, notice.criticalCount, notice.criticalCount))
            }
        }
        val text = parts.joinToString(" · ")
        post(
            NOTICE_NOTIFICATION_ID,
            baseBuilder(CHANNEL_COMMITMENTS, openIntent(notice.singleTaskId))
                .setContentTitle(context.getString(R.string.notification_notice_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)),
        )
    }

    override fun showReviewReminder(kind: ReviewKind) {
        val (id, title, text) = when (kind) {
            ReviewKind.DAILY -> Triple(
                DAILY_REVIEW_NOTIFICATION_ID,
                R.string.notification_daily_title,
                R.string.notification_daily_text,
            )
            ReviewKind.WEEKLY -> Triple(
                WEEKLY_REVIEW_NOTIFICATION_ID,
                R.string.notification_weekly_title,
                R.string.notification_weekly_text,
            )
        }
        post(
            id,
            baseBuilder(CHANNEL_TASKS, reviewIntent(kind))
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(text)),
        )
    }

    private fun reviewIntent(kind: ReviewKind): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_REVIEW_TYPE, kind.name)
        return PendingIntent.getActivity(
            context,
            "review_${kind.name}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun cancelTaskReminder(taskId: String) = manager.cancel(notificationIdFor(taskId))

    private fun baseBuilder(channel: String, content: PendingIntent) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_task)
            .setContentIntent(content)
            .setAutoCancel(true)

    @SuppressLint("MissingPermission")
    private fun post(id: Int, builder: NotificationCompat.Builder) {
        // Sin permiso concedido no se notifica ni se insiste.
        if (manager.areNotificationsEnabled()) manager.notify(id, builder.build())
    }

    private fun openIntent(taskId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        taskId?.let { intent.putExtra(MainActivity.EXTRA_TASK_ID, it) }
        return PendingIntent.getActivity(
            context,
            taskId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            (action + taskId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_COMPLETE = "com.executiveos.action.COMPLETE"
        const val ACTION_SNOOZE = "com.executiveos.action.SNOOZE"
    }
}

@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var publisher: NotificationPublisher
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(MainActivity.EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                publisher.cancelTaskReminder(taskId)
                when (intent.action) {
                    AndroidNotificationPublisher.ACTION_COMPLETE ->
                        tasks.setStatus(taskId, TaskStatus.COMPLETED)
                    AndroidNotificationPublisher.ACTION_SNOOZE -> snooze(taskId)
                }
            } catch (_: IllegalArgumentException) {
                // La tarea cambió de estado desde que se mostró el aviso: no hay nada que hacer.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun snooze(taskId: String) {
        val at = clock.instant().plus(Duration.ofMinutes(SNOOZE_MINUTES))
        scheduler.scheduleTaskReminder(reminders.setTaskReminder(taskId, at), at)
    }
}

private const val KEY_REMINDER_ID = "reminder_id"
private const val KEY_REVIEW_KIND = "review_kind"
private const val MORNING_WORK = "morning_notice"
private const val DAILY_REVIEW_WORK = "daily_review"
private const val WEEKLY_REVIEW_WORK = "weekly_review"

/**
 * WorkManager sin alarmas exactas: el permiso correspondiente no se justifica aquí, y Android
 * puede retrasar unos minutos cada aviso para ahorrar batería.
 */
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    override fun scheduleTaskReminder(reminderId: String, at: Instant) {
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayUntil(at))
            .setInputData(Data.Builder().putString(KEY_REMINDER_ID, reminderId).build())
            .build()
        workManager.enqueueUniqueWork(taskWorkName(reminderId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelTaskReminder(reminderId: String) {
        workManager.cancelUniqueWork(taskWorkName(reminderId))
    }

    override fun scheduleRecurring(settings: ReminderSettings, replaceExisting: Boolean) {
        val now = LocalDateTime.now(clock)
        val policy = if (replaceExisting) {
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        syncPeriodic<MorningNoticeWorker>(
            name = MORNING_WORK,
            enabled = settings.deadlineRemindersEnabled || settings.criticalAlertsEnabled,
            first = ReminderRules.nextDaily(now, settings.morningNoticeMinute),
            periodDays = 1,
            data = Data.EMPTY,
            settings = settings,
            policy = policy,
        )
        syncPeriodic<ReviewReminderWorker>(
            name = DAILY_REVIEW_WORK,
            enabled = settings.dailyReviewEnabled,
            first = ReminderRules.nextDaily(now, settings.dailyReviewMinute),
            periodDays = 1,
            data = reviewData(ReviewKind.DAILY),
            settings = settings,
            policy = policy,
        )
        syncPeriodic<ReviewReminderWorker>(
            name = WEEKLY_REVIEW_WORK,
            enabled = settings.weeklyReviewEnabled,
            first = ReminderRules.nextWeekly(now, settings.weeklyReviewDay, settings.weeklyReviewMinute),
            periodDays = 7,
            data = reviewData(ReviewKind.WEEKLY),
            settings = settings,
            policy = policy,
        )
    }

    private inline fun <reified W : CoroutineWorker> syncPeriodic(
        name: String,
        enabled: Boolean,
        first: LocalDateTime,
        periodDays: Long,
        data: Data,
        settings: ReminderSettings,
        policy: ExistingPeriodicWorkPolicy,
    ) {
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val at = ReminderRules.applyQuietHours(first, settings).atZone(clock.zone).toInstant()
        val request = PeriodicWorkRequestBuilder<W>(periodDays, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(at))
            .setInputData(data)
            .build()
        workManager.enqueueUniquePeriodicWork(name, policy, request)
    }

    private fun reviewData(kind: ReviewKind) =
        Data.Builder().putString(KEY_REVIEW_KIND, kind.name).build()

    private fun delayUntil(at: Instant): Duration =
        Duration.between(clock.instant(), at).coerceAtLeast(Duration.ZERO)

    private fun taskWorkName(reminderId: String) = "task_reminder_$reminderId"
}

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: ReminderProcessor,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val result = processor.deliverTaskReminder(reminderId)
        if (result is DeliveryResult.Deferred) {
            scheduler.scheduleTaskReminder(reminderId, result.until)
        }
        return Result.success()
    }
}

@HiltWorker
class MorningNoticeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: ReminderProcessor,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        processor.runMorningNotice()
        return Result.success()
    }
}

@HiltWorker
class ReviewReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: ReminderProcessor,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = ReviewKind.entries.firstOrNull { it.name == inputData.getString(KEY_REVIEW_KIND) }
            ?: return Result.failure()
        processor.runReviewReminder(kind)
        return Result.success()
    }
}
