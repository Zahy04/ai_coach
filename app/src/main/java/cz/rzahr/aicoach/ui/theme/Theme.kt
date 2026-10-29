package cz.rzahr.aicoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = SignalContainer,
    onPrimaryContainer = OnSignalContainer,
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = MossContainer,
    onSecondaryContainer = Color(0xFF0B2A1C),
    tertiary = Cobalt,
    onTertiary = Color.White,
    tertiaryContainer = CobaltContainer,
    onTertiaryContainer = Color(0xFF0A1A5C),
    background = PaperBackground,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperContainer,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHigh,
    outline = PaperOutline,
    outlineVariant = PaperHairline,
    inverseSurface = Ink,
    inverseOnSurface = PaperBackground,
    inversePrimary = SignalDark,
    error = Brick,
    onError = Color.White,
    errorContainer = BrickContainer,
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color.Black
)

private val DarkScheme = darkColorScheme(
    primary = SignalDark,
    onPrimary = Color(0xFF2B0D00),
    primaryContainer = SignalContainerDark,
    onPrimaryContainer = OnSignalContainerDark,
    secondary = MossDark,
    onSecondary = Color(0xFF00391F),
    secondaryContainer = MossContainerDark,
    onSecondaryContainer = Color(0xFFCDE8D8),
    tertiary = CobaltDark,
    onTertiary = Color(0xFF0A1A5C),
    tertiaryContainer = CobaltContainerDark,
    onTertiaryContainer = Color(0xFFDCE2FF),
    background = GraphiteBackground,
    onBackground = Chalk,
    surface = GraphiteSurface,
    onSurface = Chalk,
    surfaceVariant = GraphiteContainer,
    onSurfaceVariant = ChalkMuted,
    surfaceContainerLowest = GraphiteBackground,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = GraphiteContainer,
    surfaceContainerHigh = GraphiteContainerHigh,
    surfaceContainerHighest = GraphiteContainerHigh,
    outline = GraphiteOutline,
    outlineVariant = GraphiteHairline,
    inverseSurface = Chalk,
    inverseOnSurface = GraphiteBackground,
    inversePrimary = Signal,
    error = BrickDark,
    onError = Color(0xFF690005),
    errorContainer = BrickContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val LocalDataColors = staticCompositionLocalOf { LightDataColors }
private val LocalInverseDataColors = staticCompositionLocalOf { DarkDataColors }

/** Barvy dat (kalorie, makra, voda, hodnocení) pro aktuální režim. */
@Composable
fun dataColors(): DataColors = LocalDataColors.current

/** Datové barvy pro plochy v inverzní barvě (inverseSurface – „inkoustová" karta). */
@Composable
fun inverseDataColors(): DataColors = LocalInverseDataColors.current

@Composable
fun AiCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalDataColors provides if (darkTheme) DarkDataColors else LightDataColors,
        LocalInverseDataColors provides if (darkTheme) LightDataColors else DarkDataColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
