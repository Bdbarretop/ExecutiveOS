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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.DefaultCategories
import com.executiveos.core.designsystem.component.SCREEN_TITLE_TAG
import com.executiveos.core.navigation.MoreDestination
import com.executiveos.core.navigation.NavigationTags
import com.executiveos.core.navigation.TopLevelDestination
import com.executiveos.feature.capture.CAPTURE_SAVE_TAG
import com.executiveos.feature.capture.CAPTURE_TITLE_TAG
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
        val tag = NavigationTags.topLevel(destination)
        // Al salir de una pantalla completa, la barra reaparece tras guardar el borrador.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun waitForText(value: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openCapture() {
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(CAPTURE_TITLE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
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
    fun captureTravelsFromTheFormToTheInbox() {
        openCapture()
        composeRule.onNodeWithTag(CAPTURE_TITLE_TAG).performTextInput("Preparar informe de riesgos")
        composeRule.onNodeWithTag(CAPTURE_SAVE_TAG).performScrollTo().performClick()
        waitForText(text(R.string.capture_saved))

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        waitForFirstScreen()
        openTopLevel(TopLevelDestination.INBOX)

        waitForText("Preparar informe de riesgos")
    }

    @Test
    fun addingContextOpensTheFullRecord() {
        openCapture()
        composeRule.onNodeWithTag(CAPTURE_TITLE_TAG).performTextInput("Revisar propuesta")

        composeRule.onNodeWithText(text(R.string.capture_add_context))
            .performScrollTo()
            .performClick()

        waitForText(text(R.string.field_what_to_do))
        assertScreenTitle(R.string.task_title)
    }

    /** Vuelve a «Según el sistema» para no alterar las preferencias reales del teléfono. */
    @Test
    fun settingsReadsTheRoomCatalogAndSavesTheTheme() {
        openTopLevel(TopLevelDestination.MORE)
        composeRule.onNodeWithTag(NavigationTags.more(MoreDestination.SETTINGS)).performClick()
        waitForText(DefaultCategories.all.size.toString())

        selectTheme(R.string.settings_theme_light)
        selectTheme(R.string.settings_theme_system)
    }

    private fun selectTheme(@StringRes option: Int) {
        val matcher = hasText(text(option))
        composeRule.onNode(matcher).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
