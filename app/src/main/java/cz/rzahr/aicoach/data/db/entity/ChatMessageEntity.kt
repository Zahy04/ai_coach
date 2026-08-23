package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long,
    val imagePath: String? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}
