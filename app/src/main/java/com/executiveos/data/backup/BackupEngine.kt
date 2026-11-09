package com.executiveos.data.backup

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.executiveos.core.database.AppDatabase
import com.executiveos.data.repository.OfflineInboxRepository
import com.executiveos.data.repository.OfflineLearningRepository
import com.executiveos.data.repository.OfflinePlanningRepository
import com.executiveos.data.repository.OfflineProjectRepository
import com.executiveos.data.repository.OfflineReviewRepository
import com.executiveos.data.repository.OfflineTaskRepository
import com.executiveos.domain.backup.BackupCheck
import com.executiveos.domain.backup.BackupSummary
import com.executiveos.domain.backup.ExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal class PasswordRequiredException : Exception()

internal class NewerBackupException : Exception()

internal class InvalidBackupException(message: String) : Exception(message)

private data class TableColumn(val type: String)

class BackupEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val clock: Clock,
) {
    suspend fun create(format: ExportFormat, password: String?): ByteArray {
        val document = database.withTransaction { readDocument(database.openHelper.writableDatabase) }
        return when (format) {
            ExportFormat.PROTECTED_JSON -> BackupCrypto.encrypt(
                BackupCodec.encode(document),
                requireNotNull(password) { "La copia protegida necesita contraseña" }.toCharArray(),
            )
            ExportFormat.PLAIN_JSON -> BackupCodec.encode(document)
            ExportFormat.CSV_ZIP -> BackupCodec.csvZip(document)
        }
    }

    suspend fun check(data: ByteArray, password: String?): BackupCheck = try {
        BackupCheck.Valid(summarize(openValidated(data, password)))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: PasswordRequiredException) {
        BackupCheck.NeedsPassword
    } catch (_: WrongPasswordException) {
        BackupCheck.WrongPasswordOrDamaged
    } catch (_: NewerBackupException) {
        BackupCheck.NewerVersion
    } catch (_: Exception) {
        BackupCheck.Invalid
    }

    /** La copia se ensaya entera antes de tocar los datos, y el reemplazo es una sola transacción. */
    suspend fun restore(data: ByteArray, password: String?) {
        val document = openValidated(data, password)
        database.withTransaction { replaceAll(database.openHelper.writableDatabase, document) }
    }

    private suspend fun openValidated(data: ByteArray, password: String?): BackupDocument {
        val json = if (BackupCrypto.isEncrypted(data)) {
            BackupCrypto.decrypt(data, (password ?: throw PasswordRequiredException()).toCharArray())
        } else {
            data
        }
        val document = BackupCodec.decode(json)
        if (document.schemaVersion > AppDatabase.SCHEMA_VERSION) throw NewerBackupException()
        rehearse(document)
        return document
    }

    /**
     * Aplica la copia en una base temporal y la lee con los repositorios reales. Así se detecta lo
     * que SQLite admite pero la app no podría leer, como un estado desconocido o una fecha rota.
     */
    private suspend fun rehearse(document: BackupDocument) {
        val scratch = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            scratch.withTransaction { replaceAll(scratch.openHelper.writableDatabase, document) }
            OfflineInboxRepository(scratch, clock).observeEntries().first()
            OfflineTaskRepository(scratch, clock).observeTaskList().first()
            OfflineProjectRepository(scratch, clock).observeProjects().first()
            val planning = OfflinePlanningRepository(scratch, clock)
            planning.observeBlocks().first()
            planning.capacity.first()
            OfflineReviewRepository(scratch, clock).observeHistory().first()
            val learning = OfflineLearningRepository(scratch, clock)
            learning.observeGoals().first()
            learning.observeSessions().first()
            scratch.reminderDao().all()
        } finally {
            scratch.close()
        }
    }

    private fun summarize(document: BackupDocument) = BackupSummary(
        createdOn = document.createdAt.atZone(clock.zone).toLocalDate(),
        tasks = document.tables["tasks"].orEmpty().count { it["status"]?.content != "INBOX" },
        projects = document.tables["projects"].orEmpty().size,
        timeBlocks = document.tables["time_blocks"].orEmpty().size,
        reviews = document.tables["reviews"].orEmpty().size,
        learningGoals = document.tables["learning_goals"].orEmpty().size,
    )

    private fun readDocument(db: SupportSQLiteDatabase) = BackupDocument(
        schemaVersion = AppDatabase.SCHEMA_VERSION,
        createdAt = clock.instant(),
        tables = tableNames(db).associateWith { readRows(db, it) },
    )

    private fun tableNames(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (name !in INTERNAL_TABLES && !name.startsWith("sqlite_")) add(name)
                }
            }
        }

    private fun readRows(db: SupportSQLiteDatabase, table: String): List<BackupRow> =
        db.query("SELECT * FROM `$table`").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).associate { index ->
                        cursor.getColumnName(index) to cursor.valueAt(index)
                    })
                }
            }
        }

    private fun Cursor.valueAt(index: Int): JsonPrimitive = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(index))
        else -> error("Tipo de columna no admitido")
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Map<String, TableColumn> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(COLUMN_NAME), TableColumn(cursor.getString(COLUMN_TYPE)))
                }
            }
        }

    /**
     * Los nombres de tablas y columnas se comprueban contra el esquema real antes de usarlos:
     * un archivo manipulado no puede introducir SQL.
     */
    private fun replaceAll(db: SupportSQLiteDatabase, document: BackupDocument) {
        val schema = tableNames(db).associateWith { columns(db, it) }
        validateStructure(schema, document)
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        schema.keys.forEach { db.execSQL("DELETE FROM `$it`") }
        document.tables.forEach { (table, rows) ->
            val tableColumns = schema.getValue(table)
            rows.forEach { row -> insert(db, table, tableColumns, row) }
        }
        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) throw InvalidBackupException("Referencias rotas")
        }
    }

    private fun validateStructure(
        schema: Map<String, Map<String, TableColumn>>,
        document: BackupDocument,
    ) {
        if (!document.tables.keys.containsAll(REQUIRED_TABLES)) {
            throw InvalidBackupException("Faltan tablas esenciales")
        }
        document.tables.forEach { (table, rows) ->
            val tableColumns = schema[table] ?: throw InvalidBackupException("Tabla desconocida")
            rows.forEach { row ->
                row.forEach { (name, value) ->
                    val column = tableColumns[name] ?: throw InvalidBackupException("Columna desconocida")
                    if (!matches(column.type, value)) throw InvalidBackupException("Tipo incorrecto")
                }
            }
        }
    }

    private fun matches(type: String, value: JsonPrimitive): Boolean = when {
        value is JsonNull -> true
        type == "TEXT" -> value.isString
        type == "INTEGER" -> !value.isString && value.longOrNull != null
        type == "REAL" -> !value.isString && value.doubleOrNull != null
        else -> false
    }

    private fun insert(
        db: SupportSQLiteDatabase,
        table: String,
        tableColumns: Map<String, TableColumn>,
        row: BackupRow,
    ) {
        val names = row.keys.toList()
        val args = names.map<String, Any?> { name ->
            val value = row.getValue(name)
            when {
                value is JsonNull -> null
                tableColumns.getValue(name).type == "INTEGER" -> value.longOrNull
                tableColumns.getValue(name).type == "REAL" -> value.doubleOrNull
                else -> value.content
            }
        }
        db.execSQL(
            "INSERT INTO `$table` (${names.joinToString(",") { "`$it`" }}) " +
                "VALUES (${names.joinToString(",") { "?" }})",
            args.toTypedArray(),
        )
    }

    private companion object {
        val INTERNAL_TABLES = setOf("room_master_table", "android_metadata")
        val REQUIRED_TABLES = setOf("categories", "tasks")
        const val COLUMN_NAME = 1
        const val COLUMN_TYPE = 2
    }
}
