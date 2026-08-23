package cz.rzahr.aicoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.rzahr.aicoach.data.db.dao.ChatMessageDao
import cz.rzahr.aicoach.data.db.dao.FactDao
import cz.rzahr.aicoach.data.db.dao.FoodEntryDao
import cz.rzahr.aicoach.data.db.dao.ProgressPhotoDao
import cz.rzahr.aicoach.data.db.dao.WeightEntryDao
import cz.rzahr.aicoach.data.db.dao.WorkoutEntryDao
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.data.db.entity.FactEntity
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.data.db.entity.WorkoutEntryEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        WeightEntryEntity::class,
        FoodEntryEntity::class,
        WorkoutEntryEntity::class,
        FactEntity::class,
        ProgressPhotoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun workoutEntryDao(): WorkoutEntryDao
    abstract fun factDao(): FactDao
    abstract fun progressPhotoDao(): ProgressPhotoDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN imagePath TEXT DEFAULT NULL")
            }
        }
    }
}
