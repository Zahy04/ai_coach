package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.WorkoutEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutEntryDao {

    @Query("SELECT * FROM workout_entries ORDER BY timestamp DESC, id DESC")
    fun observeAllDesc(): Flow<List<WorkoutEntryEntity>>

    @Query("SELECT * FROM workout_entries WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<WorkoutEntryEntity>

    @Insert
    suspend fun insert(entry: WorkoutEntryEntity): Long

    @Query("DELETE FROM workout_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
