package cz.rzahr.aicoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val calorieGoal by viewModel.calorieGoal.collectAsStateWithLifecycle()
    val proteinGoal by viewModel.proteinGoal.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()

    var keyInput by rememberSaveable { mutableStateOf<String?>(null) }
    var modelInput by rememberSaveable { mutableStateOf<String?>(null) }
    var calorieGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var proteinGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apiKey, model, calorieGoal, proteinGoal) {
        if (keyInput == null) keyInput = apiKey
        if (modelInput == null) modelInput = model
        if (calorieGoalInput == null) calorieGoalInput = calorieGoal.toString()
        if (proteinGoalInput == null) proteinGoalInput = proteinGoal.toString()
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank() && availableModels.isEmpty()) {
            viewModel.loadModels()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader("Trenér")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gemini API", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = keyInput ?: "",
                        onValueChange = { keyInput = it },
                        label = { Text("API klíč") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Zobrazit klíč"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelInput ?: "",
                        onValueChange = { modelInput = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = viewModel::loadModels, enabled = !modelsLoading) {
                            if (modelsLoading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (availableModels.isEmpty()) "Načíst modely" else "Obnovit modely")
                        }
                        Text(
                            if (availableModels.isEmpty()) "Nebo zadej ID ručně" else "Dostupných: ${availableModels.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (availableModels.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showModelDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Vybrat ze seznamu (${availableModels.size} modelů)")
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.save(keyInput.orEmpty(), modelInput.orEmpty())
                            scope.launch { snackbarHostState.showSnackbar("Uloženo.") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uložit")
                    }
                }
            }

            SectionHeader("Denní cíle")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = calorieGoalInput ?: "",
                            onValueChange = { calorieGoalInput = it },
                            label = { Text("Kalorie (kcal)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = proteinGoalInput ?: "",
                            onValueChange = { proteinGoalInput = it },
                            label = { Text("Bílkoviny (g)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.saveGoals(
                                (calorieGoalInput ?: "").toIntOrNull() ?: 0,
                                (proteinGoalInput ?: "").toIntOrNull() ?: 0
                            )
                            scope.launch { snackbarHostState.showSnackbar("Cíle uloženy.") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uložit cíle")
                    }
                }
            }

            SectionHeader("Nápověda")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Jak získat API klíč", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. Jdi na aistudio.google.com a přihlas se Google účtem.\n" +
                            "2. Vytvoř API klíč (Create API key).\n" +
                            "3. Klíč vlož sem do pole API klíč.\n\n" +
                            "Gemini má volný tier, který pro běžné použití chatu bohatě stačí.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Vyber model") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(availableModels) { candidate ->
                        TextButton(
                            onClick = {
                                modelInput = candidate
                                showModelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                candidate,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (candidate == (modelInput ?: "")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("Zavřít") }
            }
        )
    }
}
