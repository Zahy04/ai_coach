package cz.rzahr.aicoach.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import cz.rzahr.aicoach.data.db.entity.LlmRequestEntity
import cz.rzahr.aicoach.data.db.entity.LlmToolCallEntity
import cz.rzahr.aicoach.data.repo.LlmStatsRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Export telemetrie modelů do JSONu pro analýzu v notebooku (issue #6).
 * Soubor se píše do cacheDir, ke kterému ukazuje FileProvider z manifestu,
 * takže se dá sdílet přes share sheet bez jakéhokoli runtime oprávnění.
 */
@Singleton
class LlmStatsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: LlmStatsRepository
) {

    /** Vrátí [Uri] sdíleného JSONu, nebo null pokud zatím nemáme žádná data. */
    suspend fun exportToCache(now: Long = System.currentTimeMillis()): Uri? {
        val requests = statsRepository.allRequests()
        val toolCalls = statsRepository.allToolCalls()
        if (requests.isEmpty() && toolCalls.isEmpty()) return null

        val report = StatsReport(
            generatedAt = iso(now),
            appVersion = appVersion(),
            summary = StatsAggregator.summarize(requests, toolCalls),
            requests = requests.map { it.toExport() },
            toolCalls = toolCalls.map { it.toExport() }
        )
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val stamp = FILE_STAMP.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
        val file = File(dir, "llm_stats_$stamp.json")
        // encodeDefaults = true, jinak by v JSONu chyběly false/0 a notebook by
        // musel volat .get() na každé pole.
        file.writeText(JSON.encodeToString(StatsReport.serializer(), report))

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    }.getOrDefault("unknown")

    private fun iso(millis: Long): String = ISO.format(Instant.ofEpochMilli(millis))

    @Serializable
    private data class StatsReport(
        val generatedAt: String,
        val appVersion: String,
        /** Předpočítané agregáty – v notebooku nemusíš nic groupovat. */
        val summary: StatsAggregator.Summary,
        val requests: List<RequestRow>,
        val toolCalls: List<ToolCallRow>
    )

    @Serializable
    private data class RequestRow(
        val id: Long,
        val timestamp: Long,
        val iso: String,
        val provider: String,
        val model: String,
        val attempt: Int,
        val round: Int,
        val durationMs: Long,
        val firstTokenMs: Long?,
        val httpStatus: Int?,
        val success: Boolean,
        val errorKind: String?,
        val overloaded: Boolean?,
        val retryAfterMs: Long?,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?,
        val finishReason: String?,
        val toolCallCount: Int?,
        val replyChars: Int?,
        val textFallbackChars: Int?,
        val errorMessage: String?
    )

    @Serializable
    private data class ToolCallRow(
        val id: Long,
        val timestamp: Long,
        val iso: String,
        val provider: String,
        val model: String,
        val toolName: String,
        val outcome: String,
        val errorKind: String?,
        val durationMs: Long,
        val viaTextFallback: Boolean,
        val round: Int,
        val foodName: String?,
        val quantityG: Double?,
        val modelCalories: Int?,
        val modelProteinG: Double?,
        val modelCarbsG: Double?,
        val modelFatG: Double?,
        val finalCalories: Int?,
        val finalProteinG: Double?,
        val finalCarbsG: Double?,
        val finalFatG: Double?,
        val finalSource: String?,
        val savedValue: Double?
    )

    private fun LlmRequestEntity.toExport() = RequestRow(
        id = id,
        timestamp = timestamp,
        iso = iso(timestamp),
        provider = provider,
        model = model,
        attempt = attempt,
        round = round,
        durationMs = durationMs,
        firstTokenMs = firstTokenMs,
        httpStatus = httpStatus,
        success = success,
        errorKind = errorKind,
        overloaded = overloaded,
        retryAfterMs = retryAfterMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        finishReason = finishReason,
        toolCallCount = toolCallCount,
        replyChars = replyChars,
        textFallbackChars = textFallbackChars,
        errorMessage = errorMessage
    )

    private fun LlmToolCallEntity.toExport() = ToolCallRow(
        id = id,
        timestamp = timestamp,
        iso = iso(timestamp),
        provider = provider,
        model = model,
        toolName = toolName,
        outcome = outcome,
        errorKind = errorKind,
        durationMs = durationMs,
        viaTextFallback = viaTextFallback,
        round = round,
        foodName = foodName,
        quantityG = quantityG,
        modelCalories = modelCalories,
        modelProteinG = modelProteinG,
        modelCarbsG = modelCarbsG,
        modelFatG = modelFatG,
        finalCalories = finalCalories,
        finalProteinG = finalProteinG,
        finalCarbsG = finalCarbsG,
        finalFatG = finalFatG,
        finalSource = finalSource,
        savedValue = savedValue
    )

    private companion object {
        const val EXPORT_DIR = "exports"
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        /**
         * Vlastní instance, ne ta sdílená z DI: ta má explicitNulls = false pro
         * Gemini API. V exportu chceme null raději vidět explicitně – v pandasu
         * je pak jasné, že hodnota chybí, a ne že sloupec neexistuje.
         */
        val JSON = Json { prettyPrint = true; encodeDefaults = true }
    }
}