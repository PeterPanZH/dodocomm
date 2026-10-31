@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import com.dodocomm.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dodocomm.app.ui.model.HistoryFilter
import com.dodocomm.app.ui.screens.AutomationScreen
import com.dodocomm.app.ui.screens.ChannelsScreen
import com.dodocomm.app.ui.screens.CrashLogsScreen
import com.dodocomm.app.ui.screens.DiagnosticsScreen
import com.dodocomm.app.ui.screens.RemoteManagementScreen
import com.dodocomm.app.ui.screens.HistoryScreen
import com.dodocomm.app.ui.screens.HomeScreen
import com.dodocomm.app.ui.screens.KeepAliveGuideScreen
import com.dodocomm.app.ui.screens.RoutingRulesScreen
import com.dodocomm.app.ui.screens.SettingsScreen
import com.dodocomm.app.ui.screens.SettingsSection
import com.dodocomm.app.ui.screens.TemplateEditorScreen

/** All navigation destinations. */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val GUIDE = "guide"
    const val TEMPLATE = "template"
    const val SETTINGS_SECTION_PREFIX = "settings_"
    const val CHANNELS = "channels"
    const val ROUTING = "routing"
    const val DIAGNOSTICS = "diagnostics"
    const val AUTOMATION = "automation"
    const val REMOTE = "remote"
    const val CRASH_LOGS = "crash_logs"

    val tabRoutes: Set<String> = setOf(HOME, HISTORY, SETTINGS)
}

private enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "首页", Icons.Rounded.Home),
    HISTORY(Routes.HISTORY, "记录", Icons.Rounded.History),
    SETTINGS(Routes.SETTINGS, "设置", Icons.Rounded.Tune),
}

/** Snackbar payload that also carries whether the message is an error. */
private class AppSnackbarVisuals(
    override val message: String,
    val isError: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/**
 * Root scaffold: bottom navigation for the three primary tabs, a full-screen
 * keep-alive guide route, and the app-wide snackbar host fed by [AppViewModel].
 */
@Composable
fun Sms2BarkRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(AppSnackbarVisuals(event.message, event.isError))
        }
    }

    // Re-probe permissions / battery / service state whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in Routes.tabRoutes

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val isError = (data.visuals as? AppSnackbarVisuals)?.isError == true
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseSurface
                    },
                    contentColor = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    },
                    dismissActionContentColor = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { height -> height / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { height -> height / 2 },
            ) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { tab -> navigateToTab(tab.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateHistory = { filter ->
                        if (filter != null) viewModel.setHistoryFilter(filter)
                        navigateToTab(Routes.HISTORY)
                    },
                    onNavigateSettings = { navigateToTab(Routes.SETTINGS) },
                    onNavigateGuide = { navController.navigate(Routes.GUIDE) },
                    onNavigateDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(viewModel = viewModel)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateGuide = { navController.navigate(Routes.GUIDE) },
                    onNavigateTemplate = { navController.navigate(Routes.TEMPLATE) },
                    onNavigateChannels = { navController.navigate(Routes.CHANNELS) },
                    onNavigateRouting = { navController.navigate(Routes.ROUTING) },
                    onNavigateDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onNavigateAutomation = { navController.navigate(Routes.AUTOMATION) },
                    onNavigateSection = { section ->
                        navController.navigate(Routes.SETTINGS_SECTION_PREFIX + section.route)
                    },
                )
            }
            SettingsSection.entries.filter { it != SettingsSection.OVERVIEW }.forEach { section ->
                composable(
                    route = Routes.SETTINGS_SECTION_PREFIX + section.route,
                    enterTransition = {
                        slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(200))
                    },
                    exitTransition = { fadeOut(tween(140)) },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = {
                        slideOutHorizontally(tween(220)) { width -> width / 4 } + fadeOut(tween(140))
                    },
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateGuide = { navController.navigate(Routes.GUIDE) },
                        onNavigateTemplate = { navController.navigate(Routes.TEMPLATE) },
                        onNavigateChannels = { navController.navigate(Routes.CHANNELS) },
                        onNavigateRouting = { navController.navigate(Routes.ROUTING) },
                        onNavigateDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                        onNavigateAutomation = { navController.navigate(Routes.AUTOMATION) },
                        section = section,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = Routes.DIAGNOSTICS,
                enterTransition = {
                    slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { width -> width / 4 } + fadeOut(tween(140))
                },
            ) {
                DiagnosticsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenChannels = { navController.navigate(Routes.CHANNELS) },
                    onOpenRouting = { navController.navigate(Routes.ROUTING) },
                    onOpenGuide = { navController.navigate(Routes.GUIDE) },
                    onOpenRetryHistory = {
                        viewModel.setHistoryFilter(HistoryFilter.RETRY)
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettingsSection = { section ->
                        navController.navigate(Routes.SETTINGS_SECTION_PREFIX + section.route)
                    },
                    onOpenCrashLogs = { navController.navigate(Routes.CRASH_LOGS) },
                )
            }
            composable(
                route = Routes.AUTOMATION,
                enterTransition = {
                    slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { width -> width / 4 } + fadeOut(tween(140))
                },
            ) {
                AutomationScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenRemote = { navController.navigate(Routes.REMOTE) },
                )
            }
            composable(
                route = Routes.REMOTE,
                enterTransition = {
                    slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { width -> width / 4 } + fadeOut(tween(140))
                },
            ) {
                RemoteManagementScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.GUIDE,
                enterTransition = {
                    slideInHorizontally(tween(260)) { width -> width / 4 } + fadeIn(tween(220))
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(240)) { width -> width / 4 } + fadeOut(tween(160))
                },
            ) {
                KeepAliveGuideScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.CHANNELS,
                enterTransition = {
                    slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { width -> width / 4 } + fadeOut(tween(140))
                },
            ) {
                ChannelsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ROUTING,
                enterTransition = {
                    slideInHorizontally(tween(240)) { width -> width / 4 } + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { width -> width / 4 } + fadeOut(tween(140))
                },
            ) {
                RoutingRulesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.TEMPLATE,
                enterTransition = {
                    slideInHorizontally(tween(260)) { width -> width / 4 } + fadeIn(tween(220))
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(240)) { width -> width / 4 } + fadeOut(tween(160))
                },
            ) {
                TemplateEditorScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onSelect: (AppTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            AppTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = { onSelect(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
