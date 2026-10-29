package cz.rzahr.aicoach.ui.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.ui.components.MetricText
import cz.rzahr.aicoach.ui.components.Pill
import cz.rzahr.aicoach.ui.components.SectionTitle
import cz.rzahr.aicoach.ui.components.SegmentedTabs
import cz.rzahr.aicoach.ui.components.SignalCard
import cz.rzahr.aicoach.ui.food.SwipeToDelete
import cz.rzahr.aicoach.ui.theme.dataColors
import cz.rzahr.aicoach.util.formatDateTime
import cz.rzahr.aicoach.util.oneDecimal
import kotlinx.coroutines.launch

data class WeightActions(
    val onAdd: (Double, String?) -> Unit = { _, _ -> },
    val onUpdate: (Long, Double, String?) -> Unit = { _, _, _ -> },
    val onDelete: (WeightEntryEntity) -> Unit = {}
)

@Composable
fun WeightScreen(viewModel: WeightViewModel = hiltViewModel()) {
    val entries by viewModel.entriesDesc.collectAsStateWithLifecycle()
    val goalWeightKg by viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    WeightContent(
        entries = entries,
        goalWeightKg = goalWeightKg,
        snackbarHostState = snackbarHostState,
        actions = WeightActions(
            onAdd = viewModel::add,
            onUpdate = viewModel::update,
            onDelete = { entry ->
                viewModel.delete(entry.id)
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        context.getString(R.string.weight_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
                }
            }
        )
    )
}

private val PERIODS = listOf(30, 90, null)

@Composable
fun WeightContent(
    entries: List<WeightEntryEntity>,
    goalWeightKg: Double?,
    actions: WeightActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WeightEntryEntity?>(null) }
    var periodIndex by rememberSaveable { mutableIntStateOf(1) }
    val periodDays = PERIODS[periodIndex]
    val filtered = remember(entries, periodDays) {
        periodDays?.let { days ->
            val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            entries.filter { it.timestamp >= cutoff }
        } ?: entries
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = stringResource(R.string.weight_title),
                actions = {
                    CircleIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.weight_add),
                        onClick = { showAddDialog = true },
                        container = MaterialTheme.colorScheme.inverseSurface,
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.MonitorWeight,
                        title = stringResource(R.string.weight_empty_title),
                        description = stringResource(R.string.weight_empty_desc),
                        action = {
                            Button(onClick = { showAddDialog = true }) { Text(stringResource(R.string.weight_add)) }
                        }
                    )
                }
                return@LazyColumn
            }
            item(key = "hero") { CurrentWeightCard(entries, filtered, goalWeightKg, periodDays) }
            item(key = "chart") {
                SignalCard {
                    SegmentedTabs(
                        options = listOf(
                            stringResource(R.string.period_30_days),
                            stringResource(R.string.period_90_days),
                            stringResource(R.string.period_all)
                        ),
                        selectedIndex = periodIndex,
                        onSelect = { periodIndex = it }
                    )
                    Spacer(Modifier.height(16.dp))
                    WeightChart(filtered, goalWeightKg)
                }
            }
            item(key = "history_title") { SectionTitle(stringResource(R.string.weight_history)) }
            item(key = "history") {
                SignalCard(contentPadding = 0.dp) {
                    entries.forEachIndexed { index, entry ->
                        androidx.compose.runtime.key(entry.id) {
                            SwipeToDelete(onDelete = { actions.onDelete(entry) }) {
                                WeightRow(
                                    entry = entry,
                                    previous = entries.getOrNull(index + 1),
                                    goalWeightKg = goalWeightKg,
                                    onClick = { editEntry = entry }
                                )
                            }
                        }
                        if (index < entries.lastIndex) {
                            Box(
                                Modifier
                                    .padding(horizontal = 18.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        WeightDialog(
            title = stringResource(R.string.weight_dialog_add_title),
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { weight, note ->
                actions.onAdd(weight, note)
                showAddDialog = false
            }
        )
    }
    editEntry?.let { entry ->
        WeightDialog(
            title = stringResource(R.string.weight_dialog_edit_title),
            initial = entry,
            onDismiss = { editEntry = null },
            onSave = { weight, note ->
                actions.onUpdate(entry.id, weight, note)
                editEntry = null
            },
            onDelete = {
                editEntry = null
                actions.onDelete(entry)
            }
        )
    }
}

@Composable
private fun CurrentWeightCard(
    all: List<WeightEntryEntity>,
    period: List<WeightEntryEntity>,
    goalWeightKg: Double?,
    periodDays: Int?
) {
    val latest = all.first()
    SignalCard(container = MaterialTheme.colorScheme.inverseSurface, border = null, contentPadding = 22.dp) {
        val onInk = MaterialTheme.colorScheme.inverseOnSurface
        Eyebrow(stringResource(R.string.weight_current), color = onInk.copy(alpha = 0.65f))
        Spacer(Modifier.height(8.dp))
        MetricText(value = latest.weightKg.oneDecimal(), unit = "kg", color = onInk)
        Text(
            latest.timestamp.formatDateTime(),
            style = MaterialTheme.typography.labelMedium,
            color = onInk.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (period.size >= 2) {
                val change = period.first().weightKg - period.last().weightKg
                val text = signed(change) + " kg · " + if (periodDays != null) {
                    stringResource(R.string.weight_change_days, periodDays)
                } else {
                    stringResource(R.string.weight_change_all)
                }
                Pill(text, color = onInk)
            }
            if (goalWeightKg != null) {
                Pill(
                    stringResource(R.string.weight_to_goal, kotlin.math.abs(latest.weightKg - goalWeightKg).oneDecimal(), goalWeightKg.oneDecimal()),
                    color = onInk
                )
            }
        }
    }
}

@Composable
private fun WeightRow(
    entry: WeightEntryEntity,
    previous: WeightEntryEntity?,
    goalWeightKg: Double?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.timestamp.formatDateTime(), style = MaterialTheme.typography.titleSmall)
            entry.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (previous != null) {
            val delta = entry.weightKg - previous.weightKg
            // Směrem k cíli = dobře; bez cíle neutrálně.
            val towardGoal = goalWeightKg?.let { kotlin.math.abs(entry.weightKg - it) < kotlin.math.abs(previous.weightKg - it) }
            val color = when {
                kotlin.math.abs(delta) < 0.05 || towardGoal == null -> MaterialTheme.colorScheme.onSurfaceVariant
                towardGoal -> dataColors().good
                else -> dataColors().bad
            }
            Text(signed(delta), style = MaterialTheme.typography.labelLarge, color = color)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            entry.weightKg.oneDecimal(),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            " kg",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun signed(value: Double): String = (if (value > 0.0) "+" else if (value < 0.0) "−" else "±") + kotlin.math.abs(value).oneDecimal()

@Composable
private fun WeightDialog(
    title: String,
    initial: WeightEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var weightText by rememberSaveable { mutableStateOf(initial?.weightKg?.let { "%.1f".format(java.util.Locale.US, it) }.orEmpty()) }
    var noteText by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    val parsed = weightText.trim().replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed in 20.0..400.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.weight_field_label)) },
                    singleLine = true,
                    isError = weightText.isNotBlank() && !valid,
                    supportingText = if (weightText.isNotBlank() && !valid) {
                        { Text(stringResource(R.string.weight_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.note_field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(it, noteText.takeIf { n -> n.isNotBlank() }) } },
                enabled = valid
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
