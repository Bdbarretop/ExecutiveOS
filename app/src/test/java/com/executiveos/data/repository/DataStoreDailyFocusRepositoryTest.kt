package com.executiveos.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.domain.dashboard.DailyFocus
import com.executiveos.di.preferencesCorruptionHandler
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Con Robolectric porque hace varias escrituras seguidas: en JVM puro, DataStore usa un
 * renombrado que en Windows no puede reemplazar un archivo existente.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreDailyFocusRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val today = LocalDate.of(2026, 11, 11)

    private fun withRepository(block: suspend (DataStoreDailyFocusRepository) -> Unit) = runBlocking {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = preferencesCorruptionHandler(),
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(temporaryFolder.root, "focus.preferences_pb") },
        )
        try {
            block(DataStoreDailyFocusRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun startsWithoutAdjustments() = withRepository { repository ->
        assertNull(repository.focus.first())
    }

    @Test
    fun pinnedAndDismissedTasksSurviveClosingTheApp() {
        withRepository {
            it.pin("elegida", today)
            it.dismiss("descartada", today)
        }

        withRepository { repository ->
            assertEquals(
                DailyFocus(today, setOf("elegida"), setOf("descartada")),
                repository.focus.first(),
            )
        }
    }

    @Test
    fun pinningUndoesARemovalAndViceVersa() = withRepository { repository ->
        repository.dismiss("a", today)
        repository.pin("a", today)
        assertEquals(DailyFocus(today, setOf("a"), emptySet()), repository.focus.first())

        repository.dismiss("a", today)
        assertEquals(DailyFocus(today, emptySet(), setOf("a")), repository.focus.first())
    }

    @Test
    fun aNewDayDiscardsYesterdaysAdjustments() = withRepository { repository ->
        repository.pin("ayer", today.minusDays(1))

        repository.pin("hoy", today)

        assertEquals(DailyFocus(today, setOf("hoy"), emptySet()), repository.focus.first())
    }

    @Test
    fun resetClearsTheDay() = withRepository { repository ->
        repository.pin("a", today)

        repository.reset(today)

        assertEquals(DailyFocus(today), repository.focus.first())
    }
}
