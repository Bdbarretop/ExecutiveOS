package com.executiveos.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Asegura que el esquema exportado existe y coincide con las entidades compiladas. */
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
        helper.createDatabase(DATABASE_NAME, AppDatabase.SCHEMA_VERSION).close()

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            AppDatabase.SCHEMA_VERSION,
            validateDroppedTables = true,
        ).close()
    }

    private companion object {
        const val DATABASE_NAME = "schema-validation.db"
    }
}
