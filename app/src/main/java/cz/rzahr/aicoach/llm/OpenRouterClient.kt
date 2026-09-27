package cz.rzahr.aicoach.llm

import android.util.Log
import cz.rzahr.aicoach.data.repo.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

class OpenRouterException(message: String) : Exception(message)

@Serializable
private data class OrModelsResponse(
    val data: List<OrModelInfo> = emptyList()
)

@Serializable
internal data class OrModelInfo(
    val id: String = "",
    val supported_parameters: List<String> = emptyList()
)

@Serializable
private data class OrStreamChunk(
    val choices: List<OrChoice> = emptyList()
)

@Serializable
private data class OrChoice(
    val delta: OrDelta = OrDelta(),
    val finish_reason: String? = null
)

@Serializable
private data class OrDelta(
    val content: String? = null,
    val tool_calls: List<OrToolCallDelta> = emptyList()
)

@Serializable
private data class OrToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: OrFunctionDelta? = null
)

@Serializable
private data class OrFunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
private data class OrErrorResponse(
    val error: OrErrorInfo? = null
)

@Serializable
private data class OrErrorInfo(
    val message: String? = null,
    val code: Int? = null
)

internal data class OpenRouterToolCall(
    val id: String,
    val name: String,
    val args: JsonObject
)

private data class RoundResult(
    val text: String,
    val calls: List<OpenRouterToolCall>
)

private class ToolCallAcc(val index: Int) {
    var id: String? = null
    var name: String? = null
    val args = StringBuilder()
}

/**
 * OpenAI-kompatibilní klient pro OpenRouter (issue #2 – alternativa k přetížené Gemini).
 * Historie se předává v neutrálních [Content]/[Part] a mapuje se na OpenAI messages;
 * tool loop (nástroje, rescue textových simulací) zrcadlí [GeminiClient].
 */
