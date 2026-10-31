package cz.rzahr.aicoach.ui.theme

import androidx.compose.ui.graphics.Color

// „Signal" – teplý papír, inkoust a signální oranžová. Data mají vlastní barvy,
// akcent je jen jeden, aby čísla zůstala čitelná a nic nekřičelo.

// Světlý režim
internal val PaperBackground = Color(0xFFF4F1EA)
internal val PaperSurface = Color(0xFFFFFDF8)
internal val PaperContainer = Color(0xFFEBE6DB)
internal val PaperContainerHigh = Color(0xFFE2DCCF)
internal val PaperOutline = Color(0xFFCFC7B6)
internal val PaperHairline = Color(0xFFE3DDD0)
internal val Ink = Color(0xFF1B1A16)
internal val InkMuted = Color(0xFF6B665A)

internal val Signal = Color(0xFFE4521B)
internal val SignalContainer = Color(0xFFFFDCCC)
internal val OnSignalContainer = Color(0xFF3A1100)
internal val Moss = Color(0xFF2D6A4F)
internal val MossContainer = Color(0xFFCDE8D8)
internal val Cobalt = Color(0xFF3454C2)
internal val CobaltContainer = Color(0xFFDCE2FF)
internal val Brick = Color(0xFFB3261E)
internal val BrickContainer = Color(0xFFF9DEDA)

// Tmavý režim
internal val GraphiteBackground = Color(0xFF11110F)
internal val GraphiteSurface = Color(0xFF1A1916)
internal val GraphiteContainer = Color(0xFF24231F)
internal val GraphiteContainerHigh = Color(0xFF2E2D28)
internal val GraphiteOutline = Color(0xFF494640)
internal val GraphiteHairline = Color(0xFF2F2E29)
internal val Chalk = Color(0xFFF2EEE4)
internal val ChalkMuted = Color(0xFFA39E91)

internal val SignalDark = Color(0xFFFF7A45)
internal val SignalContainerDark = Color(0xFF5C2511)
internal val OnSignalContainerDark = Color(0xFFFFDBCC)
internal val MossDark = Color(0xFF7FCBA4)
internal val MossContainerDark = Color(0xFF1F4734)
internal val CobaltDark = Color(0xFF9EB1FF)
internal val CobaltContainerDark = Color(0xFF26346E)
internal val BrickDark = Color(0xFFFFB4AB)
internal val BrickContainerDark = Color(0xFF6B1A14)

/** Datové barvy (kalorie, makra, voda, hodnocení), zvlášť pro světlý a tmavý režim. */
data class DataColors(
    val calories: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,
    val good: Color,
    val okay: Color,
    val bad: Color
)

internal val LightDataColors = DataColors(
    calories = Signal,
    protein = Cobalt,
    carbs = Color(0xFFC98A12),
    fat = Color(0xFFB8427A),
    water = Color(0xFF1F86B8),
    good = Color(0xFF2E8A57),
    okay = Color(0xFFC98A12),
    bad = Brick
)

internal val DarkDataColors = DataColors(
    calories = SignalDark,
    protein = CobaltDark,
    carbs = Color(0xFFF2C14E),
    fat = Color(0xFFF08DB8),
    water = Color(0xFF6CC6EE),
    good = Color(0xFF6FD39A),
    okay = Color(0xFFF2C14E),
    bad = BrickDark
)
