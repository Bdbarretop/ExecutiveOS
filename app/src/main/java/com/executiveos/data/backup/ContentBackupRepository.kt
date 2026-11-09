package com.executiveos.data.backup

import android.content.Context
import androidx.core.net.toUri
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.local.entity.ReminderStatus
import com.executiveos.domain.backup.BackupCheck
import com.executiveos.domain.backup.BackupRepository
import com.executiveos.domain.backup.ExportFormat
import com.executiveos.domain.reminders.ReminderScheduler
import com.executiveos.domain.reminders.ReminderType
import com.executiveos.domain.repository.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Los archivos llegan por el selector del sistema, de modo que la app no pide permisos. */
class ContentBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: BackupEngine,
    private val database: AppDatabase,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
) : BackupRepository {
    override suspend fun export(format: ExportFormat, destination: String, password: String?) {
        withContext(Dispatchers.IO) {
            val data = engine.create(format, password)
            val stream = context.contentResolver.openOutputStream(destination.toUri(), "wt")
                ?: throw IOException("Destino no disponible")
            stream.use { it.write(data) }
        }
    }

    override suspend fun check(source: String, password: String?): BackupCheck =
        withContext(Dispatchers.IO) {
            try {
                engine.check(read(source), password)
            } catch (_: IOException) {
                BackupCheck.Invalid
            }
        }

    override suspend fun restore(source: String, password: String?) {
        withContext(Dispatchers.IO) {
            engine.restore(read(source), password)
            rescheduleReminders()
        }
    }

    private fun read(source: String): ByteArray {
        val stream = context.contentResolver.openInputStream(source.toUri())
            ?: throw IOException("Archivo no disponible")
        return stream.use { it.readBytes() }
    }

    /** Los avisos programados pertenecen a los datos anteriores; los de la copia se programan aquí. */
    private suspend fun rescheduleReminders() {
        val now = clock.instant()
        database.reminderDao().all()
            .filter {
                it.type == ReminderType.TASK &&
                    it.status == ReminderStatus.SCHEDULED &&
                    it.triggerAt.isAfter(now)
            }
            .forEach { reminderScheduler.scheduleTaskReminder(it.id, it.triggerAt) }
        reminderScheduler.scheduleRecurring(reminderRepository.settings.first(), replaceExisting = true)
    }
}
