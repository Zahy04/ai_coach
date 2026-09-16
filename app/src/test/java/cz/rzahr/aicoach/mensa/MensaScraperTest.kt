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

    // ---- Denní stránka (?clPodsystem=N): fotky, alergeny, váhy ----

    private fun dailyHtml(): String = """
        <html><body>
        <span class="podsys-title"><b>Technická menza Středa 16. 9. 2026</b></span>
        <div class='row row-cols-1 row-cols-md-2 g-4'>
          <div class="col"><div class="card h-100 shadow"><div class="card-body">
            <span class="badge rounded-pill text-bg-secondary me-2">Polévky</span>
            <h6 class="card-title mb-1">
              <input type="hidden" id="JN409073x" value="Kuřecí vývar se zeleninou a tarhoňou ">
              Kuřecí vývar se zeleninou a tarhoňou</h6>
            <input id="Alg409073x" type="hidden" value="1,9">
          </div></div></div>
          <div class="col"><div class="card h-100 shadow"><div class="row g-0 h-100">
            <div class="col-4 col-md-4 pe-2">
              <img src="showfotodb.php?JidloID=409076" class="img-fluid" alt="foto jídla">
              <input id="img409076x" type="hidden" value="showfotodb.php?JidloID=409076">
            </div>
            <div class="col"><div class="col d-flex flex-column h-100">
              <span class="badge rounded-pill text-bg-secondary me-2">Hlavní jídla</span>
              <span class="badge rounded-pill text-bg-success me-2">1</span>
              <h6 class="card-title mb-1">
                <input type="hidden" id="JN409076x" value="Kuřecí steak, hranolky/ vařené brambory">
                Kuřecí steak, hranolky</h6>
              <p class="text-muted mb-2">120 g</p>
              <input id="Alg409076x" type="hidden" value="7">
            </div></div>
          </div></div></div>
          <div class="col"><div class="card h-100 shadow"><div class="row g-0 h-100">
            <div class="col-4 col-md-4 pe-2">
              <img src="showfotodb.php?JidloID=409080" class="img-fluid" alt="foto jídla">
            </div>
            <div class="col"><div class="col d-flex flex-column h-100">
              <span class="badge rounded-pill text-bg-secondary me-2">Moučníky</span>
              <h6 class="card-title mb-1">
                <input type="hidden" id="JN409080x" value="Meruňková kapsa">
                Meruňková kapsa</h6>
              <p class="text-muted mb-2">1 ks</p>
            </div></div>
          </div></div></div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `denni stranka vrati datum a vsechna jidla`() {
        val data = scraper.parseDailyHtml(3, dailyHtml())

        assertEquals(LocalDate.of(2026, 9, 16), data.date)
        assertEquals(3, data.rows.size)
    }

    @Test
    fun `polevka nema fotku ani gramaz ale ma alergeny`() {
        val data = scraper.parseDailyHtml(3, dailyHtml())
        val soup = data.rows.first { it.category == "Polévky" }

        assertEquals("Kuřecí vývar se zeleninou a tarhoňou", soup.name)
        assertEquals(null, soup.photoUrl)
        assertEquals(null, soup.grams)
        assertEquals(409073, soup.jidloId)
        assertEquals("1,9", soup.allergens)
    }

    @Test
    fun `hlavni jidlo ma fotku gramaz jidloId a spravnou kategorii`() {
        val data = scraper.parseDailyHtml(3, dailyHtml())
        val main = data.rows.first { it.jidloId == 409076 }

        // výdejní číslo "1" se nesmí splést s kategorií
        assertEquals("Hlavní jídla", main.category)
        assertEquals("Kuřecí steak, hranolky/ vařené brambory", main.name)
        assertEquals(120, main.grams)
        assertEquals(
            "https://agata.suz.cvut.cz/jidelnicky/showfotodb.php?JidloID=409076",
            main.photoUrl
        )
        assertEquals("7", main.allergens)
    }

    @Test
    fun `kusova vaha neni gramaz`() {
        val data = scraper.parseDailyHtml(3, dailyHtml())
        val dessert = data.rows.first { it.jidloId == 409080 }

        assertEquals(null, dessert.grams)
        assertTrue(dessert.photoUrl!!.contains("JidloID=409080"))
    }

    @Test
    fun `parseGrams zvladne nezlomitelnou mezeru`() {
        assertEquals(120, scraper.parseGrams("120 g"))
        assertEquals(350, scraper.parseGrams("350 g"))
        assertEquals(null, scraper.parseGrams("1 ks"))
        assertEquals(null, scraper.parseGrams(""))
    }
}
