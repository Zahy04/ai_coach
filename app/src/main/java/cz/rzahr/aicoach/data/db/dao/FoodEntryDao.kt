package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    @Query("SELECT * FROM food_entries ORDER BY timestamp DESC, id DESC")
    fun observeAllDesc(): Flow<List<FoodEntryEntity>>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM food_entries WHERE timestamp >= :since")
    fun observeCaloriesSince(since: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(proteinG), 0) FROM food_entries WHERE timestamp >= :since")
    fun observeProteinSince(since: Long): Flow<Double>

    @Query("SELECT * FROM food_entries WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<FoodEntryEntity>

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
