package com.cautious5.crisis_coach.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cautious5.crisis_coach.ui.screens.dashboard.DashboardScreen
import com.cautious5.crisis_coach.ui.screens.translate.TranslateScreen
import com.cautious5.crisis_coach.ui.screens.imagetriage.ImageTriageScreen
import com.cautious5.crisis_coach.ui.screens.knowledge.KnowledgeScreen
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.Constants.Routes
import com.cautious5.crisis_coach.utils.Constants.NavigationLabels
import com.guru.fontawesomecomposelib.FaIcon
import com.guru.fontawesomecomposelib.FaIconType
import com.guru.fontawesomecomposelib.FaIcons

/**
 * Main navigation component for Crisis Coach app using Jetpack Compose Navigation
 * Implements bottom navigation with proper state management and deep linking support
 */

private const val TAG = LogTags.NAVIGATION

/**
 * Navigation destination data class
 */
data class NavigationDestination(
    val route: String,
    val label: String,
    val icon: FaIconType,
    val selectedIcon: FaIconType? = null
)

/**
 * Available navigation destinations
 */
val navigationDestinations = listOf(
    NavigationDestination(
        route = Routes.DASHBOARD,
        label = NavigationLabels.DASHBOARD,
        icon = FaIcons.Home
    ),
    NavigationDestination(
        route = Routes.TRANSLATE,
        label = NavigationLabels.TRANSLATE,
        icon = FaIcons.Microphone
    ),
    NavigationDestination(
        route = Routes.IMAGE_TRIAGE,
        label = NavigationLabels.IMAGE_TRIAGE,
        icon = FaIcons.Camera
    ),
    NavigationDestination(
        route = Routes.KNOWLEDGE,
        label = NavigationLabels.KNOWLEDGE,
        icon = FaIcons.QuestionCircle
    )
)

/**
 * Main app navigation scaffold with bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DASHBOARD,
    onSettingsClick: () -> Unit = {}
) {
    Log.d(TAG, "Setting up app navigation with start destination: $startDestination")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val currentRoute = navController.getCurrentRoute()
                    Text(
                        text = NavigationHelper.getRouteLabel(currentRoute ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            AppBottomNavigation(navController = navController)
        }
    ) { paddingValues ->
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Bottom navigation bar component
 */
@Composable
private fun AppBottomNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = modifier.height(80.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        navigationDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == destination.route
            } == true

            NavigationBarItem(
                icon = {
                    FaIcon(
                        faIcon = if (selected && destination.selectedIcon != null)
                            destination.selectedIcon
                        else
                            destination.icon,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = selected,
                onClick = {
                    Log.d(TAG, "Navigating to: ${destination.route}")
                    navigateToDestination(navController, destination.route)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/**
 * Navigation host containing all screen destinations
 */
@Composable
private fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Dashboard Screen
        composable(Routes.DASHBOARD) {
            Log.d(TAG, "Displaying Dashboard screen")
            DashboardScreen(
                onNavigateToTranslate = {
                    navigateToDestination(navController, Routes.TRANSLATE)
                },
                onNavigateToImageTriage = {
                    navigateToDestination(navController, Routes.IMAGE_TRIAGE)
                },
                onNavigateToKnowledge = {
                    navigateToDestination(navController, Routes.KNOWLEDGE)
                }
            )
        }

        // Translation Screen
        composable(Routes.TRANSLATE) {
            Log.d(TAG, "Displaying Translate screen")
            TranslateScreen()
        }

        // Image Triage Screen
        composable(Routes.IMAGE_TRIAGE) {
            Log.d(TAG, "Displaying Image Triage screen")
            ImageTriageScreen()
        }

        // Knowledge Base Screen
        composable(Routes.KNOWLEDGE) {
            Log.d(TAG, "Displaying Knowledge screen")
            KnowledgeScreen()
        }
    }
}

/**
 * Navigate to a destination with proper back stack management
 */
private fun navigateToDestination(
    navController: NavHostController,
    route: String
) {
    try {
        navController.navigate(route) {
            // Pop up to the start destination and save state
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            // Avoid multiple copies of the same destination
            launchSingleTop = true
            // Restore state when re-selecting a previously selected item
            restoreState = true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Navigation error to route: $route", e)
    }
}

/**
 * Extension function to check if current destination matches route
 */
@Composable
fun NavHostController.isCurrentDestination(route: String): Boolean {
    val navBackStackEntry by currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route == route
}

/**
 * Extension function to get current route
 */
@Composable
fun NavHostController.getCurrentRoute(): String? {
    val navBackStackEntry by currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

/**
 * Navigation state management for ViewModels
 */
class NavigationState {
    private val _currentRoute = mutableStateOf<String?>(null)
    val currentRoute: State<String?> = _currentRoute

    private val _canNavigateBack = mutableStateOf(false)
    val canNavigateBack: State<Boolean> = _canNavigateBack

    fun updateCurrentRoute(route: String?) {
        _currentRoute.value = route
        Log.d(TAG, "Current route updated to: $route")
    }

    fun updateCanNavigateBack(canNavigate: Boolean) {
        _canNavigateBack.value = canNavigate
    }
}

/**
 * Remember navigation state across recomposition
 */
@Composable
fun rememberNavigationState(): NavigationState {
    return remember { NavigationState() }
}

/**
 * Navigation helpers for ViewModels
 */
object NavigationHelper {

    /**
     * Checks if a route requires specific permissions
     */
    fun requiresPermissions(route: String): List<String> {
        return when (route) {
            Routes.TRANSLATE -> listOf(
                android.Manifest.permission.RECORD_AUDIO
            )
            Routes.IMAGE_TRIAGE -> listOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            Routes.KNOWLEDGE -> listOf(
                android.Manifest.permission.RECORD_AUDIO // For voice search
            )
            else -> emptyList()
        }
    }

    /**
     * Gets the navigation label for a route
     */
    fun getRouteLabel(route: String): String {
        return when (route) {
            Routes.DASHBOARD -> NavigationLabels.DASHBOARD
            Routes.TRANSLATE -> NavigationLabels.TRANSLATE
            Routes.IMAGE_TRIAGE -> NavigationLabels.IMAGE_TRIAGE
            Routes.KNOWLEDGE -> NavigationLabels.KNOWLEDGE
            else -> "Unknown"
        }
    }

    /**
     * Checks if route is a main destination (shown in bottom nav)
     */
    fun isMainDestination(route: String): Boolean {
        return navigationDestinations.any { it.route == route }
    }

    /**
     * Gets the default route when app starts
     */
    fun getDefaultRoute(): String = Routes.DASHBOARD

    /**
     * Validates if a route exists
     */
    fun isValidRoute(route: String): Boolean {
        val validRoutes = listOf(
            Routes.DASHBOARD,
            Routes.TRANSLATE,
            Routes.IMAGE_TRIAGE,
            Routes.KNOWLEDGE,
            Routes.SETTINGS
        )
        return validRoutes.contains(route)
    }
}

/**
 * Navigation analytics helper
 */
object NavigationAnalytics {
    private var navigationStartTime = 0L

    fun onNavigationStart(fromRoute: String?, toRoute: String) {
        navigationStartTime = System.currentTimeMillis()
        Log.d(TAG, "Navigation started: $fromRoute -> $toRoute")
    }

    fun onNavigationComplete(route: String) {
        val duration = System.currentTimeMillis() - navigationStartTime
        Log.d(TAG, "Navigation completed to $route in ${duration}ms")
    }

    fun onNavigationError(route: String, error: Throwable) {
        Log.e(TAG, "Navigation error to $route", error)
    }
}