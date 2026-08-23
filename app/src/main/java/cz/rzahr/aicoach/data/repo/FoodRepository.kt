package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.FoodEntryDao
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodEntryDao
) {

    fun observeAllDesc(): Flow<List<FoodEntryEntity>> = dao.observeAllDesc()

    fun observeTodayCalories(): Flow<Int> = dao.observeCaloriesSince(startOfToday())

    fun observeTodayProtein(): Flow<Double> = dao.observeProteinSince(startOfToday())

    suspend fun since(timestamp: Long): List<FoodEntryEntity> = dao.since(timestamp)

    suspend fun add(
        name: String,
        calories: Int? = null,
        proteinG: Double? = null,
        carbsG: Double? = null,
        fatG: Double? = null,
        timestamp: Long = System.currentTimeMillis(),
        source: String = FoodEntryEntity.SOURCE_CHAT
    ): Long = dao.insert(
        FoodEntryEntity(
            name = name,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            timestamp = timestamp,
            source = source
        )
    )

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
