@file:OptIn(ExperimentalTextApi::class)

package cz.rzahr.aicoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
    manropeFont(FontWeight.ExtraBold),
    manropeFont(FontWeight.Black)
)

private val defaultTypography = Typography()

val AppTypography: Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Normal),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium)
)
