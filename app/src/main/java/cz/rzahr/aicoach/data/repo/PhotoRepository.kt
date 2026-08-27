package cz.rzahr.aicoach.data.repo

import android.content.Context
import android.net.Uri
import cz.rzahr.aicoach.data.db.dao.PoseFolderDao
import cz.rzahr.aicoach.data.db.dao.ProgressPhotoDao
import cz.rzahr.aicoach.data.db.entity.PoseFolderEntity
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProgressPhotoDao,
    private val poseFolderDao: PoseFolderDao
) {

    fun observeAllDesc(): Flow<List<ProgressPhotoEntity>> = dao.observeAllDesc()

    fun observeByPose(pose: String): Flow<List<ProgressPhotoEntity>> = dao.observeByPose(pose)

    fun observeDistinctPoses(): Flow<List<String>> = dao.observeDistinctPoses()

    fun observeCustomFolders(): Flow<List<PoseFolderEntity>> = poseFolderDao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeById(id: Long): Flow<ProgressPhotoEntity?> = dao.observeById(id)

    suspend fun addCapturedPhoto(file: File, note: String? = null, pose: String = ProgressPhotoEntity.POSE_OTHER): Long = withContext(Dispatchers.IO) {
        val target = targetFile()
        file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        file.delete()
        dao.insert(ProgressPhotoEntity(filePath = target.absolutePath, timestamp = System.currentTimeMillis(), note = note, pose = pose))
    }

    suspend fun addPhotoFromUri(uri: Uri, note: String? = null): Long = withContext(Dispatchers.IO) {
        val target = targetFile()
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Nelze otevřít vybraný obrázek.")
        input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        dao.insert(ProgressPhotoEntity(filePath = target.absolutePath, timestamp = System.currentTimeMillis(), note = note))
    }

    suspend fun addPhotosFromUris(uris: List<Uri>, pose: String = ProgressPhotoEntity.POSE_OTHER): Int = withContext(Dispatchers.IO) {
        var count = 0
        uris.forEach { uri ->
            try {
                val target = targetFile()
                val input = context.contentResolver.openInputStream(uri) ?: return@forEach
                input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
                dao.insert(ProgressPhotoEntity(filePath = target.absolutePath, timestamp = System.currentTimeMillis(), pose = pose))
                count++
            } catch (_: Exception) {
                // přeskočíme nečitelný obrázek
            }
        }
        count
    }

    suspend fun moveToPose(id: Long, pose: String) = dao.updatePose(id, pose)

    suspend fun delete(photo: ProgressPhotoEntity) = withContext(Dispatchers.IO) {
        File(photo.filePath).delete()
        dao.deleteById(photo.id)
    }

    suspend fun createFolder(name: String): Boolean {
        val normalized = name.trim().lowercase().replace(Regex("\\s+"), "_")
        if (normalized.isBlank()) return false
        return poseFolderDao.insert(PoseFolderEntity(normalized)) != -1L
    }

    suspend fun deleteFolder(name: String) = poseFolderDao.deleteByName(name)

    suspend fun createCaptureFile(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    private fun targetFile(): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "photo_${System.currentTimeMillis()}.jpg")
    }
}
