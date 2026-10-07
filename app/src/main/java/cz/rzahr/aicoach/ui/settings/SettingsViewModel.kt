package cz.rzahr.aicoach.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.export.LlmStatsExporter
import cz.rzahr.aicoach.data.repo.GitHubIssueRepository
import cz.rzahr.aicoach.data.repo.LlmStatsRepository
import cz.rzahr.aicoach.data.repo.SettingsRepository
import cz.rzahr.aicoach.llm.LlmRouter
import cz.rzahr.aicoach.llm.ModelFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val llmRouter: LlmRouter,
    private val gitHubIssueRepository: GitHubIssueRepository,
    private val llmStatsRepository: LlmStatsRepository,
    private val llmStatsExporter: LlmStatsExporter
) : ViewModel() {

    val githubPat: StateFlow<String> = settingsRepository.githubPat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val statsRequestCount: StateFlow<Int> = llmStatsRepository.observeRequestCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val statsToolCallCount: StateFlow<Int> = llmStatsRepository.observeToolCallCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Vrátí Uri JSONu v cache, nebo null když zatím nemáme žádná data.
     * Samotné sdílení dělá UI – ViewModel nemá Activity, ze které by se
     * share sheet spustil (startActivity z Application kontextu spadne).
     */
    suspend fun exportStats(): Uri? = llmStatsExporter.exportToCache()

    private val _issueSending = MutableStateFlow(false)
    val issueSending: StateFlow<Boolean> = _issueSending.asStateFlow()

    /** Nový token se uloží jen když něco napsal (prázdné = ponechat). */
    fun saveGithubPat(newPat: String?) {
        viewModelScope.launch {
            if (!newPat.isNullOrBlank()) {
                settingsRepository.setGithubPat(newPat)
            }
        }
    }

    fun sendIssue(title: String, description: String, onResult: (Result<String>) -> Unit) {
        if (_issueSending.value) return
        viewModelScope.launch {
            _issueSending.value = true
            onResult(
                runCatching { gitHubIssueRepository.createIssue(title, description) }
            )
            _issueSending.value = false
        }
    }

    val apiKey: StateFlow<String> = settingsRepository.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val openRouterApiKey: StateFlow<String> = settingsRepository.openRouterApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val model: StateFlow<String> = settingsRepository.model
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_MODEL)

    val calorieGoal: StateFlow<Int> = settingsRepository.dailyCalorieGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_CALORIE_GOAL)

    val proteinGoal: StateFlow<Int> = settingsRepository.dailyProteinGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_PROTEIN_GOAL)

    val waterGoal: StateFlow<Int> = settingsRepository.dailyWaterGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_WATER_GOAL_ML)

    val goalWeightKg: StateFlow<Double?> = settingsRepository.goalWeightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val filterModels: StateFlow<Boolean> = settingsRepository.filterModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_MODELS)

    val filterFamilyPro: StateFlow<Boolean> = settingsRepository.filterFamilyPro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_FAMILY_PRO)
    val filterFamilyFlash: StateFlow<Boolean> = settingsRepository.filterFamilyFlash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_FAMILY_FLASH)
    val filterFamilyFlashLite: StateFlow<Boolean> = settingsRepository.filterFamilyFlashLite
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_FAMILY_FLASH_LITE)
    val filterFamilyGemma: StateFlow<Boolean> = settingsRepository.filterFamilyGemma
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_FAMILY_GEMMA)
    val filterFamilyOther: StateFlow<Boolean> = settingsRepository.filterFamilyOther
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_FAMILY_OTHER)

    private val enabledFamilies: StateFlow<Set<ModelFilters.Family>> = combine(
        filterFamilyPro, filterFamilyFlash, filterFamilyFlashLite, filterFamilyGemma, filterFamilyOther
    ) { pro, flash, lite, gemma, other ->
        buildSet {
            if (pro) add(ModelFilters.Family.PRO)
            if (flash) add(ModelFilters.Family.FLASH)
            if (lite) add(ModelFilters.Family.FLASH_LITE)
            if (gemma) add(ModelFilters.Family.GEMMA)
            if (other) add(ModelFilters.Family.OTHER)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelFilters.DEFAULT_FAMILIES)

    private val _allModels = MutableStateFlow<List<String>>(emptyList())
    val allModels: StateFlow<List<String>> = _allModels.asStateFlow()
    val totalModelsCount: StateFlow<Int> = _allModels.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hiddenModels: StateFlow<Set<String>> = settingsRepository.hiddenModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val shownModels: StateFlow<Set<String>> = settingsRepository.shownModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val familyCounts: StateFlow<Map<ModelFilters.Family, Int>> =
        _allModels.map(ModelFilters::countByFamily)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Gemini rodinný filtr (OpenRouter ho nepoužívá). */
    private val geminiFilteredModels: StateFlow<List<String>> =
        combine(_allModels, filterModels, enabledFamilies, hiddenModels, shownModels) { all, filter, families, hidden, shown ->
            if (filter) ModelFilters.filterBySelection(all, families, hidden, shown) else all
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filtrovaný seznam; qwen (OpenRouter) je vždy první, Gemini modely dle filtru. */
    val availableModels: StateFlow<List<String>> =
        combine(geminiFilteredModels, _allModels, hiddenModels) { filtered, all, hidden ->
            ModelFilters.pinQwenFirst(filtered, all, hidden)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Viditelnost jednotlivých modelů nezávisle na hlavním vypínači (dialog Spravovat modely). */
    val modelVisibility: StateFlow<Map<String, Boolean>> =
        combine(_allModels, enabledFamilies, hiddenModels, shownModels) { all, families, hidden, shown ->
            all.associateWith { ModelFilters.isVisibleUnified(it, families, hidden, shown) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    /** Uloží model (provider se odvodí automaticky: qwen → OpenRouter, jinak Gemini) + vyplněné klíče. */
    fun save(model: String, newGeminiKey: String?, newOpenRouterKey: String?) {
        viewModelScope.launch {
            if (!newGeminiKey.isNullOrBlank()) {
                settingsRepository.setApiKey(newGeminiKey)
            }
            if (!newOpenRouterKey.isNullOrBlank()) {
                settingsRepository.setOpenRouterApiKey(newOpenRouterKey)
            }
            settingsRepository.setModelAndProvider(model)
        }
    }

    fun saveGoals(calorieGoal: Int, proteinGoal: Int) {
        viewModelScope.launch {
            settingsRepository.setDailyCalorieGoal(calorieGoal)
            settingsRepository.setDailyProteinGoal(proteinGoal)
        }
    }

    fun saveWaterGoal(waterGoalMl: Int) {
        viewModelScope.launch { settingsRepository.setDailyWaterGoal(waterGoalMl) }
    }

    fun saveGoalWeight(weightKg: Double?) {
        viewModelScope.launch { settingsRepository.setGoalWeightKg(weightKg) }
    }

    fun setFilterModels(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterModels(enabled) }
    }

    fun setFilterFamilyPro(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterFamilyPro(enabled) }
    }

    fun setFilterFamilyFlash(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterFamilyFlash(enabled) }
    }

    fun setFilterFamilyFlashLite(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterFamilyFlashLite(enabled) }
    }

    fun setFilterFamilyGemma(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterFamilyGemma(enabled) }
    }

    fun setFilterFamilyOther(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFilterFamilyOther(enabled) }
    }

    /** Přepne viditelnost jednoho konkrétního modelu (dialog Spravovat modely). */
    fun setModelVisible(name: String, visible: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        // Qwen se řídí jen skrytím (rodinný filtr ho neřeší) – skrýt = do hidden.
        val familyOn = if (clean == ModelFilters.QWEN_MODEL_ID) {
            true
        } else {
            ModelFilters.classify(clean) in enabledFamilies.value
        }
        viewModelScope.launch { settingsRepository.setModelVisible(clean, visible, familyOn) }
    }

    fun loadModels() {
        if (_modelsLoading.value) return
        viewModelScope.launch {
            _modelsLoading.value = true
            try {
                val fresh = llmRouter.fetchUnifiedModels()
                _allModels.value = fresh
                settingsRepository.pruneModelOverrides(fresh.toSet())
            } catch (_: Exception) {
                // chybu ignorujeme – uživatel může zadat model ručně
            } finally {
                _modelsLoading.value = false
            }
        }
    }
}
