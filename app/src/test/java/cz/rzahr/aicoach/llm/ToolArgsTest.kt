package cz.rzahr.aicoach.llm

import cz.rzahr.aicoach.llm.ToolArgs.within
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgsTest {

    private fun args(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `cele cislo zapsane jako desetinne se precte`() {
        // kotlinx intOrNull vrací pro 500.0 null → voda se dřív tiše zapsala jako 250 ml.
        val a = args("""{"amount_ml":500.0,"fact_id":3.0,"calories":"350","weight":"78,5"}""")
        assertEquals(500, ToolArgs.int(a, "amount_ml"))
        assertEquals(3L, ToolArgs.long(a, "fact_id"))
        assertEquals(350, ToolArgs.int(a, "calories"))
        assertEquals(78.5, ToolArgs.double(a, "weight")!!, 0.0001)
    }

    @Test
    fun `chybejici null a nesmysly jsou null`() {
        val a = args("""{"x":null,"y":"abc","z":{"a":1},"s":"  "}""")
        assertNull(ToolArgs.int(a, "missing"))
        assertNull(ToolArgs.int(a, "x"))
        assertNull(ToolArgs.int(a, "y"))
        assertNull(ToolArgs.int(a, "z"))
        assertNull(ToolArgs.string(a, "s"))
    }

    @Test
    fun `boolean prijme i text`() {
        val a = args("""{"a":true,"b":"true","c":"no"}""")
        assertTrue(ToolArgs.boolean(a, "a")!!)
        assertTrue(ToolArgs.boolean(a, "b")!!)
        assertNull(ToolArgs.boolean(a, "c"))
    }

    @Test
    fun `within zahodi hodnoty mimo rozsah`() {
        assertNull((-5).within(1..5000))
        assertEquals(250, 250.within(1..5000))
        assertNull(785.0.within(ToolArgs.MIN_WEIGHT_KG..ToolArgs.MAX_WEIGHT_KG))
    }

    @Test
    fun `odhad modelu ma prednost pred Open Food Facts`() {
        val model = ResolvedNutrition(calories = 260, proteinG = 5.0, carbsG = 56.0, fatG = 0.6, refGrams = 200.0)
        val dryRice = OffNutrition("Rýže dlouhozrnná", 350, 7.0, 78.0, 0.6)
        val result = NutritionChoice.choose(model, dryRice, 200.0)
        assertFalse(result.fromOff)
        assertEquals(260, result.nutrition.calories)
    }

    @Test
    fun `Open Food Facts doplni jen chybejici kalorie`() {
        val model = ResolvedNutrition(calories = null, proteinG = null, carbsG = null, fatG = null, refGrams = 150.0)
        val off = OffNutrition("Jogurt", 60, 4.0, 5.0, 3.0)
        val result = NutritionChoice.choose(model, off, 150.0)
        assertTrue(result.fromOff)
        assertEquals(90, result.nutrition.calories)
        assertEquals(6.0, result.nutrition.proteinG!!, 0.0001)
        assertEquals(150.0, result.nutrition.refGrams!!, 0.0001)
    }

    @Test
    fun `gemini modely nejnovejsi verze prvni`() {
        val sorted = listOf("gemini-2.0-flash", "gemini-3.0-flash", "gemini-2.5-flash", "gemini-3.0-pro-preview")
            .sortedWith(GeminiClient.MODEL_COMPARATOR)
        assertEquals(listOf("gemini-3.0-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-3.0-pro-preview"), sorted)
    }
}
