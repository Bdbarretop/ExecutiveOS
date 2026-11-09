package com.executiveos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.executiveos.domain.dashboard.DailyFocus
import com.executiveos.domain.repository.DailyFocusRepository
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreDailyFocusRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DailyFocusRepository {

    override val focus: Flow<DailyFocus?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toDailyFocus() }

    override suspend fun pin(taskId: String, today: LocalDate) = update(today) {
        copy(pinnedIds = pinnedIds + taskId, dismissedIds = dismissedIds - taskId)
    }

    override suspend fun dismiss(taskId: String, today: LocalDate) = update(today) {
        copy(pinnedIds = pinnedIds - taskId, dismissedIds = dismissedIds + taskId)
    }

    override suspend fun reset(today: LocalDate) = update(today) { DailyFocus(today) }

    /** Lo guardado de otro día no se arrastra: cada día empieza con la recomendación limpia. */
    private suspend fun update(today: LocalDate, transform: DailyFocus.() -> DailyFocus) {
        dataStore.edit { stored ->
            val updated = (stored.toDailyFocus()?.takeIf { it.date == today } ?: DailyFocus(today))
                .transform()
            stored[FOCUS_DATE] = updated.date.toString()
            stored[FOCUS_PINNED] = updated.pinnedIds
            stored[FOCUS_DISMISSED] = updated.dismissedIds
        }
    }

    private fun Preferences.toDailyFocus(): DailyFocus? =
        this[FOCUS_DATE].toLocalDateOrNull()?.let { date ->
            DailyFocus(date, this[FOCUS_PINNED].orEmpty(), this[FOCUS_DISMISSED].orEmpty())
        }

    private companion object {
        val FOCUS_DATE = stringPreferencesKey("focus_date")
        val FOCUS_PINNED = stringSetPreferencesKey("focus_pinned")
        val FOCUS_DISMISSED = stringSetPreferencesKey("focus_dismissed")
    }
}
