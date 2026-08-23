package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val CATEGORY_PREFERENCE = "PREFERENCE"
        const val CATEGORY_DISLIKE = "DISLIKE"
        const val CATEGORY_DIET = "DIET"
        const val CATEGORY_GOAL = "GOAL"
        const val CATEGORY_HABIT = "HABIT"
        const val CATEGORY_HEALTH = "HEALTH"
        const val CATEGORY_OTHER = "OTHER"

        val CATEGORIES = setOf(
            CATEGORY_PREFERENCE,
            CATEGORY_DISLIKE,
            CATEGORY_DIET,
            CATEGORY_GOAL,
            CATEGORY_HABIT,
            CATEGORY_HEALTH,
            CATEGORY_OTHER
        )

        fun categoryLabel(category: String): String = when (category) {
            CATEGORY_PREFERENCE -> "Oblíbené"
            CATEGORY_DISLIKE -> "Neoblíbené"
            CATEGORY_DIET -> "Jídelníček"
            CATEGORY_GOAL -> "Cíl"
            CATEGORY_HABIT -> "Zvyk"
            CATEGORY_HEALTH -> "Zdraví"
            else -> "Jiné"
        }
    }
}
