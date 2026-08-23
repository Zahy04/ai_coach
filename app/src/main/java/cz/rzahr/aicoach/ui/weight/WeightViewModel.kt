package cz.rzahr.aicoach.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.data.repo.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weightRepository: WeightRepository
) : ViewModel() {

    val entriesDesc: StateFlow<List<WeightEntryEntity>> = weightRepository.observeAllDesc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(weightKg: Double, note: String?) {
        viewModelScope.launch { weightRepository.add(weightKg, note) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { weightRepository.delete(id) }
    }
}
