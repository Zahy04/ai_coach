package cz.rzahr.aicoach.ui.food

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.FoodEntryEntity
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.ui.components.LinearMeter
import cz.rzahr.aicoach.ui.components.SignalCard
import cz.rzahr.aicoach.ui.theme.dataColors
import cz.rzahr.aicoach.util.Locales
import cz.rzahr.aicoach.util.formatTime
import cz.rzahr.aicoach.util.grouped
import cz.rzahr.aicoach.util.toLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

data class FoodActions(
    val onOpenMensa: () -> Unit = {},
    val onOpenScan: () -> Unit = {},
    val onAdd: (String, Int?, Double?, Double?, Double?, Int?) -> Unit = { _, _, _, _, _, _ -> },
    val onUpdate: (Long, String, Int?, Double?, Double?, Double?, Int?) -> Unit = { _, _, _, _, _, _, _ -> },
    val onDelete: (FoodEntryEntity) -> Unit = {}
)

@Composable
fun FoodDiaryScreen(
    onOpenMensa: () -> Unit,
    onOpenScan: () -> Unit,
    viewModel: FoodViewModel = hiltViewModel()
) {
    val entries by viewModel.entriesDesc.collectAsStateWithLifecycle()
    val calorieGoal by viewModel.calorieGoal.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    FoodDiaryContent(
        entries = entries,
        calorieGoal = calorieGoal,
        snackbarHostState = snackbarHostState,
        actions = FoodActions(
            onOpenMensa = onOpenMensa,
            onOpenScan = onOpenScan,
            onAdd = viewModel::add,
            onUpdate = viewModel::update,
            onDelete = { entry ->
                viewModel.delete(entry.id)
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        context.getString(R.string.food_deleted, entry.name),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
                }
            }
        )
    )
}