@Singleton
class OpenRouterClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
    private val toolExecutor: ToolExecutor
) : LlmClient {

    override suspend fun chat(
        systemPrompt: String,
        history: List<Content>,
        onDelta: suspend (String) -> Unit
    ): ChatTurnResult = withContext(Dispatchers.IO) {
        val apiKey = settings.openRouterApiKey.first()
        if (apiKey.isBlank()) {
            throw OpenRouterException("Chybí OpenRouter API klíč. Zadej ho v Nastavení.")
        }
        val model = normalizeModel(settings.model.first())
        val messages = toOpenAiMessages(systemPrompt, history).toMutableList()
        val events = mutableListOf<String>()
        val turnContext = TurnContext()
        // Narativ ze všech kol – UI streamuje průběžně, ukládá se celek.
        val narrative = mutableListOf<String>()

        repeat(MAX_TOOL_ROUNDS) { round ->
            val (roundText, calls) = streamRound(apiKey, model, messages, onDelta)
            if (roundText.isNotBlank()) {
                ToolTextFallback.sanitizeModelText(roundText).trim()
                    .takeIf { it.isNotBlank() }?.let { narrative.add(it) }
            }
            if (calls.isEmpty()) {
                // Žádný skutečný tool call – zkus rescue z textové simulace.
                val rescue = ToolTextFallback.extractExecutableCalls(roundText)
                if (rescue.isNotEmpty()) {
                    Log.d(TAG, "chat: rescue $round – textových pseudo-volání: ${rescue.size}")
                    if (roundText.isNotBlank()) messages += assistantMessage(roundText, emptyList())
                    rescue.forEachIndexed { i, call ->
                        val result = toolExecutor.execute(call, turnContext)
                        result.event?.let { events.add(it) }
                        messages += toolMessage("rescue-$round-$i", result.response)
                    }
                    // Pokračujeme na další kolo, aby model po rescue dopsal přirozenou odpověď.
                    return@repeat
                }
                val finalText = narrative.joinToString("\n\n").trim()
                if (finalText.isEmpty()) throw OpenRouterException("Model vrátil prázdnou odpověď.")
                val clean = ToolTextFallback.sanitizeModelText(finalText).trim()
                if (clean.isEmpty()) {
                    return@withContext ChatTurnResult("Hotovo. ✅", events)
                }
                return@withContext ChatTurnResult(clean, events)
            }

            Log.d(TAG, "chat: kolo $round – toolCalls: ${calls.map { it.name }}")
            messages += assistantMessage(roundText, calls)
            calls.forEach { call ->
                val result = toolExecutor.execute(FunctionCall(call.name, call.args), turnContext)
                result.event?.let { events.add(it) }
                messages += toolMessage(call.id, result.response)
            }
        }
        throw OpenRouterException("Příliš mnoho kol volání nástrojů.")
    }

    private suspend fun streamRound(
        apiKey: String,
        model: String,
        messages: List<JsonObject>,
        onDelta: suspend (String) -> Unit
    ): RoundResult {
        var attempt = 0
        while (true) {
            val body = buildJsonObject {
                put("model", model)
                putJsonArray("messages") { messages.forEach { add(it) } }
                put("tools", ToolSpecs.openAiTools())
                put("tool_choice", "auto")
                put("temperature", CHAT_TEMPERATURE)
                put("stream", true)
            }
            val httpRequest = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", APP_REFERER)
                .addHeader("X-Title", APP_TITLE)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = http.newCall(httpRequest).await()

            if (!response.isSuccessful) {
                val retryAfter = response.header("Retry-After")
                val errorText = response.use { it.body?.string().orEmpty() }
                val retryable = response.code == 429 || response.code == 503
                if (retryable && attempt < MAX_RETRIES - 1) {
                    val wait = RetryDelays.computeRetryDelayMs(attempt, errorText, retryAfter)
                    Log.w(TAG, "chat 429/503 – retry ${attempt + 1}/$MAX_RETRIES za ${wait}ms")
                    delay(wait)
                    attempt++
                    continue
                }
                val message = if (retryable) {
                    "Model je momentálně přetížen. Zkus to prosím za chvíli."
                } else {
                    errorMessage(errorText, response.code)
                }
                Log.w(TAG, "chat failed HTTP ${response.code}: ${errorText.take(300)}")
                throw OpenRouterException(message)
            }

            val source: BufferedSource = response.body?.source()
                ?: throw OpenRouterException("Prázdná odpověď serveru.")

            val text = StringBuilder()
            val toolAcc = mutableMapOf<Int, ToolCallAcc>()
            try {
                source.use { src ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue
                        currentCoroutineContext().ensureActive()
                        val element = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                            ?: continue
                        element["error"]?.let {
                            val msg = runCatching {
                                json.decodeFromString(OrErrorResponse.serializer(), payload).error?.message
                            }.getOrNull()
                            throw OpenRouterException(
                                msg?.takeIf { it.isNotBlank() } ?: "Model vrátil chybu."
                            )
                        }
                        val chunk = runCatching {
                            json.decodeFromString(OrStreamChunk.serializer(), payload)
                        }.getOrNull() ?: continue
                        val delta = chunk.choices.firstOrNull()?.delta ?: continue
                        delta.content?.let { c ->
                            if (c.isNotEmpty()) {
                                text.append(c)
                                onDelta(c)
                            }
                        }
                        delta.tool_calls.forEach { tc ->
                            val acc = toolAcc.getOrPut(tc.index) { ToolCallAcc(tc.index) }
                            tc.id?.takeIf { it.isNotBlank() }?.let { acc.id = it }
                            tc.function?.name?.takeIf { it.isNotBlank() }?.let { acc.name = it }
                            tc.function?.arguments?.let { acc.args.append(it) }
                        }
                    }
                }
            } catch (e: IOException) {
                if (text.isEmpty() && toolAcc.isEmpty()) {
                    throw OpenRouterException("Síťová chyba během streamování: ${e.message}")
                }
            }
            val calls = toolAcc.toSortedMap().values.map { acc ->
                val argsText = acc.args.toString()
                val args = if (argsText.isBlank()) {
                    buildJsonObject { }
                } else {
                    runCatching { json.parseToJsonElement(argsText).jsonObject }.getOrNull()
                        ?: throw OpenRouterException("Neplatné argumenty nástroje ${acc.name ?: "?"}.")
                }
                OpenRouterToolCall(acc.id ?: "call-${acc.index}", acc.name.orEmpty(), args)
            }.filter { it.name.isNotBlank() }
            return RoundResult(text.toString(), calls)
        }
    }

    override suspend fun fetchModelNames(): List<String> = withContext(Dispatchers.IO) {
        val apiKey = settings.openRouterApiKey.first()
        if (apiKey.isBlank()) throw OpenRouterException("Chybí OpenRouter API klíč. Zadej ho v Nastavení.")
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", APP_REFERER)
            .addHeader("X-Title", APP_TITLE)
            .get()
            .build()
        val response = http.newCall(request).execute()
        val text = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) {
            throw OpenRouterException(errorMessage(text, response.code))
        }
        val parsed = json.decodeFromString(OrModelsResponse.serializer(), text)
        filterToolModels(parsed.data)
    }

    private fun errorMessage(text: String, code: Int): String {
        val apiMessage = runCatching {
            json.decodeFromString(OrErrorResponse.serializer(), text).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val hint = when (code) {
            401 -> " Zkontroluj OpenRouter API klíč v Nastavení."
            402 -> " Nedostatek kreditu na OpenRouter účtu."
            404 -> " Zkontroluj název modelu v Nastavení (model asi neexistuje nebo nepodporuje nástroje)."
            else -> ""
        }
        return (apiMessage ?: "HTTP $code") + hint
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(OpenRouterException("Síťová chyba: ${e.message}"))
                }
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    companion object {
        private const val TAG = "OpenRouterClient"
        internal const val BASE_URL = "https://openrouter.ai/api/v1"
        private const val APP_REFERER = "https://github.com/Zahy04/ai_coach"
        private const val APP_TITLE = "AI Coach"
        private const val MAX_TOOL_ROUNDS = 6
        private const val MAX_RETRIES = 6
        private const val CHAT_TEMPERATURE = 0.3f

        /** Defaultní model: qwen3.8-27b zdarma – dle Artificial Analysis lepší než Gemini Flash-Lite. */
        internal const val DEFAULT_MODEL = SettingsRepository.DEFAULT_OPENROUTER_MODEL
        private const val MUSE_CONTRIBUTOR = "meta/muse-spark-1.3-contributor"
        private const val MUSE_FULL = "meta/muse-spark-1.3"

        internal fun normalizeModel(raw: String): String {
            val cleaned = raw.trim()
            // OpenRouter id vždy obsahuje "/" (provider/model); jinak default.
            // Chrání i před pozůstatkem Gemini názvu po přepnutí providera.
            if (cleaned.isBlank() || !cleaned.contains('/')) {
                return DEFAULT_MODEL
            }
            return cleaned
        }

        /** Jen modely s podporou tools, bez duplicit, seřazené (qwen3.8-27b:free první). */
        internal fun filterToolModels(models: List<OrModelInfo>): List<String> =
            models
                .filter { model -> model.supported_parameters.any { it.equals("tools", true) } }
                .map { it.id.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .let(::sortModels)

        /** qwen3.8-27b:free první, pak Muse Spark, pak ostatní free, pak zbytek. */
        internal fun sortModels(names: List<String>): List<String> {
            fun rank(name: String): Int = when {
                name == DEFAULT_MODEL -> 0
                name == MUSE_CONTRIBUTOR -> 1
                name == MUSE_FULL -> 2
                name.endsWith(":free") -> 3
                else -> 4
            }
            return names.sortedWith(compareBy({ n: String -> rank(n) }, { n: String -> n }))
        }

        /**
         * Mapování neutrální historie na OpenAI messages (čisté – pro testy).
         * "model" → "assistant", fotky → image_url s data URI.
         */
        internal fun toOpenAiMessages(systemPrompt: String, history: List<Content>): List<JsonObject> {
            val out = mutableListOf<JsonObject>()
            out += buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
            history.forEach { content ->
                val assistant = content.role == "model"
                val role = if (assistant) "assistant" else "user"
                val texts = content.parts.mapNotNull { it.text }
                // Model se z textových simulací toolů nemá co učit – očistit.
                val cleanTexts = if (assistant) {
                    texts.mapNotNull { text ->
                        if (!ToolTextFallback.containsPseudoCall(text)) text
                        else ToolTextFallback.sanitizeModelText(text).takeIf { it.isNotBlank() }
                    }
                } else {
                    texts
                }
                val images = content.parts.mapNotNull { it.inlineData }
                if (images.isEmpty()) {
                    out += buildJsonObject {
                        put("role", role)
                        put("content", cleanTexts.joinToString("\n"))
                    }
                } else {
                    out += buildJsonObject {
                        put("role", role)
                        putJsonArray("content") {
                            cleanTexts.forEach { t ->
                                addJsonObject {
                                    put("type", "text")
                                    put("text", t)
                                }
                            }
                            images.forEach { img ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:${img.mimeType};base64,${img.data}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return out
        }

        internal fun assistantMessage(text: String, calls: List<OpenRouterToolCall>): JsonObject =
            buildJsonObject {
                put("role", "assistant")
                if (text.isNotBlank()) put("content", text)
                if (calls.isNotEmpty()) putJsonArray("tool_calls") {
                    calls.forEach { c ->
                        addJsonObject {
                            put("id", c.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", c.name)
                                put("arguments", Json.encodeToString(JsonObject.serializer(), c.args))
                            }
                        }
                    }
                }
            }

        internal fun toolMessage(callId: String, response: JsonObject): JsonObject = buildJsonObject {
            put("role", "tool")
            put("tool_call_id", callId)
            put("content", Json.encodeToString(JsonObject.serializer(), response))
        }

        /**
         * Nestreamované dokončení pro MensaEstimator (JSON s odhady).
         * Vrací text odpovědi, null = vyčerpané retrye (429/503).
         */
        suspend fun completeJson(
            http: OkHttpClient,
            json: Json,
            apiKey: String,
            model: String,
            prompt: String,
            imageParts: List<Part> = emptyList(),
            baseUrl: String = BASE_URL,
            maxAttempts: Int = 3
        ): String? {
            val userContent: JsonElement = if (imageParts.isEmpty()) {
                JsonPrimitive(prompt)
            } else {
                buildJsonArray {
                    addJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    }
                    imageParts.mapNotNull { it.inlineData }.forEach { img ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:${img.mimeType};base64,${img.data}")
                            }
                        }
                    }
                }
            }
            val body = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", userContent)
                    }
                }
                put("temperature", 0.2f)
                put("stream", false)
            }
            var attempt = 0
            while (true) {
                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", APP_REFERER)
                    .addHeader("X-Title", APP_TITLE)
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                val response = http.newCall(request).execute()
                val retryAfter = response.header("Retry-After")
                val text = response.use { it.body?.string().orEmpty() }
                if (response.isSuccessful) {
                    return runCatching {
                        json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("message")
                            ?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    }.getOrNull().orEmpty()
                }
                val retryable = response.code == 429 || response.code == 503
                if (retryable && attempt < maxAttempts - 1) {
                    delay(RetryDelays.computeRetryDelayMs(attempt, text, retryAfter))
                    attempt++
                    continue
                }
                if (retryable) return null
                throw OpenRouterException(staticErrorMessage(json, text, response.code))
            }
        }

        internal fun staticErrorMessage(json: Json, text: String, code: Int): String {
            val apiMessage = runCatching {
                json.decodeFromString(OrErrorResponse.serializer(), text).error?.message
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val hint = when (code) {
                401 -> " Zkontroluj OpenRouter API klíč v Nastavení."
                402 -> " Nedostatek kreditu na OpenRouter účtu."
                404 -> " Zkontroluj název modelu v Nastavení (model asi neexistuje nebo nepodporuje nástroje)."
                else -> ""
            }
            return (apiMessage ?: "HTTP $code") + hint
        }
    }
}
