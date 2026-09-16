package cz.rzahr.aicoach.mensa

import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class MensaWeekData(
    val rows: List<ParsedMeal>,
    val closedDays: Set<LocalDate>
)

data class MensaDayData(
    val date: LocalDate,
    val rows: List<ParsedMeal>
)

data class ParsedMeal(
    val systemId: Int,
    val date: LocalDate,
    val category: String,
    val name: String,
    val grams: Int?,
    val jidloId: Int? = null,
    val photoUrl: String? = null,
    val allergens: String? = null
)

@Singleton
class MensaScraper @Inject constructor(
    http: OkHttpClient
) {

    /**
     * Stránky SUZ vyžadují PHP session — první request nastaví cookie a vrátí jen shell,
     * teprve další s cookies obsahují menu. Proto vlastní CookieJar + druhý pokus.
     */
    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, List<okhttp3.Cookie>>()

    private val client = http.newBuilder()
        .cookieJar(object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
                cookieStore[url.host].orEmpty()
        })
        .build()

    suspend fun scrape(systemId: Int): MensaWeekData? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?clPodsystem=$systemId&lang=cs"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .get()
            .build()

        // první dotaz získá session cookie, druhý teprve menu
        var lastResult: MensaWeekData? = null
        repeat(2) { attempt ->
            try {
                val html = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string() ?: return@withContext null
                }
                val parsed = parseHtml(systemId, html)
                if (parsed.rows.isNotEmpty() || parsed.closedDays.isNotEmpty()) {
                    lastResult = parsed
                    return@withContext parsed
                }
                lastResult = parsed
                if (attempt == 0) delay(500)
            } catch (_: IOException) {
                return@withContext null
            }
        }
        lastResult
    }

    /**
     * Denní jídelníček — primární zdroj pro dnešek. Oproti týdennímu obsahuje
     * ID jídla, fotku (showfotodb.php?JidloID=…), alergeny a cenu.
     * Fotky kuchyně vystavuje až kolem 10:30–10:45 daného dne, do té doby
     * se vrací jídla bez photoUrl.
     */
    suspend fun scrapeDaily(systemId: Int): MensaDayData? = withContext(Dispatchers.IO) {
        val url = "$DAILY_BASE_URL?clPodsystem=$systemId&lang=cs"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .get()
            .build()

        repeat(2) { attempt ->
            try {
                val html = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string() ?: return@withContext null
                }
                val parsed = parseDailyHtml(systemId, html)
                if (parsed.rows.isNotEmpty()) return@withContext parsed
                if (attempt == 0) delay(500)
            } catch (_: IOException) {
                return@withContext null
            }
        }
        null
    }

    /**
     * Parser denní stránky (?clPodsystem=N). Každé jídlo je karta
     * `div.col > div.card` s odznakem kategorie, skrytým inputem JN{id}x
     * (kanonický název), váhou v `p.text-muted`, fotkou
     * `img[src*=showfotodb.php?JidloID=]` a alergeny v inputu Alg{id}x.
     * Polévky fotku ani váhu nemají.
     */
    fun parseDailyHtml(systemId: Int, html: String): MensaDayData {
        val document = Jsoup.parse(html)
        val headerText = document.select("span.podsys-title").firstOrNull()?.text().orEmpty()
        val date = dayHeaderRegex.find(headerText)?.let { match ->
            LocalDate.of(
                match.groupValues[3].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[1].toInt()
            )
        } ?: LocalDate.now()

        val rows = mutableListOf<ParsedMeal>()
        for (card in document.select("div.col > div.card")) {
            val category = card.select("span.badge.rounded-pill")
                .map { it.text().trim() }
                // výdejní číslo ("5") není kategorie
                .firstOrNull { it.isNotBlank() && !it.all(Char::isDigit) }
                ?: continue

            val nameInput = card.selectFirst("input[id^=JN][id\$=x]")
            val idFromName = nameInput?.id()?.let { jidloIdRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            val name = nameInput?.attr("value")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: card.selectFirst("h6.card-title")?.text()?.trim().orEmpty()
            if (name.isBlank()) continue
            if (name.equals("ZAVŘENO", ignoreCase = true)) continue

            val grams = card.selectFirst("p.text-muted")?.text()?.let(::parseGrams)

            val photoSrc = card.selectFirst("img[src*=showfotodb.php]")?.attr("src")?.trim()
            val idFromPhoto = photoSrc?.let { photoIdRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            val jidloId = idFromName ?: idFromPhoto
            val photoUrl = when {
                photoSrc.isNullOrBlank() -> null
                photoSrc.startsWith("http") -> photoSrc
                else -> DAILY_BASE_URL + photoSrc.removePrefix("./").removePrefix("/")
            }

            val allergens = card.selectFirst("input[id^=Alg]")?.attr("value")?.trim()
                ?.takeIf { it.isNotBlank() }

            rows += ParsedMeal(
                systemId = systemId,
                date = date,
                category = category,
                name = name,
                grams = grams,
                jidloId = jidloId,
                photoUrl = photoUrl,
                allergens = allergens
            )
        }
        return MensaDayData(date, rows)
    }

    /** "120 g" (i s nezlomitelnou mezerou) → 120; "1 ks" a jiné → null. */
    fun parseGrams(raw: String): Int? {
        val normalized = raw.replace('\u00A0', ' ').trim()
        return gramsRegex.matchEntire(normalized)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun parseHtml(systemId: Int, html: String): MensaWeekData {
        val dayRegex = Regex(
            "^(?:Pondělí|Úterý|Středa|Čtvrtek|Pátek|Sobota|Neděle)\\s+(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})$"
        )
        val gramsRegex = Regex("^(\\d+)\\s*g\\s+(.*)$", RegexOption.DOT_MATCHES_ALL)
        val categories = setOf(
            "Polévky",
            "Hlavní jídla",
            "Vegetariánská jídla",
            "Veganská jídla",
            "Minutky",
            "Specialita dne",
            "Moučníky"
        )

        val rows = mutableListOf<ParsedMeal>()
        val closedDays = mutableSetOf<LocalDate>()
        var currentDate: LocalDate? = null
        var currentCategory: String? = null

        // Stránka není tabulka, ale Bootstrap karty z divů:
        // hlavička dne = listový div "Středa <b>26. 8. 2026</b>",
        // kategorie = div.menu-type, jídlo = div.menu-name.
        val document = Jsoup.parse(html)
        for (cell in document.select("div")) {
            // zajímají nás jen "listové" divy (bez přímých div-dětí) —
            // pozor: Element.select("div") matchuje i sebe sama!
            val hasDirectDivChild = cell.children().any { it.tagName() == "div" }
            if (hasDirectDivChild) continue

            val text = cell.text().trim()
            if (text.isEmpty()) continue

            val dayMatch = dayRegex.matchEntire(text)
            if (dayMatch != null) {
                currentDate = LocalDate.of(
                    dayMatch.groupValues[3].toInt(),
                    dayMatch.groupValues[2].toInt(),
                    dayMatch.groupValues[1].toInt()
                )
                currentCategory = null
                continue
            }

            if (currentDate == null) continue

            if (text in categories) {
                currentCategory = text
                continue
            }

            if (currentCategory == null) continue

            if (text.equals("ZAVŘENO", ignoreCase = true)) {
                closedDays += currentDate
                continue
            }

            // poznámka pod jídelníčkem apod.
            if (text.startsWith("Upozornění") || text.startsWith("Na jídelním lístku")) continue

            val gramMatch = gramsRegex.matchEntire(text)
            val mealName: String
            val grams: Int?
            if (gramMatch != null) {
                grams = gramMatch.groupValues[1].toIntOrNull()
                mealName = gramMatch.groupValues[2].trim()
            } else {
                grams = null
                mealName = text
            }
            if (mealName.isBlank()) continue

            rows += ParsedMeal(
                systemId = systemId,
                date = currentDate,
                category = currentCategory,
                name = mealName,
                grams = grams
            )
        }
        return MensaWeekData(rows, closedDays)
    }

    companion object {
        private const val BASE_URL = "https://agata.suz.cvut.cz/jidelnicky/indexTyden.php"
        const val DAILY_BASE_URL = "https://agata.suz.cvut.cz/jidelnicky/"
        private const val USER_AGENT = "AiCoach/1.0 (android)"

        private val dayHeaderRegex = Regex(
            "(?:Pondělí|Úterý|Středa|Čtvrtek|Pátek|Sobota|Neděle)\\s+(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})"
        )
        private val jidloIdRegex = Regex("JN(\\d+)x")
        private val photoIdRegex = Regex("JidloID=(\\d+)")
        private val gramsRegex = Regex("^(\\d+)\\s*g\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)

        fun photoUrlFor(jidloId: Int): String =
            "${DAILY_BASE_URL}showfotodb.php?JidloID=$jidloId"

        fun weekKeyFor(date: LocalDate): String =
            date.with(java.time.DayOfWeek.MONDAY).toEpochDay().toString()

        fun systemName(systemId: Int): String = when (systemId) {
            MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM -> "Studentský dům"
            MensaMealEntity.SYSTEM_TECHNICKA -> "Technická menza"
            else -> "Menza $systemId"
        }

        fun czechWeekNumber(date: LocalDate): Int = date.get(WeekFields.of(Locale.forLanguageTag("cs")).weekOfWeekBasedYear())
    }
}