@Composable
fun FoodDiaryContent(
    entries: List<FoodEntryEntity>,
    calorieGoal: Int,
    actions: FoodActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<FoodEntryEntity?>(null) }
    val grouped = remember(entries) { entries.groupBy { it.timestamp.toLocalDate() }.toList() }
    val todayTotal = grouped.firstOrNull { it.first == LocalDate.now() }?.second?.sumOf { it.calories ?: 0 } ?: 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = stringResource(R.string.food_title),
                eyebrow = stringResource(R.string.food_today_total, todayTotal.grouped(), calorieGoal.grouped()),
                actions = {
                    CircleIconButton(
                        icon = Icons.Filled.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan_title),
                        onClick = actions.onOpenScan
                    )
                    CircleIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.food_add),
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
            item(key = "mensa") { MensaEntryCard(actions.onOpenMensa) }
            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Restaurant,
                        title = stringResource(R.string.food_empty_title),
                        description = stringResource(R.string.food_empty_desc)
                    )
                }
            }
            grouped.forEach { (day, dayEntries) ->
                item(key = "day_$day") {
                    DayHeader(day = day, entries = dayEntries, calorieGoal = calorieGoal)
                }
                item(key = "list_$day") {
                    SignalCard(contentPadding = 0.dp) {
                        dayEntries.forEachIndexed { index, entry ->
                            androidx.compose.runtime.key(entry.id) {
                                SwipeToDelete(onDelete = { actions.onDelete(entry) }) {
                                    FoodRow(entry = entry, onClick = { editEntry = entry })
                                }
                            }
                            if (index < dayEntries.lastIndex) {
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
    }

    if (showAddDialog) {
        FoodDialog(
            title = stringResource(R.string.food_dialog_add_title),
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, calories, protein, carbs, fat, grams ->
                actions.onAdd(name, calories, protein, carbs, fat, grams)
                showAddDialog = false
            }
        )
    }
    editEntry?.let { entry ->
        FoodDialog(
            title = stringResource(R.string.food_dialog_edit_title),
            initial = entry,
            onDismiss = { editEntry = null },
            onSave = { name, calories, protein, carbs, fat, grams ->
                actions.onUpdate(entry.id, name, calories, protein, carbs, fat, grams)
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
private fun MensaEntryCard(onClick: () -> Unit) {
    SignalCard(
        onClick = onClick,
        container = MaterialTheme.colorScheme.secondaryContainer,
        border = null,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mensa_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    stringResource(R.string.mensa_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, entries: List<FoodEntryEntity>, calorieGoal: Int) {
    val total = entries.sumOf { it.calories ?: 0 }
    val locale = Locale.forLanguageTag(Locales.currentTag())
    val label = when (day) {
        LocalDate.now() -> stringResource(R.string.day_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern("EEEE", locale))
    }
    Column(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow(day.format(DateTimeFormatter.ofPattern("d. MMMM", locale)))
                Text(label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                stringResource(R.string.kcal_value_of, total.grouped(), calorieGoal.grouped()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearMeter(
            progress = if (calorieGoal > 0) total / calorieGoal.toFloat() else 0f,
            color = dataColors().calories,
            height = 4.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.4f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun FoodRow(entry: FoodEntryEntity, onClick: () -> Unit) {
    val data = dataColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                buildList {
                    add(entry.timestamp.formatTime())
                    entry.grams?.let { add("$it g") }
                    add(sourceLabel(entry.source))
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val macros = listOfNotNull(
                entry.proteinG?.takeIf { it >= 0.5 }?.let { Triple(data.protein, R.string.macro_protein_short, it) },
                entry.carbsG?.takeIf { it >= 0.5 }?.let { Triple(data.carbs, R.string.macro_carbs_short, it) },
                entry.fatG?.takeIf { it >= 0.5 }?.let { Triple(data.fat, R.string.macro_fat_short, it) }
            )
            if (macros.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    macros.forEach { (color, label, grams) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(label, kotlin.math.round(grams).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                entry.calories?.grouped() ?: "—",
                style = MaterialTheme.typography.titleMedium
            )
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun sourceLabel(source: String): String = when (source) {
    FoodEntryEntity.SOURCE_API -> stringResource(R.string.ev_source_off)
    FoodEntryEntity.SOURCE_MANUAL -> stringResource(R.string.food_source_manual)
    FoodEntryEntity.SOURCE_SCAN -> stringResource(R.string.food_source_scan)
    else -> stringResource(R.string.food_source_coach)
}

/** Přidání i úprava jídla – desetinná čárka i tečka, prázdné pole = neznámá hodnota. */
@Composable
private fun FoodDialog(
    title: String,
    initial: FoodEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Double?, Double?, Double?, Int?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var grams by rememberSaveable { mutableStateOf(initial?.grams?.toString().orEmpty()) }
    var calories by rememberSaveable { mutableStateOf(initial?.calories?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(initial?.proteinG?.let(::trimNum).orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(initial?.carbsG?.let(::trimNum).orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(initial?.fatG?.let(::trimNum).orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.food_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(grams, { grams = it }, stringResource(R.string.food_grams_label), Modifier.weight(1f), decimal = false)
                    NumberField(calories, { calories = it }, stringResource(R.string.food_kcal_label), Modifier.weight(1f), decimal = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it }, stringResource(R.string.macro_protein_label), Modifier.weight(1f))
                    NumberField(carbs, { carbs = it }, stringResource(R.string.macro_carbs_label), Modifier.weight(1f))
                    NumberField(fat, { fat = it }, stringResource(R.string.macro_fat_label), Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        calories.parseIntOrNull(),
                        protein.parseDoubleOrNull(),
                        carbs.parseDoubleOrNull(),
                        fat.parseDoubleOrNull(),
                        grams.parseIntOrNull()
                    )
                },
                enabled = name.isNotBlank()
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

@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimal: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}

/** „150,5" i „150.5"; desetinné číslo do celočíselného pole se zaokrouhlí místo zahození. */
internal fun String.parseDoubleOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

internal fun String.parseIntOrNull(): Int? = parseDoubleOrNull()?.let { kotlin.math.round(it).toInt() }

internal fun trimNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
