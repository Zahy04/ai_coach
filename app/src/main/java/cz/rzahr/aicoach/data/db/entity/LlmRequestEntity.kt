package cz.rzahr.aicoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Jeden HTTP request na LLM API – včetně neúspěšných pokusů a retry.
 * Jeden řádek = jedno měření latence, takže 429/503 retry se nepromítají
 * do průměrné odezvy modelu jako by byly úspěch (issue #6).
 */
@Entity(tableName = "llm_requests")
data class LlmRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val provider: String,
    val model: String,
    /** Pořadí pokusu v rámci tahu (0 = první, 1+ = retry po 429/503). */
    val attempt: Int,
    /** Kolo tahu, ve kterém request vznikl (0 = první odpověď, 1+ = po nástroji). */
    val round: Int = 0,
    val durationMs: Long,
    /** Doba do prvního delta chunku (null = žádný token nepřišel). */
    val firstTokenMs: Long? = null,
    val httpStatus: Int? = null,
    val success: Boolean,
    /** Důvod neúspěchu – konstanty v companionu. */
    val errorKind: String? = null,
    /** true pro 429 = kvóta/rate limit, false pro 503 = přetížení. */
    val overloaded: Boolean? = null,
    val retryAfterMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val finishReason: String? = null,
    /** Počet function callů přijatých v tomto requestu. */
    val toolCallCount: Int? = null,
    val replyChars: Int? = null,
    /** Kolik znaků model zapsal jako textovou simulaci toolu (rescue). */
    val textFallbackChars: Int? = null,
    val errorMessage: String? = null
) {
    companion object {
        const val ERROR_OVERLOADED = "overloaded"
        const val ERROR_RATE_LIMIT = "rate_limit"
        const val ERROR_NETWORK = "network"
        const val ERROR_HTTP = "http"
        const val ERROR_EMPTY = "empty"

        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENROUTER = "openrouter"
    }
}
