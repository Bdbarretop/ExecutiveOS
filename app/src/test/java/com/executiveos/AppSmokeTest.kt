package com.executiveos

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.DefaultCategories
import com.executiveos.core.designsystem.component.SCREEN_TITLE_TAG
import com.executiveos.core.navigation.MoreDestination
import com.executiveos.core.navigation.NavigationTags
import com.executiveos.core.navigation.TopLevelDestination
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Arranca la actividad real con el grafo real de Hilt, Room y DataStore. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class AppSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

    private fun assertScreenTitle(@StringRes id: Int) {
        composeRule.onNodeWithTag(SCREEN_TITLE_TAG).assertTextEquals(text(id))
    }

    @Before
    fun waitForFirstScreen() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(SCREEN_TITLE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun opensOnHomeWithBottomNavigationAndCaptureButton() {
        assertScreenTitle(R.string.section_home)
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(NavigationTags.topLevel(destination)).assertIsDisplayed()
        }
        // El lector de pantalla debe poder anunciar el nombre del botón.
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(text(R.string.action_capture))
    }

    @Test
    fun bottomNavigationSwitchesBetweenSections() {
        composeRule.onNodeWithTag(NavigationTags.topLevel(TopLevelDestination.TASKS)).performClick()
        assertScreenTitle(R.string.section_tasks)

        composeRule.onNodeWithTag(NavigationTags.topLevel(TopLevelDestination.INBOX)).performClick()
        assertScreenTitle(R.string.section_inbox)
    }

    @Test
    fun captureOpensFullScreenAndStatesItIsNotBuiltYet() {
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON).performClick()

        assertScreenTitle(R.string.capture_title)
        composeRule.onNodeWithText(text(R.string.placeholder_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON).assertDoesNotExist()
    }

    @Test
    fun moreListsTheRemainingSections() {
        composeRule.onNodeWithTag(NavigationTags.topLevel(TopLevelDestination.MORE)).performClick()

        assertScreenTitle(R.string.section_more)
        MoreDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(NavigationTags.more(destination)).assertIsDisplayed()
        }
    }

    @Test
    fun settingsReadsRealCatalogFromRoomAndAppliesTheSelectedTheme() {
        composeRule.onNodeWithTag(NavigationTags.topLevel(TopLevelDestination.MORE)).performClick()
        composeRule.onNodeWithTag(NavigationTags.more(MoreDestination.SETTINGS)).performClick()
        assertScreenTitle(R.string.section_settings)

        val expectedCount = DefaultCategories.all.size.toString()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(expectedCount).fetchSemanticsNodes().isNotEmpty()
        }

        // La opción solo aparece marcada cuando el valor ha vuelto desde DataStore.
        val darkOption = hasText(text(R.string.settings_theme_dark))
        composeRule.onNode(darkOption).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(darkOption and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
