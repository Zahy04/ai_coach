package cz.rzahr.aicoach.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TurnContext] nese provider a model do telemetrie nástrojů (issue #6) –
 * bez nich by se v exportu nedalo poznat, kterému modelu patří odhad.
 */
class TurnContextTest {

    @Test
    fun `prazdny kontext ma prazdneho providera a model`() {
        val ctx = TurnContext()

        assertEquals("", ctx.provider)
        assertEquals("", ctx.model)
        assertEquals(0, ctx.round)
        assertFalse(ctx.viaTextFallback)
    }

    @Test
    fun `kontext si drzi providera, model a stav kola`() {
        val ctx = TurnContext("openrouter", "qwen/qwen3.8-27b:free")

        ctx.round = 2
        ctx.viaTextFallback = true

        assertEquals("openrouter", ctx.provider)
        assertEquals("qwen/qwen3.8-27b:free", ctx.model)
        assertEquals(2, ctx.round)
        assertTrue(ctx.viaTextFallback)
        // Zapsané jídlo se sdílí mezi koly jednoho tahu, ne mezi zprávami.
        assertTrue(ctx.loggedFoods.isEmpty())
    }
}