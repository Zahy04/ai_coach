package cz.rzahr.aicoach.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.rzahr.aicoach.ui.chat.ChatScreen
import cz.rzahr.aicoach.ui.dashboard.DashboardScreen
import cz.rzahr.aicoach.ui.food.FoodDiaryScreen
import cz.rzahr.aicoach.ui.mensa.MensaScreen
import cz.rzahr.aicoach.ui.photos.PhotoCompareScreen
import cz.rzahr.aicoach.ui.photos.PhotoDetailScreen
import cz.rzahr.aicoach.ui.photos.PhotosScreen
import cz.rzahr.aicoach.ui.settings.SettingsScreen
import cz.rzahr.aicoach.ui.weight.WeightScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat?weekly={weekly}"
    const val CHAT_PLAIN = "chat"
    const val WEIGHT = "weight"
    const val FOOD = "food"
    const val PHOTOS = "photos"
    const val SETTINGS = "settings"
    const val PHOTO_DETAIL = "photo_detail/{photoId}"
    const val PHOTO_COMPARE = "photo_compare/{firstId}/{secondId}"
    const val MENSA = "mensa"

    fun photoDetail(photoId: Long) = "photo_detail/$photoId"
    fun photoCompare(firstId: Long, secondId: Long) = "photo_compare/$firstId/$secondId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Přehled", Icons.Filled.SpaceDashboard),
    BottomTab(Routes.CHAT, "Chat", Icons.Filled.Chat),
    BottomTab(Routes.WEIGHT, "Váha", Icons.Filled.MonitorWeight),
    BottomTab(Routes.FOOD, "Jídlo", Icons.Filled.Restaurant),
    BottomTab(Routes.PHOTOS, "Fotky", Icons.Filled.PhotoLibrary)
)

@Composable
fun AiCoachApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                val target = if (tab.route == Routes.CHAT) Routes.CHAT_PLAIN else tab.route
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideInHorizontally(tween(320)) { it / 8 } + fadeIn(tween(320))
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = {
                slideOutHorizontally(tween(280)) { it / 8 } + fadeOut(tween(240))
            }
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onWeeklySummary = { navController.navigate("chat?weekly=true") },
                    onOpenTab = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("weekly") {
                    type = NavType.StringType
                    defaultValue = "false"
                })
            ) { entry ->
                ChatScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    weeklySummaryRequested = entry.arguments?.getString("weekly") == "true"
                )
            }
            composable(Routes.WEIGHT) { WeightScreen() }
            composable(Routes.FOOD) {
                FoodDiaryScreen(onOpenMensa = { navController.navigate(Routes.MENSA) })
            }
            composable(Routes.MENSA) {
                MensaScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PHOTOS) {
                PhotosScreen(
                    onOpenDetail = { navController.navigate(Routes.photoDetail(it)) },
                    onOpenCompare = { first, second -> navController.navigate(Routes.photoCompare(first, second)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PHOTO_DETAIL,
                arguments = listOf(navArgument("photoId") { type = NavType.LongType })
            ) { entry ->
                val photoId = entry.arguments?.getLong("photoId") ?: 0L
                PhotoDetailScreen(
                    photoId = photoId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.PHOTO_COMPARE,
                arguments = listOf(
                    navArgument("firstId") { type = NavType.LongType },
                    navArgument("secondId") { type = NavType.LongType }
                )
            ) { entry ->
                val firstId = entry.arguments?.getLong("firstId") ?: 0L
                val secondId = entry.arguments?.getLong("secondId") ?: 0L
                PhotoCompareScreen(
                    firstId = firstId,
                    secondId = secondId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
