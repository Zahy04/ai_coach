package cz.rzahr.aicoach.data.repo

import android.content.Context
import android.net.Uri
import android.util.Base64
import cz.rzahr.aicoach.data.db.dao.ChatMessageDao
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.llm.Content
import cz.rzahr.aicoach.llm.InlineData
import cz.rzahr.aicoach.llm.Part
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ChatMessageDao
) {

    fun observeMessages(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun addUserMessage(text: String, imagePath: String? = null): Long = dao.insert(
        ChatMessageEntity(
            role = ChatMessageEntity.ROLE_USER,
            content = text,
            timestamp = System.currentTimeMillis(),
            imagePath = imagePath
        )
    )

    suspend fun addModelMessage(text: String): Long = dao.insert(
        ChatMessageEntity(
            role = ChatMessageEntity.ROLE_MODEL,
            content = text,
            timestamp = System.currentTimeMillis()
        )
    )

    suspend fun historyContents(limit: Int): List<Content> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        return dao.latest(limit).reversed().map { message ->
            val parts = mutableListOf<Part>()
            if (message.role == ChatMessageEntity.ROLE_USER) {
                message.imagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        parts += Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64))
                    }
                }
                parts += Part(
                    text = "[${formatter.format(Instant.ofEpochMilli(message.timestamp))}] ${message.content}"
                )
            } else {
                parts += Part(text = message.content)
            }
            Content(role = message.role, parts = parts)
        }
    }

    suspend fun importChatImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
        val target = File(dir, "img_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Nelze otevřít vybraný obrázek.")
        input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        target.absolutePath
    }

    suspend fun importChatImageFile(source: File): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
        val target = File(dir, "img_${System.currentTimeMillis()}.jpg")
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        source.delete()
        target.absolutePath
    }

    fun createCaptureFile(): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "chat_capture_${System.currentTimeMillis()}.jpg")
    }

    suspend fun deleteImageFile(path: String?) = withContext(Dispatchers.IO) {
        path?.let { File(it).delete() }
    }

    suspend fun lastModelMessage(): ChatMessageEntity? = dao.lastModel()

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.deleteAll()
}
