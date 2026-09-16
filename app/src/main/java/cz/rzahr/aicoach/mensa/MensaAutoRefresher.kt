package cz.rzahr.aicoach.mensa

import android.content.Context
import cz.rzahr.aicoach.data.repo.MensaRepository
import cz.rzahr.aicoach.data.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Jednou denně (při prvním otevření aplikace) tiše obnoví jídelníček na pozadí,
 * aby byl hotový dřív, než se uživatel dostane do sekce Menza.
 */
@Singleton
class MensaAutoRefresher @Inject constructor(
    private val mensaRepository: MensaRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext @Suppress("unused") private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshIfStale() {
        scope.launch {
            try {
                val today = LocalDate.now().toEpochDay().toString()
                val fetchedToday = settingsRepository.mensaLastFetchDay.first() == today
                // Fotky přibývají až kolem 10:30–10:45 — i když už se dnes stahovalo,
                // zkusíme je dotáhnout, dokud dnešní jídla nemají žádnou fotku.
                if (fetchedToday && !mensaRepository.needsPhotoRefresh()) return@launch
                runCatching { mensaRepository.refresh(force = false) }
                settingsRepository.setMensaLastFetchDay(today)
            } catch (_: Exception) {
                // tichý pokus — zkusí se znovu při příštím otevření
            }
        }
    }
}
