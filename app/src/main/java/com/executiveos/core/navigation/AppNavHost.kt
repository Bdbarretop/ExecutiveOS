package com.executiveos.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.designsystem.component.SectionPlaceholder
import com.executiveos.feature.capture.QuickCaptureScreen
import com.executiveos.feature.dashboard.DashboardScreen
import com.executiveos.feature.inbox.InboxScreen
import com.executiveos.feature.more.MoreScreen
import com.executiveos.feature.settings.PrioritySettingsScreen
import com.executiveos.feature.settings.SettingsScreen
import com.executiveos.feature.tasks.TaskEditorScreen
import com.executiveos.feature.tasks.TasksScreen
import com.executiveos.feature.templates.TemplateEditorScreen
import com.executiveos.feature.templates.TemplatesScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val navigateUp: () -> Unit = { navController.navigateUp() }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            DashboardScreen(
                onOpenTask = { navController.navigate(TaskEditorRoute(it)) },
                onOpenView = { navController.navigate(TasksRoute(it.name)) },
            )
        }
        composable<InboxRoute> {
            InboxScreen(onOpenTask = { navController.navigate(TaskEditorRoute(it)) })
        }
        composable<TasksRoute> {
            TasksScreen(onOpenTask = { navController.navigate(TaskEditorRoute(it)) })
        }
        composable<ProjectsRoute> { PendingSection(R.string.section_projects, phase = 4) }

        navigation<MoreGraph>(startDestination = MoreRoute) {
            composable<MoreRoute> {
                MoreScreen(onDestinationSelected = { navController.navigate(it.route) })
            }
            composable<PlanningRoute> {
                PendingSection(R.string.section_planning, phase = 4, onNavigateUp = navigateUp)
            }
            composable<LearningRoute> {
                PendingSection(R.string.section_learning, phase = 5, onNavigateUp = navigateUp)
            }
            composable<ReviewsRoute> {
                PendingSection(R.string.section_reviews, phase = 5, onNavigateUp = navigateUp)
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onNavigateUp = navigateUp,
                    onOpenTemplates = { navController.navigate(TemplatesRoute) },
                    onOpenPrioritySettings = { navController.navigate(PrioritySettingsRoute) },
                )
            }
        }

        composable<CaptureRoute> {
            QuickCaptureScreen(
                onClose = navigateUp,
                onOpenTask = { taskId, section ->
                    navController.navigate(TaskEditorRoute(taskId, section?.name)) {
                        if (section != null) popUpTo<CaptureRoute> { inclusive = true }
                    }
                },
                onManageTemplates = { navController.navigate(TemplatesRoute) },
            )
        }
        composable<TaskEditorRoute> {
            TaskEditorScreen(
                onClose = navigateUp,
                onOpenTask = { navController.navigate(TaskEditorRoute(it)) },
            )
        }
        composable<PrioritySettingsRoute> { PrioritySettingsScreen(onClose = navigateUp) }
        composable<TemplatesRoute> {
            TemplatesScreen(
                onNavigateUp = navigateUp,
                onOpenTemplate = { navController.navigate(TemplateEditorRoute(it)) },
            )
        }
        composable<TemplateEditorRoute> { TemplateEditorScreen(onClose = navigateUp) }
    }
}

@Composable
private fun PendingSection(
    @StringRes titleRes: Int,
    phase: Int,
    onNavigateUp: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ExecutiveTopBar(title = stringResource(titleRes), onNavigateUp = onNavigateUp)
        SectionPlaceholder(phase = phase)
    }
}
