package com.executiveos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.executiveos.domain.model.ThemeMode
import com.executiveos.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferences: UserPreferencesRepository,
) : ViewModel() {
    /** `null` mientras se lee la preferencia, para no pintar un primer fotograma con otro tema. */
    val themeMode: StateFlow<ThemeMode?> = userPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
