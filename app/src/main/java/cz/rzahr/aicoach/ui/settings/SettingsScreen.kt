package cz.rzahr.aicoach.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.ui.components.SectionHeader
import cz.rzahr.aicoach.llm.ModelFilters
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
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val modelVisibility by viewModel.modelVisibility.collectAsStateWithLifecycle()
    val totalModelsCount by viewModel.totalModelsCount.collectAsStateWithLifecycle()
    val filterModels by viewModel.filterModels.collectAsStateWithLifecycle()
    val filterFamilyPro by viewModel.filterFamilyPro.collectAsStateWithLifecycle()
    val filterFamilyFlash by viewModel.filterFamilyFlash.collectAsStateWithLifecycle()
    val filterFamilyFlashLite by viewModel.filterFamilyFlashLite.collectAsStateWithLifecycle()
    val filterFamilyGemma by viewModel.filterFamilyGemma.collectAsStateWithLifecycle()
    val filterFamilyOther by viewModel.filterFamilyOther.collectAsStateWithLifecycle()
    val familyCounts by viewModel.familyCounts.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    val waterGoal by viewModel.waterGoal.collectAsStateWithLifecycle()
    val goalWeightKg by viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val githubPat by viewModel.githubPat.collectAsStateWithLifecycle()
    val issueSending by viewModel.issueSending.collectAsStateWithLifecycle()

    var newKeyInput by rememberSaveable { mutableStateOf("") }
    var modelInput by rememberSaveable { mutableStateOf<String?>(null) }
    var calorieGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var proteinGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var waterGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var goalWeightInput by rememberSaveable { mutableStateOf<String?>(null) }
    var newPatInput by rememberSaveable { mutableStateOf("") }
    var patVisible by rememberSaveable { mutableStateOf(false) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showIssueDialog by remember { mutableStateOf(false) }
    var currentLangTag by rememberSaveable { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { java.util.Locale.getDefault().language }) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(model, calorieGoal, proteinGoal) {
        if (modelInput == null) modelInput = model
        if (calorieGoalInput == null) calorieGoalInput = calorieGoal.toString()
        if (proteinGoalInput == null) proteinGoalInput = proteinGoal.toString()
    }

    LaunchedEffect(waterGoal, goalWeightKg) {
        if (waterGoalInput == null) waterGoalInput = waterGoal.toString()
        if (goalWeightInput == null) {
            goalWeightInput = goalWeightKg?.let { String.format(java.util.Locale.forLanguageTag("cs"), "%.1f", it) }.orEmpty()
        }
    }

    LaunchedEffect(apiKey, totalModelsCount) {
        if (apiKey.isNotBlank() && totalModelsCount == 0) {
            viewModel.loadModels()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            SectionHeader(stringResource(R.string.settings_section_coach))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_gemini_api), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (apiKey.isNotBlank()) "✓ Klíč je uložen" else "⚠ Klíč chybí",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    OutlinedTextField(
                        value = newKeyInput,
                        onValueChange = { newKeyInput = it },
                        label = { Text(stringResource(R.string.settings_new_api_key)) },
                        placeholder = { Text(stringResource(R.string.keep_blank_to_keep)) },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.settings_show_key)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelInput ?: "",
                        onValueChange = { modelInput = it },
                        label = { Text(stringResource(R.string.settings_model)) },
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
                            Text(if (totalModelsCount == 0) stringResource(R.string.settings_load_models) else stringResource(R.string.settings_refresh_models))
                        }
                        Text(
                            if (totalModelsCount == 0) {
                                stringResource(R.string.settings_models_manual_hint)
                            } else if (filterModels) {
                                stringResource(
                                    R.string.settings_models_count_filtered,
                                    availableModels.size,
                                    totalModelsCount
                                )
                            } else {
                                stringResource(R.string.settings_models_count, totalModelsCount)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = filterModels,
                            onCheckedChange = viewModel::setFilterModels
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_filter_models),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.settings_filter_models_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (filterModels) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp)
                        ) {
                            FamilyFilterRow(
                                checked = filterFamilyPro,
                                onCheckedChange = viewModel::setFilterFamilyPro,
                                label = stringResource(R.string.settings_filter_family_pro),
                                count = familyCounts[ModelFilters.Family.PRO] ?: 0,
                                showCount = totalModelsCount > 0
                            )
                            FamilyFilterRow(
                                checked = filterFamilyFlash,
                                onCheckedChange = viewModel::setFilterFamilyFlash,
                                label = stringResource(R.string.settings_filter_family_flash),
                                count = familyCounts[ModelFilters.Family.FLASH] ?: 0,
                                showCount = totalModelsCount > 0
                            )
                            FamilyFilterRow(
                                checked = filterFamilyFlashLite,
                                onCheckedChange = viewModel::setFilterFamilyFlashLite,
                                label = stringResource(R.string.settings_filter_family_flash_lite),
                                count = familyCounts[ModelFilters.Family.FLASH_LITE] ?: 0,
                                showCount = totalModelsCount > 0
                            )
                            FamilyFilterRow(
                                checked = filterFamilyGemma,
                                onCheckedChange = viewModel::setFilterFamilyGemma,
                                label = stringResource(R.string.settings_filter_family_gemma),
                                count = familyCounts[ModelFilters.Family.GEMMA] ?: 0,
                                showCount = totalModelsCount > 0
                            )
                            FamilyFilterRow(
                                checked = filterFamilyOther,
                                onCheckedChange = viewModel::setFilterFamilyOther,
                                label = stringResource(R.string.settings_filter_family_other),
                                count = familyCounts[ModelFilters.Family.OTHER] ?: 0,
                                showCount = totalModelsCount > 0
                            )
                        }
                    }
                    if (availableModels.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showModelDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_pick_from_list, availableModels.size))
                        }
                    }
                    if (totalModelsCount > 0) {
                        OutlinedButton(
                            onClick = { showManageDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_manage_models, totalModelsCount))
                        }
                    }
                    val savedMsg = stringResource(R.string.settings_saved_key_and_model)
                    val modelOnlyMsg = stringResource(R.string.settings_saved_model_only)
                    Button(
                        onClick = {
                            val savedWithKey = newKeyInput.isNotBlank()
                            viewModel.save(modelInput.orEmpty(), newKeyInput.takeIf { it.isNotBlank() })
                            scope.launch { snackbarHostState.showSnackbar(if (savedWithKey) savedMsg else modelOnlyMsg) }
                            newKeyInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_goals))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_section_goals), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = calorieGoalInput ?: "",
                            onValueChange = { calorieGoalInput = it },
                            label = { Text(stringResource(R.string.settings_goal_calories)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = proteinGoalInput ?: "",
                            onValueChange = { proteinGoalInput = it },
                            label = { Text(stringResource(R.string.settings_goal_protein)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = waterGoalInput ?: "",
                            onValueChange = { waterGoalInput = it },
                            label = { Text(stringResource(R.string.settings_goal_water)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = goalWeightInput ?: "",
                            onValueChange = { goalWeightInput = it },
                            label = { Text(stringResource(R.string.settings_goal_weight)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val goalsSavedMsg = stringResource(R.string.settings_goals_saved)
                    Button(
                        onClick = {
                            viewModel.saveGoals(
                                (calorieGoalInput ?: "").toIntOrNull() ?: 0,
                                (proteinGoalInput ?: "").toIntOrNull() ?: 0
                            )
                            viewModel.saveWaterGoal((waterGoalInput ?: "").toIntOrNull() ?: 0)
                            viewModel.saveGoalWeight((goalWeightInput ?: "").replace(',', '.').toDoubleOrNull())
                            scope.launch { snackbarHostState.showSnackbar(goalsSavedMsg) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_save_goals))
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_language))
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentLangTag == "cs",
                        onClick = {
                            currentLangTag = "cs"
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("cs"))
                        },
                        label = { Text(stringResource(R.string.language_cs)) }
                    )
                    FilterChip(
                        selected = currentLangTag != "cs",
                        onClick = {
                            currentLangTag = "en"
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        },
                        label = { Text(stringResource(R.string.language_en)) }
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_feedback))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (githubPat.isNotBlank()) "✓ Token je uložen" else "⚠ Token chybí",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (githubPat.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    OutlinedTextField(
                        value = newPatInput,
                        onValueChange = { newPatInput = it },
                        label = { Text(stringResource(R.string.settings_new_github_token)) },
                        placeholder = { Text(stringResource(R.string.keep_blank_to_keep)) },
                        singleLine = true,
                        visualTransformation = if (patVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Vytvoř na github.com/settings/personal-access-tokens/new token pro repozitář ai_coach s oprávněním „Issues: Read and write“. Ulož ho sem a pak můžeš hlásit problémy přímo z aplikace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tokenSavedMsg = stringResource(R.string.settings_token_saved)
                        Button(onClick = {
                            viewModel.saveGithubPat(newPatInput.takeIf { it.isNotBlank() })
                            newPatInput = ""
                            scope.launch { snackbarHostState.showSnackbar(tokenSavedMsg) }
                        }) { Text(stringResource(R.string.settings_save_token)) }
                        OutlinedButton(onClick = { showIssueDialog = true }) {
                            Text(stringResource(R.string.settings_report_issue))
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_help_section))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_howto_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_howto_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    var issueTitle by remember { mutableStateOf("") }
    var issueDescription by remember { mutableStateOf("") }

    if (showIssueDialog) {
        AlertDialog(
            onDismissRequest = { if (!issueSending) showIssueDialog = false },
            title = { Text(stringResource(R.string.settings_report_issue)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = issueTitle,
                        onValueChange = { issueTitle = it },
                        label = { Text(stringResource(R.string.issue_title_label)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = issueDescription,
                        onValueChange = { issueDescription = it },
                        label = { Text(stringResource(R.string.issue_description_label)) },
                        minLines = 3,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                val createdTpl = stringResource(R.string.issue_created, "%s")
                val errTpl = stringResource(R.string.issue_error, "%s")
                TextButton(
                    onClick = {
                        viewModel.sendIssue(issueTitle, issueDescription) { result ->
                            scope.launch {
                                result.fold(
                                    onSuccess = { url ->
                                        snackbarHostState.showSnackbar(createdTpl.format(url))
                                        issueTitle = ""
                                        issueDescription = ""
                                        showIssueDialog = false
                                    },
                                    onFailure = { e ->
                                        snackbarHostState.showSnackbar(errTpl.format(e.message ?: ""))
                                    }
                                )
                            }
                        }
                    },
                    enabled = !issueSending && issueTitle.isNotBlank()
                ) {
                    Text(stringResource(if (issueSending) R.string.issue_sending else R.string.issue_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.settings_pick_model_title)) },
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
                TextButton(onClick = { showModelDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showManageDialog) {
        // Viditelné modely první, jinak pořadí z API.
        val manageList = allModels.sortedBy { !(modelVisibility[it] ?: false) }
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text(stringResource(R.string.settings_manage_models_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(manageList, key = { it }) { candidate ->
                        val visible = modelVisibility[candidate] ?: false
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setModelVisible(candidate, !visible) }
                        ) {
                            Checkbox(
                                checked = visible,
                                onCheckedChange = { viewModel.setModelVisible(candidate, it) }
                            )
                            Text(
                                candidate,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (visible) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun FamilyFilterRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    count: Int,
    showCount: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            if (showCount) "$label ($count)" else label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
