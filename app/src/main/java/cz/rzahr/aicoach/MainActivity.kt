package cz.rzahr.aicoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.rzahr.aicoach.ui.navigation.AiCoachApp
import cz.rzahr.aicoach.ui.theme.AiCoachTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiCoachTheme {
                AiCoachApp()
            }
        }
    }
}
