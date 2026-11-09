package com.executiveos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.executiveos.di.createPreferencesDataStore
import com.executiveos.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUserPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val preferencesFile: File
        get() = File(temporaryFolder.root, "test.preferences_pb")

    /** Cierra el almacén al terminar: simula que la app se cierra y vuelve a abrirse. */
    private fun withDataStore(block: suspend (DataStore<Preferences>) -> Unit) = runBlocking {
        val job = Job()
        val dataStore = createPreferencesDataStore(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { preferencesFile },
        )
        try {
            block(dataStore)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun followsTheSystemThemeByDefault() = withDataStore { dataStore ->
        val repository = DataStoreUserPreferencesRepository(dataStore)

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun selectedThemeSurvivesReopeningTheStore() {
        withDataStore { DataStoreUserPreferencesRepository(it).setThemeMode(ThemeMode.DARK) }

        withDataStore { dataStore ->
            val repository = DataStoreUserPreferencesRepository(dataStore)

            assertEquals(ThemeMode.DARK, repository.themeMode.first())
        }
    }

    @Test
    fun unknownStoredValueFallsBackToSystem() = withDataStore { dataStore ->
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "VALOR_DE_OTRA_VERSION" }
        val repository = DataStoreUserPreferencesRepository(dataStore)

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun corruptFileFallsBackToDefaultsInsteadOfCrashing() {
        preferencesFile.writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37, 0x42))

        withDataStore { dataStore ->
            val repository = DataStoreUserPreferencesRepository(dataStore)

            assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        }
    }
}
