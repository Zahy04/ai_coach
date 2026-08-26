package cz.rzahr.aicoach.llm

import cz.rzahr.aicoach.data.repo.FactRepository
import cz.rzahr.aicoach.data.repo.WaterRepository
import cz.rzahr.aicoach.data.repo.WeightRepository
import cz.rzahr.aicoach.util.Locales
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
        val czech = Locales.isCzech()

        return buildString {
            if (czech) {
                appendLine("Jsi osobní fitness trenér a výživový poradce. Odpovídej česky, přátelsky, stručně a konkrétně.")
            } else {
                appendLine("You are a personal fitness coach and nutrition advisor. Answer in English, in a friendly, concise and concrete way.")
            }
            appendLine("Aktuální datum a čas / Current date and time: $now")
            if (czech) {
                appendLine("Každá zpráva uživatele začíná časovou značkou [yyyy-MM-dd HH:mm] — ber ji v úvahu (kdy jedl, kdy cvičil apod.).")
            } else {
                appendLine("Each user message starts with a timestamp [yyyy-MM-dd HH:mm] — take it into account (when they ate, trained etc.).")
            }
            appendLine()

            if (weights.isNotEmpty()) {
                if (czech) appendLine("Historie váhy uživatele:") else appendLine("User's weight history:")
                val dateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy")
                weights.forEach { entry ->
                    val date = java.time.Instant.ofEpochMilli(entry.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(dateFormatter)
                    appendLine("- $date: ${entry.weightKg} kg")
                }
                appendLine()
            }

            if (czech) {
                appendLine("Dnes vypito vody: $waterToday ml.")
            } else {
                appendLine("Water drunk today: $waterToday ml.")
            }
            appendLine()

            if (facts.isNotEmpty()) {
                if (czech) {
                    appendLine("Co víš o uživateli (id použiješ v delete_fact):")
                } else {
                    appendLine("What you know about the user (use ids in delete_fact):")
                }
                facts.forEach { fact ->
                    appendLine("- [id=${fact.id}] (${fact.category}) ${fact.content}")
                }
                appendLine()
            }

            appendLine("Pravidla / Rules:")
            if (czech) {
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
            } else {
                appendLine("- When the user mentions their current weight, call save_weight.")
                appendLine("- When the user mentions what they ate or drank, call log_food and ALWAYS include quantity_g (an estimate of the amount). The app fills in macros from the Open Food Facts database — don't rely on your own estimates.")
                appendLine("- A meal consisting of multiple components (side dish, meat, vegetables, sauce…) MUST be split into separate log_food calls — each component with its own estimated grams. Example: 'chicken with beans and cucumber' = three calls: chicken (~200 g), beans (~150 g), cucumber (~100 g). Never log a combination as one meal.")
                appendLine("- When the user mentions drinking water, call log_water.")
                appendLine("- When the user describes a workout or physical activity, call log_workout.")
                appendLine("- Only call save_fact for genuinely NEW information about the user. If the information is already in the list above, don't save it again.")
                appendLine("- If the user changes or refines existing information (e.g. a new goal), call save_fact with the fact_id of the existing note so it gets overwritten.")
                appendLine("- When the user says some stored information is no longer valid, call delete_fact with its id.")
                appendLine("- After writing via tools, don't write long summaries or lists of what you saved. Reply briefly and naturally.")
                appendLine("- Give practical advice about food, calories, training and recovery.")
            }
        }
    }

    companion object {
        private const val WEIGHT_HISTORY_LIMIT = 10
    }
}
