package com.executiveos.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.domain.model.ThemeMode
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DatabaseStatus {
    data object Loading : DatabaseStatus
    data class Ready(val categoryCount: Int) : DatabaseStatus
    data object Unavailable : DatabaseStatus
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val databaseStatus: DatabaseStatus = DatabaseStatus.Loading,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val databaseStatus = categoryRepository.observeCategories()
        .map<_, DatabaseStatus> { DatabaseStatus.Ready(categoryCount = it.size) }
        .catch { emit(DatabaseStatus.Unavailable) }

    val uiState: StateFlow<SettingsUiState> =
        combine(userPreferences.themeMode, databaseStatus, ::SettingsUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
