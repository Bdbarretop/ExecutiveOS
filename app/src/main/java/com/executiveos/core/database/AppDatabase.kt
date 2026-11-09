package com.executiveos.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.executiveos.data.local.dao.AuditLogDao
import com.executiveos.data.local.dao.CategoryDao
import com.executiveos.data.local.dao.InboxDao
import com.executiveos.data.local.dao.NoteDao
import com.executiveos.data.local.dao.ProjectDao
import com.executiveos.data.local.dao.TagDao
import com.executiveos.data.local.dao.TaskDao
import com.executiveos.data.local.dao.TemplateDao
import com.executiveos.data.local.dao.UserSettingsDao
import com.executiveos.data.local.entity.AuditLogEntity
import com.executiveos.data.local.entity.CaptureTemplateEntity
import com.executiveos.data.local.entity.CategoryEntity
import com.executiveos.data.local.entity.InboxItemEntity
import com.executiveos.data.local.entity.NoteEntity
import com.executiveos.data.local.entity.CapacitySettingsEntity
import com.executiveos.data.local.entity.PlanningDao
import com.executiveos.data.local.entity.ProjectEntity
import com.executiveos.data.local.entity.ProjectMilestoneEntity
import com.executiveos.data.local.entity.ProjectRiskEntity
import com.executiveos.data.local.entity.ProjectTrackingDao
import com.executiveos.data.local.entity.TimeBlockEntity
import com.executiveos.data.local.entity.ReminderDao
import com.executiveos.data.local.entity.ReminderEntity
import com.executiveos.data.local.entity.ReminderSettingsEntity
import com.executiveos.data.local.entity.TagEntity
import com.executiveos.data.local.entity.TaskDependencyEntity
import com.executiveos.data.local.entity.TaskEntity
import com.executiveos.data.local.entity.TaskTagCrossRef
import com.executiveos.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        CategoryEntity::class,
        InboxItemEntity::class,
        TaskEntity::class,
        ProjectEntity::class,
        NoteEntity::class,
        CaptureTemplateEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        AuditLogEntity::class,
        TaskDependencyEntity::class,
        UserSettingsEntity::class,
        ReminderEntity::class,
        ReminderSettingsEntity::class,
        ProjectMilestoneEntity::class,
        ProjectRiskEntity::class,
        TimeBlockEntity::class,
        CapacitySettingsEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AddCaptureTablesMigration::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun inboxDao(): InboxDao
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun noteDao(): NoteDao
    abstract fun templateDao(): TemplateDao
    abstract fun tagDao(): TagDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun projectTrackingDao(): ProjectTrackingDao
    abstract fun planningDao(): PlanningDao

    companion object {
        const val SCHEMA_VERSION = 5
        const val FILE_NAME = "executive_os.db"
    }
}

/** Las tablas nuevas son aditivas; quienes ya tenían la app reciben también las plantillas. */
class AddCaptureTablesMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        seedDefaultTemplates(db, System.currentTimeMillis())
    }
}
