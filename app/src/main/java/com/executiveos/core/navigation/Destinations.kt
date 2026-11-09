package com.executiveos.core.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.executiveos.R
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object InboxRoute
@Serializable data object TasksRoute
@Serializable data object ProjectsRoute
@Serializable data object MoreGraph
@Serializable data object MoreRoute
@Serializable data object PlanningRoute
@Serializable data object LearningRoute
@Serializable data object ReviewsRoute
@Serializable data object SettingsRoute
@Serializable data object CaptureRoute
@Serializable data class TaskEditorRoute(val taskId: String, val section: String? = null)
@Serializable data object TemplatesRoute
@Serializable data class TemplateEditorRoute(val templateId: String)
@Serializable data object PrioritySettingsRoute

/** Secciones de la ficha a las que se puede saltar desde la captura rápida. */
enum class TaskSection { CONTEXT, IMPORTANCE, NEXT_ACTION, MORE }

/** Espacios de la barra inferior. Material recomienda un máximo de cinco. */
enum class TopLevelDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val route: Any,
) {
    HOME(R.string.section_home, R.drawable.ic_home, HomeRoute),
    INBOX(R.string.section_inbox, R.drawable.ic_inbox, InboxRoute),
    TASKS(R.string.section_tasks, R.drawable.ic_task, TasksRoute),
    PROJECTS(R.string.section_projects, R.drawable.ic_folder, ProjectsRoute),
    MORE(R.string.section_more, R.drawable.ic_more, MoreGraph),
}

/** Secciones principales que no caben en la barra inferior y se abren desde «Más». */
enum class MoreDestination(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val route: Any,
) {
    PLANNING(
        R.string.section_planning,
        R.string.section_planning_description,
        R.drawable.ic_calendar,
        PlanningRoute,
    ),
    LEARNING(
        R.string.section_learning,
        R.string.section_learning_description,
        R.drawable.ic_school,
        LearningRoute,
    ),
    REVIEWS(
        R.string.section_reviews,
        R.string.section_reviews_description,
        R.drawable.ic_review,
        ReviewsRoute,
    ),
    SETTINGS(
        R.string.section_settings,
        R.string.section_settings_description,
        R.drawable.ic_tune,
        SettingsRoute,
    ),
}

object NavigationTags {
    const val CAPTURE_BUTTON = "capture_button"

    fun topLevel(destination: TopLevelDestination): String = "nav_${destination.name.lowercase()}"

    fun more(destination: MoreDestination): String = "more_${destination.name.lowercase()}"
}
