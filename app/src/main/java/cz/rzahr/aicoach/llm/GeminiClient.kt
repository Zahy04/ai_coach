package cz.rzahr.aicoach.llm

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
        val model = settings.model.first()
        val contents = history.toMutableList()
        val events = mutableListOf<String>()
        val turnContext = TurnContext()

        repeat(MAX_TOOL_ROUNDS) {
            val modelContent = streamRound(apiKey, model, systemPrompt, contents, onDelta)
            if (modelContent.parts.isEmpty()) {
                throw GeminiException("Model vrátil prázdnou odpověď.")
            }
            contents.add(modelContent)

            val calls = modelContent.parts.mapNotNull { it.functionCall }
            if (calls.isEmpty()) {
                val text = modelContent.parts.mapNotNull { it.text }.joinToString("\n").trim()
                if (text.isEmpty()) throw GeminiException("Model vrátil prázdnou odpověď.")
                return@withContext ChatTurnResult(text, events)
            }

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
                generationConfig = GenerationConfig(temperature = 0.7f)
            )
            val bodyJson = json.encodeToString(GenerateContentRequest.serializer(), request)
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
                    runCatching { json.decodeFromString(ApiError.serializer(), errorText).error?.message }
                        .getOrNull()
                        ?: "HTTP ${response.code}"
                }
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
            part.functionCall != null -> accumulated.add(part)
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
            .map { it.name.removePrefix("models/") }
            .sortedDescending()
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
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_TOOL_ROUNDS = 6
        private const val MAX_RETRIES = 4
        private const val RETRY_BASE_DELAY_MS = 2000L
    }
}
