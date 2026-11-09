package com.executiveos.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.data.local.entity.CategoryEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val seedInstant = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(seedInstant, ZoneOffset.UTC)

    private fun openFileDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app-database-test.db")
            .addCallback(DefaultDataSeedCallback(clock))
            .build()

    private fun openInMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()

    @Test
    fun newDatabaseIsSeededWithTheDefaultCatalogInOrder() = runBlocking {
        val database = openInMemoryDatabase()

        val categories = database.categoryDao().observeAll().first()

        assertEquals(DefaultCategories.all.map { it.name }, categories.map { it.name })
        assertEquals(DefaultCategories.all.map { it.id }, categories.map { it.id })
        assertTrue(categories.all { it.isSystem && it.createdAt == seedInstant })
        database.close()
    }

    @Test
    fun reopeningTheDatabaseKeepsDataAndDoesNotSeedAgain() = runBlocking {
        val firstSession = openFileDatabase()
        firstSession.categoryDao().insert(userCategory("Consultoría", sortOrder = 7))
        firstSession.close()

        val secondSession = openFileDatabase()
        val names = secondSession.categoryDao().observeAll().first().map { it.name }

        assertEquals(DefaultCategories.all.size + 1, names.size)
        assertEquals("Consultoría", names.last())
        secondSession.close()
    }

    @Test
    fun duplicateCategoryNameIsRejectedInsteadOfReplacingData() = runBlocking {
        val database = openInMemoryDatabase()

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.categoryDao().insert(userCategory("Trabajo", sortOrder = 99)) }
        }

        val stored = database.categoryDao().observeAll().first()
        assertEquals(DefaultCategories.all.size, stored.size)
        database.close()
    }

    private fun userCategory(name: String, sortOrder: Int) = CategoryEntity(
        id = "11111111-2222-4333-8444-555555555555",
        name = name,
        sortOrder = sortOrder,
        isSystem = false,
        createdAt = seedInstant,
    )
}
