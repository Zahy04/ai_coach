package cz.rzahr.aicoach.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.FactEntity
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.data.db.entity.WorkoutEntryEntity
import cz.rzahr.aicoach.data.repo.FactRepository
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.data.repo.PhotoRepository
import cz.rzahr.aicoach.data.repo.SettingsRepository
import cz.rzahr.aicoach.data.repo.WaterRepository
import cz.rzahr.aicoach.data.repo.WeightRepository
import cz.rzahr.aicoach.data.repo.WorkoutRepository
import cz.rzahr.aicoach.util.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    foodRepository: FoodRepository,
    weightRepository: WeightRepository,
    private val factRepository: FactRepository,
    workoutRepository: WorkoutRepository,
    photoRepository: PhotoRepository,
    settingsRepository: SettingsRepository,
    private val waterRepository: WaterRepository
) : ViewModel() {

    /** Emitne vždy při přechodu na nový den, aby se "dnešní" data aktualizovala. */
    private val dayTicker = flow {
        while (true) {
            emit(LocalDate.now())
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis() + 1000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayDate: StateFlow<LocalDate> = dayTicker
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCalories: StateFlow<Int> = dayTicker
        .flatMapLatest { foodRepository.observeTodayCalories() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayProtein: StateFlow<Double> = dayTicker
        .flatMapLatest { foodRepository.observeTodayProtein() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val calorieGoal: StateFlow<Int> = settingsRepository.dailyCalorieGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    val proteinGoal: StateFlow<Int> = settingsRepository.dailyProteinGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 130)

    val latestWeight: StateFlow<WeightEntryEntity?> = weightRepository.observeAllDesc()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weightDelta: StateFlow<Double?> = weightRepository.observeAllDesc()
        .map { entries ->
            if (entries.size >= 2) entries[0].weightKg - entries[1].weightKg else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val facts: StateFlow<List<FactEntity>> = factRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentWorkouts: StateFlow<List<WorkoutEntryEntity>> = workoutRepository.observeAllDesc()
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workoutsTotal: StateFlow<Int> = workoutRepository.observeAllDesc()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val photoCount: StateFlow<Int> = photoRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekCalories: StateFlow<List<Int>> = dayTicker
        .flatMapLatest {
            foodRepository.observeAllDesc().map { entries ->
                val today = LocalDate.now()
                (6 downTo 0).map { offset ->
                    val day = today.minusDays(offset.toLong())
                    entries
                        .filter { it.timestamp.toLocalDate() == day }
                        .sumOf { it.calories ?: 0 }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(7) { 0 })

    @OptIn(ExperimentalCoroutinesApi::class)
    val waterToday: StateFlow<Int> = dayTicker
        .flatMapLatest { waterRepository.observeTodayMl() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val waterGoal: StateFlow<Int> = settingsRepository.dailyWaterGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_WATER_GOAL_ML)

    val goalWeightKg: StateFlow<Double?> = settingsRepository.goalWeightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Počet dní v řadě, kdy uživatel zapsal váhu NEBO jídlo. */
    val streakDays: StateFlow<Int> = combine(
        foodRepository.observeAllDesc(),
        weightRepository.observeAllDesc()
    ) { foods, weights ->
        val loggedDays = buildSet {
            foods.forEach { add(it.timestamp.toLocalDate()) }
            weights.forEach { add(it.timestamp.toLocalDate()) }
        }
        var count = 0
        var day = LocalDate.now()
        if (day !in loggedDays) day = day.minusDays(1)
        while (day in loggedDays) {
            count++
            day = day.minusDays(1)
        }
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val badges: StateFlow<List<Badge>> = combine(
        streakDays,
        weightRepository.observeAllDesc(),
        foodRepository.observeAllDesc(),
        photoRepository.observeCount()
    ) { streak, weights, foods, photos ->
        listOf(
            Badge("3 dny v řadě", streak >= 3),
            Badge("7 dní v řadě", streak >= 7),
            Badge("30 dní v řadě", streak >= 30),
            Badge("10 vážení", weights.size >= 10),
            Badge("50 jídel", foods.size >= 50),
            Badge("20 fotek", photos >= 20)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWater(amountMl: Int) {
        viewModelScope.launch { waterRepository.add(amountMl) }
    }

    fun updateFact(id: Long, content: String) {
        viewModelScope.launch {
            if (content.isNotBlank()) factRepository.updateContent(id, content)
        }
    }

    fun deleteFact(id: Long) {
        viewModelScope.launch { factRepository.delete(id) }
    }
}

data class Badge(
    val label: String,
    val earned: Boolean
)
