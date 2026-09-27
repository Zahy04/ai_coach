package cz.rzahr.aicoach.llm

import kotlin.random.Random

/**
 * Pauza před dalším pokusem při 429/503 (issue #2).
 * Přednost má serverem navržená pauza – "retryDelay" v JSON těle (Gemini),
 * jinak Retry-After hlavička (sekundy), jinak exponenciální backoff + jitter.
 * Čisté funkce – testovatelné bez sítě.
 */
internal object RetryDelays {

    private val RETRY_DELAY_REGEX = Regex("\"retryDelay\":\\s*\"(\\d+)s\"")

    fun computeRetryDelayMs(
        attempt: Int,
        errorText: String,
        retryAfterHeader: String?,
        baseDelayMs: Long = 2000L,
        jitterMs: Long = Random.nextLong(0L, 1000L)
    ): Long {
        val serverWaitMs = RETRY_DELAY_REGEX.find(errorText)
            ?.groupValues?.getOrNull(1)?.toLongOrNull()?.times(1000L)
            ?: retryAfterHeader?.let { parseRetryAfterMs(it) }
        val backoffMs = baseDelayMs * (1L shl attempt)
        return (serverWaitMs ?: (backoffMs + jitterMs)).coerceIn(1000L, 90_000L)
    }

    /** Retry-After v sekundách; HTTP-date formát nepodporujeme (servery posílají sekundy). */
    fun parseRetryAfterMs(value: String): Long? =
        value.trim().toLongOrNull()?.times(1000L)?.takeIf { it >= 0 }
}
