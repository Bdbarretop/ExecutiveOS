package com.executiveos.data.repository

import androidx.room.withTransaction
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.ReminderEntity
import com.executiveos.data.local.entity.ReminderSettingsEntity
import com.executiveos.data.local.entity.ReminderStatus
import com.executiveos.domain.reminders.ReminderSettings
import com.executiveos.domain.reminders.ReminderType
import com.executiveos.domain.reminders.TaskReminder
import com.executiveos.domain.repository.ReminderRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineReminderRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) : ReminderRepository {
    private val dao = database.reminderDao()

    override val settings: Flow<ReminderSettings> = dao.observeSettings().map { row ->
        row?.toDomain() ?: ReminderSettings()
    }

    override suspend fun saveSettings(settings: ReminderSettings) {
        dao.saveSettings(settings.toEntity(clock.instant()))
    }

    override fun observeTaskReminder(taskId: String): Flow<TaskReminder?> =
        dao.observeActive(taskId).map { it?.toTaskReminder() }

    override suspend fun setTaskReminder(taskId: String, at: Instant): String =
        database.withTransaction {
            dao.findActive(taskId)?.let { dao.updateStatus(it.id, ReminderStatus.CANCELLED) }
            val id = UUID.randomUUID().toString()
            dao.insertIfAbsent(
                ReminderEntity(
                    id = id,
                    taskId = taskId,
                    type = ReminderType.TASK,
                    triggerAt = at,
                    status = ReminderStatus.SCHEDULED,
                    dedupeKey = "TASK:$id",
                    createdAt = clock.instant(),
                ),
            )
            id
        }

    override suspend fun clearTaskReminder(taskId: String): String? = database.withTransaction {
        dao.findActive(taskId)?.also { dao.updateStatus(it.id, ReminderStatus.CANCELLED) }?.id
    }

    override suspend fun findScheduled(reminderId: String): TaskReminder? =
        dao.findScheduled(reminderId)?.toTaskReminder()

    override suspend fun markDelivered(reminderId: String) {
        dao.updateStatus(reminderId, ReminderStatus.DELIVERED)
    }

    override suspend fun registerOnce(key: String, type: ReminderType): Boolean {
        val now = clock.instant()
        return dao.insertIfAbsent(
            ReminderEntity(
                id = UUID.randomUUID().toString(),
                taskId = null,
                type = type,
                triggerAt = now,
                status = ReminderStatus.DELIVERED,
                dedupeKey = key,
                createdAt = now,
            ),
        ) != -1L
    }

    override suspend fun deleteDeliveredBefore(instant: Instant) {
        dao.deleteFinishedBefore(instant)
    }
}

private fun ReminderEntity.toTaskReminder(): TaskReminder? =
    taskId?.let { TaskReminder(id, it, triggerAt) }

private fun ReminderSettingsEntity.toDomain() = ReminderSettings(
    deadlineRemindersEnabled = deadlineEnabled,
    deadlineDaysBefore = deadlineDaysBefore,
    criticalAlertsEnabled = criticalEnabled,
    morningNoticeMinute = morningMinute,
    dailyReviewEnabled = dailyReviewEnabled,
    dailyReviewMinute = dailyReviewMinute,
    weeklyReviewEnabled = weeklyReviewEnabled,
    weeklyReviewDay = weeklyReviewDay,
    weeklyReviewMinute = weeklyReviewMinute,
    quietHoursEnabled = quietEnabled,
    quietStartMinute = quietStartMinute,
    quietEndMinute = quietEndMinute,
)

private fun ReminderSettings.toEntity(now: Instant) = ReminderSettingsEntity(
    deadlineEnabled = deadlineRemindersEnabled,
    deadlineDaysBefore = deadlineDaysBefore,
    criticalEnabled = criticalAlertsEnabled,
    morningMinute = morningNoticeMinute,
    dailyReviewEnabled = dailyReviewEnabled,
    dailyReviewMinute = dailyReviewMinute,
    weeklyReviewEnabled = weeklyReviewEnabled,
    weeklyReviewDay = weeklyReviewDay,
    weeklyReviewMinute = weeklyReviewMinute,
    quietEnabled = quietHoursEnabled,
    quietStartMinute = quietStartMinute,
    quietEndMinute = quietEndMinute,
    updatedAt = now,
)
