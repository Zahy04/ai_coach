package cz.rzahr.aicoach.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.data.repo.ChatRepository
import cz.rzahr.aicoach.data.repo.FoodRepository
import cz.rzahr.aicoach.data.repo.SettingsRepository
import cz.rzahr.aicoach.data.repo.WeightRepository
import cz.rzahr.aicoach.data.repo.WorkoutRepository
import cz.rzahr.aicoach.llm.LlmRouter
import cz.rzahr.aicoach.llm.ModelFilters
import cz.rzahr.aicoach.llm.PromptBuilder
import cz.rzahr.aicoach.util.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val llmRouter: LlmRouter,
    private val promptBuilder: PromptBuilder,
    private val foodRepository: FoodRepository,
    private val weightRepository: WeightRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val model: StateFlow<String> = settingsRepository.model
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_MODEL)

    private val _allModels = MutableStateFlow<List<String>>(emptyList())

    private val filterModels: StateFlow<Boolean> = settingsRepository.filterModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_FILTER_MODELS)

    private val enabledFamilies: StateFlow<Set<ModelFilters.Family>> = combine(
        settingsRepository.filterFamilyPro,
        settingsRepository.filterFamilyFlash,
        settingsRepository.filterFamilyFlashLite,
        settingsRepository.filterFamilyGemma,
        settingsRepository.filterFamilyOther
    ) { pro, flash, lite, gemma, other ->
        buildSet {
            if (pro) add(ModelFilters.Family.PRO)
            if (flash) add(ModelFilters.Family.FLASH)
            if (lite) add(ModelFilters.Family.FLASH_LITE)
            if (gemma) add(ModelFilters.Family.GEMMA)
            if (other) add(ModelFilters.Family.OTHER)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelFilters.DEFAULT_FAMILIES)

    /** Stejný filtr jako v Nastavení – dropdown v chatu ukazuje jen vybrané modely.
     *  Qwen (OpenRouter) je vždy první, Gemini modely dle rodinného filtru. */
    val availableModels: StateFlow<List<String>> = combine(
        _allModels,
        filterModels,
        enabledFamilies,
        settingsRepository.hiddenModels,
        settingsRepository.shownModels
    ) { all, filter, families, hidden, shown ->
        val base = if (filter) ModelFilters.filterBySelection(all, families, hidden, shown) else all
        ModelFilters.pinQwenFirst(base, all, hidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    fun loadModels() {
        if (_modelsLoading.value) return
        viewModelScope.launch {
            _modelsLoading.value = true
            try {
                val fresh = llmRouter.fetchUnifiedModels()
                if (fresh.names.isNotEmpty()) _allModels.value = fresh.names
                if (fresh.complete) settingsRepository.pruneModelOverrides(fresh.names.toSet())
            } catch (_: Exception) {
                // tiché selhání — lze zadat ručně v Nastavení
            } finally {
                _modelsLoading.value = false
            }
        }
    }

    /** Výběr modelu zároveň přepne providera (qwen → OpenRouter, jinak Gemini). */
    fun selectModel(model: String) {
        viewModelScope.launch { settingsRepository.setModelAndProvider(model) }
    }

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Klíč se kontroluje podle vybraného modelu (qwen → OpenRouter, jinak Gemini). */
    val hasApiKey: StateFlow<Boolean> = combine(
        model,
        settingsRepository.apiKey,
        settingsRepository.openRouterApiKey
    ) { currentModel, geminiKey, openRouterKey ->
        (if ('/' in currentModel) openRouterKey else geminiKey).isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _toolEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val toolEvents: SharedFlow<String> = _toolEvents

    private val _pendingImagePath = MutableStateFlow<String?>(null)
    val pendingImagePath: StateFlow<String?> = _pendingImagePath.asStateFlow()

    private var generationJob: Job? = null

    private val _attachError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fotku se nepodařilo načíst (poškozený soubor, nepodporovaný formát). */
    val attachError: SharedFlow<Unit> = _attachError

    fun attachImage(uri: Uri) {
        if (_sending.value) return
        viewModelScope.launch {
            runCatching { chatRepository.importChatImage(uri) }
                .onSuccess(::replacePendingImage)
                .onFailure { _attachError.tryEmit(Unit) }
        }
    }

    fun attachCapturedImage(file: File) {
        if (_sending.value || !file.exists()) return
        viewModelScope.launch {
            runCatching { chatRepository.importChatImageFile(file) }
                .onSuccess(::replacePendingImage)
                .onFailure { _attachError.tryEmit(Unit) }
        }
    }

    /** Nahrazená (neodeslaná) fotka by jinak zůstala na disku navždy. */
    private fun replacePendingImage(path: String) {
        val previous = _pendingImagePath.value
        _pendingImagePath.value = path
        if (previous != null && previous != path) {
            viewModelScope.launch { chatRepository.deleteImageFile(previous) }
        }
    }

    fun newCaptureFile(): File = chatRepository.createCaptureFile()

    fun clearPendingImage() {
        val path = _pendingImagePath.value
        _pendingImagePath.value = null
        if (path != null) {
            viewModelScope.launch { chatRepository.deleteImageFile(path) }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        val imagePath = _pendingImagePath.value
        if ((message.isEmpty() && imagePath == null) || _sending.value) return
        _pendingImagePath.value = null
        launchTurn { chatRepository.addUserMessage(message.ifBlank { "(foto)" }, imagePath) }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun retryGeneration() {
        if (_sending.value) return
        launchTurn()
    }

    fun regenerateLastResponse() {
        if (_sending.value) return
        launchTurn {
            chatRepository.lastModelMessage()?.let { chatRepository.deleteById(it.id) }
        }
    }

    fun sendWeeklySummary() {
        if (_sending.value) return
        launchTurn {
            val now = System.currentTimeMillis()
            val weekAgo = now - 7L * 24 * 60 * 60 * 1000
            val foods = foodRepository.since(weekAgo)
            val workouts = workoutRepository.since(weekAgo)
            val weights = weightRepository.latestAsc(20).filter { it.timestamp >= weekAgo }
            val dayFormatter = DateTimeFormatter.ofPattern("d. M.").withZone(ZoneId.systemDefault())

            val message = if (cz.rzahr.aicoach.util.Locales.isCzech()) {
                buildString {
                    appendLine("Chci týdenní shrnutí. Tady jsou moje data za posledních 7 dní:")
                    if (weights.isNotEmpty()) {
                        appendLine("Váha: " + weights.joinToString(", ") { "${it.timestamp.formatDate()}: ${it.weightKg} kg" })
                    } else {
                        appendLine("Váha: žádná měření")
                    }
                    if (foods.isNotEmpty()) {
                        appendLine("Jídla (${foods.size}):")
                        foods.take(40).forEach { food ->
                            val day = dayFormatter.format(Instant.ofEpochMilli(food.timestamp))
                            appendLine("- $day ${food.name}" + (food.calories?.let { " ($it kcal)" } ?: ""))
                        }
                    } else {
                        appendLine("Jídla: žádné záznamy")
                    }
                    if (workouts.isNotEmpty()) {
                        appendLine("Tréninky (${workouts.size}):")
                        workouts.forEach { workout ->
                            val day = dayFormatter.format(Instant.ofEpochMilli(workout.timestamp))
                            appendLine("- $day ${workout.name}" + (workout.durationMinutes?.let { " ($it min)" } ?: ""))
                        }
                    } else {
                        appendLine("Tréninky: žádné záznamy")
                    }
                    append("Vyhodnoť mi prosím tento týden: co dělám dobře, co zlepšit, a dej mi 3 konkrétní doporučení na další týden.")
                }
            } else {
                buildString {
                    appendLine("I want a weekly summary. Here is my data from the last 7 days:")
                    if (weights.isNotEmpty()) {
                        appendLine("Weight: " + weights.joinToString(", ") { "${it.timestamp.formatDate()}: ${it.weightKg} kg" })
                    } else {
                        appendLine("Weight: no measurements")
                    }
                    if (foods.isNotEmpty()) {
                        appendLine("Meals (${foods.size}):")
                        foods.take(40).forEach { food ->
                            val day = dayFormatter.format(Instant.ofEpochMilli(food.timestamp))
                            appendLine("- $day ${food.name}" + (food.calories?.let { " ($it kcal)" } ?: ""))
                        }
                    } else {
                        appendLine("Meals: no records")
                    }
                    if (workouts.isNotEmpty()) {
                        appendLine("Workouts (${workouts.size}):")
                        workouts.forEach { workout ->
                            val day = dayFormatter.format(Instant.ofEpochMilli(workout.timestamp))
                            appendLine("- $day ${workout.name}" + (workout.durationMinutes?.let { " ($it min)" } ?: ""))
                        }
                    } else {
                        appendLine("Workouts: no records")
                    }
                    append("Please evaluate my week: what I'm doing well, what to improve, and give me 3 concrete recommendations for next week.")
                }
            }

            chatRepository.addUserMessage(message)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearChat() {
        if (_sending.value) return
        // clear() maže i složku s fotkami, tedy i neodeslanou přílohu.
        _pendingImagePath.value = null
        viewModelScope.launch { chatRepository.clear() }
    }

    /**
     * Příznak odesílání se nastaví synchronně ještě před první suspendací – dřív se
     * nastavoval až po zápisu do DB a rychlé dvojí ťuknutí spustilo dvě generování
     * (a nástroje zapsaly data dvakrát).
     */
    private fun launchTurn(prepare: suspend () -> Unit = {}) {
        _sending.value = true
        _error.value = null
        generationJob = viewModelScope.launch {
            try {
                prepare()
                generateResponse()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Neznámá chyba."
            } finally {
                _streamingText.value = null
                _sending.value = false
            }
        }
    }

    private suspend fun generateResponse() {
        _streamingText.value = ""
        val history = chatRepository.historyContents(HISTORY_LIMIT)
        val systemPrompt = promptBuilder.build()
        val result = llmRouter.chat(systemPrompt, history) { delta ->
            _streamingText.value = (_streamingText.value ?: "") + delta
        }
        chatRepository.addModelMessage(result.reply)
        result.toolEvents.forEach { _toolEvents.tryEmit(it) }
    }

    companion object {
        private const val HISTORY_LIMIT = 50
    }
}
