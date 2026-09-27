package cz.rzahr.aicoach.llm

import cz.rzahr.aicoach.data.repo.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Fasáda nad LLM providery – volí aktivního klienta podle nastavení
 * (Gemini / OpenRouter, issue #2).
 */
@Singleton
class LlmRouter @Inject constructor(
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val openRouter: OpenRouterClient
) : LlmClient {

    val provider: Flow<String> = settings.provider

    private suspend fun active(): LlmClient =
        if (settings.provider.first() == SettingsRepository.PROVIDER_OPENROUTER) openRouter
        else gemini

    override suspend fun chat(
        systemPrompt: String,
        history: List<Content>,
        onDelta: suspend (String) -> Unit
    ): ChatTurnResult = active().chat(systemPrompt, history, onDelta)

    override suspend fun fetchModelNames(): List<String> = active().fetchModelNames()

    /** API klíč aktivního providera (pro kontrolu vyplněnosti). */
    suspend fun activeApiKey(): String {
        return if (settings.provider.first() == SettingsRepository.PROVIDER_OPENROUTER) {
            settings.openRouterApiKey.first()
        } else {
            settings.apiKey.first()
        }
    }
}
