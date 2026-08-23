package cz.rzahr.aicoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val OnProtein = Color(0xFF062740)
private val OnCalories = Color(0xFF2E1200)
private val ErrorContainerDark = Color(0xFF4A1016)
private val OnErrorContainerDark = Color(0xFFFFD9DC)

private val DarkScheme = darkColorScheme(
    primary = LimePrimary,
    onPrimary = LimeOnPrimary,
    primaryContainer = LimeContainer,
    onPrimaryContainer = LimeOnContainer,
    secondary = ProteinBlue,
    onSecondary = OnProtein,
    secondaryContainer = SurfaceHighDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = CaloriesOrange,
    onTertiary = OnCalories,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = DangerRed,
    onError = Color(0xFF3A0008),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

data class ExtendedColors(
    val calories: Color = CaloriesOrange,
    val protein: Color = ProteinBlue,
    val carbs: Color = CarbsAmber,
    val fat: Color = FatCoral,
    val success: Color = SuccessGreen
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

@Composable
fun extendedColors(): ExtendedColors = LocalExtendedColors.current

@Composable
fun AiCoachTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides ExtendedColors()) {
        MaterialTheme(
            colorScheme = DarkScheme,
            typography = AppTypography,
            content = content
        )
    }
}
