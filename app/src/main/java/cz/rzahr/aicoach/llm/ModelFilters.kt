package cz.rzahr.aicoach.llm

/**
 * Filtr modelů pro výběr v Nastavení / chatu.
 *
 * Hlavní vypínač filtruje nabídku na uživatelem vybrané rodiny modelů.
 * Výchozí výběr (přednastavený filtr):
 * - Gemini Pro (všechny verze),
 * - Gemini Flash (všechny verze),
 * - Gemini Flash-Lite (všechny verze),
 * - Gemma (všechny verze, vč. Gemma 3 / 4).
 *
 * Nad rodinami jde navíc naklikat jednotlivé modely:
 * - `hidden` schová konkrétní model, i když je jeho rodina zapnutá,
 * - `shown` naopak ukáže konkrétní model, i když je jeho rodina vypnutá
 *   (takhle jde vybrat třeba jen 2–3 oblíbené modely a zbytek schovat).
 *
 * Vypnutím hlavního vypínače se vrátí plný seznam z API.
 */
object ModelFilters {

    enum class Family {
        PRO,
        FLASH,
        FLASH_LITE,
        GEMMA,
        OTHER
    }

    /** Zařadí model do rodiny podle názvu (všechny verze dané řady). */
    fun classify(rawName: String): Family {
        val name = rawName.trim().removePrefix("models/").lowercase().replace('_', '-')
        if (name.contains("gemma")) return Family.GEMMA
        if (name.contains("gemini")) {
            // Flash-Lite musí před Flash – jeho název obsahuje i "flash".
            if (name.contains("flash-lite") || name.contains("flashlite")) return Family.FLASH_LITE
            if (name.contains("flash")) return Family.FLASH
            if (name.contains("pro")) return Family.PRO
        }
        return Family.OTHER
    }

    fun isRecommended(rawName: String): Boolean =
        classify(rawName) != Family.OTHER

    fun filterRecommended(models: List<String>): List<String> =
        models.filter(::isRecommended)

    /** Přednastavený výběr = Pro / Flash / Flash-Lite / Gemma. */
    val DEFAULT_FAMILIES: Set<Family> =
        setOf(Family.PRO, Family.FLASH, Family.FLASH_LITE, Family.GEMMA)

    /** Nechá jen modely z vybraných rodin. */
    fun filterByFamilies(models: List<String>, enabled: Set<Family>): List<String> =
        models.filter { classify(it) in enabled }

    /** Je model vidět při daném výběru rodin + individuálních výjimek? */
    fun isVisibleBySelection(
        rawName: String,
        families: Set<Family>,
        hidden: Set<String> = emptySet(),
        shown: Set<String> = emptySet()
    ): Boolean {
        val name = rawName.trim()
        return (classify(name) in families && name !in hidden) || name in shown
    }

    /** Nechá jen modely viditelné při daném výběru rodin + individuálních výjimek. */
    fun filterBySelection(
        models: List<String>,
        families: Set<Family>,
        hidden: Set<String> = emptySet(),
        shown: Set<String> = emptySet()
    ): List<String> = models.filter { isVisibleBySelection(it, families, hidden, shown) }

    /** Počty modelů v jednotlivých rodinách (pro popisky v Nastavení). */
    fun countByFamily(models: List<String>): Map<Family, Int> =
        models.groupingBy(::classify).eachCount()
}
