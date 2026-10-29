package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.FactDao
import cz.rzahr.aicoach.data.db.entity.FactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactRepositoryTest {

    private class FakeFactDao : FactDao {
        val facts = linkedMapOf<Long, FactEntity>()
        private var nextId = 1L

        override fun observeAll(): Flow<List<FactEntity>> = flowOf(facts.values.toList())
        override suspend fun all() = facts.values.toList()
        override suspend fun byId(id: Long) = facts[id]
        override suspend fun findByCategoryAndContent(category: String, normalizedContent: String) =
            facts.values.firstOrNull { it.category == category && it.content.trim().lowercase() == normalizedContent }
        override suspend fun insert(fact: FactEntity): Long {
            val id = if (fact.id != 0L) fact.id else nextId++
            facts[id] = fact.copy(id = id)
            return id
        }
        override suspend fun update(id: Long, category: String, content: String, updatedAt: Long) {
            facts[id]?.let { facts[id] = it.copy(category = category, content = content, updatedAt = updatedAt) }
        }
        override suspend fun deleteById(id: Long): Int = if (facts.remove(id) != null) 1 else 0
    }

    @Test
    fun `neexistujici fact_id fakt nezahodi`() = runTest {
        val dao = FakeFactDao()
        val repo = FactRepository(dao)
        val result = repo.upsert("GOAL", "Zhubnout na 80 kg", factId = 42)
        assertTrue(result.created)
        assertEquals(listOf("Zhubnout na 80 kg"), dao.facts.values.map { it.content })
    }

    @Test
    fun `existujici fact_id se prepise`() = runTest {
        val dao = FakeFactDao()
        val repo = FactRepository(dao)
        val id = repo.upsert("GOAL", "Zhubnout na 80 kg").id
        val result = repo.upsert("GOAL", "Zhubnout na 78 kg", factId = id)
        assertFalse(result.created)
        assertEquals(listOf("Zhubnout na 78 kg"), dao.facts.values.map { it.content })
    }

    @Test
    fun `smazani hlasi zda fakt existoval a jde vratit`() = runTest {
        val dao = FakeFactDao()
        val repo = FactRepository(dao)
        val id = repo.upsert("DIET", "Nejí lepek").id
        val fact = dao.facts.getValue(id)
        assertTrue(repo.delete(id))
        assertFalse(repo.delete(id))
        repo.restore(fact)
        assertEquals(fact, dao.facts[id])
    }
}
