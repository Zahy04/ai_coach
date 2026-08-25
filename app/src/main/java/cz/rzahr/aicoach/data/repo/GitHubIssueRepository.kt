package cz.rzahr.aicoach.data.repo

import android.os.Build
import cz.rzahr.aicoach.llm.ApiError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class GitHubIssueResponse(
    @Suppress("PropertyName") val html_url: String? = null,
    val message: String? = null
)

@Serializable
private data class GitHubIssuePayload(
    val title: String,
    val body: String
)

@Singleton
class GitHubIssueRepository @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository
) {

    /**
     * Vytvoří issue v repozitáři. Vyžaduje PAT uložený v nastavení
     * (fine-grained token s právem Issues: write pro tento repozitář).
     *
     * @return URL vytvořeného issue.
     */
    suspend fun createIssue(title: String, description: String): String = withContext(Dispatchers.IO) {
        val pat = settings.githubPat.first()
        if (pat.isBlank()) {
            throw IllegalStateException("Chybí GitHub token. Vlož ho výše v Nastavení.")
        }

        val fullBody = buildString {
            append(description.trim())
            append("\n\n---\n")
            append("_Zařazeno z aplikace AI Coach_\n")
            append("- Zařízení: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("- Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        }

        val payload = json.encodeToString(
            GitHubIssuePayload.serializer(),
            GitHubIssuePayload(title = title.trim(), body = fullBody)
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/issues")
            .addHeader("Authorization", "Bearer $pat")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .post(payload)
            .build()

        try {
            val response = http.newCall(request).execute()
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val parsed = runCatching {
                        json.decodeFromString(GitHubIssueResponse.serializer(), text)
                    }.getOrNull()
                    parsed?.html_url ?: "https://github.com/$REPO/issues"
                } else {
                    val apiMessage = runCatching {
                        json.decodeFromString(ApiError.serializer(), text).error?.message
                    }.getOrNull()
                    throw IllegalStateException(
                        apiMessage ?: when (resp.code) {
                            401 -> "Neplatný token."
                            403, 404 -> "Token nemá oprávnění k tomuto repozitáři."
                            422 -> "GitHub odmítl issue (chybí název?)"
                            else -> "HTTP ${resp.code}"
                        }
                    )
                }
            }
        } catch (_: IOException) {
            throw IllegalStateException("Síťová chyba — zkontroluj připojení.")
        }
    }

    companion object {
        private const val REPO = "Zahy04/ai_coach"
    }
}
