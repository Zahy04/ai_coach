package cz.rzahr.aicoach.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.rzahr.aicoach.data.db.entity.ScannedProductEntity

@Dao
interface ScannedProductDao {

    @Query("SELECT * FROM scanned_products WHERE code = :code")
    suspend fun get(code: String): ScannedProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ScannedProductEntity)
}
