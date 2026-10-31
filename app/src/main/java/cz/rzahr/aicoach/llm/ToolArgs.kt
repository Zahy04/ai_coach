package cz.rzahr.aicoach.llm

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Tolerantní čtení argumentů nástrojů. Modely posílají čísla různě:
 * 500, 500.0, "500", "78,5" – kotlinx `intOrNull` by na 500.0 vrátil null
 * a hodnota by se tiše nahradila výchozí. Čisté funkce – testovatelné bez Androidu.
 */
internal object ToolArgs {

    private fun JsonObject?.primitive(key: String): JsonPrimitive? =
        (this?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }

    fun string(args: JsonObject?, key: String): String? =
        args.primitive(key)?.content?.trim()?.takeIf { it.isNotEmpty() }

    fun double(args: JsonObject?, key: String): Double? =
        args.primitive(key)?.content?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun int(args: JsonObject?, key: String): Int? =
        double(args, key)?.takeIf { it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.roundToInt()

    fun long(args: JsonObject?, key: String): Long? =
        double(args, key)?.takeIf { it in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble() }?.roundToLong()

    fun boolean(args: JsonObject?, key: String): Boolean? =
        args.primitive(key)?.let { it.booleanOrNull ?: it.content.trim().lowercase().toBooleanStrictOrNull() }

    /** Hodnota mimo věrohodný rozsah = jako by chyběla (model halucinoval). */
    fun <T : Comparable<T>> T?.within(range: ClosedRange<T>): T? = this?.takeIf { it in range }

    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 400.0
    const val MAX_WATER_ML = 5000
    const val MAX_PORTION_G = 5000.0
    const val MAX_KCAL = 10_000
    const val MAX_MACRO_G = 1000.0
    const val MAX_WORKOUT_MIN = 24 * 60
}

/**
 * Výběr výživových hodnot pro log_food. Odhad modelu má přednost – zná kontext
 * (vařené vs. syrové, velikost porce). Open Food Facts doplní jen tehdy, když model
 * kalorie neposlal: první výsledek hledání bývá často jiný produkt (suchá rýže,
 * instantní směs), takže přepisovat jím rozumný odhad dělalo i trojnásobné chyby.
 */
internal object NutritionChoice {

    data class Result(val nutrition: ResolvedNutrition, val fromOff: Boolean)

    fun needsLookup(model: ResolvedNutrition): Boolean = model.calories == null

    fun choose(model: ResolvedNutrition, off: OffNutrition?, quantityG: Double?): Result {
        if (!needsLookup(model) || off == null) return Result(model, fromOff = false)
        val grams = quantityG ?: 100.0
        val factor = grams / 100.0
        return Result(
            ResolvedNutrition(
                calories = off.caloriesPer100g?.let { (it * factor).roundToInt() },
                proteinG = off.proteinPer100g?.times(factor),
                carbsG = off.carbsPer100g?.times(factor),
                fatG = off.fatPer100g?.times(factor),
                refGrams = grams
            ),
            fromOff = true
        )
    }
}
