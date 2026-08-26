package cz.rzahr.aicoach.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FoodViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val entriesDesc: StateFlow<List<FoodEntryEntity>> = foodRepository.observeAllDesc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calorieGoal: StateFlow<Int> = settingsRepository.dailyCalorieGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    fun add(name: String, calories: Int?, proteinG: Double?, carbsG: Double?, fatG: Double?, grams: Int? = null) {
        viewModelScope.launch {
            foodRepository.add(
                name = name,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                grams = grams,
                source = FoodEntryEntity.SOURCE_MANUAL
            )
        }
    }

    fun update(id: Long, name: String, calories: Int?, proteinG: Double?, carbsG: Double?, fatG: Double?, grams: Int? = null) {
        viewModelScope.launch {
            foodRepository.update(id, name, calories, proteinG, carbsG, fatG, grams)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { foodRepository.delete(id) }
    }
}
