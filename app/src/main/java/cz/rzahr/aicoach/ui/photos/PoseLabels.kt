package cz.rzahr.aicoach.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity

@Composable
fun poseLabel(pose: String): String = when (pose) {
    ProgressPhotoEntity.POSE_FRONT_DOUBLE_BICEPS -> stringResource(R.string.pose_front_double_biceps)
    ProgressPhotoEntity.POSE_BACK_DOUBLE_BICEPS -> stringResource(R.string.pose_back_double_biceps)
    ProgressPhotoEntity.POSE_SIDE_TRICEPS -> stringResource(R.string.pose_side_triceps)
    ProgressPhotoEntity.POSE_MOST_MUSCULAR -> stringResource(R.string.pose_most_muscular)
    ProgressPhotoEntity.POSE_OVERHEAD_TRICEPS -> stringResource(R.string.pose_overhead_triceps)
    ProgressPhotoEntity.POSE_ZYZZ -> stringResource(R.string.pose_zyzz)
    ProgressPhotoEntity.POSE_OTHER -> stringResource(R.string.pose_other)
    else -> pose
}
