package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ručně zadaný produkt pod čárovým kódem – když ho Open Food Facts nezná (skener, issue #1). */
@Entity(tableName = "scanned_products")
data class ScannedProductEntity(
    @PrimaryKey val code: String,
    val name: String,
    val caloriesPer100g: Int?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val updatedAt: Long = System.currentTimeMillis()
)
