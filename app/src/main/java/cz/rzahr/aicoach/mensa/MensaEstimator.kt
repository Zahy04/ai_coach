package cz.rzahr.aicoach.mensa

import android.util.Log
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import cz.rzahr.aicoach.data.repo.SettingsRepository
import cz.rzahr.aicoach.llm.ApiError
import cz.rzahr.aicoach.llm.Content
import cz.rzahr.aicoach.llm.GenerateContentRequest
import cz.rzahr.aicoach.llm.GenerateContentResponse
import cz.rzahr.aicoach.llm.GenerationConfig
import cz.rzahr.aicoach.llm.Part
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class MensaEstimation(
    val index: Int,
    val kcalMin: Int?,
    val kcalMax: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val verdict: String?
)

@Serializable
private data class MensaEstimateItem(
    @SerialName("index") val index: Int? = null,
    @SerialName("kcal_min") val kcalMin: Int? = null,
    @SerialName("kcal_max") val kcalMax: Int? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("verdict") val verdict: String? = null
)

@Serializable
private data class MensaEstimateResponse(
    val meals: List<MensaEstimateItem> = emptyList()
)

data class CoreMessages(
    val emptyReply: String = "Model returned no text",
    val reasonFmt: String = " (reason: %1\$s)",
    val invalidFormat: String = "Invalid response format: %1\$s",
    val networkError: String = "Network error: %1\$s",
    val http: String = "HTTP %1\$d",
    val overloaded: String = "Model is overloaded."
)

class MensaEstimationException(message: String) : Exception(message)

@Singleton
class MensaEstimator @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository
) {

    suspend fun estimate(
        systemId: Int,
        meals: List<MensaMealEntity>,
        patient: Boolean = false
    ): List<MensaEstimation>? =
        withContext(Dispatchers.IO) {
            if (meals.isEmpty()) {
                Log.d(TAG, "estimate: žádná jídla k odhadu (systemId=$systemId)")
                return@withContext emptyList()
            }
            val apiKey = settings.apiKey.first()
            if (apiKey.isBlank()) {
                Log.e(TAG, "estimate: API klíč je PRÁZDNÝ — zadej ho v Nastavení")
                throw MensaEstimationException(context.getString(R.string.gemini_key_blank))
            }

            val model = settings.model.first()
            Log.d(TAG, "estimate: systemId=$systemId, jídel=${meals.size}, model=$model, patient=$patient")
            val prompt = MensaEstimatorCore.buildPrompt(meals)
            val responseText = try {
                MensaEstimatorCore.generateWithRetry(
                    http, json, apiKey, model, prompt,
                    maxRetries = if (patient) 6 else 3,
                    baseDelayMs = if (patient) 3000L else 1500L,
                    msgs = localizedMessages()
                )
            } catch (_: IOException) {
                throw MensaEstimationException(context.getString(R.string.err_network))
            } ?: run {
                Log.e(TAG, "estimate: vyčerpané retrye (429/503)")
                throw MensaEstimationException(context.getString(R.string.mensa_exhausted))
            }

            Log.d(TAG, "estimate: odpověď (${responseText.length} znaků): ${responseText.take(300)}")
            val result = MensaEstimatorCore.parseEstimates(json, responseText, meals.size, localizedMessages())
            Log.d(TAG, "estimate: naparsováno ${result?.size ?: 0} odhadů")
            result
        }

    private fun localizedMessages() = CoreMessages(
        emptyReply = context.getString(R.string.mensa_empty_text),
        reasonFmt = context.getString(R.string.mensa_reason_suffix),
        invalidFormat = context.getString(R.string.mensa_invalid_format),
        networkError = context.getString(R.string.err_network),
        http = context.getString(R.string.gemini_http),
        overloaded = context.getString(R.string.mensa_overloaded)
    )

    companion object {
        private const val TAG = "MensaEstimator"
    }
}

/**
 * Čisté jádro odhadce bez Android závislostí — kvůli jednotkovým testům.
 */
object MensaEstimatorCore {

