package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.WorkoutEntryDao
import cz.rzahr.aicoach.data.db.entity.WorkoutEntryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutEntryDao
) {

    fun observeAllDesc(): Flow<List<WorkoutEntryEntity>> = dao.observeAllDesc()

    suspend fun since(timestamp: Long): List<WorkoutEntryEntity> = dao.since(timestamp)

    suspend fun add(
        name: String,
        durationMinutes: Int? = null,
        caloriesBurned: Int? = null,
        note: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long = dao.insert(
        WorkoutEntryEntity(
            name = name,
            durationMinutes = durationMinutes,
            caloriesBurned = caloriesBurned,
            note = note?.takeIf { it.isNotBlank() },
            timestamp = timestamp
        )
    )

    suspend fun delete(id: Long) = dao.deleteById(id)
}
