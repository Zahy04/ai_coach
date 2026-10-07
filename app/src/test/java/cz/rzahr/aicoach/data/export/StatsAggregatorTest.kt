package cz.rzahr.aicoach.data.export

import cz.rzahr.aicoach.data.db.entity.LlmRequestEntity
import cz.rzahr.aicoach.data.db.entity.LlmToolCallEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agregace statistik modelů (issue #6) – běží nad skutečným [StatsAggregator],
 * ne nad jeho kopií, aby test chránil to, co se opravdu píše do exportu.
 */
class StatsAggregatorTest {

    private fun request(
        model: String = MODEL_A,
        success: Boolean = true,
        durationMs: Long = 1000,
        firstTokenMs: Long? = 200,
        errorKind: String? = null,
        attempt: Int = 0,
        completionTokens: Int? = 50
    ) = LlmRequestEntity(
        timestamp = NOW,
        provider = LlmRequestEntity.PROVIDER_GEMINI,
        model = model,
        attempt = attempt,
        durationMs = durationMs,
        firstTokenMs = firstTokenMs,
        httpStatus = if (success) 200 else 429,
        success = success,
        errorKind = errorKind,
        completionTokens = completionTokens
    )

    private fun foodCall(
        model: String = MODEL_A,
        outcome: String = LlmToolCallEntity.OUTCOME_OK,
        errorKind: String? = null,
        modelCalories: Int? = null,
        modelProteinG: Double? = null,
        modelFatG: Double? = null,
        finalCalories: Int? = null,
        finalProteinG: Double? = null,
        finalFatG: Double? = null,
        finalSource: String? = null,
        viaTextFallback: Boolean = false
    ) = LlmToolCallEntity(
        timestamp = NOW,
        provider = LlmRequestEntity.PROVIDER_GEMINI,
        model = model,
        toolName = "log_food",
        outcome = outcome,
        errorKind = errorKind,
        durationMs = 50,
        viaTextFallback = viaTextFallback,
        modelCalories = modelCalories,
        modelProteinG = modelProteinG,
        modelFatG = modelFatG,
        finalCalories = finalCalories,
        finalProteinG = finalProteinG,
        finalFatG = finalFatG,
        finalSource = finalSource
    )

    private fun stats(
        requests: List<LlmRequestEntity> = emptyList(),
        toolCalls: List<LlmToolCallEntity> = emptyList()
    ) = StatsAggregator.summarize(requests, toolCalls).models.single()

    @Test
    fun `429 a 503 se pocitaji zvlastni sloupec, ne jako uspech`() {
        val rows = listOf(
            request(success = true, durationMs = 1000),
            request(success = false, errorKind = LlmRequestEntity.ERROR_RATE_LIMIT),
            request(success = false, errorKind = LlmRequestEntity.ERROR_OVERLOADED)
        )

        val model = stats(requests = rows)

        assertEquals(3, model.attempts)
        assertEquals(1, model.attemptsOk)
        assertEquals(
            mapOf(
                LlmRequestEntity.ERROR_RATE_LIMIT to 1,
                LlmRequestEntity.ERROR_OVERLOADED to 1
            ),
            model.errorsByKind
        )
        // Průměrná latence se počítá jen z úspěšných requestů – jinak by se
        // krátký okamžitý 429 průměroval jako rychlá odpověď modelu.
        assertEquals(1000.0, model.latencyMsMean!!, 0.001)
    }

    @Test
    fun `fail rate je podil neuspesnych pokusu a retry ma vyssi attempt`() {
        val rows = listOf(
            request(success = false, errorKind = LlmRequestEntity.ERROR_RATE_LIMIT, attempt = 0),
            request(success = false, errorKind = LlmRequestEntity.ERROR_RATE_LIMIT, attempt = 1),
            request(success = true, attempt = 2)
        )

        val model = stats(requests = rows)

        assertEquals(2, model.retries)
        assertEquals(2.0 / 3.0, model.failureRate!!, 0.001)
    }

    @Test
    fun `p95 latence se interpoluje, nebere posledni hodnotu`() {
        val rows = (1..100).map { request(durationMs = it * 100L) }

        val model = stats(requests = rows)

        assertEquals(5050.0, model.latencyMsMean!!, 1.0)
        // 100 měření 100…10 000: interpolovaný p50 = (5000+5100)/2, p95 leží
        // mezi 9 500 a 9 600 – ne maximum 10 000.
        assertEquals(5050.0, model.latencyMsP50!!, 1.0)
        assertEquals(9505.0, model.latencyMsP95!!, 1.0)
    }

    @Test
    fun `prazdna latence a chyby daji null, ne nulu`() {
        // Jediný request selhal – průměr úspěšných latencí nemá co počítat,
        // 0 by se v grafu četl jako „model odpovídal okamžitě".
        val model = stats(requests = listOf(request(success = false, errorKind = LlmRequestEntity.ERROR_NETWORK)))

        assertNull(model.latencyMsMean)
        assertNull(model.latencyMsP50)
        assertNull(model.latencyMsP95)
        assertEquals(0, model.foodCalls)
    }

    @Test
    fun `chyba odhadu kalorii se pocita jen kdyz je OFF podklad`() {
        // Model odhadl 600, OFF řekl 500 → 20 % chyba.
        val withOff = foodCall(
            modelCalories = 600,
            finalCalories = 500,
            finalSource = LlmToolCallEntity.SOURCE_OFF
        )
        val withoutOff = foodCall(
            modelCalories = 600,
            finalCalories = 600,
            finalSource = LlmToolCallEntity.SOURCE_MODEL
        )

        val off = stats(toolCalls = listOf(withOff))
        val fromModel = stats(toolCalls = listOf(withoutOff))

        assertEquals(20.0, off.calorieErrorPctMean!!, 0.001)
        assertEquals(1, off.calorieComparables)
        // Bez OFF podkladu není s čím porovnat – musí vyjít null, ne 0 %.
        assertNull(fromModel.calorieErrorPctMean)
        assertEquals(0, fromModel.calorieComparables)
        // Samotné vyplněné hodnoty se do průměrů počítat mohou.
        assertEquals(600.0, fromModel.finalCaloriesMean!!, 0.001)
        assertEquals(1, fromModel.foodFromModel)
    }

    @Test
    fun `chyba bílkovin a tuku ma vlastni pocet comparables`() {
        val rows = listOf(
            // Model bílkoviny 12 g, OFF 10 g → 20 %; tuky 4 g vs 5 g → 20 %.
            foodCall(
                modelCalories = 500,
                finalCalories = 500,
                modelProteinG = 12.0,
                finalProteinG = 10.0,
                modelFatG = 4.0,
                finalFatG = 5.0,
                finalSource = LlmToolCallEntity.SOURCE_OFF
            ),
            // OFF bílkoviny u druhého jídla nemá (časté) – tuky spočítat jde.
            foodCall(
                modelCalories = 400,
                finalCalories = 400,
                modelFatG = 10.0,
                finalFatG = 10.0,
                finalSource = LlmToolCallEntity.SOURCE_OFF
            )
        )

        val model = stats(toolCalls = rows)

        assertEquals(20.0, model.proteinErrorPctMean!!, 0.001)
        assertEquals(2, model.calorieComparables)
        assertEquals(2, model.macroComparables)
        assertEquals(10.0, model.fatErrorPctMean!!, 0.001)
    }

    @Test
    fun `preskoceny a chybny tool call nejsou uspech`() {
        val rows = listOf(
            foodCall(outcome = LlmToolCallEntity.OUTCOME_OK),
            // Už zapsané – model neudělal chybu, ale data se neuložila.
            foodCall(outcome = LlmToolCallEntity.OUTCOME_SKIPPED),
            foodCall(outcome = LlmToolCallEntity.OUTCOME_ERROR, errorKind = LlmToolCallEntity.ERROR_MISSING_PARAM)
        )

        val model = stats(toolCalls = rows)

        assertEquals(3, model.toolCalls)
        assertEquals(1, model.toolCallsOk)
        assertEquals(1, model.toolCallsSkipped)
        assertEquals(1, model.toolCallsError)
        assertEquals(1.0 / 3.0, model.toolErrorRate!!, 0.001)
    }

    @Test
    fun `prumer latence a tokenu po modelech se nespaji`() {
        val requests = listOf(
            request(model = MODEL_A, durationMs = 1000, completionTokens = 100),
            request(model = MODEL_A, durationMs = 2000, completionTokens = 200),
            request(model = MODEL_B, durationMs = 500, completionTokens = 50)
        )

        val models = StatsAggregator.summarize(requests = requests).models

        assertEquals(listOf(MODEL_A, MODEL_B), models.map { it.model })
        assertEquals(1500.0, models[0].latencyMsMean!!, 0.001)
        assertEquals(200.0, models[0].firstTokenMsMean!!, 0.001)
        assertEquals(300, models[0].completionTokensTotal)
        assertEquals(500.0, models[1].latencyMsMean!!, 0.001)
        assertEquals(50, models[1].completionTokensTotal)
    }

    @Test
    fun `textovy rescue toolu se odlisuje od skutecneho function callu`() {
        val rows = listOf(foodCall(viaTextFallback = true), foodCall(viaTextFallback = false))

        assertEquals(1, stats(toolCalls = rows).toolCallsViaTextFallback)
    }

    @Test
    fun `export bez dat neni chyba, ale prazdny seznam modelu`() {
        assertTrue(StatsAggregator.summarize(emptyList(), emptyList()).models.isEmpty())
    }

    private companion object {
        const val MODEL_A = "gemini-2.5-flash"
        const val MODEL_B = "gemini-2.5-pro"
        const val NOW = 1_700_000_000_000L
    }
}