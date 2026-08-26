package cz.rzahr.aicoach.ui.mensa

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.mensa.MensaScraper
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaScreen(
    onBack: () -> Unit,
    viewModel: MensaViewModel = hiltViewModel()
) {
    val selectedSystem by viewModel.selectedSystem.collectAsStateWithLifecycle()
    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val estimateError by viewModel.estimateError.collectAsStateWithLifecycle()
    val loggedMealIds by viewModel.loggedMealIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mensa_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(force = true) }, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
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
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSystem == MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM,
                    onClick = { viewModel.selectSystem(MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM) },
                    label = { Text(stringResource(R.string.mensa_system_sd)) }
                )
                FilterChip(
                    selected = selectedSystem == MensaMealEntity.SYSTEM_TECHNICKA,
                    onClick = { viewModel.selectSystem(MensaMealEntity.SYSTEM_TECHNICKA) },
                    label = { Text(stringResource(R.string.mensa_system_tech)) }
                )
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.mensa_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            error?.let { message ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            estimateError?.let { message ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.mensa_estimate_prefix, "$message "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when {
                viewModel.isWeekend -> {
                    EmptyState(
                        icon = Icons.Filled.Refresh,
                        title = stringResource(R.string.mensa_weekend_title),
                        description = stringResource(R.string.mensa_weekend_desc)
                    )
                }
                meals.isEmpty() && !loading -> {
                    EmptyState(
                        icon = Icons.Filled.Refresh,
                        title = stringResource(R.string.mensa_no_meals_title),
                        description = stringResource(R.string.mensa_no_meals_desc)
                    )
                }
                else -> {
                    val grouped = remember(meals) {
                        meals.groupBy { it.category }.toList()
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        grouped.forEach { (category, categoryMeals) ->
                            item(key = "cat_$category") {
                                Text(
                                    category,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            items(categoryMeals.size, key = { categoryMeals[it].id }) { index ->
                                val meal = categoryMeals[index]
                                MensaMealRow(
                                    meal = meal,
                                    logged = meal.id in loggedMealIds,
                                    onLog = { viewModel.logToDiary(meal) }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MensaMealRow(
    meal: MensaMealEntity,
    logged: Boolean,
    onLog: () -> Unit
) {
    val ext = extendedColors()
    val ratingColor = when (meal.rating) {
        MensaMealEntity.RATING_GREEN -> ext.success
        MensaMealEntity.RATING_YELLOW -> ext.carbs
        MensaMealEntity.RATING_RED -> MaterialTheme.colorScheme.error
        else -> null
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ratingColor ?: MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meal.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (meal.kcalMin != null && meal.kcalMax != null) {
                            append("≈ ${meal.kcalMin}–${meal.kcalMax} kcal")
                        } else {
                            append("odhad není dostupný")
                        }
                        val macros = listOfNotNull(
                            meal.proteinG?.let { "B ${it.toInt()} g" },
                            meal.carbsG?.let { "S ${it.toInt()} g" },
                            meal.fatG?.let { "T ${it.toInt()} g" }
                        )
                        if (macros.isNotEmpty()) append(" · ").append(macros.joinToString(", "))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onLog,
                enabled = !logged
            ) {
                Text(stringResource(if (logged) R.string.mensa_logged_button else R.string.mensa_log_button))
            }
        }
    }
}
