package cz.rzahr.aicoach.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFiltersTest {

    @Test
    fun `pro flash a flash-lite vsechny verze projdou`() {
        assertTrue(ModelFilters.isRecommended("gemini-2.5-pro"))
        assertTrue(ModelFilters.isRecommended("gemini-2.5-flash"))
        assertTrue(ModelFilters.isRecommended("gemini-3-flash-lite"))
        assertTrue(ModelFilters.isRecommended("models/gemini-2.0-flash-lite-001"))
        assertTrue(ModelFilters.isRecommended("gemini-3.6-flash-lite"))
    }

    @Test
    fun `gemma projde`() {
        assertTrue(ModelFilters.isRecommended("gemma-3-27b-it"))
        assertTrue(ModelFilters.isRecommended("gemma-4-test"))
    }

    @Test
    fun `ostatni se vyfiltruji`() {
        assertFalse(ModelFilters.isRecommended("gemini-embedding-001"))
        assertFalse(ModelFilters.isRecommended("imagen-3.0-generate-002"))
        assertFalse(ModelFilters.isRecommended("veo-2.0-generate-001"))
        assertFalse(ModelFilters.isRecommended("learnlm-2.0-flash-experimental"))
        assertFalse(ModelFilters.isRecommended(""))
    }

    @Test
    fun `filtr zachova jen doporucene`() {
        val input = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-embedding-001",
            "gemma-3-27b-it",
            "imagen-3.0-generate-002"
        )
        val filtered = ModelFilters.filterRecommended(input)
        assertTrue(filtered.contains("gemini-2.5-pro"))
        assertTrue(filtered.contains("gemini-2.5-flash"))
        assertTrue(filtered.contains("gemma-3-27b-it"))
        assertFalse(filtered.contains("gemini-embedding-001"))
        assertFalse(filtered.contains("imagen-3.0-generate-002"))
    }

    @Test
    fun `classify zaradi model do spravne rodiny`() {
        assertEquals(ModelFilters.Family.PRO, ModelFilters.classify("gemini-2.5-pro"))
        assertEquals(ModelFilters.Family.FLASH, ModelFilters.classify("gemini-2.5-flash"))
        // Flash-Lite obsahuje i "flash", presto patri do lite rodiny.
        assertEquals(ModelFilters.Family.FLASH_LITE, ModelFilters.classify("gemini-3-flash-lite"))
        assertEquals(ModelFilters.Family.FLASH_LITE, ModelFilters.classify("models/gemini-2.0-flash-lite-001"))
        assertEquals(ModelFilters.Family.GEMMA, ModelFilters.classify("gemma-3-27b-it"))
        assertEquals(ModelFilters.Family.OTHER, ModelFilters.classify("gemini-embedding-001"))
        assertEquals(ModelFilters.Family.OTHER, ModelFilters.classify("imagen-3.0-generate-002"))
        assertEquals(ModelFilters.Family.OTHER, ModelFilters.classify(""))
    }

    @Test
    fun `filterByFamilies necha jen vybrane rodiny`() {
        val input = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.0-flash-lite-001",
            "gemma-3-27b-it",
            "gemini-embedding-001"
        )
        val onlyFlash = ModelFilters.filterByFamilies(
            input, setOf(ModelFilters.Family.FLASH)
        )
        assertEquals(listOf("gemini-2.5-flash"), onlyFlash)

        val default = ModelFilters.filterByFamilies(input, ModelFilters.DEFAULT_FAMILIES)
        assertTrue(default.contains("gemini-2.5-pro"))
        assertTrue(default.contains("gemini-2.5-flash"))
        assertTrue(default.contains("gemini-2.0-flash-lite-001"))
        assertTrue(default.contains("gemma-3-27b-it"))
        assertFalse(default.contains("gemini-embedding-001"))

        val all = ModelFilters.filterByFamilies(
            input, setOf(
                ModelFilters.Family.PRO,
                ModelFilters.Family.FLASH,
                ModelFilters.Family.FLASH_LITE,
                ModelFilters.Family.GEMMA,
                ModelFilters.Family.OTHER
            )
        )
        assertEquals(input, all)
    }

    @Test
    fun `countByFamily spocita rodiny`() {
        val counts = ModelFilters.countByFamily(
            listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash", "gemma-3-27b-it")
        )
        assertEquals(1, counts[ModelFilters.Family.PRO])
        assertEquals(2, counts[ModelFilters.Family.FLASH])
        assertEquals(1, counts[ModelFilters.Family.GEMMA])
        assertNull(counts[ModelFilters.Family.OTHER])
    }

    @Test
    fun `hidden schova model se zapnutou rodinou`() {
        val input = listOf("gemini-2.5-pro", "gemini-2.5-flash")
        val filtered = ModelFilters.filterBySelection(
            input,
            ModelFilters.DEFAULT_FAMILIES,
            hidden = setOf("gemini-2.5-pro")
        )
        assertEquals(listOf("gemini-2.5-flash"), filtered)
    }

    @Test
    fun `shown ukaze model s vypnutou rodinou`() {
        val input = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-embedding-001")
        // Vsechny rodiny vypnute, jen jeden rucne zaskrtnuty model.
        val filtered = ModelFilters.filterBySelection(
            input,
            families = emptySet(),
            shown = setOf("gemini-2.5-flash")
        )
        assertEquals(listOf("gemini-2.5-flash"), filtered)
    }

    @Test
    fun `bez vyjimek je vyber stejny jako rodiny`() {
        val input = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-embedding-001")
        assertEquals(
            ModelFilters.filterByFamilies(input, ModelFilters.DEFAULT_FAMILIES),
            ModelFilters.filterBySelection(input, ModelFilters.DEFAULT_FAMILIES)
        )
    }

    @Test
    fun `isVisibleBySelection kombinuje rodiny a vyjimky`() {
        val families = ModelFilters.DEFAULT_FAMILIES
        assertTrue(ModelFilters.isVisibleBySelection("gemini-2.5-pro", families))
        assertFalse(
            ModelFilters.isVisibleBySelection("gemini-2.5-pro", families, hidden = setOf("gemini-2.5-pro"))
        )
        assertFalse(ModelFilters.isVisibleBySelection("gemini-embedding-001", families))
        assertTrue(
            ModelFilters.isVisibleBySelection(
                "gemini-embedding-001", families, shown = setOf("gemini-embedding-001")
            )
        )
    }
}