    fun buildPrompt(meals: List<MensaMealEntity>): String {
        val listing = buildString {
            appendLine("Seznam jídel z menzy:")
            meals.forEachIndexed { index, meal ->
                val grams = meal.grams?.let { "$it g" } ?: "neznámá gramáž"
                appendLine("$index | ${meal.category} | $grams | ${meal.name}")
            }
        }
        return """
            Jsi výživový expert. Níže je seznam jídel z české studentské menzy.
            Pro KAŽDOU položku odhadni nutriční hodnoty pro jednu porci tak, jak je popsaná
            (gramáž je u masa uváděna v syrovém stavu, u salátů celková hmotnost; připočítej
            běžnou přílohu a omáčky, pokud jsou zmíněné).

            Vrať VÝHRADNĚ JSON objekt ve tvaru:
            {"meals":[{"index":0,"kcal_min":600,"kcal_max":850,"protein_g":35,"carbs_g":70,"fat_g":25,"verdict":"GREEN"}]}

            Pravidla pro verdict (celkové fitness hodnocení jídla):
            - GREEN = vhodné pro fitness (dost bílkovin, méně smaženého, rozumné kalorie)
            - YELLOW = průměrné (vyvážené, ale těžší přílohy/omáčky)
            - RED = kalorická bomby a smažená jídla s málo bílkovinami

            $listing
        """.trimIndent()
    }

    /** Vrací vnitřní text odpovědi (JSON s odhady); null znamená „nevratno, jdi dál". */
    suspend fun generateWithRetry(
        http: OkHttpClient,
        json: Json,
        apiKey: String,
        model: String,
        prompt: String,
        baseUrl: String = BASE_URL,
        maxRetries: Int = 6,
        baseDelayMs: Long = 3000L,
        msgs: CoreMessages = CoreMessages()
    ): String? {
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            val request = GenerateContentRequest(
                contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(temperature = 0.2f, responseMimeType = "application/json")
            )
            val bodyJson = json.encodeToString(GenerateContentRequest.serializer(), request)
            val httpRequest = Request.Builder()
                .url("$baseUrl/$model:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = http.newCall(httpRequest).execute()
            val responseText = response.use { resp ->
                resp.body?.string().orEmpty()
            }

            if (response.isSuccessful) {
                // z API obálky vytáhneme samotný text (JSON s odhady)
                val envelope = runCatching {
                    json.decodeFromString(GenerateContentResponse.serializer(), responseText)
                }.getOrNull()
                val inner = envelope?.candidates?.firstOrNull()?.content?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString("\n")
                    .orEmpty()
                if (inner.isBlank()) {
                    val reason = envelope?.candidates?.firstOrNull()?.finishReason
                        ?.let { " (důvod: $it)" }
                        .orEmpty()
                    throw MensaEstimationException(msgs.emptyReply + String.format(msgs.reasonFmt, reason))
                }
                return inner
            }

            val retryable = response.code == 429 || response.code == 503
            lastError = if (retryable) {
                MensaEstimationException(msgs.overloaded)
            } else {
                runCatching { json.decodeFromString(ApiError.serializer(), responseText).error?.message }
                    .getOrNull()
                    ?.let { MensaEstimationException(it) }
                    ?: MensaEstimationException(String.format(msgs.http, response.code))
            }

            if (!retryable) throw lastError!!

            // Gemini v 429 často posílá "retryDelay": "23s" — respektujeme ho
            val serverWaitSec = Regex("\"retryDelay\":\\s*\"(\\d+)s\"")
                .find(responseText)?.groupValues?.get(1)?.toLongOrNull()
                ?.times(1000L)
            val wait = (serverWaitSec ?: (baseDelayMs * (1L shl attempt)))
                .coerceIn(1000L, 90_000L)
            delay(wait)
        }
        // vyčerpané retrye — vrátíme null, appka zobrazí jídla bez odhadů
        return null
    }

    fun parseEstimates(json: Json, responseText: String, mealCount: Int, msgs: CoreMessages = CoreMessages()): List<MensaEstimation>? {
        // očistíme případné markdown ohraničení
        val cleaned = responseText.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        val items: List<MensaEstimateItem> = try {
            json.decodeFromString(MensaEstimateResponse.serializer(), cleaned).meals
        } catch (_: Exception) {
            try {
                // model mohl vrátit i holé pole [ {...}, {...} ]
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(MensaEstimateItem.serializer()),
                    cleaned
                )
            } catch (_: Exception) {
                throw MensaEstimationException(String.format(msgs.invalidFormat, cleaned.take(120)))
            }
        }

        return items.mapNotNull { item ->
            val idx = item.index ?: return@mapNotNull null
            if (idx < 0 || idx >= mealCount) return@mapNotNull null
            MensaEstimation(
                index = idx,
                kcalMin = item.kcalMin,
                kcalMax = item.kcalMax,
                proteinG = item.proteinG,
                carbsG = item.carbsG,
                fatG = item.fatG,
                verdict = item.verdict?.uppercase()?.takeIf {
                    it == MensaMealEntity.RATING_GREEN ||
                        it == MensaMealEntity.RATING_YELLOW ||
                        it == MensaMealEntity.RATING_RED
                }
            )
        }.takeIf { it.isNotEmpty() }
    }

    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
}
