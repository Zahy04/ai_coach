package cz.rzahr.aicoach.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.dao.ScannedProductDao
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.data.db.entity.ScannedProductEntity
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

/** EAN-8 až EAN-14 / UPC – jen číslice. */
internal fun isProductCode(code: String): Boolean = code.length in 8..14 && code.all { it in '0'..'9' }

/** Kalorie z maker (4/4/9 kcal/g), když je uživatel nevyplní; bez maker null. */
internal fun kcalFromMacros(proteinG: Double?, carbsG: Double?, fatG: Double?): Int? {
    if (proteinG == null && carbsG == null && fatG == null) return null
    return ((proteinG ?: 0.0) * 4 + (carbsG ?: 0.0) * 4 + (fatG ?: 0.0) * 9).roundToInt()
}

/** Nalezený produkt i s kódem; [local] = ručně zadaný a uložený v appce (dá se upravit). */
data class ScannedProduct(
    val code: String,
    val nutrition: OffNutrition,
    val local: Boolean
)

/** Otevřené ruční zadání: kód a u úpravy uloženého produktu jeho dosavadní hodnoty. */
data class ManualEntry(
    val code: String,
    val initial: OffNutrition?
)

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
 * Skener čárových kódů (issue #1): kód → uložené ruční produkty → Open Food Facts → gramáž → zápis.
 * Neznámý kód jde zadat ručně (na 100 g) a příště se najde lokálně.
 * Filtrace duplicit kódů (ML Kit posílá každý frame) přes handledCode.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val offClient: OpenFoodFactsClient,
    private val foodRepository: FoodRepository,
    private val scannedProductDao: ScannedProductDao
) : ViewModel() {

    private val _lookingUp = MutableStateFlow(false)
    val lookingUp: StateFlow<Boolean> = _lookingUp.asStateFlow()

    private val _product = MutableStateFlow<ScannedProduct?>(null)
    val product: StateFlow<ScannedProduct?> = _product.asStateFlow()

    /** Kód, pro který OFF nic nenašel – obrazovka ukáže chybu. */
    private val _notFoundCode = MutableStateFlow<String?>(null)
    val notFoundCode: StateFlow<String?> = _notFoundCode.asStateFlow()

    /** Kód, pro který je otevřený dialog ručního zadání (nový produkt nebo úprava uloženého). */
    private val _manualEntry = MutableStateFlow<ManualEntry?>(null)
    val manualEntry: StateFlow<ManualEntry?> = _manualEntry.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var handledCode: String? = null

    fun onBarcode(code: String) {
        val clean = code.trim()
        // OFF zná jen EAN/UPC; QR s URL apod. by rozbil cestu dotazu.
        if (!isProductCode(clean)) return
        if (clean == handledCode || _lookingUp.value || _product.value != null || _manualEntry.value != null) return
        handledCode = clean
        viewModelScope.launch {
            _lookingUp.value = true
            _notFoundCode.value = null
            _product.value = scannedProductDao.get(clean)?.let { ScannedProduct(clean, it.toNutrition(), local = true) }
                ?: offClient.lookupBarcode(clean)?.let { ScannedProduct(clean, it, local = false) }
            _lookingUp.value = false
            if (_product.value == null) _notFoundCode.value = clean
        }
    }

    /** Po chybě povolit skenovat znovu (jiný / stejný kód). */
    fun clearNotFound() {
        _notFoundCode.value = null
        handledCode = null
    }

    /** Ruční zadání pro nenalezený kód, nebo úprava uloženého produktu z dialogu gramáže. */
    fun startManualEntry() {
        val notFound = _notFoundCode.value
        val product = _product.value
        _manualEntry.value = when {
            notFound != null -> ManualEntry(notFound, initial = null)
            product != null -> ManualEntry(product.code, initial = product.nutrition)
            else -> return
        }
        _notFoundCode.value = null
        _product.value = null
    }

    /** Zrušení ručního zadání – zpět ke skenování. */
    fun cancelManualEntry() {
        _manualEntry.value = null
        _product.value = null
        handledCode = null
    }

    /** Uloží produkt pod kód (příští sken ho najde) a pokračuje dialogem gramáže. */
    fun saveManual(name: String, calories: Int?, proteinG: Double?, carbsG: Double?, fatG: Double?) {
        val code = _manualEntry.value?.code ?: return
        val entity = ScannedProductEntity(
            code = code,
            name = name,
            caloriesPer100g = calories ?: kcalFromMacros(proteinG, carbsG, fatG),
            proteinPer100g = proteinG,
            carbsPer100g = carbsG,
            fatPer100g = fatG
        )
        viewModelScope.launch {
            scannedProductDao.upsert(entity)
            _manualEntry.value = null
            _product.value = ScannedProduct(code, entity.toNutrition(), local = true)
        }
    }

    /** Zpět ke skenování (zrušení dialogu gramáže). */
    fun resetProduct() {
        _product.value = null
        handledCode = null
    }

    fun saveGrams(grams: Int) {
        val product = _product.value ?: return
        val safeGrams = grams.coerceIn(1, 5000)
        val scaled = scaleForGrams(product.nutrition, safeGrams)
        viewModelScope.launch {
            foodRepository.add(
                name = scaled.name.ifBlank { product.code },
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

private fun ScannedProductEntity.toNutrition() = OffNutrition(
    productName = name,
    caloriesPer100g = caloriesPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g
)
