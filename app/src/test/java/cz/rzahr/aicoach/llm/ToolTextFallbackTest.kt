package cz.rzahr.aicoach.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pokrývá záchrannou síť pro textové simulace toolů
 * (model napíše `log_food(...)` jako text místo function callu)
 * a serializaci requestu, která tool calling dřív tiše rozbíjela.
 */
class ToolTextFallbackTest {

    // Reálná ukázka z produkce.
    private val prodExample =
        "Zapisuji: log_food(name=\"toastový chléb\", quantity_g=180, calories=450, " +
            "protein_g=15, carbs_g=90, fat_g=3) Dneska už máš splněno."

    @Test
    fun `produkcni priklad se prevede na spustitelny call`() {
        val calls = ToolTextFallback.extractExecutableCalls(prodExample)
        assertEquals(1, calls.size)
        val call = calls[0]
        assertEquals("log_food", call.name)
        val args = call.args!!
        assertEquals("toastový chléb", args["name"]!!.jsonPrimitive.content)
        assertEquals(180, args["quantity_g"]!!.jsonPrimitive.doubleOrNull!!.toInt())
        assertEquals(450, args["calories"]!!.jsonPrimitive.intOrNull)
        assertEquals(15.0, args["protein_g"]!!.jsonPrimitive.doubleOrNull!!, 0.0001)
    }

    @Test
    fun `sanitizace odstrani pseudo-volani ale necha okolni text`() {
        val clean = ToolTextFallback.sanitizeModelText(prodExample)
        assertFalse(clean.contains("log_food"))
        assertTrue(clean.contains("Zapisuji:"))
        assertTrue(clean.contains("Dneska už máš splněno."))
    }

    @Test
    fun `zavorky v uvozovkach extrakci nerozbiji`() {
        val text = "log_food(name=\"Kuře (prsa) 200g\", calories=300)"
        val calls = ToolTextFallback.extractExecutableCalls(text)
        assertEquals(1, calls.size)
        assertEquals("Kuře (prsa) 200g", calls[0].args!!["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `vicenasobne log_food volani zustanou samostatna`() {
        val text = "log_food(name=\"kuřecí\", quantity_g=200) " +
            "log_food(name=\"fazole\", quantity_g=150) " +
            "log_food(name=\"okurka\", quantity_g=100)"
        val calls = ToolTextFallback.extractExecutableCalls(text)
        assertEquals(3, calls.size)
        assertEquals("kuřecí", calls[0].args!!["name"]!!.jsonPrimitive.content)
        assertEquals("okurka", calls[2].args!!["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `prazdne volani bez povinnych parametru se nespusti`() {
        assertTrue(ToolTextFallback.extractExecutableCalls("log_food()").isEmpty())
        assertTrue(ToolTextFallback.extractExecutableCalls("save_weight()").isEmpty())
        assertTrue(ToolTextFallback.extractExecutableCalls("log_food(calories=100)").isEmpty())
    }

    @Test
    fun `save_weight validuje rozsah`() {
        assertEquals(1, ToolTextFallback.extractExecutableCalls("save_weight(weight_kg=78.5)").size)
        assertTrue(ToolTextFallback.extractExecutableCalls("save_weight(weight_kg=-5)").isEmpty())
        assertTrue(ToolTextFallback.extractExecutableCalls("save_weight(weight_kg=900)").isEmpty())
    }

    @Test
    fun `desetinna carka se pochopi jako tecka`() {
        val calls = ToolTextFallback.extractExecutableCalls("save_weight(weight_kg=78,5)")
        assertEquals(1, calls.size)
        assertEquals(78.5, calls[0].args!!["weight_kg"]!!.jsonPrimitive.doubleOrNull!!, 0.0001)
    }

    @Test
    fun `code fence s toolem se odstrani cely`() {
        val text = "Tady je zápis:\n```json\nlog_food(name=\"chleba\", calories=200)\n```\nHotovo."
        val clean = ToolTextFallback.sanitizeModelText(text)
        assertFalse(clean.contains("log_food"))
        assertFalse(clean.contains("```"))
        assertTrue(clean.contains("Hotovo."))
    }

    @Test
    fun `bezny text bez toolu se nemeni`() {
        val text = "Dneska sis vedl skvěle, dej si k večeři víc bílkovin."
        assertEquals(text, ToolTextFallback.sanitizeModelText(text))
        assertTrue(ToolTextFallback.extractExecutableCalls(text).isEmpty())
    }

    @Test
    fun `request serializace obsahuje toolConfig a vynechava nully`() {
        // Stejná konfigurace jako produkční AppModule.
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = "sys"))),
            contents = listOf(Content(role = "user", parts = listOf(Part(text = "ahoj")))),
            tools = listOf(Tools(functionDeclarations = ToolSpecs.declarations)),
            toolConfig = ToolConfig(FunctionCallingConfig(mode = "AUTO")),
            generationConfig = GenerationConfig(temperature = 0.3f)
        )
        val encoded = json.encodeToString(GenerateContentRequest.serializer(), request)
        assertTrue(encoded.contains("functionCallingConfig"))
        assertTrue(encoded.contains("\"AUTO\""))
        assertTrue(encoded.contains("log_food"))
        // S explicitNulls=false nesmí request obsahovat explicitní nully,
        // které Gemini API tiše degradovalo (ignorování tools).
        assertFalse(encoded.contains(":null"))
    }

    @Test
    fun `deklarace toolu zakazuji textovou simulaci`() {
        ToolSpecs.declarations.forEach { decl ->
            assertTrue(
                "Popis ${decl.name} musí zakazovat psaní toolu jako text",
                decl.description.contains("function calling")
            )
        }
        assertNotNull(ToolSpecs.declarations.firstOrNull { it.name == ToolSpecs.LOG_FOOD })
    }

    @Test
    fun `delete_fact vyzaduje id`() {
        assertEquals(1, ToolTextFallback.extractExecutableCalls("delete_fact(fact_id=12)").size)
        assertTrue(ToolTextFallback.extractExecutableCalls("delete_fact()").isEmpty())
    }

    @Test
    fun `neznama funkce se ignoruje`() {
        assertNull(ToolTextFallback.toFunctionCall(
            ToolTextFallback.PseudoCall("smazat_vse", "", 0, 1)
        ))
    }
}
