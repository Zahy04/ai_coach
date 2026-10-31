package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.FactDao
import cz.rzahr.aicoach.data.db.entity.FactEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class UpsertResult(
    val id: Long,
    val created: Boolean
)

@Singleton
class FactRepository @Inject constructor(
    private val dao: FactDao
) {

    fun observeAll(): Flow<List<FactEntity>> = dao.observeAll()

    suspend fun all(): List<FactEntity> = dao.all()

    suspend fun upsert(category: String, content: String, factId: Long? = null): UpsertResult {
        val normalizedCategory = normalizeCategory(category)
        val trimmedContent = content.trim()
        val now = System.currentTimeMillis()

        // Neexistující id (model si ho vymyslel) nesmí fakt tiše zahodit – pak se vloží nový.
        if (factId != null && dao.byId(factId) != null) {
            dao.update(factId, normalizedCategory, trimmedContent, now)
            return UpsertResult(id = factId, created = false)
        }

        val existing = dao.findByCategoryAndContent(normalizedCategory, trimmedContent.lowercase())
        if (existing != null) {
            dao.update(existing.id, normalizedCategory, trimmedContent, now)
            return UpsertResult(id = existing.id, created = false)
        }

        val newId = dao.insert(
            FactEntity(
                category = normalizedCategory,
                content = trimmedContent,
                createdAt = now,
                updatedAt = now
            )
        )
        return UpsertResult(id = newId, created = true)
    }

    suspend fun updateContent(id: Long, content: String) {
        val fact = dao.byId(id) ?: return
        dao.update(id, fact.category, content.trim(), System.currentTimeMillis())
    }

    /** @return true, když fakt existoval a byl smazán. */
    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    /** Vrátí smazaný fakt (Zpět ve snackbaru) i s původním id. */
    suspend fun restore(fact: FactEntity) {
        dao.insert(fact)
    }

    private fun normalizeCategory(category: String): String {
        val upper = category.uppercase().trim()
        return if (upper in FactEntity.CATEGORIES) upper else FactEntity.CATEGORY_OTHER
    }
}
