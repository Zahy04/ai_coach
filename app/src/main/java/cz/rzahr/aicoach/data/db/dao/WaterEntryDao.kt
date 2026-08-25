package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE timestamp >= :since")
    fun observeSumSince(since: Long): Flow<Int>

    @Query("SELECT * FROM water_entries ORDER BY timestamp DESC, id DESC")
    fun observeAllDesc(): Flow<List<WaterEntryEntity>>

    @Insert
    suspend fun insert(entry: WaterEntryEntity): Long

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
