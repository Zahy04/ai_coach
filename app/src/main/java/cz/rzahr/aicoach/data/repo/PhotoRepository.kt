package cz.rzahr.aicoach.data.repo

import android.content.Context
import android.net.Uri
import cz.rzahr.aicoach.data.db.dao.ProgressPhotoDao
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
    private val dao: ProgressPhotoDao
) {

    fun observeAllDesc(): Flow<List<ProgressPhotoEntity>> = dao.observeAllDesc()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeById(id: Long): Flow<ProgressPhotoEntity?> = dao.observeById(id)

    suspend fun addCapturedPhoto(file: File, note: String? = null): Long = withContext(Dispatchers.IO) {
        val target = targetFile()
        file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        file.delete()
        dao.insert(ProgressPhotoEntity(filePath = target.absolutePath, timestamp = System.currentTimeMillis(), note = note))
    }

    suspend fun addPhotoFromUri(uri: Uri, note: String? = null): Long = withContext(Dispatchers.IO) {
        val target = targetFile()
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Nelze otevřít vybraný obrázek.")
        input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        dao.insert(ProgressPhotoEntity(filePath = target.absolutePath, timestamp = System.currentTimeMillis(), note = note))
    }

    suspend fun delete(photo: ProgressPhotoEntity) = withContext(Dispatchers.IO) {
        File(photo.filePath).delete()
        dao.deleteById(photo.id)
    }

    suspend fun createCaptureFile(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    private fun targetFile(): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "photo_${System.currentTimeMillis()}.jpg")
    }
}
