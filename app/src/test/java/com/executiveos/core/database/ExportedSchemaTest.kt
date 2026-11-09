package com.executiveos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Falla si las entidades cambian sin subir la versión ni versionar el esquema en Git. */
@RunWith(AndroidJUnit4::class)
class ExportedSchemaTest {
    @Test
    fun committedSchemaMatchesCompiledEntities() {
        assertEquals(committedIdentityHash(), compiledIdentityHash())
    }

    private fun committedIdentityHash(): String {
        val schemaPath = "schemas/${AppDatabase::class.java.name}/${AppDatabase.SCHEMA_VERSION}.json"
        return Json.parseToJsonElement(File(schemaPath).readText()).jsonObject
            .getValue("database").jsonObject
            .getValue("identityHash").jsonPrimitive.content
    }

    private fun compiledIdentityHash(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            return database.openHelper.writableDatabase
                .query("SELECT identity_hash FROM room_master_table")
                .use { cursor ->
                    cursor.moveToFirst()
                    cursor.getString(0)
                }
        } finally {
            database.close()
        }
    }
}
