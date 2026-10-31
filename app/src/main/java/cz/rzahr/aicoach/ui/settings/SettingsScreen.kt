package cz.rzahr.aicoach.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.llm.ModelFilters
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.ui.components.SegmentedTabs
import cz.rzahr.aicoach.ui.components.SignalCard
import cz.rzahr.aicoach.ui.theme.dataColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val calorieGoal by viewModel.calorieGoal.collectAsStateWithLifecycle()
    val proteinGoal by viewModel.proteinGoal.collectAsStateWithLifecycle()
    val waterGoal by viewModel.waterGoal.collectAsStateWithLifecycle()
    val goalWeightKg by viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val totalModelsCount by viewModel.totalModelsCount.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    val filterModels by viewModel.filterModels.collectAsStateWithLifecycle()
    val filterFamilyPro by viewModel.filterFamilyPro.collectAsStateWithLifecycle()
    val filterFamilyFlash by viewModel.filterFamilyFlash.collectAsStateWithLifecycle()
    val filterFamilyFlashLite by viewModel.filterFamilyFlashLite.collectAsStateWithLifecycle()
    val filterFamilyGemma by viewModel.filterFamilyGemma.collectAsStateWithLifecycle()
    val filterFamilyOther by viewModel.filterFamilyOther.collectAsStateWithLifecycle()
    val familyCounts by viewModel.familyCounts.collectAsStateWithLifecycle()
    val modelVisibility by viewModel.modelVisibility.collectAsStateWithLifecycle()
    val githubPat by viewModel.githubPat.collectAsStateWithLifecycle()
    val issueSending by viewModel.issueSending.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(resId: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(resId)) }
    }

    var modelInput by rememberSaveable { mutableStateOf<String?>(null) }
    var calorieGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var proteinGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var waterGoalInput by rememberSaveable { mutableStateOf<String?>(null) }
    var goalWeightInput by rememberSaveable { mutableStateOf<String?>(null) }
    // Tajné hodnoty jen v paměti – ne do saved state (ten systém drží mimo proces).
    var newGeminiKeyInput by remember { mutableStateOf("") }
    var newOpenRouterKeyInput by remember { mutableStateOf("") }
    var newPatInput by remember { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showIssueDialog by rememberSaveable { mutableStateOf(false) }
    var issueTitle by rememberSaveable { mutableStateOf("") }
    var issueDescription by rememberSaveable { mutableStateOf("") }
    var currentLangTag by rememberSaveable {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { java.util.Locale.getDefault().language })
    }

    LaunchedEffect(model, calorieGoal, proteinGoal) {
        if (modelInput == null) modelInput = model
        if (calorieGoalInput == null) calorieGoalInput = calorieGoal.toString()
        if (proteinGoalInput == null) proteinGoalInput = proteinGoal.toString()
    }
    LaunchedEffect(waterGoal, goalWeightKg) {
        if (waterGoalInput == null) waterGoalInput = waterGoal.toString()
        if (goalWeightInput == null) goalWeightInput = goalWeightKg?.let { "%.1f".format(java.util.Locale.US, it) }.orEmpty()
    }
    LaunchedEffect(apiKey, openRouterApiKey, totalModelsCount) {
        if ((apiKey.isNotBlank() || openRouterApiKey.isNotBlank()) && totalModelsCount == 0) viewModel.loadModels()
    }

    val goalWeightText = goalWeightInput.orEmpty().trim()
    val parsedGoalWeight = goalWeightText.replace(',', '.').toDoubleOrNull()
    val goalWeightInvalid = goalWeightText.isNotEmpty() && (parsedGoalWeight == null || parsedGoalWeight !in 20.0..400.0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppHeader(title = stringResource(R.string.settings_title), onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section(stringResource(R.string.settings_section_coach)) {
                SecretField(
                    title = stringResource(R.string.settings_gemini_api),
                    stored = apiKey.isNotBlank(),
                    value = newGeminiKeyInput,
                    onValueChange = { newGeminiKeyInput = it },
                    label = stringResource(R.string.settings_new_api_key)
                )
                SecretField(
                    title = stringResource(R.string.settings_openrouter_api),
                    stored = openRouterApiKey.isNotBlank(),
                    value = newOpenRouterKeyInput,
                    onValueChange = { newOpenRouterKeyInput = it },
                    label = stringResource(R.string.settings_new_api_key)
                )
                OutlinedTextField(
                    value = modelInput.orEmpty(),
                    onValueChange = { modelInput = it },
                    label = { Text(stringResource(R.string.settings_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::loadModels, enabled = !modelsLoading) {
                        if (modelsLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (totalModelsCount == 0) R.string.settings_load_models else R.string.settings_refresh_models
                            )
                        )
                    }
                    if (availableModels.isNotEmpty()) {
                        OutlinedButton(onClick = { showModelDialog = true }) {
                            Text(stringResource(R.string.settings_pick_from_list, availableModels.size))
                        }
                    }
                }
                Text(
                    when {
                        totalModelsCount == 0 -> stringResource(R.string.settings_models_manual_hint)
                        filterModels -> stringResource(R.string.settings_models_count_filtered, availableModels.size, totalModelsCount)
                        else -> stringResource(R.string.settings_models_count, totalModelsCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CheckRow(
                    checked = filterModels,
                    onCheckedChange = viewModel::setFilterModels,
                    title = stringResource(R.string.settings_filter_models),
                    subtitle = stringResource(R.string.settings_filter_models_desc)
                )
                if (filterModels) {
                    Column(Modifier.padding(start = 16.dp)) {
                        val show = totalModelsCount > 0
                        fun label(text: String, family: ModelFilters.Family) =
                            if (show) "$text (${familyCounts[family] ?: 0})" else text
                        CheckRow(filterFamilyPro, viewModel::setFilterFamilyPro, label(stringResource(R.string.settings_filter_family_pro), ModelFilters.Family.PRO))
                        CheckRow(filterFamilyFlash, viewModel::setFilterFamilyFlash, label(stringResource(R.string.settings_filter_family_flash), ModelFilters.Family.FLASH))
                        CheckRow(filterFamilyFlashLite, viewModel::setFilterFamilyFlashLite, label(stringResource(R.string.settings_filter_family_flash_lite), ModelFilters.Family.FLASH_LITE))
                        CheckRow(filterFamilyGemma, viewModel::setFilterFamilyGemma, label(stringResource(R.string.settings_filter_family_gemma), ModelFilters.Family.GEMMA))
                        CheckRow(filterFamilyOther, viewModel::setFilterFamilyOther, label(stringResource(R.string.settings_filter_family_other), ModelFilters.Family.OTHER))
                    }
                }
                if (totalModelsCount > 0) {
                    TextButton(onClick = { showManageDialog = true }) {
                        Text(stringResource(R.string.settings_manage_models, totalModelsCount))
                    }
                }
                Button(
                    onClick = {
                        val savedWithKey = newGeminiKeyInput.isNotBlank() || newOpenRouterKeyInput.isNotBlank()
                        viewModel.save(
                            modelInput.orEmpty(),
                            newGeminiKey = newGeminiKeyInput.takeIf { it.isNotBlank() },
                            newOpenRouterKey = newOpenRouterKeyInput.takeIf { it.isNotBlank() }
                        )
                        newGeminiKeyInput = ""
                        newOpenRouterKeyInput = ""
                        toast(if (savedWithKey) R.string.settings_saved_key_and_model else R.string.settings_saved_model_only)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_save)) }
            }

            Section(stringResource(R.string.settings_section_goals)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(calorieGoalInput.orEmpty(), { calorieGoalInput = it }, stringResource(R.string.settings_goal_calories), Modifier.weight(1f))
                    NumberField(proteinGoalInput.orEmpty(), { proteinGoalInput = it }, stringResource(R.string.settings_goal_protein), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(waterGoalInput.orEmpty(), { waterGoalInput = it }, stringResource(R.string.settings_goal_water), Modifier.weight(1f))
                    OutlinedTextField(
                        value = goalWeightInput.orEmpty(),
                        onValueChange = { goalWeightInput = it },
                        label = { Text(stringResource(R.string.settings_goal_weight)) },
                        singleLine = true,
                        isError = goalWeightInvalid,
                        supportingText = if (goalWeightInvalid) {
                            { Text(stringResource(R.string.weight_invalid)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        // Neplatné číslo = ponechat stávající cíl (repozitář navíc odmítne ≤ 0).
                        viewModel.saveGoals(
                            calorieGoalInput?.trim()?.toIntOrNull() ?: calorieGoal,
                            proteinGoalInput?.trim()?.toIntOrNull() ?: proteinGoal
                        )
                        waterGoalInput?.trim()?.toIntOrNull()?.let(viewModel::saveWaterGoal)
                        // Prázdné pole = bez cíle; neplatné tlačítko nepustí.
                        viewModel.saveGoalWeight(parsedGoalWeight)
                        toast(R.string.settings_goals_saved)
                    },
                    enabled = !goalWeightInvalid,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_save_goals)) }
            }

            Section(stringResource(R.string.settings_language)) {
                SegmentedTabs(
                    options = listOf(stringResource(R.string.language_cs), stringResource(R.string.language_en)),
                    selectedIndex = if (currentLangTag.startsWith("cs")) 0 else 1,
                    onSelect = { index ->
                        val tag = if (index == 0) "cs" else "en"
                        currentLangTag = tag
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                )
            }

            Section(stringResource(R.string.settings_section_feedback)) {
                SecretField(
                    title = stringResource(R.string.settings_github_title),
                    stored = githubPat.isNotBlank(),
                    value = newPatInput,
                    onValueChange = { newPatInput = it },
                    label = stringResource(R.string.settings_new_github_token),
                    storedText = stringResource(R.string.settings_pat_stored),
                    missingText = stringResource(R.string.settings_pat_missing)
                )
                Text(
                    stringResource(R.string.settings_pat_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveGithubPat(newPatInput.takeIf { it.isNotBlank() })
                            newPatInput = ""
                            toast(R.string.settings_token_saved)
                        },
                        enabled = newPatInput.isNotBlank()
                    ) { Text(stringResource(R.string.settings_save_token)) }
                    OutlinedButton(onClick = { showIssueDialog = true }, enabled = githubPat.isNotBlank()) {
                        Text(stringResource(R.string.settings_report_issue))
                    }
                }
            }

            Section(stringResource(R.string.settings_help_section)) {
                Text(stringResource(R.string.settings_howto_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_howto_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_howto_body_openrouter), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showIssueDialog) {
        AlertDialog(
            onDismissRequest = { if (!issueSending) showIssueDialog = false },
            title = { Text(stringResource(R.string.issue_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = issueTitle,
                        onValueChange = { issueTitle = it },
                        label = { Text(stringResource(R.string.issue_title_label)) },
                        singleLine = true,
                        enabled = !issueSending
                    )
                    OutlinedTextField(
                        value = issueDescription,
                        onValueChange = { issueDescription = it },
                        label = { Text(stringResource(R.string.issue_description_label)) },
                        minLines = 3,
                        maxLines = 8,
                        enabled = !issueSending
                    )
                    Text(
                        stringResource(R.string.issue_device_info_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendIssue(issueTitle, issueDescription) { result ->
                            result.fold(
                                onSuccess = { url ->
                                    // Dialog zavřít hned – dřív zůstal s aktivním tlačítkem po dobu
                                    // snackbaru a druhé ťuknutí založilo duplicitní issue.
                                    showIssueDialog = false
                                    issueTitle = ""
                                    issueDescription = ""
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.issue_created, url)) }
                                },
                                onFailure = { e ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.issue_error, e.message.orEmpty()))
                                    }
                                }
                            )
                        }
                    },
                    enabled = !issueSending && issueTitle.isNotBlank()
                ) {
                    Text(stringResource(if (issueSending) R.string.issue_sending else R.string.issue_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }, enabled = !issueSending) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.settings_pick_model_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(availableModels, key = { it }) { candidate ->
                        val selected = candidate == modelInput
                        Text(
                            candidate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    modelInput = candidate
                                    showModelDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        )
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
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(manageList, key = { it }) { candidate ->
                        val visible = modelVisibility[candidate] ?: false
                        CheckRow(visible, { viewModel.setModelVisible(candidate, it) }, candidate)
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
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Eyebrow(title, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
        SignalCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Pole pro tajný klíč: uložená hodnota se nikdy nezobrazuje, jen stav „uloženo / chybí". */
@Composable
private fun SecretField(
    title: String,
    stored: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    storedText: String = stringResource(R.string.key_stored),
    missingText: String = stringResource(R.string.key_missing)
) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                if (stored) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (stored) dataColors().good else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                (if (stored) storedText else missingText).trimStart('✓', '⚠', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = if (stored) dataColors().good else MaterialTheme.colorScheme.error
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.keep_blank_to_keep)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.settings_show_key)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
