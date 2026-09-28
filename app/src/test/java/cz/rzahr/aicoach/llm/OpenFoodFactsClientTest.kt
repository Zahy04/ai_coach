package cz.rzahr.aicoach.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Testy barcode lookupu v Open Food Facts (skener, issue #1). */
class OpenFoodFactsClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()
    private lateinit var client: OpenFoodFactsClient

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenFoodFactsClient(http, json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lookupBarcode vrati produkt vcetne ceskeho nazvu`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"product":{"product_name":"Chips","product_name_cs":"Brambůrky",
                |"nutriments":{"energy-kcal_100g":536.0,"proteins_100g":6.5,"carbohydrates_100g":53.0,"fat_100g":33.0}}}""".trimMargin()
            ).setHeader("Content-Type", "application/json")
        )
        val result = client.lookupBarcode("8591234567890", baseUrl = baseUrl())

        assertEquals("Brambůrky", result!!.productName)
        assertEquals(536, result.caloriesPer100g)
        assertEquals(6.5, result.proteinPer100g!!, 0.001)
        assertEquals("8591234567890", server.takeRequest().path!!.substringAfter("/api/v2/product/").substringBefore(".json"))
    }

    @Test
    fun `lookupBarcode vrati null pro neznamy kod`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":0,"status_verbose":"product not found"}"""))
        assertNull(client.lookupBarcode("0000000000000", baseUrl = baseUrl()))
    }

    @Test
    fun `lookupBarcode vrati null bez kalorii a pro prazdny kod`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"product":{"product_name":"Voda","nutriments":{}}}"""
            )
        )
        assertNull(client.lookupBarcode("123", baseUrl = baseUrl()))
        assertNull(client.lookupBarcode("   ", baseUrl = baseUrl()))
        assertEquals(1, server.requestCount)
    }
}
