package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC, id DESC")
    fun observeAllDesc(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun latestDesc(limit: Int): List<WeightEntryEntity>

    @Insert
    suspend fun insert(entry: WeightEntryEntity): Long

    @Query(
        "UPDATE weight_entries SET weightKg = :weightKg, note = :note WHERE id = :id"
    )
    suspend fun update(id: Long, weightKg: Double, note: String?)

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
