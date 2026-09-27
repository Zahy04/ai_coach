package cz.rzahr.aicoach.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testy čistých companion funkcí (bez DataStore / Contextu).
 * Provider se odvvozuje z názvu modelu: id s "/" = OpenRouter, jinak Gemini.
 */
class SettingsRepositoryTest {

    @Test
    fun `providerForModel rozlisi openrouter a gemini`() {
        assertEquals(
            SettingsRepository.PROVIDER_OPENROUTER,
            SettingsRepository.providerForModel("qwen/qwen3.8-27b:free")
        )
        assertEquals(
            SettingsRepository.PROVIDER_OPENROUTER,
            SettingsRepository.providerForModel("meta/muse-spark-1.3")
        )
        assertEquals(
            SettingsRepository.PROVIDER_GEMINI,
            SettingsRepository.providerForModel("gemini-3.6-flash-lite")
        )
        assertEquals(
            SettingsRepository.PROVIDER_GEMINI,
            SettingsRepository.providerForModel("")
        )
    }

    @Test
    fun `defaultModelFor vrati default providera`() {
        assertEquals(
            "qwen/qwen3.8-27b:free",
            SettingsRepository.defaultModelFor(SettingsRepository.PROVIDER_OPENROUTER)
        )
        assertEquals(
            "gemini-3.6-flash-lite",
            SettingsRepository.defaultModelFor(SettingsRepository.PROVIDER_GEMINI)
        )
    }
}
