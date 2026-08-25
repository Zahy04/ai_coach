package cz.rzahr.aicoach.data.repo

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        const val DEFAULT_CALORIE_GOAL = 2000
        const val DEFAULT_PROTEIN_GOAL = 130
        const val DEFAULT_WATER_GOAL_ML = 2500
    }
}
