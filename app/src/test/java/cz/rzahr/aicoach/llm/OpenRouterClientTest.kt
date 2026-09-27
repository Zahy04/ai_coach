package cz.rzahr.aicoach.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Testy OpenRouter klienta proti mockovanému /v1 API.
 * Streamovací chat() testovat nejde bez Android Contextu (SettingsRepository),
 * proto se pokrývají čisté companion funkce + nestreamované completeJson.
 */
class OpenRouterClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    private fun baseUrl() = server.url("/v1").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `historie se mapuje na OpenAI messages vcetne fotky`() {
        val history = listOf(
            Content(
                role = "user",
                parts = listOf(
                    Part(inlineData = InlineData(mimeType = "image/jpeg", data = "AAA")),
                    Part(text = "[2026-09-27 10:00] Co je tohle za jídlo?")
                )
            ),
            Content(role = "model", parts = listOf(Part(text = "Vypadá to na guláš.")))
        )
        val messages = OpenRouterClient.toOpenAiMessages("sys", history)
        assertEquals(3, messages.size)
        assertEquals("system", messages[0]["role"]!!.jsonPrimitive.content)
        val user = messages[1]
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        val content = user["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        val image = content[1].jsonObject
        assertEquals("image_url", image["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,AAA",
            image["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        )
        assertEquals("assistant", messages[2]["role"]!!.jsonPrimitive.content)
        assertEquals("Vypadá to na guláš.", messages[2]["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `razeni modelu qwen prvni pak muse pak free`() {
        val sorted = OpenRouterClient.sortModels(
            listOf("openai/gpt-4o", "meta/muse-spark-1.3", "zzz/model:free", "qwen/qwen3.8-27b:free", "meta/muse-spark-1.3-contributor")
        )
        assertEquals(
            listOf(
                "qwen/qwen3.8-27b:free",
                "meta/muse-spark-1.3-contributor",
                "meta/muse-spark-1.3",
                "zzz/model:free",
                "openai/gpt-4o"
            ),
            sorted
        )
    }

    @Test
    fun `normalizeModel pada zpet na default`() {
        assertEquals("qwen/qwen3.8-27b:free", OpenRouterClient.normalizeModel(""))
        assertEquals("qwen/qwen3.8-27b:free", OpenRouterClient.normalizeModel("gemini-3.6-flash-lite"))
        assertEquals("meta/muse-spark-1.3", OpenRouterClient.normalizeModel("  meta/muse-spark-1.3  "))
    }

    @Test
    fun `openAiTools ma 6 nastroju ve spravne obalce`() {
        val tools = ToolSpecs.openAiTools()
        assertEquals(6, tools.size)
        val names = tools.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(names.containsAll(listOf("save_weight", "log_food", "log_water", "log_workout", "save_fact", "delete_fact")))
        tools.forEach {
            assertEquals("function", it.jsonObject["type"]!!.jsonPrimitive.content)
            assertTrue(it.jsonObject["function"]!!.jsonObject.containsKey("parameters"))
        }
    }

    @Test
    fun `assistantMessage a toolMessage maji OpenAI tvar`() {
        val call = buildJsonObject { put("name", "log_water") }
        val msg = OpenRouterClient.assistantMessage(
            "Zapisuji.",
            listOf(OpenRouterToolCall("call-1", "log_water", call))
        )
        assertEquals("assistant", msg["role"]!!.jsonPrimitive.content)
        val tc = msg["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("call-1", tc["id"]!!.jsonPrimitive.content)
        assertEquals("log_water", tc["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)

        val tool = OpenRouterClient.toolMessage("call-1", buildJsonObject { put("status", "ok") })
        assertEquals("tool", tool["role"]!!.jsonPrimitive.content)
        assertEquals("call-1", tool["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `completeJson vrati text odpovedi`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":"{\"meals\":[]}"}}]}"""
            ).setHeader("Content-Type", "application/json")
        )
        val text = OpenRouterClient.completeJson(
            client, json, "k", "qwen/qwen3.8-27b:free", "prompt",
            baseUrl = baseUrl()
        )
        assertEquals("{\"meals\":[]}", text)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/chat/completions"))
        assertEquals("Bearer k", recorded.getHeader("Authorization"))
    }

    @Test
    fun `completeJson po 429 vrati null`() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"busy"}}"""))
        }
        val text = OpenRouterClient.completeJson(
            client, json, "k", "m", "p",
            baseUrl = baseUrl(), maxAttempts = 3
        )
        assertEquals(null, text)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `completeJson 401 hodi srozumitelnou chybu`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        try {
            OpenRouterClient.completeJson(client, json, "k", "m", "p", baseUrl = baseUrl())
            error("Měla být vyhozena OpenRouterException")
        } catch (e: OpenRouterException) {
            assertTrue(e.message!!.contains("bad key"))
            assertTrue(e.message!!.contains("Nastavení"))
        }
    }

    @Test
    fun `filterToolModels propusti jen modely s tools a seradi`() {
        val models = listOf(
            OrModelInfo("openai/gpt-4o", listOf("tools", "temperature")),
            OrModelInfo("qwen/qwen3.8-27b:free", listOf("tools")),
            OrModelInfo("no-tools/model", listOf("temperature")),
            OrModelInfo("  ", listOf("tools")),
            OrModelInfo("openai/gpt-4o", listOf("tools"))
        )
        assertEquals(
            listOf("qwen/qwen3.8-27b:free", "openai/gpt-4o"),
            OpenRouterClient.filterToolModels(models)
        )
    }
}
