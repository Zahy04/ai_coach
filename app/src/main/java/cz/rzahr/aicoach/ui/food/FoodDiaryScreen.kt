package cz.rzahr.aicoach.ui.food

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.ui.components.GlowProgressRing
import cz.rzahr.aicoach.ui.components.bounceClick
import cz.rzahr.aicoach.ui.theme.extendedColors
import cz.rzahr.aicoach.util.formatDayHeader
import cz.rzahr.aicoach.util.formatTime
import cz.rzahr.aicoach.util.toLocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDiaryScreen(
    onOpenMensa: () -> Unit,
    viewModel: FoodViewModel = hiltViewModel()
) {
    val entries by viewModel.entriesDesc.collectAsStateWithLifecycle()
    val calorieGoal by viewModel.calorieGoal.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<FoodEntryEntity?>(null) }

    val grouped = remember(entries) {
        entries.groupBy { it.timestamp.toLocalDate() }.toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.food_title)) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.food_add))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .bounceClick(onClick = onOpenMensa)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mensa_entry_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.mensa_entry_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box(Modifier.weight(1f)) {
                if (entries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        EmptyState(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.food_empty_title),
                            description = stringResource(R.string.food_empty_desc)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        grouped.forEach { (day, dayEntries) ->
                            item(key = "day_$day") {
                                DayCard(
                                    dayEntries = dayEntries,
                                    dayHeader = capitalize(day.formatDayHeader()),
                                    // records label resolved inside DayCard
                                    calorieGoal = calorieGoal,
                                    onDeleteEntry = { viewModel.delete(it) },
                                    onEditEntry = { editEntry = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFoodDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, calories, protein, carbs, fat ->
                viewModel.add(name, calories, protein, carbs, fat)
                showAddDialog = false
            }
        )
    }

    editEntry?.let { entry ->
        EditFoodDialog(
            initial = entry,
            onDismiss = { editEntry = null },
            onSave = { name, calories, protein, carbs, fat ->
                viewModel.update(entry.id, name, calories, protein, carbs, fat)
                editEntry = null
            },
            onDelete = {
                viewModel.delete(entry.id)
                editEntry = null
            }
        )
    }
}

private fun capitalize(text: String): String =
    text.replaceFirstChar { it.uppercase() }

@Composable
private fun DayCard(
    dayEntries: List<FoodEntryEntity>,
    dayHeader: String,
    calorieGoal: Int,
    onDeleteEntry: (Long) -> Unit,
    onEditEntry: (FoodEntryEntity) -> Unit
) {
    val ext = extendedColors()
    val dayCalories = dayEntries.sumOf { it.calories ?: 0 }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayHeader, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (dayEntries.size) {
                            1 -> stringResource(R.string.records_one, 1)
                            in 2..4 -> stringResource(R.string.records_few, dayEntries.size)
                            else -> stringResource(R.string.records_many, dayEntries.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlowProgressRing(
                    progress = if (calorieGoal > 0) dayCalories.toFloat() / calorieGoal else 0f,
                    value = "$dayCalories",
                    label = "/ $calorieGoal kcal",
                    ringColor = ext.calories,
                    ringSize = 60.dp,
                    stroke = 6.dp,
                    valueStyle = MaterialTheme.typography.titleSmall,
                    labelStyle = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(10.dp))
            dayEntries.forEachIndexed { index, entry ->
                FoodRow(
                    entry = entry,
                    onDelete = { onDeleteEntry(entry.id) },
                    onEdit = { onEditEntry(entry) }
                )
                if (index < dayEntries.size - 1) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(
    entry: FoodEntryEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val ext = extendedColors()
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onDelete)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(entry.timestamp.formatTime())
                        entry.calories?.let { append(" · $it kcal") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val macros = listOfNotNull(
            entry.proteinG?.takeIf { it > 0 }?.let { Triple(ext.protein, it, stringResource(R.string.macro_protein_short, it.toInt())) },
            entry.carbsG?.takeIf { it > 0 }?.let { Triple(ext.carbs, it, stringResource(R.string.macro_carbs_short, it.toInt())) },
            entry.fatG?.takeIf { it > 0 }?.let { Triple(ext.fat, it, stringResource(R.string.macro_fat_short, it.toInt())) }
        )
        if (macros.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                macros.forEach { (color, grams, label) ->
                    MacroBar(
                        fraction = (grams / macros.maxOf { it.second }).toFloat(),
                        color = color,
                        label = label,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroBar(
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.22f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.08f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun EditFoodDialog(
    initial: FoodEntryEntity,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Double?, Double?, Double?) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var calories by rememberSaveable { mutableStateOf(initial.calories?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(initial.proteinG?.let { trimNum(it) }.orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(initial.carbsG?.let { trimNum(it) }.orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(initial.fatG?.let { trimNum(it) }.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upravit jídlo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.food_name_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text(stringResource(R.string.food_calories_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text(stringResource(R.string.macro_protein_short, 0).substringBefore(' ')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text(stringResource(R.string.macro_carbs_short, 0).substringBefore(' ')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text(stringResource(R.string.macro_fat_short, 0).substringBefore(' ')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        calories.toIntOrNull(),
                        protein.replace(',', '.').toDoubleOrNull(),
                        carbs.replace(',', '.').toDoubleOrNull(),
                        fat.replace(',', '.').toDoubleOrNull()
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Uložit") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Smazat", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Zrušit") }
            }
        }
    )
}

private fun trimNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun AddFoodDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int?, Double?, Double?, Double?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.food_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.food_name_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text(stringResource(R.string.food_calories_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text(stringResource(R.string.macro_protein_short, 0).substringBefore(' ')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text(stringResource(R.string.macro_carbs_short, 0).substringBefore(' ')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text(stringResource(R.string.macro_fat_short, 0).substringBefore(' ')) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        calories.toIntOrNull(),
                        protein.replace(',', '.').toDoubleOrNull(),
                        carbs.replace(',', '.').toDoubleOrNull(),
                        fat.replace(',', '.').toDoubleOrNull()
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Uložit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        }
    )
}
