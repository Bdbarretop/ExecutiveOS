package com.executiveos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.executiveos.data.local.entity.AuditLogEntity
import com.executiveos.data.local.entity.CaptureTemplateEntity
import com.executiveos.data.local.entity.InboxItemEntity
import com.executiveos.data.local.entity.InboxItemWithDetails
import com.executiveos.data.local.entity.NoteEntity
import com.executiveos.data.local.entity.ProjectEntity
import com.executiveos.data.local.entity.TagEntity
import com.executiveos.data.local.entity.TaskDependencyEntity
import com.executiveos.data.local.entity.TaskEntity
import com.executiveos.data.local.entity.TaskReferenceRow
import com.executiveos.data.local.entity.TaskTagCrossRef
import com.executiveos.data.local.entity.TaskTitleRow
import com.executiveos.data.local.entity.TaskWithRelations
import com.executiveos.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    /** Devuelve -1 si el identificador ya existe: así una doble pulsación no duplica. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: InboxItemEntity): Long

    @Update
    suspend fun update(item: InboxItemEntity)

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun findById(id: String): InboxItemEntity?

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    @Query("SELECT * FROM inbox_items ORDER BY created_at DESC")
    fun observeAll(): Flow<List<InboxItemWithDetails>>
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE source_inbox_item_id = :itemId LIMIT 1")
    suspend fun findByInboxItem(itemId: String): TaskEntity?

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT id, title FROM tasks " +
            "WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'ARCHIVED')",
    )
    fun observeOpenTitles(): Flow<List<TaskTitleRow>>

    /** Las capturas sin procesar viven en la bandeja, no en las vistas de tareas. */
    @Transaction
    @Query("SELECT * FROM tasks WHERE status != 'INBOX'")
    fun observeListRows(): Flow<List<TaskWithRelations>>

    @Query("SELECT id, title, status FROM tasks WHERE parent_task_id = :parentId ORDER BY created_at")
    fun observeSubtasks(parentId: String): Flow<List<TaskReferenceRow>>

    @Query("SELECT id, title, status FROM tasks")
    fun observeReferences(): Flow<List<TaskReferenceRow>>

    @Query(
        "SELECT t.id, t.title, t.status FROM tasks t " +
            "JOIN task_dependencies d ON d.depends_on_task_id = t.id " +
            "WHERE d.task_id = :taskId ORDER BY t.title COLLATE NOCASE",
    )
    fun observeDependencies(taskId: String): Flow<List<TaskReferenceRow>>

    @Query("SELECT * FROM task_dependencies")
    fun observeAllDependencies(): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies")
    suspend fun allDependencies(): List<TaskDependencyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDependency(dependency: TaskDependencyEntity): Long

    @Query("DELETE FROM task_dependencies WHERE task_id = :taskId AND depends_on_task_id = :dependsOnId")
    suspend fun removeDependency(taskId: String, dependsOnId: String)
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = :id")
    fun observe(id: String = UserSettingsEntity.SINGLETON_ID): Flow<UserSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: UserSettingsEntity)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(crossRef: TaskTagCrossRef)

    @Query("DELETE FROM task_tags WHERE task_id = :taskId")
    suspend fun unlinkAll(taskId: String)

    @Query(
        "SELECT t.name FROM tags t JOIN task_tags x ON x.tag_id = t.id " +
            "WHERE x.task_id = :taskId ORDER BY t.name",
    )
    fun observeNames(taskId: String): Flow<List<String>>

    @Query(
        "SELECT t.name FROM tags t JOIN task_tags x ON x.tag_id = t.id " +
            "WHERE x.task_id = :taskId ORDER BY t.name",
    )
    suspend fun namesOf(taskId: String): List<String>
}

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun findById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): NoteEntity?
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM capture_templates ORDER BY sort_order")
    fun observeAll(): Flow<List<CaptureTemplateEntity>>

    @Query("SELECT * FROM capture_templates WHERE id = :id")
    fun observeById(id: String): Flow<CaptureTemplateEntity?>

    @Query("SELECT * FROM capture_templates WHERE id = :id")
    suspend fun findById(id: String): CaptureTemplateEntity?

    @Update
    suspend fun update(template: CaptureTemplateEntity)
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE entity_id = :entityId ORDER BY created_at")
    suspend fun findFor(entityId: String): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log WHERE entity_id IN (:entityIds) ORDER BY created_at DESC")
    fun observeFor(entityIds: List<String>): Flow<List<AuditLogEntity>>
}
