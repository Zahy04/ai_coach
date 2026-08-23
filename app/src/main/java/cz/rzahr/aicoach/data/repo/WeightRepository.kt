package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.WeightEntryDao
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightEntryDao
) {

    fun observeAllDesc(): Flow<List<WeightEntryEntity>> = dao.observeAllDesc()

    suspend fun latestAsc(limit: Int): List<WeightEntryEntity> =
        dao.latestDesc(limit).reversed()

    suspend fun add(weightKg: Double, note: String? = null, timestamp: Long = System.currentTimeMillis()): Long =
        dao.insert(WeightEntryEntity(weightKg = weightKg, timestamp = timestamp, note = note?.takeIf { it.isNotBlank() }))

    suspend fun delete(id: Long) = dao.deleteById(id)
}
