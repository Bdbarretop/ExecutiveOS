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
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)

    private fun assertScreenTitle(@StringRes id: Int) {
        composeRule.onNodeWithTag(SCREEN_TITLE_TAG).assertTextEquals(text(id))
    }

    private fun openTopLevel(destination: TopLevelDestination) {
        composeRule.onNodeWithTag(NavigationTags.topLevel(destination)).performClick()
    }

    @Before
    fun waitForFirstScreen() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(SCREEN_TITLE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun captureButtonIsVisibleAndHasAnAccessibleName() {
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(text(R.string.action_capture))
    }

    @Test
    fun everySectionIsReachable() {
        val topLevel = TopLevelDestination.entries - TopLevelDestination.MORE
        topLevel.forEach { destination ->
            openTopLevel(destination)
            assertScreenTitle(destination.labelRes)
        }

        MoreDestination.entries.forEach { destination ->
            openTopLevel(TopLevelDestination.MORE)
            composeRule.onNodeWithTag(NavigationTags.more(destination)).performClick()
            assertScreenTitle(destination.labelRes)
            composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            assertScreenTitle(R.string.section_more)
        }
    }

    @Test
    fun captureOpensFullScreenAndStatesItIsNotBuiltYet() {
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON).performClick()

        assertScreenTitle(R.string.capture_title)
        composeRule.onNodeWithText(text(R.string.placeholder_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON).assertDoesNotExist()
    }

    /** Deja guardado el tema claro para comprobar después que sobrevive al cierre de la app. */
    @Test
    fun settingsReadsTheRoomCatalogAndSavesTheTheme() {
        openTopLevel(TopLevelDestination.MORE)
        composeRule.onNodeWithTag(NavigationTags.more(MoreDestination.SETTINGS)).performClick()

        val expectedCount = DefaultCategories.all.size.toString()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(expectedCount).fetchSemanticsNodes().isNotEmpty()
        }

        val lightOption = hasText(text(R.string.settings_theme_light))
        composeRule.onNode(lightOption).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(lightOption and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
