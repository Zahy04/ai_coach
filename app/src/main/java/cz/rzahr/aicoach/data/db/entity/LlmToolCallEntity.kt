package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Jeden tool call vykonaný během tahu – hlavně log_food, kde nás zajímá,
 * kolik calorie model odhadl a co nakonec uložil Open Food Facts.
 *
 * Klíčové k porovnání model vs. API je [modelCalories] vs [finalCalories]
 * (a stejně tak bílkoviny/sacharidy/tuky): dřív se odhad modelu v [log_food]
 * zahazoval, jakmile OFF cokoliv našel, takže se jeho přesnost nedala vůbec
 * měřit (issue #6).
 */
@Entity(tableName = "llm_tool_calls")
data class LlmToolCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val provider: String,
    val model: String,
    val toolName: String,
    /** [OUTCOME_OK] = zapsáno, [OUTCOME_SKIPPED] = model to už zapsal, [OUTCOME_ERROR] = selhalo. */
    val outcome: String,
    val errorKind: String? = null,
    val durationMs: Long,
    /** true = model tool simuloval textem a myli jsme ho z videje (ToolTextFallback). */
    val viaTextFallback: Boolean = false,
    /** Kolo tahu, ve kterém volání přišlo (0-based). */
    val round: Int = 0,

    // log_food – odhadnuté hodnoty (to, co model poslal jako argumenty)
    val foodName: String? = null,
    val quantityG: Double? = null,
    val modelCalories: Int? = null,
    val modelProteinG: Double? = null,
    val modelCarbsG: Double? = null,
    val modelFatG: Double? = null,

    // log_food – co nakonec uložil deník (OFF, nebo odhad modelu když OFF nic nenašlo)
    val finalCalories: Int? = null,
    val finalProteinG: Double? = null,
    val finalCarbsG: Double? = null,
    val finalFatG: Double? = null,
    val finalSource: String? = null,

    // ostatní nástroje: uložená hodnota (kg, ml, …)
    val savedValue: Double? = null
) {
    companion object {
        const val SOURCE_OFF = "openfoodfacts"
        const val SOURCE_MODEL = "model"

        const val OUTCOME_OK = "ok"
        /** Už zapsané jídlo / podezření na duplicitu – model neudělal chybu, data se jen neuložila. */
        const val OUTCOME_SKIPPED = "skipped"
        const val OUTCOME_ERROR = "error"

        const val ERROR_UNKNOWN_TOOL = "unknown_tool"
        const val ERROR_MISSING_PARAM = "missing_param"
        const val ERROR_THROWN = "thrown"
    }
}