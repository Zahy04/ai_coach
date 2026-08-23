package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE role = 'model' ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun lastModel(): ChatMessageEntity?

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
