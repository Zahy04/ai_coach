package cz.rzahr.aicoach.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.llm.OffNutrition
import cz.rzahr.aicoach.llm.OpenFoodFactsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Jídlo přepočtené na zadanou gramáž (čisté – pro testy). */
internal data class ScaledFood(
    val name: String,
    val calories: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val grams: Int
)

internal fun scaleForGrams(nutrition: OffNutrition, grams: Int): ScaledFood {
    val factor = grams / 100.0
    // Half-up na jedno desetinné místo (kotlin.math.round by půlil sudé → 3,25 by dalo 3,2).
    fun round1(value: Double?): Double? =
        value?.let { kotlin.math.floor(it * factor * 10 + 0.5) / 10.0 }
    return ScaledFood(
        name = nutrition.productName,
        calories = nutrition.caloriesPer100g?.let { (it * factor).roundToInt() },
        proteinG = round1(nutrition.proteinPer100g),
        carbsG = round1(nutrition.carbsPer100g),
        fatG = round1(nutrition.fatPer100g),
        grams = grams
    )
}

/**
 * Skener čárových kódů (issue #1): kód → Open Food Facts → gramáž → zápis.
 * Filtrace duplicit kódů (ML Kit posílá každý frame) přes handledCode.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val offClient: OpenFoodFactsClient,
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _lookingUp = MutableStateFlow(false)
    val lookingUp: StateFlow<Boolean> = _lookingUp.asStateFlow()

    private val _product = MutableStateFlow<OffNutrition?>(null)
    val product: StateFlow<OffNutrition?> = _product.asStateFlow()

    /** Kód, pro který OFF nic nenašel – obrazovka ukáže chybu. */
    private val _notFoundCode = MutableStateFlow<String?>(null)
    val notFoundCode: StateFlow<String?> = _notFoundCode.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var handledCode: String? = null

    fun onBarcode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty() || clean == handledCode || _lookingUp.value || _product.value != null) return
        handledCode = clean
        viewModelScope.launch {
            _lookingUp.value = true
            _notFoundCode.value = null
            _product.value = offClient.lookupBarcode(clean)
            _lookingUp.value = false
            if (_product.value == null) _notFoundCode.value = clean
        }
    }

    /** Po chybě povolit skenovat znovu (jiný / stejný kód). */
    fun clearNotFound() {
        _notFoundCode.value = null
        handledCode = null
    }

    /** Zpět ke skenování (zrušení dialogu gramáže). */
    fun resetProduct() {
        _product.value = null
        handledCode = null
    }

    fun saveGrams(grams: Int) {
        val product = _product.value ?: return
        val safeGrams = grams.coerceIn(1, 5000)
        val scaled = scaleForGrams(product, safeGrams)
        viewModelScope.launch {
            foodRepository.add(
                name = scaled.name.ifBlank { handledCode.orEmpty() },
                calories = scaled.calories,
                proteinG = scaled.proteinG,
                carbsG = scaled.carbsG,
                fatG = scaled.fatG,
                grams = scaled.grams,
                source = FoodEntryEntity.SOURCE_SCAN
            )
            _saved.value = true
        }
    }
}
