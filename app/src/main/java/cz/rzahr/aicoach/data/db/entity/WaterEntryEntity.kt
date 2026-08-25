package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_entries")
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMl: Int,
    val timestamp: Long
)
