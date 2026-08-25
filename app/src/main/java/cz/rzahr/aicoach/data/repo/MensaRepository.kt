package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.MensaMealDao
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import cz.rzahr.aicoach.mensa.MensaEstimator
import cz.rzahr.aicoach.mensa.MensaScraper
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class MensaRefreshResult(
    val ok: Boolean,
    val estimateError: String? = null
)

@Singleton
class MensaRepository @Inject constructor(
    private val scraper: MensaScraper,
    private val estimator: MensaEstimator,
    private val dao: MensaMealDao,
    private val settingsRepository: SettingsRepository
) {

    fun observeMeals(systemId: Int, epochDay: Long): Flow<List<MensaMealEntity>> =
        dao.observeMeals(systemId, epochDay)

    /**
     * Stáhne týdenní menu obou menz a uloží ho. Některé menzy už mají publikovaný
     * i následující týden, proto se každé jídlo ukládá s klíčem SVÉHO týdne.
     * Jídla bez odhadu se pokusí doodhadnout přes Gemini při každém refreshi.
     */
    suspend fun refresh(force: Boolean): MensaRefreshResult {
        var anySuccess = false
        var scrapeError = false
        var estimateError: String? = null

        // scrapovat max 1× denně (auto-refresher i ruční refresh sdílejí tento příznak),
        // ale odhad-doplňování necháme proběhnout vždycky (rychlé, když není co doodhadnout)
        val today = LocalDate.now().toEpochDay().toString()
        val alreadyFetchedToday = settingsRepository.mensaLastFetchDay.first() == today

        for (systemId in listOf(MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM, MensaMealEntity.SYSTEM_TECHNICKA)) {
            val currentWeekKey = MensaScraper.weekKeyFor(LocalDate.now())
            val cachedCurrentWeek = dao.countBySystemWeek(systemId, currentWeekKey)
            if (!force && (alreadyFetchedToday || cachedCurrentWeek > 0)) {
                anySuccess = true
            } else {
                val scraped = scraper.scrape(systemId)
                if (scraped == null) {
                    scrapeError = true
                } else {
                    anySuccess = true

                    // přepíšeme týdny, které výsledek pokrývá
                    val coveredKeys = scraped.rows.map { MensaScraper.weekKeyFor(it.date) }.distinct()
                    coveredKeys.forEach { dao.deleteBySystemWeek(systemId, it) }

                    val entities = scraped.rows.map { row ->
                        MensaMealEntity(
                            systemId = systemId,
                            weekKey = MensaScraper.weekKeyFor(row.date),
                            epochDay = row.date.toEpochDay(),
                            category = row.category,
                            name = row.name,
                            grams = row.grams
                        )
                    }
                    if (entities.isNotEmpty()) dao.insertAll(entities)
                }
            }

            // samo-oprava: jídla bez odhadu zkusíme doodhadnout
            val missing = dao.getWithoutEstimate(systemId)
            if (missing.isNotEmpty() && estimateError == null) {
                try {
                    // ruční refresh = trpělivý (čeká i na přetížení), automatický = rychlý
                    val estimates = estimator.estimate(systemId, missing, patient = force)
                    estimates.orEmpty().forEach { estimate ->
                        val meal = missing.getOrNull(estimate.index) ?: return@forEach
                        dao.updateEstimate(
                            id = meal.id,
                            kcalMin = estimate.kcalMin,
                            kcalMax = estimate.kcalMax,
                            proteinG = estimate.proteinG,
                            carbsG = estimate.carbsG,
                            fatG = estimate.fatG,
                            rating = estimate.verdict
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MensaRepository", "estimate selhal: ${e.message}", e)
                    estimateError = e.message ?: "Odhad kalorií se nepodařil."
                }
            }
        }

        if (anySuccess) settingsRepository.setMensaLastFetchDay(today)

        return MensaRefreshResult(
            ok = anySuccess,
            estimateError = estimateError ?: if (scrapeError && !anySuccess) {
                "Nepodařilo se načíst jídelníček ze stránek SUZ."
            } else {
                null
            }
        )
    }
}
