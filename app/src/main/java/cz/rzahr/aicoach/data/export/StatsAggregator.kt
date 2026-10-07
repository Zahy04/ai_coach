package cz.rzahr.aicoach.data.export

import cz.rzahr.aicoach.data.db.entity.LlmRequestEntity
import cz.rzahr.aicoach.data.db.entity.LlmToolCallEntity
import kotlinx.serialization.Serializable

/**
 * Čistá agregace telemetrie modelů (issue #6) – bez Androidu a bez Roomu,
 * aby se dala testovat v JVM (`StatsAggregatorTest`) a použít i mimo export.
 *
 * Rozpad je záměrně jednoduchý: jeden řádek v JSONu = jeden model, aby se ve
 * notebooku dalo `df.set_index("model")` a hned kreslit grafy.
 */
object StatsAggregator {

    @Serializable
    data class Summary(
        val models: List<ModelSummary> = emptyList()
    )

    @Serializable
    data class ModelSummary(
        val model: String,
        val provider: String,
        /** Kolik HTTP pokusů (retry je každý zvlášť – to je celý smysl `attempt`). */
        val attempts: Int,
        val attemptsOk: Int,
        /** 0…1, null když zatím žádný pokus nebyl. */
        val failureRate: Double?,
        /** Latence nad timeoutem – jen úspěšné pokusy, jinak by se krátký 429 průměroval jako rychlý model. */
        val latencyMsMean: Double?,
        val latencyMsP50: Double?,
        val latencyMsP95: Double?,
        val firstTokenMsMean: Double?,
        val firstTokenMsP50: Double?,
        val retries: Int,
        val errorsByKind: Map<String, Int>,
        val promptTokensTotal: Int,
        val completionTokensTotal: Int,
        val totalTokensTotal: Int,
        val replyCharsTotal: Int,
        val toolCalls: Int,
        val toolCallsOk: Int,
        /** Model jídlo už zapsal – nechybí, ale do průměrů kalorií nepočítáme. */
        val toolCallsSkipped: Int,
        val toolCallsError: Int,
        /** log_food volaná jako text místo function callu – model neumí tool calling. */
        val toolCallsViaTextFallback: Int,
        val toolErrorRate: Double?,
        val foodCalls: Int,
        val foodFromOff: Int,
        val foodFromModel: Int,
        val modelCaloriesMean: Double?,
        val finalCaloriesMean: Double?,
        /** Relativní chyba odhadu modelu proti Open Food Facts v %; null = nebylo s čím porovnat. */
        val calorieErrorPctMean: Double?,
        val proteinErrorPctMean: Double?,
        val carbsErrorPctMean: Double?,
        val fatErrorPctMean: Double?,
        /** Kolik volání šlo porovnat proti OFF (u bílkovin/tuků bývá méně – OFF je často neúplné). */
        val calorieComparables: Int,
        val macroComparables: Int
    )

    fun summarize(
        requests: List<LlmRequestEntity>,
        toolCalls: List<LlmToolCallEntity> = emptyList()
    ): Summary {
        val models = (requests.map { it.model } + toolCalls.map { it.model })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        return Summary(models.map { model -> summarizeModel(model, requests, toolCalls) })
    }

