package com.extensionbox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.extensionbox.app.ui.components.AppScaffold
import com.extensionbox.app.ui.screens.*
import com.extensionbox.app.ui.theme.ExtensionBoxTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.apply(this)
        enableEdgeToEdge()
        requestPerms()

        setContent {
            val themeIndex by Prefs.getIntFlow(this, "app_theme", ThemeHelper.MONET).collectAsState(initial = ThemeHelper.MONET)
            
            // Re-apply activity theme when index changes (for window background etc)
            LaunchedEffect(themeIndex) {
                ThemeHelper.apply(this@MainActivity)
            }

            ExtensionBoxTheme {
                MainApp()
            }
        }
    }

    private fun requestPerms() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }
}

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    data object Extensions : Screen("extensions", "Extensions", Icons.Filled.Extension, Icons.Outlined.Extension)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object About : Screen("about", "About", Icons.Filled.Info, Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Dashboard, Screen.Extensions, Screen.Settings, Screen.About)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val currentScreen = screens.find { it.route == currentDestination?.route } ?: Screen.Dashboard
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val dashboardViewModel: com.extensionbox.app.ui.viewmodel.DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val isTopLevel = currentDestination?.route in screens.map { it.route }
    val title = if (isTopLevel) {
        currentScreen.title
    } else if (currentDestination?.route?.startsWith("module/") == true) {
        val key = navBackStackEntry?.arguments?.getString("key") ?: ""
        com.extensionbox.app.ui.ModuleRegistry.nameFor(key)
    } else {
        ""
    }

    AppScaffold(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (!isTopLevel) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        bottomBar = {
            if (isTopLevel) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        screens.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    ) 
                                },
                                label = { Text(screen.title) },
                                selected = selected,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ),
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            enterTransition = { 
                fadeIn(animationSpec = tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400))
            },
            exitTransition = { 
                fadeOut(animationSpec = tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400))
            },
            popEnterTransition = { 
                fadeIn(animationSpec = tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400))
            },
            popExitTransition = { 
                fadeOut(animationSpec = tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400))
            }
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(viewModel = dashboardViewModel, onModuleClick = { key ->
                navController.navigate("module/$key")
            }) }
            composable(Screen.Extensions.route) { ExtensionsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.About.route) { AboutScreen() }
            composable("module/{key}") { backStackEntry ->
                val key = backStackEntry.arguments?.getString("key") ?: return@composable
                ModuleDetailScreen(moduleKey = key, viewModel = dashboardViewModel)
            }
        }
    }
}
