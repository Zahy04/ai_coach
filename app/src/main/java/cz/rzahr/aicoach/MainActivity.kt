package cz.rzahr.aicoach

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cz.rzahr.aicoach.mensa.MensaAutoRefresher
import cz.rzahr.aicoach.ui.navigation.AiCoachApp
import cz.rzahr.aicoach.ui.theme.AiCoachTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var mensaAutoRefresher: MensaAutoRefresher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // jednou denně tiše obnovit jídelníček menzy na pozadí
        lifecycleScope.launch { mensaAutoRefresher.refreshIfStale() }

        setContent {
            AiCoachTheme {
                AiCoachApp()
            }
        }
    }
}
