@file:OptIn(ExperimentalTextApi::class)

package cz.rzahr.aicoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cz.rzahr.aicoach.R

private fun manropeFont(weight: FontWeight): Font = Font(
    resId = R.font.manrope_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val Manrope = FontFamily(
    manropeFont(FontWeight.Normal),
    manropeFont(FontWeight.Medium),
    manropeFont(FontWeight.SemiBold),
    manropeFont(FontWeight.Bold),
    manropeFont(FontWeight.ExtraBold)
)

private val defaultTypography = Typography()

/** Tabulkové číslice – sloupce čísel pod sebou nesmí „tancovat". */
private const val TABULAR = "tnum"

val AppTypography: Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em,
        fontFeatureSettings = TABULAR
    ),
    displayMedium = defaultTypography.displayMedium.copy(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em,
        fontFeatureSettings = TABULAR
    ),
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em,
        fontFeatureSettings = TABULAR
    ),
    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em,
        fontFeatureSettings = TABULAR
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em
    ),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = Manrope, lineHeight = 24.sp),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = Manrope),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    labelMedium = defaultTypography.labelMedium.copy(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR
    )
)

/** Malý nadpis verzálkami nad sekcí nebo číslem („DNES", „TÝDEN"). */
val EyebrowStyle: TextStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.14.em
)

/** Velké číslo na kartě (kcal, kg, ml). */
val MetricStyle: TextStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.04).em,
    fontFeatureSettings = TABULAR
)
