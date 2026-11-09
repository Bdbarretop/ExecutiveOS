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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.executiveos.core.database.DefaultCategories
import com.executiveos.core.designsystem.component.SCREEN_TITLE_TAG
import com.executiveos.core.navigation.MoreDestination
import com.executiveos.core.navigation.NavigationTags
import com.executiveos.core.navigation.TopLevelDestination
import com.executiveos.feature.capture.CAPTURE_SAVE_TAG
import com.executiveos.domain.reviews.ReviewQuestion
import com.executiveos.domain.reviews.ReviewType
import com.executiveos.feature.capture.CAPTURE_TITLE_TAG
import com.executiveos.feature.learning.LEARNING_ADD_SESSION_TAG
import com.executiveos.feature.learning.LEARNING_MINUTES_TAG
import com.executiveos.feature.learning.LEARNING_NEW_TAG
import com.executiveos.feature.learning.LEARNING_TITLE_FIELD_TAG
import com.executiveos.feature.reviews.reviewAnswerTag
import com.executiveos.feature.reviews.reviewOpenTag
import com.executiveos.feature.planning.BLOCK_SAVE_TAG
import com.executiveos.feature.planning.BLOCK_TITLE_TAG
import com.executiveos.feature.planning.PLANNING_ADD_TAG
import com.executiveos.feature.projects.PROJECTS_NEW_FIELD_TAG
import com.executiveos.feature.projects.PROJECTS_NEW_TAG
import com.executiveos.feature.projects.PROJECT_ADD_MILESTONE_TAG
import com.executiveos.feature.projects.PROJECT_MILESTONE_NAME_TAG
import com.executiveos.feature.tasks.TASKS_NEW_FIELD_TAG
import com.executiveos.feature.tasks.TASKS_NEW_TAG
import com.executiveos.feature.tasks.TASKS_SEARCH_TAG
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

    private fun capture(title: String) {
        composeRule.onNodeWithTag(CAPTURE_TITLE_TAG).performTextInput(title)
        composeRule.onNodeWithTag(CAPTURE_SAVE_TAG).performScrollTo().performClick()
        waitForText(text(R.string.capture_saved))
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
        openTopLevel(TopLevelDestination.TASKS)
        assertScreenTitle(R.string.section_tasks)

        openTopLevel(TopLevelDestination.INBOX)
        assertScreenTitle(R.string.section_inbox)
    }

    @Test
    fun captureIsFullScreenAndHidesTheNavigation() {
        openCapture()

        assertScreenTitle(R.string.capture_title)
        composeRule.onNodeWithTag(NavigationTags.CAPTURE_BUTTON).assertDoesNotExist()
    }

    @Test
    fun aBlankCaptureAsksForATitle() {
        openCapture()

        composeRule.onNodeWithTag(CAPTURE_SAVE_TAG).performScrollTo().performClick()

        waitForText(text(R.string.capture_title_required))
    }

    @Test
    fun savedCaptureAppearsInTheInboxAndTheFormStaysReady() {
        openCapture()

        capture("Preparar informe de riesgos")

        assertScreenTitle(R.string.capture_title)
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        waitForFirstScreen()
        openTopLevel(TopLevelDestination.INBOX)
        waitForText("Preparar informe de riesgos")
    }

    @Test
    fun addingContextSavesTheCaptureAndOpensTheFullRecord() {
        openCapture()
        composeRule.onNodeWithTag(CAPTURE_TITLE_TAG).performTextInput("Revisar propuesta")

        composeRule.onNodeWithText(text(R.string.capture_add_context))
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text(R.string.task_title)).fetchSemanticsNodes().isNotEmpty()
        }
        assertScreenTitle(R.string.task_title)
        waitForText(text(R.string.field_what_to_do))
    }

    @Test
    fun aCaptureCanBeConvertedToATaskFromTheInbox() {
        openCapture()
        capture("Enviar acta")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        waitForFirstScreen()
        openTopLevel(TopLevelDestination.INBOX)
        waitForText("Enviar acta")

        composeRule.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        composeRule.onNodeWithText(text(R.string.inbox_action_to_task)).performClick()

        waitForText(text(R.string.inbox_empty_pending_title))
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.inbox_tab_processed, 1))
            .performClick()
        waitForText("Enviar acta")
    }

    @Test
    fun aNewTaskCanBeCreatedSearchedAndCompleted() {
        openTopLevel(TopLevelDestination.TASKS)
        composeRule.onNodeWithTag(TASKS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(TASKS_NEW_FIELD_TAG).performTextInput("Revisar indicadores")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()

        waitForText(text(R.string.task_title))
        waitForText(text(R.string.task_status_label))
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        openTopLevel(TopLevelDestination.TASKS)
        waitForText("Revisar indicadores")

        composeRule.onNodeWithTag(TASKS_SEARCH_TAG).performTextInput("inexistente")
        waitForText(text(R.string.tasks_empty_title))
        composeRule.onNodeWithTag(TASKS_SEARCH_TAG).performTextClearance()
        waitForText("Revisar indicadores")

        composeRule.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        composeRule.onNodeWithText(text(R.string.tasks_action_complete)).performClick()
        waitForText(text(R.string.tasks_done_completed))
    }

    @Test
    fun aNewTaskShowsItsPriorityAsNotEvaluatedUntilItHasScales() {
        openTopLevel(TopLevelDestination.TASKS)
        composeRule.onNodeWithTag(TASKS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(TASKS_NEW_FIELD_TAG).performTextInput("Sin evaluar")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()

        waitForText(text(R.string.level_pending))
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("Sin evaluar: falta definir", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeRecommendsARealTaskAndLetsTheUserRemoveIt() {
        waitForText(text(R.string.home_focus_empty_title))
        openTopLevel(TopLevelDestination.TASKS)
        composeRule.onNodeWithTag(TASKS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(TASKS_NEW_FIELD_TAG).performTextInput("Preparar comité")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(text(R.string.task_status_label))
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        openTopLevel(TopLevelDestination.HOME)
        waitForText("Preparar comité")

        composeRule.onNodeWithText(text(R.string.home_focus_remove)).performScrollTo().performClick()
        waitForText(text(R.string.home_focus_empty_title))
        waitForText(text(R.string.home_focus_reset))
    }

    @Test
    fun aProjectMeasuresProgressByItsWeightedMilestones() {
        openTopLevel(TopLevelDestination.PROJECTS)
        waitForText(text(R.string.projects_empty_title))
        composeRule.onNodeWithTag(PROJECTS_NEW_TAG).performClick()
        composeRule.onNodeWithTag(PROJECTS_NEW_FIELD_TAG).performTextInput("Implantar ERP")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(text(R.string.project_tracking))
        waitForText(text(R.string.projects_no_milestones))

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
        waitForText(text(R.string.planning_day_empty))

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
        assertScreenTitle(R.string.section_planning)

        composeRule.onNodeWithText(text(R.string.planning_view_week)).performClick()
        waitForText(text(R.string.planning_capacity_title))
        composeRule.onNodeWithText(text(R.string.planning_view_month)).performClick()
        waitForText(text(R.string.planning_capacity_title))

        openTopLevel(TopLevelDestination.HOME)
        waitForText(text(R.string.home_time_planned))
        waitForText("1 h")
    }

    private fun openMoreSection(destination: MoreDestination) {
        openTopLevel(TopLevelDestination.MORE)
        composeRule.onNodeWithTag(NavigationTags.more(destination)).performScrollTo().performClick()
        assertScreenTitle(destination.labelRes)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aDailyReviewKeepsItsAnswers() {
        openMoreSection(MoreDestination.REVIEWS)
        waitForText(text(R.string.review_history_empty))

        composeRule.onNodeWithTag(reviewOpenTag(ReviewType.DAILY)).performClick()
        val field = reviewAnswerTag(ReviewQuestion.TOP_PRIORITIES)
        waitForTag(field)
        composeRule.onNodeWithTag(field).performScrollTo().performTextInput("Comité de riesgos")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        waitForText(text(R.string.review_done))
        composeRule.onNodeWithTag(reviewOpenTag(ReviewType.DAILY)).performClick()
        waitForText("Comité de riesgos")
    }

    @Test
    fun theWeeklyReviewShowsSuggestionsLabelledAsHeuristics() {
        openMoreSection(MoreDestination.REVIEWS)
        waitForTag(reviewOpenTag(ReviewType.WEEKLY))

        composeRule.onNodeWithTag(reviewOpenTag(ReviewType.WEEKLY)).performClick()

        waitForText(text(R.string.review_summary_title))
        waitForText(text(R.string.review_recommendations_note))
        waitForText(text(R.string.recommend_deep_work))
    }

    @Test
    fun aLearningGoalCountsItsSessions() {
        openMoreSection(MoreDestination.LEARNING)
        waitForText(text(R.string.learning_empty_title))

        composeRule.onNodeWithTag(LEARNING_NEW_TAG).performClick()
        composeRule.onNodeWithTag(LEARNING_TITLE_FIELD_TAG).performTextInput("Basilea III")
        composeRule.onNodeWithText("Regulación").performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(text(R.string.learning_sessions))

        composeRule.onNodeWithTag(LEARNING_ADD_SESSION_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(LEARNING_MINUTES_TAG).performTextInput("45")
        composeRule.onNodeWithText(text(R.string.action_accept)).performClick()
        waitForText(composeRule.activity.getString(R.string.learning_goal_total, "45 min"))
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        waitForText("Basilea III")
        waitForText("45 min")
    }

    @Test
    fun metricsExplainEachIndicator() {
        openMoreSection(MoreDestination.METRICS)

        waitForText(text(R.string.metrics_priority))
        waitForText(text(R.string.metrics_compliance_def))
        composeRule.onNodeWithText(text(R.string.metrics_period_quarter)).performClick()
        waitForText(text(R.string.metrics_by_category_def))
    }

    @Test
    fun moreListsTheRemainingSections() {
        openTopLevel(TopLevelDestination.MORE)

        assertScreenTitle(R.string.section_more)
        MoreDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(NavigationTags.more(destination))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun settingsReadsRealCatalogFromRoomAndAppliesTheSelectedTheme() {
        openTopLevel(TopLevelDestination.MORE)
        composeRule.onNodeWithTag(NavigationTags.more(MoreDestination.SETTINGS))
            .performScrollTo()
            .performClick()
        assertScreenTitle(R.string.section_settings)

        waitForText(DefaultCategories.all.size.toString())

        // La opción solo aparece marcada cuando el valor ha vuelto desde DataStore.
        val darkOption = hasText(text(R.string.settings_theme_dark))
        composeRule.onNode(darkOption).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(darkOption and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun templatesCanBeOpenedFromSettings() {
        openTopLevel(TopLevelDestination.MORE)
        composeRule.onNodeWithTag(NavigationTags.more(MoreDestination.SETTINGS))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText(text(R.string.settings_templates)).performClick()

        assertScreenTitle(R.string.templates_title)
        waitForText("Preparar informe")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
