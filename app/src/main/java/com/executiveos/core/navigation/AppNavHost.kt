package com.executiveos.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.executiveos.feature.capture.QuickCaptureScreen
import com.executiveos.feature.dashboard.DashboardScreen
import com.executiveos.feature.inbox.InboxScreen
import com.executiveos.feature.learning.LearningGoalScreen
import com.executiveos.feature.learning.LearningScreen
import com.executiveos.feature.metrics.MetricsScreen
import com.executiveos.feature.more.MoreScreen
import com.executiveos.feature.reviews.ReviewEditorScreen
import com.executiveos.feature.reviews.ReviewsScreen
import com.executiveos.feature.planning.BlockEditorScreen
import com.executiveos.feature.planning.PlanningScreen
import com.executiveos.feature.projects.ProjectEditorScreen
import com.executiveos.feature.projects.ProjectsScreen
import com.executiveos.feature.settings.BackupScreen
import com.executiveos.feature.settings.PrioritySettingsScreen
import com.executiveos.feature.settings.ReminderSettingsScreen
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
                onOpenProjects = { navController.navigate(ProjectsRoute) },
                onOpenPlanning = { navController.navigate(PlanningRoute) },
                onOpenBlock = { navController.navigate(BlockEditorRoute(blockId = it)) },
            )
        }
        composable<InboxRoute> {
            InboxScreen(onOpenTask = { navController.navigate(TaskEditorRoute(it)) })
        }
        composable<TasksRoute> {
            TasksScreen(onOpenTask = { navController.navigate(TaskEditorRoute(it)) })
        }
        composable<ProjectsRoute> {
            ProjectsScreen(onOpenProject = { navController.navigate(ProjectEditorRoute(it)) })
        }

        navigation<MoreGraph>(startDestination = MoreRoute) {
            composable<MoreRoute> {
                MoreScreen(onDestinationSelected = { navController.navigate(it.route) })
            }
            composable<PlanningRoute> {
                PlanningScreen(
                    onNavigateUp = navigateUp,
                    onOpenBlock = { navController.navigate(BlockEditorRoute(blockId = it)) },
                    onNewBlock = { navController.navigate(BlockEditorRoute(date = it.toString())) },
                )
            }
            composable<LearningRoute> {
                LearningScreen(
                    onNavigateUp = navigateUp,
                    onOpenGoal = { navController.navigate(LearningGoalRoute(it)) },
                )
            }
            composable<ReviewsRoute> {
                ReviewsScreen(
                    onNavigateUp = navigateUp,
                    onOpenReview = { type, date ->
                        navController.navigate(ReviewEditorRoute(type.name, date?.toString()))
                    },
                )
            }
            composable<MetricsRoute> { MetricsScreen(onNavigateUp = navigateUp) }
            composable<SettingsRoute> {
                SettingsScreen(
                    onNavigateUp = navigateUp,
                    onOpenTemplates = { navController.navigate(TemplatesRoute) },
                    onOpenPrioritySettings = { navController.navigate(PrioritySettingsRoute) },
                    onOpenReminderSettings = { navController.navigate(ReminderSettingsRoute) },
                    onOpenBackup = { navController.navigate(BackupRoute) },
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
        composable<ProjectEditorRoute> {
            ProjectEditorScreen(
                onClose = navigateUp,
                onOpenTask = { navController.navigate(TaskEditorRoute(it)) },
            )
        }
        composable<BlockEditorRoute> { BlockEditorScreen(onClose = navigateUp) }
        composable<BackupRoute> { BackupScreen(onNavigateUp = navigateUp) }
        composable<ReviewEditorRoute> { ReviewEditorScreen(onClose = navigateUp) }
        composable<LearningGoalRoute> { LearningGoalScreen(onClose = navigateUp) }
        composable<PrioritySettingsRoute> { PrioritySettingsScreen(onClose = navigateUp) }
        composable<ReminderSettingsRoute> { ReminderSettingsScreen(onClose = navigateUp) }
        composable<TemplatesRoute> {
            TemplatesScreen(
                onNavigateUp = navigateUp,
                onOpenTemplate = { navController.navigate(TemplateEditorRoute(it)) },
            )
        }
        composable<TemplateEditorRoute> { TemplateEditorScreen(onClose = navigateUp) }
    }
}
