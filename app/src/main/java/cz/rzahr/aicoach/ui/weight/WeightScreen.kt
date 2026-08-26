package cz.rzahr.aicoach.ui.weight

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.util.formatDateTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    viewModel: WeightViewModel = hiltViewModel()
) {
    val entries by viewModel.entriesDesc.collectAsStateWithLifecycle()
    val goalWeightKg by viewModel.goalWeightKg.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WeightEntryEntity?>(null) }
    var periodDays by rememberSaveable { mutableStateOf<Int?>(90) }

    val filteredEntries = remember(entries, periodDays) {
        periodDays?.let { days ->
            val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            entries.filter { it.timestamp >= cutoff }
        } ?: entries
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weight_title)) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.weight_add))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.weight_chart_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = periodDays == 30,
                                onClick = { periodDays = 30 },
                                label = { Text(stringResource(R.string.period_30_days)) }
                            )
                            FilterChip(
                                selected = periodDays == 90,
                                onClick = { periodDays = 90 },
                                label = { Text(stringResource(R.string.period_90_days)) }
                            )
                            FilterChip(
                                selected = periodDays == null,
                                onClick = { periodDays = null },
                                label = { Text(stringResource(R.string.period_all)) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        WeightChart(filteredEntries, goalWeightKg)
                    }
                }
            }
            items(entries, key = { it.id }) { entry ->
                WeightRow(
                    entry = entry,
                    onDelete = { viewModel.delete(entry.id) },
                    onEdit = {
                        editEntry = entry
                    }
                )
            }
        }
    }

    editEntry?.let { entry ->
        EditWeightDialog(
            initial = entry,
            onDismiss = { editEntry = null },
            onSave = { weight, note ->
                viewModel.update(entry.id, weight, note)
                editEntry = null
            },
            onDelete = {
                viewModel.delete(entry.id)
                editEntry = null
            }
        )
    }

    if (showAddDialog) {
        AddWeightDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { weight, note ->
                viewModel.add(weight, note)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeightRow(
    entry: WeightEntryEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .combinedClickable(onClick = onEdit, onLongClick = onDelete)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.kg_value, entry.weightKg),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    entry.timestamp.formatDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun EditWeightDialog(
    initial: WeightEntryEntity,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit,
    onDelete: () -> Unit
) {
    var weightText by rememberSaveable {
        mutableStateOf(String.format(Locale.forLanguageTag("cs"), "%.1f", initial.weightKg))
    }
    var noteText by rememberSaveable { mutableStateOf(initial.note.orEmpty()) }
    val parsed = weightText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_dialog_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.weight_field_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.note_field_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsed!!, noteText.takeIf { it.isNotBlank() }) },
                enabled = parsed != null && parsed in 20.0..400.0
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun AddWeightDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit
) {
    var weightText by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }
    val parsed = weightText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.weight_field_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.note_field_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed!!, noteText.takeIf { it.isNotBlank() }) },
                enabled = parsed != null && parsed in 20.0..400.0
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
