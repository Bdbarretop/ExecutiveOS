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

    private fun SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val CURRENT_DATABASE = "schema-validation.db"
        const val MIGRATED_DATABASE = "schema-migration.db"
    }
}
