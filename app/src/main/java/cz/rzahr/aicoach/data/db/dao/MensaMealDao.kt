package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MensaMealDao {

    @Query(
        "SELECT * FROM mensa_meals WHERE systemId = :systemId AND epochDay = :epochDay " +
            "ORDER BY id ASC"
    )
    fun observeMeals(systemId: Int, epochDay: Long): Flow<List<MensaMealEntity>>

    @Query("SELECT COUNT(*) FROM mensa_meals WHERE systemId = :systemId AND weekKey = :weekKey")
    suspend fun countBySystemWeek(systemId: Int, weekKey: String): Int

    @Query("SELECT * FROM mensa_meals WHERE systemId = :systemId AND weekKey = :weekKey ORDER BY epochDay ASC, id ASC")
    suspend fun getBySystemWeek(systemId: Int, weekKey: String): List<MensaMealEntity>

    @Query("SELECT * FROM mensa_meals WHERE systemId = :systemId AND epochDay = :epochDay ORDER BY id ASC")
    suspend fun getBySystemDay(systemId: Int, epochDay: Long): List<MensaMealEntity>

    @Query("DELETE FROM mensa_meals WHERE systemId = :systemId AND epochDay = :epochDay")
    suspend fun deleteBySystemDay(systemId: Int, epochDay: Long)

    @Query(
        "SELECT * FROM mensa_meals WHERE systemId = :systemId " +
            "AND kcalMin IS NULL AND rating IS NULL ORDER BY epochDay ASC, id ASC"
    )
    suspend fun getWithoutEstimate(systemId: Int): List<MensaMealEntity>

    /** Bez odhadu a bez fotky → čistě textový odhad. Jídla s fotkou řeší vision. */
    @Query(
        "SELECT * FROM mensa_meals WHERE systemId = :systemId " +
            "AND kcalMin IS NULL AND rating IS NULL AND photoUrl IS NULL " +
            "ORDER BY epochDay ASC, id ASC"
    )
    suspend fun getWithoutEstimateWithoutPhoto(systemId: Int): List<MensaMealEntity>

    /** Fotka je, ale vision odhad ještě neproběhl (včetně již textově odhadnutých). */
    @Query(
        "SELECT * FROM mensa_meals WHERE systemId = :systemId " +
            "AND photoUrl IS NOT NULL AND visionEstimated = 0 ORDER BY epochDay ASC, id ASC"
    )
    suspend fun getPhotoNeedingVision(systemId: Int): List<MensaMealEntity>

    @Query("DELETE FROM mensa_meals WHERE systemId = :systemId AND weekKey = :weekKey")
    suspend fun deleteBySystemWeek(systemId: Int, weekKey: String)

    @Insert
    suspend fun insertAll(meals: List<MensaMealEntity>): List<Long>

    @Query(
        "UPDATE mensa_meals SET kcalMin = :kcalMin, kcalMax = :kcalMax, proteinG = :proteinG, " +
            "carbsG = :carbsG, fatG = :fatG, rating = :rating, " +
            "visionEstimated = :visionEstimated WHERE id = :id"
    )
    suspend fun updateEstimate(
        id: Long,
        kcalMin: Int?,
        kcalMax: Int?,
        proteinG: Double?,
        carbsG: Double?,
        fatG: Double?,
        rating: String?,
        visionEstimated: Boolean = false
    )

    @Query(
        "UPDATE mensa_meals SET jidloId = :jidloId, photoUrl = :photoUrl, " +
            "allergens = :allergens WHERE id = :id"
    )
    suspend fun updatePhoto(
        id: Long,
        jidloId: Int?,
        photoUrl: String?,
        allergens: String?
    )
}
