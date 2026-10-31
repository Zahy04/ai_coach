package cz.rzahr.aicoach.data.repo

import android.content.Context
import android.net.Uri
import android.util.Base64
import cz.rzahr.aicoach.data.db.dao.ChatMessageDao
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.llm.Content
import cz.rzahr.aicoach.llm.InlineData
import cz.rzahr.aicoach.llm.Part
import cz.rzahr.aicoach.util.ImageDownscale
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

    /**
     * Historie pro LLM. Fotky se posílají jen u posledních [MAX_HISTORY_IMAGES] zpráv
     * (starší zprávy jdou jen s textem) a vždy zmenšené – jinak každá fotka zvětšila
     * každý další request o jednotky MB až k limitu API. Čtení souborů a base64
     * běží mimo hlavní vlákno.
     */
    suspend fun historyContents(limit: Int): List<Content> = withContext(Dispatchers.IO) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        val messages = dao.latest(limit).reversed()
        val withImages = messages.filter { it.imagePath != null }.takeLast(MAX_HISTORY_IMAGES).map { it.id }.toSet()
        messages.map { message ->
            val parts = mutableListOf<Part>()
            if (message.role == ChatMessageEntity.ROLE_USER) {
                message.imagePath?.let { path ->
                    val jpeg = if (message.id in withImages) encodedImage(File(path)) else null
                    if (jpeg != null) {
                        parts += Part(inlineData = InlineData(mimeType = "image/jpeg", data = jpeg))
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
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Nelze otevřít vybraný obrázek.")
        storeChatImage(bytes)
    }

    suspend fun importChatImageFile(source: File): String = withContext(Dispatchers.IO) {
        try {
            storeChatImage(source.readBytes())
        } finally {
            source.delete()
        }
    }

    /** Uloží fotku už zmenšenou jako JPEG – v chatu ani v API není důvod držet plné rozlišení. */
    private fun storeChatImage(bytes: ByteArray): String {
        val jpeg = ImageDownscale.toJpeg(bytes, maxDim = IMAGE_MAX_DIM, quality = IMAGE_QUALITY)
            ?: throw IllegalStateException("Obrázek nejde načíst.")
        val target = File(chatImagesDir(), "img_${System.currentTimeMillis()}_${System.nanoTime() % 1000}.jpg")
        target.writeBytes(jpeg)
        return target.absolutePath
    }

    /** Starší fotky z doby před zmenšováním se zmenší až při odesílání. */
    private fun encodedImage(file: File): String? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val isSmallJpeg = bytes.size <= IMAGE_PASSTHROUGH_BYTES &&
            bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val jpeg = if (isSmallJpeg) bytes
        else ImageDownscale.toJpeg(bytes, maxDim = IMAGE_MAX_DIM, quality = IMAGE_QUALITY) ?: return null
        return Base64.encodeToString(jpeg, Base64.NO_WRAP)
    }

    private fun chatImagesDir(): File = File(context.filesDir, "chat_images").apply { mkdirs() }

    fun createCaptureFile(): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "chat_capture_${System.currentTimeMillis()}.jpg")
    }

    suspend fun deleteImageFile(path: String?) = withContext(Dispatchers.IO) {
        path?.let { File(it).delete() }
    }

    suspend fun lastModelMessage(): ChatMessageEntity? = dao.lastModel()

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** Smaže historii i fotky na disku – „smazat chat" nesmí nechat soukromé fotky ležet. */
    suspend fun clear() {
        dao.deleteAll()
        withContext(Dispatchers.IO) {
            chatImagesDir().listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        const val MAX_HISTORY_IMAGES = 3
        private const val IMAGE_MAX_DIM = 1280
        private const val IMAGE_QUALITY = 85
        private const val IMAGE_PASSTHROUGH_BYTES = 600_000
    }
}
