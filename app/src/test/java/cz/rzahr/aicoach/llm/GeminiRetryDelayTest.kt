package cz.rzahr.aicoach.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testy výpočtu pauzy před retryem při 429/503 (issue #2).
 * computeRetryDelayMs je čistá funkce v companionu – bez sítě i bez Androidu.
 */
class GeminiRetryDelayTest {

    @Test
    fun `retryDelay v tele ma prednost pred vsim`() {
        val body = """{"error":{"code":429,"message":"overloaded","details":[{"retryDelay":"23s"}]}}"""
        assertEquals(23_000L, GeminiClient.computeRetryDelayMs(4, body, "5", 0L))
    }

    @Test
    fun `Retry-After hlavicka kdyz telo retryDelay neobsahuje`() {
        assertEquals(7_000L, GeminiClient.computeRetryDelayMs(0, """{"error":{}}""", "7", 0L))
    }

    @Test
    fun `exponencialni backoff bez serverovych hintu`() {
        assertEquals(2_000L, GeminiClient.computeRetryDelayMs(0, "", null, 0L))
        assertEquals(4_000L, GeminiClient.computeRetryDelayMs(1, "", null, 0L))
        assertEquals(64_000L, GeminiClient.computeRetryDelayMs(5, "", null, 0L))
    }

    @Test
    fun `jitter se pricitava k backoffu`() {
        assertEquals(2_500L, GeminiClient.computeRetryDelayMs(0, "", null, 500L))
    }

    @Test
    fun `pauza je orezana na 90s`() {
        val body = """{"error":{"details":[{"retryDelay":"600s"}]}}"""
        assertEquals(90_000L, GeminiClient.computeRetryDelayMs(0, body, null, 0L))
    }

    @Test
    fun `neplatny Retry-After se ignoruje a pouzije backoff`() {
        assertEquals(2_000L, GeminiClient.computeRetryDelayMs(0, "", "abc", 0L))
    }

    @Test
    fun `parseRetryAfterMs`() {
        assertEquals(5_000L, GeminiClient.parseRetryAfterMs("5"))
        assertEquals(10_000L, GeminiClient.parseRetryAfterMs(" 10 "))
        assertNull(GeminiClient.parseRetryAfterMs("abc"))
        assertNull(GeminiClient.parseRetryAfterMs("-3"))
    }
}
