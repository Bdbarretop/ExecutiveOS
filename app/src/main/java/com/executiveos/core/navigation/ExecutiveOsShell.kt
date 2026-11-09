package com.executiveos.core.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.executiveos.R

@Composable
fun ExecutiveOsShell(
    pendingTaskId: String? = null,
    onPendingTaskHandled: () -> Unit = {},
    pendingReviewType: String? = null,
    onPendingReviewHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // Una notificación puede pedir abrir directamente una tarea o una revisión.
    LaunchedEffect(pendingTaskId) {
        if (pendingTaskId != null) {
            navController.navigate(TaskEditorRoute(pendingTaskId))
            onPendingTaskHandled()
        }
    }
    LaunchedEffect(pendingReviewType) {
        if (pendingReviewType != null) {
            navController.navigate(ReviewEditorRoute(pendingReviewType))
            onPendingReviewHandled()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isFullScreen = currentDestination.isFullScreen()
    val adaptiveType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination.isIn(destination),
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(painterResource(destination.iconRes), contentDescription = null)
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    modifier = Modifier.testTag(NavigationTags.topLevel(destination)),
                )
            }
        },
        layoutType = if (isFullScreen) NavigationSuiteType.None else adaptiveType,
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            floatingActionButton = {
                if (!isFullScreen) {
                    CaptureButton(onClick = { navController.navigateToCapture() })
                }
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit) {
    val label = stringResource(R.string.action_capture)
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(label) },
        // La etiqueta visible de este componente no llega al árbol de accesibilidad.
        modifier = Modifier
            .testTag(NavigationTags.CAPTURE_BUTTON)
            .semantics { contentDescription = label },
    )
}

/** Capturar y editar son tareas a pantalla completa, sin navegación que distraiga. */
private fun NavDestination?.isFullScreen(): Boolean = this != null && (
    hasRoute(CaptureRoute::class) ||
        hasRoute(TaskEditorRoute::class) ||
        hasRoute(TemplateEditorRoute::class) ||
        hasRoute(PrioritySettingsRoute::class) ||
        hasRoute(ReminderSettingsRoute::class) ||
        hasRoute(ProjectEditorRoute::class) ||
        hasRoute(BlockEditorRoute::class) ||
        hasRoute(ReviewEditorRoute::class) ||
        hasRoute(LearningGoalRoute::class) ||
        hasRoute(BackupRoute::class)
    )

private fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToCapture() {
    navigate(CaptureRoute) { launchSingleTop = true }
}
