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
import com.executiveos.feature.planning.BLOCK_SAVE_TAG
import com.executiveos.feature.planning.BLOCK_TITLE_TAG
import com.executiveos.feature.planning.PLANNING_ADD_TAG
import com.executiveos.feature.projects.PROJECTS_NEW_FIELD_TAG
import com.executiveos.feature.projects.PROJECTS_NEW_TAG
import com.executiveos.feature.projects.PROJECT_ADD_MILESTONE_TAG
import com.executiveos.feature.projects.PROJECT_MILESTONE_NAME_TAG
import com.executiveos.feature.tasks.TASKS_NEW_FIELD_TAG
import com.executiveos.feature.tasks.TASKS_NEW_TAG
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

    @Test
    fun homeReflectsARealTask() {
        openTopLevel(TopLevelDestination.TASKS)
        composeRule.onNodeWithTag(TASKS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(TASKS_NEW_FIELD_TAG).performTextInput("Preparar comité")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(text(R.string.task_status_label))
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        openTopLevel(TopLevelDestination.HOME)

        waitForText("Preparar comité")
        waitForText(text(R.string.home_indicators_title))
    }

    @Test
    fun aTaskCanBeCreatedAndCompleted() {
        openTopLevel(TopLevelDestination.TASKS)
        composeRule.onNodeWithTag(TASKS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(TASKS_NEW_FIELD_TAG).performTextInput("Revisar indicadores")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(text(R.string.task_status_label))

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        openTopLevel(TopLevelDestination.TASKS)
        waitForText("Revisar indicadores")

        composeRule.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        composeRule.onNodeWithText(text(R.string.tasks_action_complete)).performClick()
        waitForText(text(R.string.tasks_done_completed))
    }

    @Test
    fun aProjectMeasuresProgressByItsMilestones() {
        openTopLevel(TopLevelDestination.PROJECTS)
        composeRule.onNodeWithTag(PROJECTS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(PROJECTS_NEW_FIELD_TAG).performTextInput("Implantar ERP")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(text(R.string.project_tracking))

        composeRule.onNodeWithTag(PROJECT_ADD_MILESTONE_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(PROJECT_MILESTONE_NAME_TAG).performTextInput("Diseño aprobado")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        val progress = composeRule.activity.getString(R.string.projects_progress, 0, 0, 1)
        waitForText(progress)
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        waitForText("Implantar ERP")
        waitForText(progress)
    }

    @Test
    fun aPlannedBlockAppearsInThePlanAndCountsOnHome() {
        openTopLevel(TopLevelDestination.MORE)
        composeRule.onNodeWithTag(NavigationTags.more(MoreDestination.PLANNING)).performClick()
        assertScreenTitle(R.string.section_planning)

        composeRule.onNodeWithTag(PLANNING_ADD_TAG).performScrollTo().performClick()
        waitForText(text(R.string.block_field_title))
        composeRule.onNodeWithTag(BLOCK_TITLE_TAG).performTextInput("Redactar informe")
        composeRule.onNodeWithTag(BLOCK_SAVE_TAG).performScrollTo().performClick()
        // El título también está en el campo: hay que esperar a que el editor se cierre.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text(R.string.block_field_title))
                .fetchSemanticsNodes().isEmpty()
        }
        waitForText("Redactar informe")

        composeRule.onNodeWithText(text(R.string.planning_view_week)).performClick()
        composeRule.onNodeWithText(text(R.string.planning_view_month)).performClick()
        composeRule.onNodeWithText(text(R.string.planning_view_day)).performClick()
        waitForText("Redactar informe")

        openTopLevel(TopLevelDestination.HOME)
        waitForText(text(R.string.home_time_planned))
        waitForText("1 h")
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
