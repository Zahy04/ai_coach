package cz.rzahr.aicoach.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.chat.ChatScreen
import cz.rzahr.aicoach.ui.dashboard.DashboardScreen
import cz.rzahr.aicoach.ui.food.FoodDiaryScreen
import cz.rzahr.aicoach.ui.mensa.MensaScreen
import cz.rzahr.aicoach.ui.photos.PhotoCompareScreen
import cz.rzahr.aicoach.ui.photos.PhotoDetailScreen
import cz.rzahr.aicoach.ui.photos.PhotosFolderScreen
import cz.rzahr.aicoach.ui.photos.PhotosScreen
import cz.rzahr.aicoach.ui.photos.PhotosSlideshowScreen
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
    const val PHOTO_FOLDER = "photos_folder/{pose}"
    const val PHOTO_SLIDESHOW = "photos_slideshow/{pose}"
    const val MENSA = "mensa"

    fun photoDetail(photoId: Long) = "photo_detail/$photoId"
    fun photoCompare(firstId: Long, secondId: Long) = "photo_compare/$firstId/$secondId"

    // Název složky volí uživatel – "/", "?", "#" nebo "%" bez kódování rozbily routu
    // (pád aplikace, nebo otevření jiné složky). Navigation argument sama dekóduje.
    fun photoFolder(pose: String) = "photos_folder/${Uri.encode(pose)}"
    fun photoSlideshow(pose: String) = "photos_slideshow/${Uri.encode(pose)}"
}

private data class BottomTab(
    val route: String,
    val navigateTo: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun AiCoachApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = setOf(Routes.DASHBOARD, Routes.FOOD, Routes.CHAT, Routes.WEIGHT, Routes.PHOTOS)
    val showBottomBar = currentRoute in tabRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Vnitřní obrazovky si stavový řádek řeší samy; spodek řeší lišta (nebo obrazovka, když lišta chybí).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute = currentRoute, onNavigate = { navController.navigateToTab(it) })
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { slideInHorizontally(tween(300)) { it / 10 } + fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { slideOutHorizontally(tween(260)) { it / 10 } + fadeOut(tween(220)) }
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onWeeklySummary = {
                        navController.navigate("chat?weekly=true") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenTab = { navController.navigateToTab(it) },
                    onOpenChat = { navController.navigateToTab(Routes.CHAT_PLAIN) }
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
                PhotosScreen(onOpenFolder = { navController.navigate(Routes.photoFolder(it)) })
            }
            composable(
                route = Routes.PHOTO_FOLDER,
                arguments = listOf(navArgument("pose") { type = NavType.StringType })
            ) { entry ->
                val pose = entry.arguments?.getString("pose") ?: ""
                PhotosFolderScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { navController.navigate(Routes.photoDetail(it)) },
                    onOpenCompare = { first, second -> navController.navigate(Routes.photoCompare(first, second)) },
                    onOpenSlideshow = { navController.navigate(Routes.photoSlideshow(pose)) }
                )
            }
            composable(
                route = Routes.PHOTO_SLIDESHOW,
                arguments = listOf(navArgument("pose") { type = NavType.StringType })
            ) {
                PhotosSlideshowScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PHOTO_DETAIL,
                arguments = listOf(navArgument("photoId") { type = NavType.LongType })
            ) { entry ->
                PhotoDetailScreen(
                    photoId = entry.arguments?.getLong("photoId") ?: 0L,
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
                PhotoCompareScreen(
                    firstId = entry.arguments?.getLong("firstId") ?: 0L,
                    secondId = entry.arguments?.getLong("secondId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Spodní lišta: čtyři záložky a uprostřed vyvýšené tlačítko kouče –
 * chat je hlavní způsob, jak do aplikace cokoli zapsat.
 */
@Composable
private fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val left = listOf(
        BottomTab(Routes.DASHBOARD, Routes.DASHBOARD, stringResource(R.string.tab_today), Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
        BottomTab(Routes.FOOD, Routes.FOOD, stringResource(R.string.tab_food), Icons.Outlined.Restaurant, Icons.Filled.Restaurant)
    )
    val right = listOf(
        BottomTab(Routes.WEIGHT, Routes.WEIGHT, stringResource(R.string.tab_weight), Icons.Outlined.MonitorWeight, Icons.Filled.MonitorWeight),
        BottomTab(Routes.PHOTOS, Routes.PHOTOS, stringResource(R.string.tab_photos), Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary)
    )
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                left.forEach { tab -> TabItem(tab, currentRoute == tab.route, onNavigate, Modifier.weight(1f)) }
                CoachTabButton(
                    selected = currentRoute == Routes.CHAT,
                    onClick = { onNavigate(Routes.CHAT_PLAIN) },
                    modifier = Modifier.weight(1f)
                )
                right.forEach { tab -> TabItem(tab, currentRoute == tab.route, onNavigate, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TabItem(tab: BottomTab, selected: Boolean, onNavigate: (String) -> Unit, modifier: Modifier) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable { onNavigate(tab.navigateTo) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun CoachTabButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.tab_coach)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.primary
                )
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    contentDescription = label
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(bounded = true),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
