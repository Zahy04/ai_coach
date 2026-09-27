package cz.rzahr.aicoach.llm

/**
 * Společné rozhraní LLM providerů (Gemini, OpenRouter).
 * Historie se předává v neutrálních [Content]/[Part] typech,
 * každý klient si je mapuje na formát svého API.
 */
interface LlmClient {

    suspend fun chat(
        systemPrompt: String,
        history: List<Content>,
        onDelta: suspend (String) -> Unit = {}
    ): ChatTurnResult

    suspend fun fetchModelNames(): List<String>
}
