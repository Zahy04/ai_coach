package cz.rzahr.aicoach.llm

import android.content.Context
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.repo.FactRepository
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.data.repo.WaterRepository
import cz.rzahr.aicoach.data.repo.WeightRepository
import cz.rzahr.aicoach.data.repo.WorkoutRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class TurnContext {
    val loggedFoods = mutableSetOf<String>()
}

/** Makra vyřešená pro konkrétní porci. */
data class ResolvedNutrition(
    val calories: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val refGrams: Double?
)

data class ToolExecutionResult(
    val event: String?,
    val response: JsonObject
)

@Singleton
class ToolExecutor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val weightRepository: WeightRepository,
    private val foodRepository: FoodRepository,
    private val workoutRepository: WorkoutRepository,
    private val factRepository: FactRepository,
    private val waterRepository: WaterRepository,
    private val openFoodFactsClient: OpenFoodFactsClient
) {

    suspend fun execute(call: FunctionCall, turnContext: TurnContext): ToolExecutionResult = try {
        val name = call.name.removePrefix("default_api:")
        when (name) {
            ToolSpecs.SAVE_WEIGHT -> saveWeight(call)
            ToolSpecs.LOG_FOOD -> logFood(call, turnContext)
            ToolSpecs.LOG_WATER -> logWater(call)
            ToolSpecs.LOG_WORKOUT -> logWorkout(call)
            ToolSpecs.SAVE_FACT -> saveFact(call)
            ToolSpecs.DELETE_FACT -> deleteFact(call)
            else -> ToolExecutionResult(
                null,
                ToolSpecs.errorResult(context.getString(R.string.err_unknown_tool, call.name))
            )
        }
    } catch (e: Exception) {
        ToolExecutionResult(null, ToolSpecs.errorResult(e.message ?: context.getString(R.string.err_unexpected)))
    }

    private suspend fun saveWeight(call: FunctionCall): ToolExecutionResult {
        val weightKg = call.args?.get("weight_kg")?.jsonPrimitive?.doubleOrNull
            ?: return missingParam("weight_kg")
        val note = call.args?.get("note")?.jsonPrimitive?.contentOrNull()
        weightRepository.add(weightKg, note)
        val formatted = String.format(Locale.forLanguageTag("cs"), "%.1f", weightKg)
        return ToolExecutionResult(
            context.getString(R.string.ev_weight_saved, formatted),
            ToolSpecs.okResult()
        )
    }

    private suspend fun logFood(call: FunctionCall, turnContext: TurnContext): ToolExecutionResult {
        val name = call.args?.get("name")?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: return missingParam("name")
        val quantityG = call.args?.get("quantity_g")?.jsonPrimitive?.doubleOrNull

        val dedupeKey = name.trim().lowercase()
        if (dedupeKey in turnContext.loggedFoods) {
            return ToolExecutionResult(
                null,
                buildJsonObject {
                    put("status", "already_logged")
                    put("message", context.getString(R.string.ev_already_logged))
                }
            )
        }
        turnContext.loggedFoods.add(dedupeKey)

        // 1) Open Food Facts → 2) odhad modelu
        var sourceLabel = context.getString(R.string.ev_source_estimate)
        var source = cz.rzahr.aicoach.data.db.entity.FoodEntryEntity.SOURCE_CHAT
        var resolvedName = name
        var resolved: ResolvedNutrition

        val off = openFoodFactsClient.search(name)
        if (off != null) {
            val factor = (quantityG ?: 100.0) / 100.0
            resolved = ResolvedNutrition(
                calories = off.caloriesPer100g?.let { (it * factor).toInt() },
                proteinG = off.proteinPer100g?.times(factor),
                carbsG = off.carbsPer100g?.times(factor),
                fatG = off.fatPer100g?.times(factor),
                refGrams = quantityG ?: 100.0
            )
            if (off.productName.isNotBlank()) resolvedName = "$name (${off.productName})"
            sourceLabel = context.getString(R.string.ev_source_off)
            source = cz.rzahr.aicoach.data.db.entity.FoodEntryEntity.SOURCE_API
        } else {
            resolved = ResolvedNutrition(
                calories = call.args?.get("calories")?.jsonPrimitive?.intOrNull,
                proteinG = call.args?.get("protein_g")?.jsonPrimitive?.doubleOrNull,
                carbsG = call.args?.get("carbs_g")?.jsonPrimitive?.doubleOrNull,
                fatG = call.args?.get("fat_g")?.jsonPrimitive?.doubleOrNull,
                refGrams = quantityG
            )
        }

        foodRepository.add(
            name = name,
            calories = resolved.calories,
            proteinG = resolved.proteinG,
            carbsG = resolved.carbsG,
            fatG = resolved.fatG,
            grams = resolved.refGrams?.toInt(),
            source = source
        )

        val event = buildString {
            append(context.getString(R.string.ev_food_saved_base, resolvedName))
            resolved.calories?.let {
                append(" ")
                append(context.getString(R.string.ev_kcal_part, formatNumber(it)))
            }
            append(" · ")
            append(sourceLabel)
            quantityG?.let {
                append(context.getString(R.string.ev_grams_part, it.toInt()))
            }
            append(".")
        }

        return ToolExecutionResult(event, buildJsonObject {
            put("status", "ok")
            put("source", sourceLabel)
            put("quantity_grams", quantityG ?: 100.0)
            resolved.calories?.let { put("calories", it) }
            resolved.proteinG?.let { put("protein_g", it) }
            resolved.carbsG?.let { put("carbs_g", it) }
            resolved.fatG?.let { put("fat_g", it) }
        })
    }

    private suspend fun logWater(call: FunctionCall): ToolExecutionResult {
        val amountMl = call.args?.get("amount_ml")?.jsonPrimitive?.intOrNull ?: 250
        waterRepository.add(amountMl)
        return ToolExecutionResult(
            context.getString(R.string.ev_water_saved, amountMl),
            buildJsonObject {
                put("status", "ok")
                put("logged_ml", amountMl)
            }
        )
    }

    private suspend fun logWorkout(call: FunctionCall): ToolExecutionResult {
        val name = call.args?.get("name")?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: return missingParam("name")
        val duration = call.args?.get("duration_minutes")?.jsonPrimitive?.intOrNull
        val caloriesBurned = call.args?.get("calories_burned")?.jsonPrimitive?.intOrNull
        val note = call.args?.get("note")?.jsonPrimitive?.contentOrNull()
        workoutRepository.add(name = name, durationMinutes = duration, caloriesBurned = caloriesBurned, note = note)
        val event = buildString {
            append(
                context.getString(
                    R.string.ev_workout_saved,
                    name,
                    duration?.let { " " + context.getString(R.string.ev_duration_part, it) } ?: ""
                )
            )
        }
        return ToolExecutionResult(event, ToolSpecs.okResult())
    }

    private suspend fun saveFact(call: FunctionCall): ToolExecutionResult {
        val category = call.args?.get("category")?.jsonPrimitive?.contentOrNull()
            ?: return missingParam("category")
        val content = call.args?.get("content")?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: return missingParam("content")
        val factId = call.args?.get("fact_id")?.jsonPrimitive?.longOrNull

        val result = factRepository.upsert(category, content, factId)
        val event = if (result.created) {
            context.getString(R.string.ev_fact_created, content)
        } else {
            context.getString(R.string.ev_fact_updated, content)
        }
        return ToolExecutionResult(event, ToolSpecs.okResult())
    }

    private suspend fun deleteFact(call: FunctionCall): ToolExecutionResult {
        val factId = call.args?.get("fact_id")?.jsonPrimitive?.longOrNull
            ?: return missingParam("fact_id")
        factRepository.delete(factId)
        return ToolExecutionResult(
            context.getString(R.string.ev_fact_deleted, factId),
            ToolSpecs.okResult()
        )
    }

    private fun missingParam(param: String): ToolExecutionResult =
        ToolExecutionResult(null, ToolSpecs.errorResult(context.getString(R.string.err_missing_param, param)))

    private fun formatNumber(value: Int): String =
        String.format(Locale.forLanguageTag("cs"), "%d", value)

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
