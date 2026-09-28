package cz.rzahr.aicoach.ui.food

import cz.rzahr.aicoach.llm.OffNutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Testy přepočtu OFF hodnot na 100 g na zadanou gramáž (skener, issue #1). */
class ScanScaleTest {

    private val chips = OffNutrition(
        productName = "Brambůrky",
        caloriesPer100g = 536,
        proteinPer100g = 6.5,
        carbsPer100g = 53.0,
        fatPer100g = 33.0
    )

    @Test
    fun `scaleForGrams prepocita na gramy a zaokrouhli`() {
        val result = scaleForGrams(chips, 50)
        assertEquals("Brambůrky", result.name)
        assertEquals(268, result.calories)
        assertEquals(3.3, result.proteinG!!, 0.001) // 3,25 → half-up 3,3
        assertEquals(26.5, result.carbsG!!, 0.001)
        assertEquals(16.5, result.fatG!!, 0.001)
        assertEquals(50, result.grams)
    }

    @Test
    fun `scaleForGrams zvladne 100g a null makra`() {
        val water = OffNutrition("Voda", 0, null, null, null)
        val result = scaleForGrams(water, 100)
        assertEquals(0, result.calories)
        assertNull(result.proteinG)
        assertNull(result.carbsG)
        assertNull(result.fatG)
    }
}