    private fun summarizeModel(
        model: String,
        requests: List<LlmRequestEntity>,
        toolCalls: List<LlmToolCallEntity>
    ): ModelSummary {
        val reqs = requests.filter { it.model == model }
        val ok = reqs.filter { it.success }
        val calls = toolCalls.filter { it.model == model }
        val callErrors = calls.filter { it.outcome == LlmToolCallEntity.OUTCOME_ERROR }
        val food = calls.filter { it.toolName == LOG_FOOD && it.outcome == LlmToolCallEntity.OUTCOME_OK }
        // Odhad modelu jde porovnat jen tam, kde jsme měli podklad z Open Food Facts –
        // u SOURCE_MODEL bychom porovnávali model s jeho vlastním odhadem, což je 0 % chyba.
        val off = food.filter {
            it.finalSource == LlmToolCallEntity.SOURCE_OFF &&
                it.modelCalories != null && it.finalCalories != null
        }
        // OFF nemusí mít vyplněná všechna makra – tuky proto porovnáváme zvlášť,
        // ať chybějící hodnota nesnížila počet comparables u(kernel.
        val fat = off.filter { it.modelFatG != null && it.finalFatG != null }

        return ModelSummary(
            model = model,
            provider = reqs.firstOrNull()?.provider ?: calls.firstOrNull()?.provider.orEmpty(),
            attempts = reqs.size,
            attemptsOk = ok.size,
            failureRate = rate(reqs.count { !it.success }, reqs.size),
            latencyMsMean = mean(ok.map { it.durationMs }),
            latencyMsP50 = ok.map { it.durationMs }.percentile(0.50),
            latencyMsP95 = ok.map { it.durationMs }.percentile(0.95),
            firstTokenMsMean = mean(ok.mapNotNull { it.firstTokenMs }),
            firstTokenMsP50 = ok.mapNotNull { it.firstTokenMs }.percentile(0.50),
            retries = reqs.count { it.attempt > 0 },
            errorsByKind = reqs.mapNotNull { it.errorKind }.groupingBy { it }.eachCount().toSortedMap(),
            promptTokensTotal = reqs.sumOf { it.promptTokens ?: 0 },
            completionTokensTotal = reqs.sumOf { it.completionTokens ?: 0 },
            totalTokensTotal = reqs.sumOf { it.totalTokens ?: 0 },
            replyCharsTotal = reqs.sumOf { it.replyChars ?: 0 },
            toolCalls = calls.size,
            toolCallsOk = calls.count { it.outcome == LlmToolCallEntity.OUTCOME_OK },
            toolCallsSkipped = calls.count { it.outcome == LlmToolCallEntity.OUTCOME_SKIPPED },
            toolCallsError = callErrors.size,
            toolCallsViaTextFallback = calls.count { it.viaTextFallback },
            toolErrorRate = rate(callErrors.size, calls.size),
            foodCalls = food.size,
            foodFromOff = food.count { it.finalSource == LlmToolCallEntity.SOURCE_OFF },
            foodFromModel = food.count { it.finalSource == LlmToolCallEntity.SOURCE_MODEL },
            modelCaloriesMean = mean(food.mapNotNull { it.modelCalories }),
            finalCaloriesMean = mean(food.mapNotNull { it.finalCalories }),
            calorieErrorPctMean = relativeErrorPct(off) {
                it.modelCalories?.toDouble() to it.finalCalories?.toDouble()
            },
            proteinErrorPctMean = relativeErrorPct(off) { it.modelProteinG to it.finalProteinG },
            carbsErrorPctMean = relativeErrorPct(off) { it.modelCarbsG to it.finalCarbsG },
            fatErrorPctMean = relativeErrorPct(fat) { it.modelFatG to it.finalFatG },
            calorieComparables = off.size,
            macroComparables = fat.size
        )
    }

    /** Průměrná relativní chyba v %; bez párů k porovnání vrátí null, ne 0 %. */
    private fun <T> relativeErrorPct(rows: List<T>, values: (T) -> Pair<Double?, Double?>): Double? {
        val errors = rows.mapNotNull { row ->
            val (model, final) = values(row)
            if (model == null || final == null || final == 0.0) null
            else Math.abs(model - final) / Math.abs(final) * 100.0
        }
        return mean(errors)
    }

    private fun rate(failures: Int, attempts: Int): Double? =
        if (attempts == 0) null else failures.toDouble() / attempts

    /** Průměr čísel libovolného typu; prázdný seznam musí dát null, ne 0. */
    private fun mean(values: List<Number>): Double? =
        if (values.isEmpty()) null else values.sumOf { it.toDouble() } / values.size

    /**
     * Percentil lineární interpolací mezi dvěma nejbližšími hodnotami – p95 ze 100
     * měření tak není rovno maximu, ale 95. hodnotě, jak to čeká pandas.
     */
    private fun List<Long>.percentile(q: Double): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val pos = q * (sorted.size - 1)
        val low = pos.toInt()
        val high = minOf(low + 1, sorted.size - 1)
        val value = sorted[low] + (pos - low) * (sorted[high] - sorted[low])
        return value.round1()
    }

    private fun Double.round1(): Double = Math.round(this * 10.0) / 10.0

    /** Jméno nástroje zachycuje i tento výčet – data.export si nesmí tahat závislost na llm. */
    private const val LOG_FOOD = "log_food"
}