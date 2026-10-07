package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.LlmToolCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LlmToolCallDao {

    @Insert
    suspend fun insert(call: LlmToolCallEntity): Long

    @Query("SELECT * FROM llm_tool_calls ORDER BY timestamp ASC, id ASC")
    suspend fun allAsc(): List<LlmToolCallEntity>

    @Query("SELECT COUNT(*) FROM llm_tool_calls")
    fun observeCount(): Flow<Int>
}