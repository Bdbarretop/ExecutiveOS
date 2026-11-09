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
import com.executiveos.feature.more.MoreScreen
import com.executiveos.feature.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val navigateUp: () -> Unit = { navController.navigateUp() }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> { PendingSection(R.string.section_home, phase = 3) }
        composable<InboxRoute> { PendingSection(R.string.section_inbox, phase = 1) }
        composable<TasksRoute> { PendingSection(R.string.section_tasks, phase = 2) }
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
            composable<SettingsRoute> { SettingsScreen(onNavigateUp = navigateUp) }
        }

        composable<CaptureRoute> {
            PendingSection(R.string.capture_title, phase = 1, onNavigateUp = navigateUp)
        }
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
