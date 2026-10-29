package cz.rzahr.aicoach.mensa

import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MensaVisionPairingTest {

    private fun meal(name: String) = MensaMealEntity(
        systemId = 3, weekKey = "w", epochDay = 0, category = "Hlavní jídla",
        name = name, photoUrl = "https://example.invalid/$name"
    )

    @Test
    fun `po nepovedenem stazeni dostane kazde jidlo svou fotku`() {
        val meals = listOf(meal("A"), meal("B"), meal("C"))
        // Fotka jídla A se nestáhla.
        val downloaded = mapOf(1 to VisionImage(base64 = "PHOTO_B"), 2 to VisionImage(base64 = "PHOTO_C"))

        val (kept, images) = MensaEstimatorCore.pairWithImages(meals, downloaded)
        val parts = MensaEstimatorCore.buildVisionParts(kept, images).drop(1)

        assertEquals(listOf("B", "C"), kept.map { it.name })
        assertEquals(
            listOf("Foto k položce 0 (B):", "PHOTO_B", "Foto k položce 1 (C):", "PHOTO_C"),
            parts.map { it.text ?: it.inlineData!!.data }
        )
    }
}
