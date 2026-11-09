package com.executiveos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.executiveos.domain.model.CaptureDraft
import com.executiveos.domain.model.DateOption
import com.executiveos.domain.repository.CaptureDraftRepository
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreCaptureDraftRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CaptureDraftRepository {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    override suspend fun loadDraft(): CaptureDraft {
        val stored = preferences.first()
        return CaptureDraft(
            id = stored[DRAFT_ID] ?: UUID.randomUUID().toString(),
            title = stored[DRAFT_TITLE].orEmpty(),
            details = stored[DRAFT_DETAILS].orEmpty(),
            categoryId = stored[DRAFT_CATEGORY] ?: stored[LAST_CATEGORY],
            dateOption = stored[DRAFT_DATE_OPTION].toDateOption(),
            customDate = stored[DRAFT_CUSTOM_DATE].toLocalDateOrNull(),
            dateIsDeadline = stored[DRAFT_IS_DEADLINE] ?: false,
            templateId = stored[DRAFT_TEMPLATE],
        )
    }

    override suspend fun saveDraft(draft: CaptureDraft) {
        dataStore.edit { stored ->
            stored[DRAFT_ID] = draft.id
            stored[DRAFT_TITLE] = draft.title
            stored[DRAFT_DETAILS] = draft.details
            stored.setOrRemove(DRAFT_CATEGORY, draft.categoryId)
            stored[DRAFT_DATE_OPTION] = draft.dateOption.name
            stored.setOrRemove(DRAFT_CUSTOM_DATE, draft.customDate?.toString())
            stored[DRAFT_IS_DEADLINE] = draft.dateIsDeadline
            stored.setOrRemove(DRAFT_TEMPLATE, draft.templateId)
        }
    }

    override suspend fun rememberCategory(categoryId: String?) {
        dataStore.edit { it.setOrRemove(LAST_CATEGORY, categoryId) }
    }

    override val lastProjectId: Flow<String?> = preferences.map { it[LAST_PROJECT] }

    override suspend fun rememberProject(projectId: String?) {
        dataStore.edit { it.setOrRemove(LAST_PROJECT, projectId) }
    }

    private companion object {
        val DRAFT_ID = stringPreferencesKey("draft_id")
        val DRAFT_TITLE = stringPreferencesKey("draft_title")
        val DRAFT_DETAILS = stringPreferencesKey("draft_details")
        val DRAFT_CATEGORY = stringPreferencesKey("draft_category")
        val DRAFT_DATE_OPTION = stringPreferencesKey("draft_date_option")
        val DRAFT_CUSTOM_DATE = stringPreferencesKey("draft_custom_date")
        val DRAFT_IS_DEADLINE = booleanPreferencesKey("draft_is_deadline")
        val DRAFT_TEMPLATE = stringPreferencesKey("draft_template")
        val LAST_CATEGORY = stringPreferencesKey("last_category")
        val LAST_PROJECT = stringPreferencesKey("last_project")
    }
}

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value == null) remove(key) else this[key] = value
}

private fun String?.toDateOption(): DateOption =
    DateOption.entries.firstOrNull { it.name == this } ?: DateOption.NONE

private fun String?.toLocalDateOrNull(): LocalDate? = this?.let {
    try {
        LocalDate.parse(it)
    } catch (_: DateTimeParseException) {
        null
    }
}
