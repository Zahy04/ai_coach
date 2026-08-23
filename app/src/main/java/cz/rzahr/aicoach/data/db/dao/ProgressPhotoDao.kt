package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {

    @Query("SELECT * FROM progress_photos ORDER BY timestamp DESC, id DESC")
    fun observeAllDesc(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT COUNT(*) FROM progress_photos")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM progress_photos WHERE id = :id")
    fun observeById(id: Long): Flow<ProgressPhotoEntity?>

    @Insert
    suspend fun insert(photo: ProgressPhotoEntity): Long

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
