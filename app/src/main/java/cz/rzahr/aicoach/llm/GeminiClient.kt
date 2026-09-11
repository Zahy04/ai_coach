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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

class GeminiException(message: String) : Exception(message)

data class ChatTurnResult(
    val reply: String,
    val toolEvents: List<String>
)

@Singleton
class GeminiClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
    private val toolExecutor: ToolExecutor
) {

    suspend fun chat(
        systemPrompt: String,
        history: List<Content>,
        onDelta: suspend (String) -> Unit = {}
    ): ChatTurnResult = withContext(Dispatchers.IO) {
        val apiKey = settings.apiKey.first()
        if (apiKey.isBlank()) {
            throw GeminiException("Chybí API klíč. Zadej ho v Nastavení.")
        }
        val model = normalizeModelName(settings.model.first())
        // Historii očistíme od starých textových simulací toolů (log_food(...) apod.),
        // jinak se z nich model učí a simulace se samy zesilují.
        val contents = sanitizeHistory(history).toMutableList()
        val events = mutableListOf<String>()
        val turnContext = TurnContext()
        // Narativ ze všech kol – dřív se ukládal jen text posledního kola,
        // zatímco UI streamovalo všechno (blikání / mizení textu).
        val narrative = mutableListOf<String>()

        repeat(MAX_TOOL_ROUNDS) { round ->
            val modelContent = streamRound(apiKey, model, systemPrompt, contents, onDelta)
            if (modelContent.parts.isEmpty()) {
                throw GeminiException("Model vrátil prázdnou odpověď.")
            }
            contents.add(modelContent)

            val roundText = modelContent.parts.mapNotNull { it.text }.joinToString("\n").trim()
            if (roundText.isNotBlank()) {
                ToolTextFallback.sanitizeModelText(roundText).trim()
                    .takeIf { it.isNotBlank() }?.let { narrative.add(it) }
            }

            val calls = modelContent.parts.mapNotNull { it.functionCall }
            if (calls.isEmpty()) {
                // Žádný skutečný function call – zkus rescue z textové simulace.
                val rescue = ToolTextFallback.extractExecutableCalls(roundText)
                if (rescue.isNotEmpty()) {
                    Log.d(TAG, "chat: rescue $round – textových pseudo-volání: ${rescue.size}")
                    val responseParts = rescue.map { call ->
                        val result = toolExecutor.execute(call, turnContext)
                        result.event?.let { events.add(it) }
                        Part(functionResponse = FunctionResponse(name = call.name, response = result.response))
                    }
                    contents.add(Content(role = "user", parts = responseParts))
                    // Pokračujeme na další kolo, aby model po rescue dopsal přirozenou odpověď.
                    return@repeat
                }
                val finalText = narrative.joinToString("\n\n").trim()
                if (finalText.isEmpty()) throw GeminiException("Model vrátil prázdnou odpověď.")
                val clean = ToolTextFallback.sanitizeModelText(finalText).trim()
                if (clean.isEmpty()) {
                    // Nástroje proběhly, ale nezbyl žádný text – radši stručné potvrzení
                    // než prázdná bublina nebo chyba.
                    return@withContext ChatTurnResult("Hotovo. ✅", events)
                }
                return@withContext ChatTurnResult(clean, events)
            }

            Log.d(TAG, "chat: kolo $round – functionCalls: ${calls.map { it.name }}")
            val responseParts = calls.map { call ->
                val result = toolExecutor.execute(call, turnContext)
                result.event?.let { events.add(it) }
                Part(functionResponse = FunctionResponse(name = call.name, response = result.response))
            }
            contents.add(Content(role = "user", parts = responseParts))
        }
        throw GeminiException("Příliš mnoho kol volání nástrojů.")
    }

    private suspend fun streamRound(
        apiKey: String,
        model: String,
        systemPrompt: String,
        contents: List<Content>,
        onDelta: suspend (String) -> Unit
    ): Content {
        var attempt = 0
        while (true) {
            val request = GenerateContentRequest(
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                contents = contents,
                tools = listOf(Tools(functionDeclarations = ToolSpecs.declarations)),
                toolConfig = ToolConfig(FunctionCallingConfig(mode = "AUTO")),
                // Nižší teplota = spolehlivější function calling. 0.7 způsobovalo
                // u lite modelů simulaci toolů textem místo skutečných volání.
                generationConfig = GenerationConfig(temperature = CHAT_TEMPERATURE)
            )
            val bodyJson = json.encodeToString(GenerateContentRequest.serializer(), request)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "request model=$model contents=${contents.size} bytes=${bodyJson.length}")
            }
            val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val httpRequest = Request.Builder()
                .url("$BASE_URL/$model:streamGenerateContent?alt=sse")
                .addHeader("x-goog-api-key", apiKey)
                .post(body)
                .build()

            val response = http.newCall(httpRequest).await()

            if (!response.isSuccessful) {
                val errorText = response.use { it.body?.string().orEmpty() }
                val retryable = response.code == 429 || response.code == 503
                if (retryable && attempt < MAX_RETRIES - 1) {
                    delay(RETRY_BASE_DELAY_MS * (1L shl attempt))
                    attempt++
                    continue
                }
                val message = if (retryable) {
                    "Model je momentálně přetížen. Zkus to prosím za chvíli."
                } else {
                    val apiMessage = runCatching {
                        json.decodeFromString(ApiError.serializer(), errorText).error?.message
                    }.getOrNull()
                    val hint = when {
                        response.code == 404 -> " Zkontroluj název modelu v Nastavení (model asi neexistuje)."
                        response.code == 400 && errorText.contains("tool", true) ->
                            " Vypadá to na nepodporovanou kombinaci model+nástroje – zkus jiný model."
                        else -> ""
                    }
                    (apiMessage ?: "HTTP ${response.code}") + hint
                }
                Log.w(TAG, "request failed HTTP ${response.code}: ${errorText.take(300)}")
                throw GeminiException(message)
            }

            val source: BufferedSource = response.body?.source()
                ?: throw GeminiException("Prázdná odpověď serveru.")

            val parts = mutableListOf<Part>()
            try {
                source.use { src ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue
                        currentCoroutineContext().ensureActive()
                        val chunk = json.decodeFromString(GenerateContentResponse.serializer(), payload)
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "chunk finish=${chunk.candidates?.firstOrNull()?.finishReason}")
                        }
                        val chunkParts = chunk.candidates?.firstOrNull()?.content?.parts ?: continue
                        chunkParts.forEach { part -> accumulatePart(parts, part, onDelta) }
                    }
                }
            } catch (e: IOException) {
                if (parts.isEmpty()) {
                    throw GeminiException("Síťová chyba během streamování: ${e.message}")
                }
            }
            return Content(role = "model", parts = parts)
        }
    }

    private suspend fun accumulatePart(
        accumulated: MutableList<Part>,
        part: Part,
        onDelta: suspend (String) -> Unit
    ) {
        when {
            part.text != null -> {
                onDelta(part.text)
                val last = accumulated.lastOrNull()
                if (last != null && last.text != null && last.functionCall == null && last.thoughtSignature == null) {
                    accumulated[accumulated.size - 1] = last.copy(text = last.text + part.text)
                } else {
                    accumulated.add(Part(text = part.text))
                }
            }
            part.functionCall != null -> {
                // Fragmenty jednoho volání rozsekaného přes více SSE chunků slepit,
                // ale SAMOSTATNÁ volání (např. 3× log_food pro vícesložkové jídlo)
                // nikdy neslučovat – proto merge jen když je předchozí stejnojmenné
                // volání neúplné (chybí mu povinné parametry).
                val incoming = part.functionCall
                val lastIndex = accumulated.size - 1
                val lastCall = accumulated.lastOrNull()?.functionCall
                if (lastCall != null &&
                    stripPrefix(lastCall.name) == stripPrefix(incoming.name) &&
                    !isCompleteCall(lastCall) &&
                    incoming.args != null
                ) {
                    val mergedArgs = JsonObject(lastCall.args?.entries.orEmpty().associate { it.key to it.value } +
                        incoming.args.entries.associate { it.key to it.value })
                    val prev = accumulated[lastIndex]
                    accumulated[lastIndex] = prev.copy(
                        functionCall = lastCall.copy(args = mergedArgs),
                        thoughtSignature = part.thoughtSignature ?: prev.thoughtSignature
                    )
                } else {
                    accumulated.add(part)
                }
            }
            part.thoughtSignature != null -> {
                val last = accumulated.lastOrNull()
                if (last != null && last.thoughtSignature == null) {
                    accumulated[accumulated.size - 1] = last.copy(thoughtSignature = part.thoughtSignature)
                } else {
                    accumulated.add(part)
                }
            }
        }
    }

    /** Povinné parametry má? Podle toho poznáme fragment vs. samostatné volání. */
    private fun isCompleteCall(call: FunctionCall): Boolean {
        val args: JsonObject = call.args ?: return false
        fun text(key: String): String? =
            (args[key] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it !is JsonNull }
                ?.content?.takeIf { it.isNotBlank() }
        return when (stripPrefix(call.name)) {
            ToolSpecs.SAVE_WEIGHT -> (args["weight_kg"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.doubleOrNull != null
            ToolSpecs.LOG_FOOD -> text("name") != null
            ToolSpecs.LOG_WATER -> true
            ToolSpecs.LOG_WORKOUT -> text("name") != null
            ToolSpecs.SAVE_FACT -> text("category") != null && text("content") != null
            ToolSpecs.DELETE_FACT -> (args["fact_id"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.let { it.longOrNull ?: it.content.toLongOrNull() } != null
            else -> true
        }
    }

    private fun stripPrefix(name: String): String = name.substringAfterLast(':').trim()

    /**
     * Očistí historii od textových simulací toolů, aby se z nich model neučil.
     * User zprávy nechává nedotčené; prázdné model zprávy zachovává v původní
     * podobě, aby se nerozbilo střídání rolí user/model.
     */
    private fun sanitizeHistory(history: List<Content>): List<Content> =
        history.map { content ->
            if (content.role != "model") return@map content
            val parts = content.parts.mapNotNull { part ->
                val text = part.text ?: return@mapNotNull part
                if (!ToolTextFallback.containsPseudoCall(text)) return@mapNotNull part
                val clean = ToolTextFallback.sanitizeModelText(text)
                if (clean.isBlank()) null else part.copy(text = clean)
            }
            if (parts.isEmpty()) content else content.copy(parts = parts)
        }

    internal fun normalizeModelName(raw: String): String {
        val cleaned = raw.trim().removePrefix("models/").trim()
        return cleaned.ifBlank { SettingsRepository.DEFAULT_MODEL }
    }

    suspend fun fetchModelNames(): List<String> = withContext(Dispatchers.IO) {
        val apiKey = settings.apiKey.first()
        if (apiKey.isBlank()) throw GeminiException("Chybí API klíč. Zadej ho v Nastavení.")
        val request = Request.Builder()
            .url("$BASE_URL?pageSize=200")
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
        val response = http.newCall(request).await()
        val text = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) {
            val message = runCatching { json.decodeFromString(ApiError.serializer(), text).error?.message }
                .getOrNull()
                ?: "HTTP ${response.code}"
            throw GeminiException(message)
        }
        val parsed = json.decodeFromString(ModelsResponse.serializer(), text)
        parsed.models
            .filter { model -> model.supportedGenerationMethods.any { it.equals("generateContent", true) } }
            .map { it.name.removePrefix("models/").trim() }
            .filter { it.isNotBlank() }
            // Modely bez podpory textu/nástrojů (embedding, image, TTS…) do pickeru nepatří –
            // dřív se nabízely a po přepnutí na ně přestal fungovat tool calling.
            .filter { name -> EXCLUDED_MODEL_SUBSTRINGS.none { name.contains(it, ignoreCase = true) } }
            .distinct()
            .sortedWith(modelComparator())
    }

    private fun modelComparator(): Comparator<String> = Comparator { a, b ->
        fun stability(name: String): Int =
            if (name.contains("preview", true) || name.contains("beta", true) ||
                name.contains("exp", true) || name.contains("alpha", true)
            ) 1 else 0

        fun family(name: String): Int {
            val n = name.lowercase().replace('_', '-')
            return when {
                // Plný flash je pro function calling spolehlivější než lite –
                // proto ho řadíme výš (lite zůstává v nabídce).
                n.contains("flash") && !n.contains("lite") -> 0
                n.contains("flash-lite") || n.contains("flashlite") -> 1
                n.contains("pro") -> 2
                else -> 3
            }
        }
        compareValuesBy(a, b, ::stability, ::family, { it })
            .let { if (it != 0) it else b.compareTo(a) }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(GeminiException("Síťová chyba: ${e.message}"))
                }
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_TOOL_ROUNDS = 6
        private const val MAX_RETRIES = 4
        private const val RETRY_BASE_DELAY_MS = 2000L
        private const val CHAT_TEMPERATURE = 0.3f
        private val EXCLUDED_MODEL_SUBSTRINGS = listOf(
            "embedding", "embed", "imagen", "image", "tts", "speech",
            "veo", "video", "audio", "aqua", "lyria", "music"
        )
    }
}
