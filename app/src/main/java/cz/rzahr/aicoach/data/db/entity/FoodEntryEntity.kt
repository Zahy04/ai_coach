package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val timestamp: Long,
    val source: String = SOURCE_CHAT
) {
    companion object {
        const val SOURCE_CHAT = "chat"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_API = "api"
        const val SOURCE_TEMPLATE = "template"
    }
}
