package com.executiveos.feature.settings

import com.executiveos.domain.model.Category
import com.executiveos.domain.model.ThemeMode
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.UserPreferencesRepository
import com.executiveos.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)
    private val categories = mockk<CategoryRepository>()

    private fun TestScope.collectState(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun combinesSavedThemeWithRealCategoryCount() = runTest {
        every { preferences.themeMode } returns flowOf(ThemeMode.DARK)
        every { categories.observeCategories() } returns flowOf(
            listOf(Category("a", "Trabajo", 0), Category("b", "Estudio", 1)),
        )
        val viewModel = SettingsViewModel(preferences, categories)

        collectState(viewModel)

        assertEquals(
            SettingsUiState(ThemeMode.DARK, DatabaseStatus.Ready(categoryCount = 2)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun reportsUnavailableDatabaseWithoutCrashing() = runTest {
        every { preferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { categories.observeCategories() } returns flow { throw IOException("sin acceso") }
        val viewModel = SettingsViewModel(preferences, categories)

        collectState(viewModel)

        assertEquals(DatabaseStatus.Unavailable, viewModel.uiState.value.databaseStatus)
    }

    @Test
    fun selectingAThemePersistsIt() = runTest {
        every { preferences.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { categories.observeCategories() } returns flowOf(emptyList())
        val viewModel = SettingsViewModel(preferences, categories)

        viewModel.onThemeSelected(ThemeMode.LIGHT)

        coVerify(exactly = 1) { preferences.setThemeMode(ThemeMode.LIGHT) }
    }
}
