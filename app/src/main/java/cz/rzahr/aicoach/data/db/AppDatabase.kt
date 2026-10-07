package cz.rzahr.aicoach.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.rzahr.aicoach.data.db.dao.ChatMessageDao
import cz.rzahr.aicoach.data.db.dao.FactDao
import cz.rzahr.aicoach.data.db.dao.FoodEntryDao
import cz.rzahr.aicoach.data.db.dao.LlmRequestDao
import cz.rzahr.aicoach.data.db.dao.LlmToolCallDao
import cz.rzahr.aicoach.data.db.dao.MensaMealDao
import cz.rzahr.aicoach.data.db.dao.PoseFolderDao
import cz.rzahr.aicoach.data.db.dao.ProgressPhotoDao
import cz.rzahr.aicoach.data.db.dao.WaterEntryDao
import cz.rzahr.aicoach.data.db.dao.WeightEntryDao
import cz.rzahr.aicoach.data.db.dao.WorkoutEntryDao
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.data.db.entity.FactEntity
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.data.db.entity.LlmRequestEntity
import cz.rzahr.aicoach.data.db.entity.LlmToolCallEntity
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import cz.rzahr.aicoach.data.db.entity.PoseFolderEntity
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import cz.rzahr.aicoach.data.db.entity.WaterEntryEntity
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.data.db.entity.WorkoutEntryEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        WeightEntryEntity::class,
        FoodEntryEntity::class,
        WorkoutEntryEntity::class,
        FactEntity::class,
        ProgressPhotoEntity::class,
        WaterEntryEntity::class,
        MensaMealEntity::class,
        PoseFolderEntity::class,
        LlmRequestEntity::class,
        LlmToolCallEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun workoutEntryDao(): WorkoutEntryDao
    abstract fun factDao(): FactDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
    abstract fun waterEntryDao(): WaterEntryDao
    abstract fun mensaMealDao(): MensaMealDao
    abstract fun poseFolderDao(): PoseFolderDao
    abstract fun llmRequestDao(): LlmRequestDao
    abstract fun llmToolCallDao(): LlmToolCallDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN imagePath TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS water_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "amountMl INTEGER NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS meal_templates")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS mensa_meals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "systemId INTEGER NOT NULL, " +
                        "weekKey TEXT NOT NULL, " +
                        "epochDay INTEGER NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "grams INTEGER, " +
                        "kcalMin INTEGER, " +
                        "kcalMax INTEGER, " +
                        "proteinG REAL, " +
                        "carbsG REAL, " +
                        "fatG REAL, " +
                        "rating TEXT)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN grams INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress_photos ADD COLUMN pose TEXT NOT NULL DEFAULT 'other'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pose_folders (name TEXT PRIMARY KEY NOT NULL)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mensa_meals ADD COLUMN jidloId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE mensa_meals ADD COLUMN photoUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE mensa_meals ADD COLUMN allergens TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE mensa_meals ADD COLUMN visionEstimated INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS llm_requests (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "provider TEXT NOT NULL, " +
                        "model TEXT NOT NULL, " +
                        "attempt INTEGER NOT NULL, " +
                        "round INTEGER NOT NULL DEFAULT 0, " +
                        "durationMs INTEGER NOT NULL, " +
                        "firstTokenMs INTEGER, " +
                        "httpStatus INTEGER, " +
                        "success INTEGER NOT NULL, " +
                        "errorKind TEXT, " +
                        "overloaded INTEGER, " +
                        "retryAfterMs INTEGER, " +
                        "promptTokens INTEGER, " +
                        "completionTokens INTEGER, " +
                        "totalTokens INTEGER, " +
                        "finishReason TEXT, " +
                        "toolCallCount INTEGER, " +
                        "replyChars INTEGER, " +
                        "textFallbackChars INTEGER, " +
                        "errorMessage TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS llm_tool_calls (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "provider TEXT NOT NULL, " +
                        "model TEXT NOT NULL, " +
                        "toolName TEXT NOT NULL, " +
                        "outcome TEXT NOT NULL, " +
                        "errorKind TEXT, " +
                        "durationMs INTEGER NOT NULL, " +
                        "viaTextFallback INTEGER NOT NULL DEFAULT 0, " +
                        "round INTEGER NOT NULL DEFAULT 0, " +
                        "foodName TEXT, " +
                        "quantityG REAL, " +
                        "modelCalories INTEGER, " +
                        "modelProteinG REAL, " +
                        "modelCarbsG REAL, " +
                        "modelFatG REAL, " +
                        "finalCalories INTEGER, " +
                        "finalProteinG REAL, " +
                        "finalCarbsG REAL, " +
                        "finalFatG REAL, " +
                        "finalSource TEXT, " +
                        "savedValue REAL)"
                )
            }
        }
    }
}
