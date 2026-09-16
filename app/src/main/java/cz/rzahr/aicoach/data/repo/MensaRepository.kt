package cz.rzahr.aicoach.data.repo

import android.content.Context
import android.util.Base64
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.dao.MensaMealDao
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import cz.rzahr.aicoach.mensa.MensaEstimator
import cz.rzahr.aicoach.mensa.MensaScraper
import cz.rzahr.aicoach.mensa.ParsedMeal
import cz.rzahr.aicoach.mensa.VisionImage
import java.time.LocalDate
import java.time.LocalTime
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val scraper: MensaScraper,
    private val estimator: MensaEstimator,
    private val dao: MensaMealDao,
    private val settingsRepository: SettingsRepository
) {

    fun observeMeals(systemId: Int, epochDay: Long): Flow<List<MensaMealEntity>> =
        dao.observeMeals(systemId, epochDay)

    /**
     * Denní stránka je primár (má fotky, alergeny, aktuální dnešek),
     * týdenní slouží jako backup — když denní ještě není (víkend, brzké ráno)
     * a pro výhled na další dny.
     *
     * Fotky kuchyně vystavuje až kolem 10:30–10:45, proto se denní stránka
     * přetahuje i během dne, dokud dnešní jídla nemají žádnou fotku.
     * Jídla s fotkou se odhadují přes vision, zbytek textově.
     */
    suspend fun refresh(force: Boolean): MensaRefreshResult {
        var anySuccess = false
        var scrapeError = false
        var estimateError: String? = null
        val today = LocalDate.now()

        for (systemId in listOf(MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM, MensaMealEntity.SYSTEM_TECHNICKA)) {
            var dailyTodayOk = false

            // 1) denní stránka — primár pro dnešek
            if (force || needsDailyRefresh(systemId, today)) {
                val daily = scraper.scrapeDaily(systemId)
                if (daily != null && daily.rows.isNotEmpty()) {
                    anySuccess = true
                    dailyTodayOk = true
                    mergeDaily(systemId, daily.date, daily.rows)
                } else if (daily == null) {
                    scrapeError = true
                }
            } else {
                anySuccess = true
                dailyTodayOk = dao.getBySystemDay(systemId, today.toEpochDay()).isNotEmpty()
            }

            // 2) týdenní stránka — backup + výhled na další dny
            val currentWeekKey = MensaScraper.weekKeyFor(today)
            val cachedCurrentWeek = dao.countBySystemWeek(systemId, currentWeekKey)
            if (force || cachedCurrentWeek == 0) {
                val scraped = scraper.scrape(systemId)
                if (scraped == null) {
                    scrapeError = true
                } else {
                    anySuccess = true
                    mergeWeekly(systemId, today, dailyTodayOk, scraped.rows)
                }
            } else {
                anySuccess = true
            }

            // 3a) textový odhad pro jídla bez fotky a bez odhadu
            val missingText = dao.getWithoutEstimateWithoutPhoto(systemId)
            if (missingText.isNotEmpty() && estimateError == null) {
                try {
                    // ruční refresh = trpělivý (čeká i na přetížení), automatický = rychlý
                    val estimates = estimator.estimate(systemId, missingText, patient = force)
                    estimates.orEmpty().forEach { estimate ->
                        val meal = missingText.getOrNull(estimate.index) ?: return@forEach
                        dao.updateEstimate(
                            id = meal.id,
                            kcalMin = estimate.kcalMin,
                            kcalMax = estimate.kcalMax,
                            proteinG = estimate.proteinG,
                            carbsG = estimate.carbsG,
                            fatG = estimate.fatG,
                            rating = estimate.verdict,
                            visionEstimated = false
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MensaRepository", "text estimate selhal: ${e.message}", e)
                    estimateError = e.message ?: "Odhad kalorií se nepodařil."
                }
            }

            // 3b) vision odhad pro jídla s fotkou (i již textově odhadnutá → zpřesnění)
            val visionMeals = dao.getPhotoNeedingVision(systemId).take(MAX_VISION_MEALS)
            if (visionMeals.isNotEmpty() && estimateError == null) {
                try {
                    val images = downloadPhotos(visionMeals)
                    if (images.isNotEmpty()) {
                        val withImages = visionMeals.filterIndexed { index, _ -> images.containsKey(index) }
                        val estimates = estimator.estimateVision(
                            systemId, withImages, images, patient = force
                        )
                        estimates.orEmpty().forEach { estimate ->
                            val meal = withImages.getOrNull(estimate.index) ?: return@forEach
                            dao.updateEstimate(
                                id = meal.id,
                                kcalMin = estimate.kcalMin,
                                kcalMax = estimate.kcalMax,
                                proteinG = estimate.proteinG,
                                carbsG = estimate.carbsG,
                                fatG = estimate.fatG,
                                rating = estimate.verdict,
                                visionEstimated = true
                            )
                        }
                        // fotka stažena, ale odhad se nevrátil pro všechna → zbytek
                        // označíme, ať vision nezkoušíme pořád dokola
                        val estimatedIds = estimates.orEmpty().mapNotNull { withImages.getOrNull(it.index)?.id }.toSet()
                        withImages.filter { it.id !in estimatedIds }.forEach { meal ->
                            if (meal.kcalMin != null) {
                                dao.updateEstimate(
                                    id = meal.id,
                                    kcalMin = meal.kcalMin,
                                    kcalMax = meal.kcalMax,
                                    proteinG = meal.proteinG,
                                    carbsG = meal.carbsG,
                                    fatG = meal.fatG,
                                    rating = meal.rating,
                                    visionEstimated = true
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MensaRepository", "vision estimate selhal: ${e.message}", e)
                    // vision je bonus — textový odhad (3a) už proběhl, takže jen logujeme
                    // a chybu hlásíme, jen když jídla nemají vůbec žádný odhad
                    if (visionMeals.any { it.kcalMin == null }) {
                        estimateError = e.message ?: "Odhad z fotky se nepodařil."
                    }
                }
            }
        }

        if (anySuccess) {
            settingsRepository.setMensaLastFetchDay(today.toEpochDay().toString())
        }

        return MensaRefreshResult(
            ok = anySuccess,
            estimateError = estimateError ?: if (scrapeError && !anySuccess) {
                context.getString(R.string.mensa_scrape_failed)
            } else {
                null
            }
        )
    }

    /**
     * Denní refresh je potřeba, když dnešek chybí, nebo když dnešní jídla
     * zatím nemají žádnou fotku a už je čas, kdy fotky bývají venku.
     * Volá i auto-refresher, aby po 10:30 dotáhl fotky na pozadí.
     */
    suspend fun needsPhotoRefresh(): Boolean {
        val today = LocalDate.now()
        if (LocalTime.now().isBefore(PHOTO_CHECK_FROM)) return false
        return listOf(
            MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM,
            MensaMealEntity.SYSTEM_TECHNICKA
        ).any { systemId ->
            val todays = dao.getBySystemDay(systemId, today.toEpochDay())
            todays.isNotEmpty() && todays.none { it.photoUrl != null }
        }
    }

    private suspend fun needsDailyRefresh(systemId: Int, today: LocalDate): Boolean {
        val todays = dao.getBySystemDay(systemId, today.toEpochDay())
        if (todays.isEmpty()) return true
        // fotky přibývají kolem 10:30–10:45 — dokud není ani jedna, zkoušíme znovu
        return todays.none { it.photoUrl != null } &&
            !LocalTime.now().isBefore(PHOTO_CHECK_FROM)
    }

    /** Denní data přepíšou daný den (jsou čerstvější a mají fotky), odhady se přenesou. */
    private suspend fun mergeDaily(systemId: Int, date: LocalDate, rows: List<ParsedMeal>) {
        val existing = dao.getBySystemDay(systemId, date.toEpochDay())
        val byJidlo = existing.filter { it.jidloId != null }.associateBy { it.jidloId }
        val byName = existing.associateBy { it.name }
        dao.deleteBySystemDay(systemId, date.toEpochDay())
        dao.insertAll(rows.map { row ->
            val prev = row.jidloId?.let { byJidlo[it] } ?: byName[row.name]
            val entity = row.toEntity()
            if (prev == null) {
                entity
            } else {
                entity.copy(
                    kcalMin = prev.kcalMin,
                    kcalMax = prev.kcalMax,
                    proteinG = prev.proteinG,
                    carbsG = prev.carbsG,
                    fatG = prev.fatG,
                    rating = prev.rating,
                    // nová fotka → vision znovu; stejná fotka → ponechat stav
                    visionEstimated = prev.visionEstimated && prev.photoUrl == entity.photoUrl
                )
            }
        })
    }

    /**
     * Týdenní data doplní zbytek týdne. Dnešek sáhne jen tehdy, když se
     * denní stránku nepodařilo stáhnout (jinak by smazala fotky).
     */
    private suspend fun mergeWeekly(
        systemId: Int,
        today: LocalDate,
        dailyTodayOk: Boolean,
        rows: List<ParsedMeal>
    ) {
        val todayEpoch = today.toEpochDay()
        val todayWeekKey = MensaScraper.weekKeyFor(today)
        val preservedToday = if (dailyTodayOk) {
            dao.getBySystemDay(systemId, todayEpoch)
        } else {
            emptyList()
        }

        val coveredKeys = rows.map { MensaScraper.weekKeyFor(it.date) }.distinct()
        for (key in coveredKeys) {
            var keyRows = rows.filter { MensaScraper.weekKeyFor(it.date) == key }
            if (key == todayWeekKey && dailyTodayOk) {
                val known = preservedToday.map { it.name }.toSet()
                keyRows = keyRows.filter { it.date.toEpochDay() != todayEpoch || it.name !in known }
            }
            dao.deleteBySystemWeek(systemId, key)
            if (keyRows.isNotEmpty()) dao.insertAll(keyRows.map { it.toEntity() })
            if (key == todayWeekKey && dailyTodayOk && preservedToday.isNotEmpty()) {
                dao.insertAll(preservedToday.map { it.copy(id = 0) })
            }
        }
    }

    private suspend fun downloadPhotos(meals: List<MensaMealEntity>): Map<Int, VisionImage> {
        val result = mutableMapOf<Int, VisionImage>()
        meals.forEachIndexed { index, meal ->
            val url = meal.photoUrl ?: return@forEachIndexed
            val bytes = estimator.fetchPhotoBytes(url)
            if (bytes != null && bytes.isNotEmpty()) {
                result[index] = VisionImage(
                    mimeType = "image/jpeg",
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                )
            }
        }
        return result
    }

    private fun ParsedMeal.toEntity() = MensaMealEntity(
        systemId = systemId,
        weekKey = MensaScraper.weekKeyFor(date),
        epochDay = date.toEpochDay(),
        category = category,
        name = name,
        grams = grams,
        jidloId = jidloId,
        photoUrl = photoUrl,
        allergens = allergens
    )

    companion object {
        /** Od tohoto času už fotky daného dne bývají venku — zkoušíme je dotáhnout. */
        private val PHOTO_CHECK_FROM: LocalTime = LocalTime.of(10, 15)
        private const val MAX_VISION_MEALS = 10
    }
}
