package com.executiveos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.executiveos.di.preferencesCorruptionHandler
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.DateOption
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreCaptureDraftRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "draft.preferences_pb")

    private fun withStore(block: suspend (DataStore<Preferences>) -> Unit) = runBlocking {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = preferencesCorruptionHandler(),
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun draftSurvivesClosingTheApp() {
        val draft = CaptureDraft(
            id = "b1",
            title = "Enviar acta",
            details = "Incluir anexos",
            categoryId = "trabajo",
            dateOption = DateOption.CUSTOM,
            customDate = LocalDate.of(2026, 12, 1),
            dateIsDeadline = true,
            templateId = "plantilla",
        )
        withStore { DataStoreCaptureDraftRepository(it).saveDraft(draft) }

        withStore { store ->
            assertEquals(draft, DataStoreCaptureDraftRepository(store).loadDraft())
        }
    }

    @Test
    fun withoutADraftEachLoadStartsWithANewIdentifier() = withStore { store ->
        val repository = DataStoreCaptureDraftRepository(store)

        assertNotEquals(repository.loadDraft().id, repository.loadDraft().id)
    }

    @Test
    fun newDraftsStartWithTheLastUsedCategory() = withStore { store ->
        val repository = DataStoreCaptureDraftRepository(store)

        repository.rememberCategory("estudio")

        assertEquals("estudio", repository.loadDraft().categoryId)
    }

    @Test
    fun corruptStoredDateIsIgnored() = withStore { store ->
        store.edit { it[stringPreferencesKey("draft_custom_date")] = "no-es-fecha" }

        assertNull(DataStoreCaptureDraftRepository(store).loadDraft().customDate)
    }
}
