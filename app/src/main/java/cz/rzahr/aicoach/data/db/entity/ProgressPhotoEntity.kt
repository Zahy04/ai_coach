package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress_photos")
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val timestamp: Long,
    val note: String? = null,
    val pose: String = POSE_OTHER
) {
    companion object {
        const val POSE_FRONT_DOUBLE_BICEPS = "front_double_biceps"
        const val POSE_BACK_DOUBLE_BICEPS = "back_double_biceps"
        const val POSE_SIDE_TRICEPS = "side_triceps"
        const val POSE_MOST_MUSCULAR = "most_muscular"
        const val POSE_OVERHEAD_TRICEPS = "overhead_triceps"
        const val POSE_ZYZZ = "zyzz_pose"
        const val POSE_OTHER = "other"

        val DEFAULT_POSES = listOf(
            POSE_FRONT_DOUBLE_BICEPS,
            POSE_BACK_DOUBLE_BICEPS,
            POSE_SIDE_TRICEPS,
            POSE_MOST_MUSCULAR,
            POSE_OVERHEAD_TRICEPS,
            POSE_ZYZZ,
            POSE_OTHER
        )
    }
}

@Entity(tableName = "pose_folders")
data class PoseFolderEntity(
    @PrimaryKey val name: String
)

