package cz.rzahr.aicoach.ui.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.data.repo.MensaRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MensaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mensaRepository: MensaRepository,
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _selectedSystem = MutableStateFlow(MensaMealEntity.SYSTEM_TECHNICKA)
    val selectedSystem: StateFlow<Int> = _selectedSystem.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _estimateError = MutableStateFlow<String?>(null)
    val estimateError: StateFlow<String?> = _estimateError.asStateFlow()

    val todayEpochDay: Long = LocalDate.now().toEpochDay()
    val isWeekend: Boolean = LocalDate.now().dayOfWeek == DayOfWeek.SATURDAY ||
        LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY

    private val _loggedMealIds = MutableStateFlow<Set<Long>>(emptySet())
    val loggedMealIds: StateFlow<Set<Long>> = _loggedMealIds.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    val meals: StateFlow<List<MensaMealEntity>> = _selectedSystem
        .flatMapLatest { systemId -> mensaRepository.observeMeals(systemId, todayEpochDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh(force = false)
    }

    fun selectSystem(systemId: Int) {
        if (_selectedSystem.value != systemId) {
            _selectedSystem.value = systemId
            refresh(force = false)
        }
    }

    fun refresh(force: Boolean = true) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = mensaRepository.refresh(force)
                if (!result.ok) {
                    _error.value = "Nepodařilo se načíst jídelníček ze stránek SUZ. Zkus obnovit později."
                }
                _estimateError.value = result.estimateError
            } catch (e: Exception) {
                _error.value = e.message ?: "Nepodařilo se načíst menu."
            } finally {
                _loading.value = false
            }
        }
    }

    fun logToDiary(meal: MensaMealEntity) {
        viewModelScope.launch {
            foodRepository.add(
                name = buildString {
                    append(meal.name)
                    meal.grams?.let { append(" ($it g)") }
                },
                calories = meal.kcalMin?.let { min ->
                    val max = meal.kcalMax ?: min
                    (min + max) / 2
                },
                proteinG = meal.proteinG,
                carbsG = meal.carbsG,
                fatG = meal.fatG,
                source = cz.rzahr.aicoach.data.db.entity.FoodEntryEntity.SOURCE_MANUAL
            )
            _loggedMealIds.value = _loggedMealIds.value + meal.id
            _events.tryEmit(context.getString(cz.rzahr.aicoach.R.string.mensa_logged_snackbar, meal.name))
        }
    }
}
