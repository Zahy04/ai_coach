package cz.rzahr.aicoach.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.repo.SettingsRepository
import cz.rzahr.aicoach.llm.GeminiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geminiClient: GeminiClient
) : ViewModel() {

    val apiKey: StateFlow<String> = settingsRepository.apiKey
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

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    fun save(apiKey: String, model: String) {
        viewModelScope.launch {
            settingsRepository.setApiKey(apiKey)
            settingsRepository.setModel(model)
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

    fun loadModels() {
        if (_modelsLoading.value) return
        viewModelScope.launch {
            _modelsLoading.value = true
            try {
                _availableModels.value = geminiClient.fetchModelNames()
            } catch (_: Exception) {
                // chybu ignorujeme – uživatel může zadat model ručně
            } finally {
                _modelsLoading.value = false
            }
        }
    }
}
