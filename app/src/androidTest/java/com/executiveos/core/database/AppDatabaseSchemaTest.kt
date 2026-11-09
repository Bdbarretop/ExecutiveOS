package com.executiveos.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Valida los esquemas exportados y las migraciones sobre SQLite real. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun exportedSchemaMatchesCurrentEntities() {
        helper.createDatabase(CURRENT_DATABASE, AppDatabase.SCHEMA_VERSION).close()

        helper.runMigrationsAndValidate(
            CURRENT_DATABASE,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).close()
    }

    @Test
    fun migratingFromVersionOneKeepsDataAndAddsTheTemplates() {
        helper.createDatabase(MIGRATED_DATABASE, 1).use { database ->
            database.execSQL(
                "INSERT INTO categories (id, name, sort_order, is_system, created_at) " +
                    "VALUES ('existente', 'Consultoría', 99, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(
            MIGRATED_DATABASE,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).use { database ->
            assertEquals(1, database.count("SELECT COUNT(*) FROM categories WHERE id = 'existente'"))
            assertEquals(DEFAULT_TEMPLATE_COUNT, database.count("SELECT COUNT(*) FROM capture_templates"))
        }
    }

    @Test
    fun migratingToVersionThreeKeepsTasksTagsAndHistory() {
        helper.createDatabase(UPGRADED_DATABASE, 2).use { database ->
            database.execSQL(
                "INSERT INTO tasks (id, status, title, delegation_status, created_at, updated_at) " +
                    "VALUES ('t1', 'PENDING', 'Informe', 'NOT_DELEGATED', 0, 0)",
            )
            database.execSQL("INSERT INTO tags (id, name) VALUES ('g1', 'comité')")
            database.execSQL("INSERT INTO task_tags (task_id, tag_id) VALUES ('t1', 'g1')")
            database.execSQL(
                "INSERT INTO audit_log (id, entity_type, entity_id, action, created_at) " +
                    "VALUES ('a1', 'TASK', 't1', 'CREATED', 0)",
            )
        }

        helper.runMigrationsAndValidate(
            UPGRADED_DATABASE,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).use { database ->
            assertEquals(1, database.count("SELECT COUNT(*) FROM tasks WHERE title = 'Informe'"))
            assertEquals(1, database.count("SELECT COUNT(*) FROM task_tags WHERE task_id = 't1'"))
            assertEquals(1, database.count("SELECT COUNT(*) FROM audit_log"))
            assertEquals(1, database.count("SELECT COUNT(*) FROM tasks WHERE manual_priority IS NULL"))
        }
    }

    @Test
    fun migratingToVersionFourKeepsTasksAndAddsReminderTables() {
        helper.createDatabase(REMINDERS_DATABASE, 3).use { database ->
            database.execSQL(
                "INSERT INTO tasks (id, status, title, delegation_status, created_at, updated_at) " +
                    "VALUES ('t1', 'PENDING', 'Informe', 'NOT_DELEGATED', 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(
            REMINDERS_DATABASE,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).use { database ->
            assertEquals(1, database.count("SELECT COUNT(*) FROM tasks WHERE id = 't1'"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM reminders"))
        }
    }

    @Test
    fun migratingToVersionFiveKeepsProjectsAndAddsPlanningTables() {
        helper.createDatabase(PLANNING_DATABASE, 4).use { database ->
            database.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES ('p1', 'Implantar ERP', 'PLANNED', 0, 0)",
            )
            database.execSQL(
                "INSERT INTO tasks " +
                    "(id, status, title, delegation_status, project_id, created_at, updated_at) " +
                    "VALUES ('t1', 'PENDING', 'Informe', 'NOT_DELEGATED', 'p1', 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(
            PLANNING_DATABASE,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).use { database ->
            assertEquals(
                1,
                database.count("SELECT COUNT(*) FROM projects WHERE id = 'p1' AND target_date IS NULL"),
            )
            assertEquals(1, database.count("SELECT COUNT(*) FROM tasks WHERE project_id = 'p1'"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM project_milestones"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM time_blocks"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM capacity_settings"))
        }
    }

    @Test
    fun migratingToVersionSixKeepsBlocksAndAddsReviewAndLearningTables() {
        helper.createDatabase(REVIEWS_DATABASE, 5).use { database ->
            database.execSQL(
                "INSERT INTO time_blocks (id, type, title, start_at, end_at, updated_at) " +
                    "VALUES ('b1', 'DEEP_WORK', 'Informe', 0, 3600000, 0)",
            )
        }

        helper.runMigrationsAndValidate(
            REVIEWS_DATABASE,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).use { database ->
            assertEquals(1, database.count("SELECT COUNT(*) FROM time_blocks WHERE id = 'b1'"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM reviews"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM learning_goals"))
            assertEquals(0, database.count("SELECT COUNT(*) FROM learning_sessions"))
        }
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val CURRENT_DATABASE = "schema-validation.db"
        const val MIGRATED_DATABASE = "schema-migration.db"
        const val UPGRADED_DATABASE = "schema-upgrade.db"
        const val REMINDERS_DATABASE = "schema-reminders.db"
        const val PLANNING_DATABASE = "schema-planning.db"
        const val REVIEWS_DATABASE = "schema-reviews.db"
    }
}
