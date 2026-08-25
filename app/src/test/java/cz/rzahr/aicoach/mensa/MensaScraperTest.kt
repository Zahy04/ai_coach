package cz.rzahr.aicoach.mensa

import java.time.LocalDate
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser testovaný na skutečném HTML Technické menzy
 * (stažené s session cookies — soubor mensa_tech.html).
 */
class MensaScraperTest {

    private val scraper = MensaScraper(OkHttpClient())

    private fun realHtml(): String =
        javaClass.getResourceAsStream("/mensa_tech.html")!!
            .readBytes().decodeToString()

    @Test
    fun `parsuje realny tyden z technicke menzy`() {
        val data = scraper.parseHtml(3, realHtml())

        assertTrue("Očekávám desítky jídel, je ${data.rows.size}", data.rows.size >= 30)
        assertTrue(
            "Měly by být rozpoznané dny",
            data.rows.any { it.date == LocalDate.of(2026, 8, 25) } &&
                data.rows.any { it.date == LocalDate.of(2026, 8, 28) }
        )
        assertTrue(data.closedDays.isEmpty())
    }

    @Test
    fun `rozpozna kategorie a gramaz`() {
        val data = scraper.parseHtml(3, realHtml())

        val soup = data.rows.first { it.name.contains("Slepičí vývar") }
        assertEquals(LocalDate.of(2026, 8, 25), soup.date)
        assertEquals("Polévky", soup.category)
        assertEquals(null, soup.grams)

        val beef = data.rows.first { it.name.startsWith("Hovězí pečeně") }
        assertEquals(120, beef.grams)
        assertEquals("Hlavní jídla", beef.category)
    }

    @Test
    fun `dnesni den obsahuje hlavni jidla`() {
        val today = LocalDate.of(2026, 8, 25)
        val data = scraper.parseHtml(3, realHtml())

        val todayMeals = data.rows.filter { it.date == today }
        assertTrue(
            "Dnešek (Úterý 25. 8.) má mít jídla, má ${todayMeals.size}",
            todayMeals.any { it.category == "Hlavní jídla" }
        )
    }
}
