package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.PoseFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoseFolderDao {

    @Query("SELECT * FROM pose_folders ORDER BY name ASC")
    fun observeAll(): Flow<List<PoseFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: PoseFolderEntity): Long

    @Query("DELETE FROM pose_folders WHERE name = :name")
    suspend fun deleteByName(name: String)
}
