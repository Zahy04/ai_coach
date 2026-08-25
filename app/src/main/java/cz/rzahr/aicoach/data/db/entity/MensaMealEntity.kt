package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mensa_meals")
data class MensaMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemId: Int,
    val weekKey: String,
    val epochDay: Long,
    val category: String,
    val name: String,
    val grams: Int? = null,
    val kcalMin: Int? = null,
    val kcalMax: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val rating: String? = null
) {
    companion object {
        const val SYSTEM_STUDENTSKEJ_DUM = 2
        const val SYSTEM_TECHNICKA = 3
        const val RATING_GREEN = "GREEN"
        const val RATING_YELLOW = "YELLOW"
        const val RATING_RED = "RED"
    }
}
