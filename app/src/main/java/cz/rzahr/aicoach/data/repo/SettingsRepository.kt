package cz.rzahr.aicoach.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val keyApiKey = stringPreferencesKey("gemini_api_key")
    private val keyModel = stringPreferencesKey("model")
    private val keyCalorieGoal = intPreferencesKey("daily_calorie_goal")
    private val keyProteinGoal = intPreferencesKey("daily_protein_goal")
    private val keyWaterGoal = intPreferencesKey("daily_water_goal_ml")
    private val keyGoalWeight = doublePreferencesKey("goal_weight_kg")
    private val keyGithubPat = stringPreferencesKey("github_pat")
    private val keyMensaLastFetchDay = stringPreferencesKey("mensa_last_fetch_day")
    private val keyFilterModels = booleanPreferencesKey("filter_ai_models")
    private val keyFilterFamilyPro = booleanPreferencesKey("filter_family_pro")
    private val keyFilterFamilyFlash = booleanPreferencesKey("filter_family_flash")
    private val keyFilterFamilyFlashLite = booleanPreferencesKey("filter_family_flash_lite")
    private val keyFilterFamilyGemma = booleanPreferencesKey("filter_family_gemma")
    private val keyFilterFamilyOther = booleanPreferencesKey("filter_family_other")
    private val keyHiddenModels = stringSetPreferencesKey("hidden_models")
    private val keyShownModels = stringSetPreferencesKey("shown_models")

    val githubPat: Flow<String> = context.dataStore.data.map { it[keyGithubPat].orEmpty() }

    suspend fun setGithubPat(value: String) {
        context.dataStore.edit { it[keyGithubPat] = value.trim() }
    }

    val mensaLastFetchDay: Flow<String> =
        context.dataStore.data.map { it[keyMensaLastFetchDay].orEmpty() }

    suspend fun setMensaLastFetchDay(value: String) {
        context.dataStore.edit { it[keyMensaLastFetchDay] = value }
    }

    val dailyWaterGoal: Flow<Int> =
        context.dataStore.data.map { it[keyWaterGoal] ?: DEFAULT_WATER_GOAL_ML }

    val goalWeightKg: Flow<Double?> =
        context.dataStore.data.map { it[keyGoalWeight] }

    suspend fun setDailyWaterGoal(value: Int) {
        if (value > 0) context.dataStore.edit { it[keyWaterGoal] = value }
    }

    suspend fun setGoalWeightKg(value: Double?) {
        context.dataStore.edit { prefs ->
            if (value != null && value > 0) {
                prefs[keyGoalWeight] = value
            } else {
                prefs.remove(keyGoalWeight)
            }
        }
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApiKey].orEmpty() }

    val model: Flow<String> = context.dataStore.data.map { it[keyModel] ?: DEFAULT_MODEL }

    /** Zapnutý filtr = v nabídce jen Gemini Pro / Flash / Flash-Lite a Gemma. Výchozí true. */
    val filterModels: Flow<Boolean> =
        context.dataStore.data.map { it[keyFilterModels] ?: DEFAULT_FILTER_MODELS }

    suspend fun setFilterModels(enabled: Boolean) {
        context.dataStore.edit { it[keyFilterModels] = enabled }
    }

    /** Které rodiny modelů filtr propouští. Default = Pro / Flash / Flash-Lite / Gemma. */
    val filterFamilyPro: Flow<Boolean> =
        context.dataStore.data.map { it[keyFilterFamilyPro] ?: DEFAULT_FILTER_FAMILY_PRO }
    val filterFamilyFlash: Flow<Boolean> =
        context.dataStore.data.map { it[keyFilterFamilyFlash] ?: DEFAULT_FILTER_FAMILY_FLASH }
    val filterFamilyFlashLite: Flow<Boolean> =
        context.dataStore.data.map { it[keyFilterFamilyFlashLite] ?: DEFAULT_FILTER_FAMILY_FLASH_LITE }
    val filterFamilyGemma: Flow<Boolean> =
        context.dataStore.data.map { it[keyFilterFamilyGemma] ?: DEFAULT_FILTER_FAMILY_GEMMA }
    val filterFamilyOther: Flow<Boolean> =
        context.dataStore.data.map { it[keyFilterFamilyOther] ?: DEFAULT_FILTER_FAMILY_OTHER }

    suspend fun setFilterFamilyPro(enabled: Boolean) {
        context.dataStore.edit { it[keyFilterFamilyPro] = enabled }
    }

    suspend fun setFilterFamilyFlash(enabled: Boolean) {
        context.dataStore.edit { it[keyFilterFamilyFlash] = enabled }
    }

    suspend fun setFilterFamilyFlashLite(enabled: Boolean) {
        context.dataStore.edit { it[keyFilterFamilyFlashLite] = enabled }
    }

    suspend fun setFilterFamilyGemma(enabled: Boolean) {
        context.dataStore.edit { it[keyFilterFamilyGemma] = enabled }
    }

    suspend fun setFilterFamilyOther(enabled: Boolean) {
        context.dataStore.edit { it[keyFilterFamilyOther] = enabled }
    }

    /**
     * Individuálně schované / vynucené modely nad rámec rodin.
     * `hidden` schová model se zapnutou rodinou, `shown` ukáže model s vypnutou rodinou.
     */
    val hiddenModels: Flow<Set<String>> =
        context.dataStore.data.map { it[keyHiddenModels].orEmpty() }

    val shownModels: Flow<Set<String>> =
        context.dataStore.data.map { it[keyShownModels].orEmpty() }

    /**
     * Přepne viditelnost jednoho modelu.
     * @param familyEnabled jestli je právě zapnutá jeho rodina – podle toho se volba
     *   uloží do správné sady, aby přežila i pozdější přepínání rodin.
     */
    suspend fun setModelVisible(name: String, visible: Boolean, familyEnabled: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            val hidden = prefs[keyHiddenModels].orEmpty().toMutableSet()
            val shown = prefs[keyShownModels].orEmpty().toMutableSet()
            if (visible) {
                hidden.remove(clean)
                if (!familyEnabled) shown.add(clean)
            } else {
                shown.remove(clean)
                if (familyEnabled) hidden.add(clean)
            }
            prefs[keyHiddenModels] = hidden
            prefs[keyShownModels] = shown
        }
    }

    /** Smaže výjimky pro modely, které API už nevrací. */
    suspend fun pruneModelOverrides(known: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[keyHiddenModels] = prefs[keyHiddenModels].orEmpty().intersect(known)
            prefs[keyShownModels] = prefs[keyShownModels].orEmpty().intersect(known)
        }
    }

    val dailyCalorieGoal: Flow<Int> =
        context.dataStore.data.map { it[keyCalorieGoal] ?: DEFAULT_CALORIE_GOAL }

    val dailyProteinGoal: Flow<Int> =
        context.dataStore.data.map { it[keyProteinGoal] ?: DEFAULT_PROTEIN_GOAL }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[keyApiKey] = value.trim() }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[keyModel] = value.trim().ifBlank { DEFAULT_MODEL } }
    }

    suspend fun setDailyCalorieGoal(value: Int) {
        if (value > 0) context.dataStore.edit { it[keyCalorieGoal] = value }
    }

    suspend fun setDailyProteinGoal(value: Int) {
        if (value > 0) context.dataStore.edit { it[keyProteinGoal] = value }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.6-flash-lite"
        const val DEFAULT_FILTER_MODELS = true
        const val DEFAULT_FILTER_FAMILY_PRO = true
        const val DEFAULT_FILTER_FAMILY_FLASH = true
        const val DEFAULT_FILTER_FAMILY_FLASH_LITE = true
        const val DEFAULT_FILTER_FAMILY_GEMMA = true
        const val DEFAULT_FILTER_FAMILY_OTHER = false
        const val DEFAULT_CALORIE_GOAL = 2000
        const val DEFAULT_PROTEIN_GOAL = 130
        const val DEFAULT_WATER_GOAL_ML = 2500
    }
}
