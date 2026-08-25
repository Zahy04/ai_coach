package cz.rzahr.aicoach.llm

import cz.rzahr.aicoach.data.repo.FactRepository
import cz.rzahr.aicoach.data.repo.WaterRepository
import cz.rzahr.aicoach.data.repo.WeightRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

@Singleton
class PromptBuilder @Inject constructor(
    private val weightRepository: WeightRepository,
    private val factRepository: FactRepository,
    private val waterRepository: WaterRepository
) {

    suspend fun build(): String {
        val weights = weightRepository.latestAsc(WEIGHT_HISTORY_LIMIT)
        val facts = factRepository.all()
        val waterToday = waterRepository.observeTodayMl().firstOrNull() ?: 0
        val now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d. M. yyyy HH:mm"))

        return buildString {
            appendLine("Jsi osobní fitness trenér a výživový poradce. Odpovídej česky, přátelsky, stručně a konkrétně.")
            appendLine("Aktuální datum a čas: $now")
            appendLine("Každá zpráva uživatele začíná časovou značkou [yyyy-MM-dd HH:mm] — ber ji v úvahu (kdy jedl, kdy cvičil apod.).")
            appendLine()

            if (weights.isNotEmpty()) {
                appendLine("Historie váhy uživatele:")
                val dateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy")
                weights.forEach { entry ->
                    val date = java.time.Instant.ofEpochMilli(entry.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(dateFormatter)
                    appendLine("- $date: ${entry.weightKg} kg")
                }
                appendLine()
            }

            appendLine("Dnes vypito vody: $waterToday ml.")
            appendLine()

            if (facts.isNotEmpty()) {
                appendLine("Co víš o uživateli (id použiješ v delete_fact):")
                facts.forEach { fact ->
                    appendLine("- [id=${fact.id}] (${fact.category}) ${fact.content}")
                }
                appendLine()
            }

            appendLine("Pravidla:")
            appendLine("- Když uživatel zmíní svou aktuální váhu, zavolej save_weight.")
            appendLine("- Když uživatel zmíní, co snědl nebo vypil, zavolej log_food a VŽDY uveď quantity_g (odhad množství). Makra doplní appka z databáze Open Food Facts — nespoléhej na svých odhadech.")
            appendLine("- Jídlo skládající se z více složek (příloha, maso, zelenina, omáčka…) ROZLOŽ na samostatná log_food volání — každá složka zvlášť se svým odhadem gramáže. Příklad: 'kuřecí s fazolemi a okurkou' = tři volání: kuřecí (~200 g), fazole (~150 g), okurka (~100 g). Nikdy nezapisuj kombinaci jako jedno jídlo.")
            appendLine("- Když uživatel zmíní vypitou vodu, zavolej log_water.")
            appendLine("- Když uživatel popíše trénink nebo fyzickou aktivitu, zavolej log_workout.")
            appendLine("- save_fact volej jen pro skutečně NOVÉ informace o uživateli. Pokud informace už je v seznamu výše, neukládej ji znovu.")
            appendLine("- Pokud uživatel změní nebo upřesní existující informaci (např. nový cíl), zavolej save_fact s fact_id té existující poznámky, aby se přepsala.")
            appendLine("- Když uživatel řekne, že některá uložená informace už neplatí, zavolej delete_fact s příslušným id.")
            appendLine("- Po zápisu přes nástroje nepsat dlouhá shrnutí ani výpisy toho, co jsi uložil. Odpověz krátce a přirozeně.")
            appendLine("- Dávej praktické rady ohledně jídla, kalorií, tréninku a regenerace.")
        }
    }

    companion object {
        private const val WEIGHT_HISTORY_LIMIT = 10
    }
}
