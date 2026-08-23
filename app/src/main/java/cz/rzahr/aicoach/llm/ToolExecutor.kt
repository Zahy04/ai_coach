package cz.rzahr.aicoach.llm

import cz.rzahr.aicoach.data.repo.FactRepository
import cz.rzahr.aicoach.data.repo.FoodRepository
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

data class ToolExecutionResult(
    val event: String?,
    val response: JsonObject
)

@Singleton
class ToolExecutor @Inject constructor(
    private val weightRepository: WeightRepository,
    private val foodRepository: FoodRepository,
    private val workoutRepository: WorkoutRepository,
    private val factRepository: FactRepository
) {

    suspend fun execute(call: FunctionCall, turnContext: TurnContext): ToolExecutionResult = try {
        val name = call.name.removePrefix("default_api:")
        when (name) {
            ToolSpecs.SAVE_WEIGHT -> saveWeight(call)
            ToolSpecs.LOG_FOOD -> logFood(call, turnContext)
            ToolSpecs.LOG_WORKOUT -> logWorkout(call)
            ToolSpecs.SAVE_FACT -> saveFact(call)
            ToolSpecs.DELETE_FACT -> deleteFact(call)
            else -> ToolExecutionResult(null, ToolSpecs.errorResult("Neznámý nástroj: ${call.name}"))
        }
    } catch (e: Exception) {
        ToolExecutionResult(null, ToolSpecs.errorResult(e.message ?: "Neočekávaná chyba"))
    }

    private suspend fun saveWeight(call: FunctionCall): ToolExecutionResult {
        val weightKg = call.args?.get("weight_kg")?.jsonPrimitive?.doubleOrNull
            ?: return ToolExecutionResult(null, ToolSpecs.errorResult("Chybí povinný parametr weight_kg."))
        val note = call.args?.get("note")?.jsonPrimitive?.contentOrNull()
        weightRepository.add(weightKg, note)
        val formatted = String.format(Locale.forLanguageTag("cs"), "%.1f", weightKg)
        return ToolExecutionResult("Váha $formatted kg uložena.", ToolSpecs.okResult())
    }

    private suspend fun logFood(call: FunctionCall, turnContext: TurnContext): ToolExecutionResult {
        val name = call.args?.get("name")?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: return ToolExecutionResult(null, ToolSpecs.errorResult("Chybí povinný parametr name."))

        val dedupeKey = name.trim().lowercase()
        if (dedupeKey in turnContext.loggedFoods) {
            return ToolExecutionResult(
                null,
                buildJsonObject {
                    put("status", "already_logged")
                    put("message", "Toto jídlo už bylo v této zprávě zaznamenáno, neukládej ho znovu.")
                }
            )
        }
        turnContext.loggedFoods.add(dedupeKey)

        val calories = call.args?.get("calories")?.jsonPrimitive?.intOrNull
        val protein = call.args?.get("protein_g")?.jsonPrimitive?.doubleOrNull
        val carbs = call.args?.get("carbs_g")?.jsonPrimitive?.doubleOrNull
        val fat = call.args?.get("fat_g")?.jsonPrimitive?.doubleOrNull
        foodRepository.add(
            name = name,
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat
        )
        val event = buildString {
            append("Jídlo „$name“ uloženo")
            calories?.let { append(" ($it kcal)") }
            append(".")
        }
        return ToolExecutionResult(event, ToolSpecs.okResult())
    }

    private suspend fun logWorkout(call: FunctionCall): ToolExecutionResult {
        val name = call.args?.get("name")?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: return ToolExecutionResult(null, ToolSpecs.errorResult("Chybí povinný parametr name."))
        val duration = call.args?.get("duration_minutes")?.jsonPrimitive?.intOrNull
        val caloriesBurned = call.args?.get("calories_burned")?.jsonPrimitive?.intOrNull
        val note = call.args?.get("note")?.jsonPrimitive?.contentOrNull()
        workoutRepository.add(name = name, durationMinutes = duration, caloriesBurned = caloriesBurned, note = note)
        val event = buildString {
            append("Trénink „$name“ uložen")
            duration?.let { append(" ($it min)") }
            append(".")
        }
        return ToolExecutionResult(event, ToolSpecs.okResult())
    }

    private suspend fun saveFact(call: FunctionCall): ToolExecutionResult {
        val category = call.args?.get("category")?.jsonPrimitive?.contentOrNull()
            ?: return ToolExecutionResult(null, ToolSpecs.errorResult("Chybí povinný parametr category."))
        val content = call.args?.get("content")?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: return ToolExecutionResult(null, ToolSpecs.errorResult("Chybí povinný parametr content."))
        val factId = call.args?.get("fact_id")?.jsonPrimitive?.longOrNull

        val result = factRepository.upsert(category, content, factId)
        val event = if (result.created) "Poznámka uložena: $content" else "Poznámka aktualizována: $content"
        return ToolExecutionResult(event, ToolSpecs.okResult())
    }

    private suspend fun deleteFact(call: FunctionCall): ToolExecutionResult {
        val factId = call.args?.get("fact_id")?.jsonPrimitive?.longOrNull
            ?: return ToolExecutionResult(null, ToolSpecs.errorResult("Chybí povinný parametr fact_id."))
        factRepository.delete(factId)
        return ToolExecutionResult("Poznámka #$factId smazána.", ToolSpecs.okResult())
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
