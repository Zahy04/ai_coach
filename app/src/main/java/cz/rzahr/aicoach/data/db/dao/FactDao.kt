package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.FactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {

    @Query("SELECT * FROM facts ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts ORDER BY id ASC")
    suspend fun all(): List<FactEntity>

    @Query(
        "SELECT * FROM facts WHERE category = :category " +
            "AND LOWER(TRIM(content)) = :normalizedContent LIMIT 1"
    )
    suspend fun findByCategoryAndContent(category: String, normalizedContent: String): FactEntity?

    @Insert
    suspend fun insert(fact: FactEntity): Long

    @Query(
        "UPDATE facts SET category = :category, content = :content, updatedAt = :updatedAt " +
            "WHERE id = :id"
    )
    suspend fun update(id: Long, category: String, content: String, updatedAt: Long)

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
