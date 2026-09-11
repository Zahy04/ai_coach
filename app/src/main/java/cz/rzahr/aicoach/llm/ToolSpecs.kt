package cz.rzahr.aicoach.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ToolSpecs {

    const val SAVE_WEIGHT = "save_weight"
    const val LOG_FOOD = "log_food"
    const val LOG_WATER = "log_water"
    const val LOG_WORKOUT = "log_workout"
    const val SAVE_FACT = "save_fact"
    const val DELETE_FACT = "delete_fact"

    val declarations = listOf(
        FunctionDeclaration(
            name = SAVE_WEIGHT,
            description = "Uloží aktuální tělesnou hmotnost uživatele v kilogramech. Volá se, když uživatel zmíní svou váhu.$NEVER_NARRATE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("weight_kg") {
                        put("type", "number")
                        put("description", "Hmotnost v kg, např. 78.5")
                    }
                    putJsonObject("note") {
                        put("type", "string")
                        put("description", "Volná poznámka k vážení")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("weight_kg")) }
            }
        ),
        FunctionDeclaration(
            name = LOG_FOOD,
            description = "Zaznamená jídlo nebo nápoj, který uživatel snědl či vypil. Kalorie a makra odhadni podle běžných hodnot, pokud je uživatel neuvede. " +
                "Pokud nástroj vrátí status duplicate_suspected, NEZAPISUJ jídlo znovu, dokud se uživatel výslovně nepotvrdí; poté zavolej log_food se stejnými údaji a force=true. " +
                "Parametry date a time vynech (jídlo se pak zapíše na dnešek a aktuální čas) — vyplň je POUZE na výslovnou žádost uživatele, že jídlo patřilo jinam (např. 'včera ráno').$NEVER_NARRATE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Název jídla nebo nápoje, např. 'Ovesná kaše s banánem'")
                    }
                    putJsonObject("quantity_g") {
                        put("type", "number")
                        put("description", "Odhad množství v gramech (nebo mililitrech u nápojů). Vždy uveď, i když jen odhaduješ.")
                    }
                    putJsonObject("calories") {
                        put("type", "integer")
                        put("description", "Odhad kalorií v kcal")
                    }
                    putJsonObject("protein_g") {
                        put("type", "number")
                        put("description", "Odhad bílkovin v gramech")
                    }
                    putJsonObject("carbs_g") {
                        put("type", "number")
                        put("description", "Odhad sacharidů v gramech")
                    }
                    putJsonObject("fat_g") {
                        put("type", "number")
                        put("description", "Odhad tuků v gramech")
                    }
                    putJsonObject("date") {
                        put("type", "string")
                        put("description", "Datum ve formátu yyyy-MM-dd, POUZE když uživatel výslovně řekne, že jedl jindy než dnes. Max. 7 dní dozadu, budoucnost zamítnuta.")
                    }
                    putJsonObject("time") {
                        put("type", "string")
                        put("description", "Čas ve formátu HH:mm, pouze spolu s date. Bez obou parametrů se použije aktuální čas.")
                    }
                    putJsonObject("force") {
                        put("type", "boolean")
                        put("description", "true pouze tehdy, když uživatel VÝSLOVNĚ potvrdil, že má jídlo zapsat znovu přesto, že na něj nástroj upozornil jako na duplicitu.")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("name")) }
            }
        ),
        FunctionDeclaration(
            name = LOG_WORKOUT,
            description = "Zaznamená trénink nebo fyzickou aktivitu, kterou uživatel vykonal.$NEVER_NARRATE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Název aktivity, např. 'Silový trénink nohou'")
                    }
                    putJsonObject("duration_minutes") {
                        put("type", "integer")
                        put("description", "Délka trvání v minutách, pokud je známá")
                    }
                    putJsonObject("calories_burned") {
                        put("type", "integer")
                        put("description", "Odhad spálených kalorií, pokud je známý")
                    }
                    putJsonObject("note") {
                        put("type", "string")
                        put("description", "Poznámka k tréninku (cvičení, váhy, pocity)")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("name")) }
            }
        ),
        FunctionDeclaration(
            name = LOG_WATER,
            description = "Zaznamená vypitou vodu. Volá se, když uživatel zmíní, že pil vodu.$NEVER_NARRATE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("amount_ml") {
                        put("type", "integer")
                        put("description", "Množství v mililitrech, např. 250 nebo 500. Pokud uživatel neřekne, použij 250.")
                    }
                }
            }
        ),
        FunctionDeclaration(
            name = SAVE_FACT,
            description = "Uloží trvalou informaci o uživateli: oblíbená/neoblíbená jídla, cíle, zvyky, jídelníček, zdravotní omezení apod. Pokud aktualizuješ existující informaci, uveď její id v parametru fact_id.$NEVER_NARRATE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("category") {
                        put("type", "string")
                        put("description", "Kategorie informace")
                        putJsonArray("enum") {
                            add(kotlinx.serialization.json.JsonPrimitive("PREFERENCE"))
                            add(kotlinx.serialization.json.JsonPrimitive("DISLIKE"))
                            add(kotlinx.serialization.json.JsonPrimitive("DIET"))
                            add(kotlinx.serialization.json.JsonPrimitive("GOAL"))
                            add(kotlinx.serialization.json.JsonPrimitive("HABIT"))
                            add(kotlinx.serialization.json.JsonPrimitive("HEALTH"))
                            add(kotlinx.serialization.json.JsonPrimitive("OTHER"))
                        }
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Text informace, např. 'Má rád kuřecí maso'")
                    }
                    putJsonObject("fact_id") {
                        put("type", "integer")
                        put("description", "Id existující poznámky, kterou chceš aktualizovat. Pouze při úpravě.")
                    }
                }
                putJsonArray("required") {
                    add(kotlinx.serialization.json.JsonPrimitive("category"))
                    add(kotlinx.serialization.json.JsonPrimitive("content"))
                }
            }
        ),
        FunctionDeclaration(
            name = DELETE_FACT,
            description = "Smaže uloženou informaci o uživateli podle id. Použij, když uživatel řekne, že nějaká informace už neplatí.$NEVER_NARRATE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("fact_id") {
                        put("type", "integer")
                        put("description", "Id informace uvedené v system promptu")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("fact_id")) }
            }
        )
    )

    private const val NEVER_NARRATE =
        " Nikdy toto volání nepiš jako text do odpovědi – vždy použij skutečné function calling."

    fun okResult(extra: JsonObject? = null): JsonObject = buildJsonObject {
        put("status", "ok")
        extra?.forEach { (key, value) -> put(key, value) }
    }

    fun errorResult(message: String): JsonObject = buildJsonObject {
        put("status", "error")
        put("message", message)
    }
}
