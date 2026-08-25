package cz.rzahr.aicoach.mensa

import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Testy jádra odhadce proti mockované Gemini API.
 * Pokrývají přesně tu cestu, která selhávala v produkci:
 * extrakci textu z API obálky a parsování výsledného JSON.
 */
class MensaEstimatorCoreTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Reálná podoba odpovědi Gemini: obálka candidates → content → parts → text s JSON. */
    private fun envelope(innerJson: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """{"candidates":[{"content":{"parts":[{"text":${innerJson.escapeAsJsonString()}}]}}]}"""
            )

    private fun String.escapeAsJsonString(): String =
        "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun meal(index: Int) = MensaMealEntity(
        id = index.toLong(),
        systemId = 3,
        weekKey = "test",
        epochDay = 0,
        category = "Hlavní jídla",
        name = "Kuřecí steak",
        grams = 120
    )

    private val properInnerJson =
        """{"meals":[{"index":0,"kcal_min":550,"kcal_max":750,"protein_g":38,"carbs_g":65,"fat_g":18,"verdict":"yellow"}]}"""

    @Test
    fun `extrahuje text z api obalky a parsuje odhady`() = runTest {
        server.enqueue(envelope(properInnerJson))
        val core = MensaEstimatorCore

        val text = core.generateWithRetry(
            client, json, "fake-key", "gemini-test",
            "prompt", baseUrl = server.url("/").toString().trimEnd('/')
        )
        assertEquals(properInnerJson, text)

        val result = core.parseEstimates(json, text!!, 1)
        assertEquals(1, result!!.size)
        assertEquals(550, result[0].kcalMin)
        assertEquals(750, result[0].kcalMax)
        assertEquals("YELLOW", result[0].verdict)
    }

    @Test
    fun `zvladne markdown ohraniceni kolem json`() = runTest {
        server.enqueue(envelope("```json\n$properInnerJson\n```"))
        val core = MensaEstimatorCore

        val text = core.generateWithRetry(
            client, json, "k", "gemini-test", "p", baseUrl = server.url("/").toString().trimEnd('/')
        )!!
        val result = core.parseEstimates(json, text, 1)
        assertEquals(550, result!![0].kcalMin)
    }

    @Test
    fun `zvladne bare pole bez objektu meals`() = runTest {
        val bareArray = """[{"index":0,"kcal_min":400,"kcal_max":600,"verdict":"green"}]"""
        server.enqueue(envelope(bareArray))

        val text = MensaEstimatorCore.generateWithRetry(
            client, json, "k", "m", "p", baseUrl = server.url("/").toString().trimEnd('/')
        )!!
        val result = MensaEstimatorCore.parseEstimates(json, text, 1)
        assertEquals("GREEN", result!![0].verdict)
    }

    @Test
    fun `prazdny candidates hodi chybu s duvodem`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"finishReason":"SAFETY"}],"promptFeedback":{"blockReason":"SAFETY"}}"""
            ).setHeader("Content-Type", "application/json")
        )

        try {
            MensaEstimatorCore.generateWithRetry(
                client, json, "k", "m", "p", baseUrl = server.url("/").toString().trimEnd('/')
            )
            fail("Měla být vyhozena MensaEstimationException")
        } catch (e: MensaEstimationException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }
    }

    @Test
    fun `nesmyslny text hodi chybu se snippetem odpovedi`() = runTest {
        server.enqueue(envelope("Promiň, nevím co po mně chceš."))
        val core = MensaEstimatorCore

        val text = core.generateWithRetry(
            client, json, "k", "m", "p", baseUrl = server.url("/").toString().trimEnd('/')
        )!!
        try {
            core.parseEstimates(json, text, 1)
            fail("Měla být vyhozena MensaEstimationException")
        } catch (e: MensaEstimationException) {
            assertTrue(e.message!!.contains("Neplatný formát"))
            assertTrue(e.message!!.contains("nevím")) // obsahuje úryvek skutečné odpovědi
        }
    }

    @Test
    fun `429 se opakuje a pak uspeje`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"overloaded"}}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"overloaded"}}"""))
        server.enqueue(envelope(properInnerJson))

        val text = MensaEstimatorCore.generateWithRetry(
            client, json, "k", "m", "p", baseUrl = server.url("/").toString().trimEnd('/'),
            maxRetries = 6, baseDelayMs = 10L
        )
        assertEquals(properInnerJson, text)

        // ověříme, že proběhla 3 volání (2× 429 + 1× úspěch)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `401 hodi rovnou chybu bez retrye`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API key"}}"""))

        try {
            MensaEstimatorCore.generateWithRetry(
                client, json, "bad-key", "m", "p", baseUrl = server.url("/").toString().trimEnd('/')
            )
            fail("Měla být vyhozena MensaEstimationException")
        } catch (e: MensaEstimationException) {
            assertEquals("Invalid API key", e.message)
        }
        assertEquals(1, server.requestCount) // žádný retry
    }

    @Test
    fun `index mimo rozsah se preskoci`() = runTest {
        val outOfRange = """{"meals":[{"index":5,"kcal_min":100,"kcal_max":200,"verdict":"red"},{"index":0,"kcal_min":300,"kcal_max":400,"verdict":"green"}]}"""
        server.enqueue(envelope(outOfRange))

        val text = MensaEstimatorCore.generateWithRetry(
            client, json, "k", "m", "p", baseUrl = server.url("/").toString().trimEnd('/')
        )!!
        val result = MensaEstimatorCore.parseEstimates(json, text, 1)
        assertEquals(1, result!!.size)
        assertEquals(300, result[0].kcalMin)
    }
}
