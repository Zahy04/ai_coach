package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.WaterEntryDao
import cz.rzahr.aicoach.data.db.entity.WaterEntryEntity
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class WaterRepository @Inject constructor(
    private val dao: WaterEntryDao
) {

    fun observeTodayMl(): Flow<Int> = dao.observeSumSince(startOfToday())

    fun observeAllDesc(): Flow<List<WaterEntryEntity>> = dao.observeAllDesc()

    suspend fun add(amountMl: Int, timestamp: Long = System.currentTimeMillis()): Long =
        dao.insert(WaterEntryEntity(amountMl = amountMl, timestamp = timestamp))

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
