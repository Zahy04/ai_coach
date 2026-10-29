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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoMeals
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.MensaMealEntity
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.ui.components.Pill
import cz.rzahr.aicoach.ui.components.SegmentedTabs
import cz.rzahr.aicoach.ui.components.SignalCard
import cz.rzahr.aicoach.ui.theme.dataColors

data class MensaState(
    val selectedSystem: Int,
    val meals: List<MensaMealEntity>,
    val loading: Boolean,
    val error: String?,
    val estimateError: String?,
    val loggedMealKeys: Set<String>,
    val isWeekend: Boolean
)

data class MensaActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSelectSystem: (Int) -> Unit = {},
    val onLog: (MensaMealEntity) -> Unit = {}
)

private val SYSTEMS = listOf(MensaMealEntity.SYSTEM_STUDENTSKEJ_DUM, MensaMealEntity.SYSTEM_TECHNICKA)

@Composable
fun MensaScreen(
    onBack: () -> Unit,
    viewModel: MensaViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }
    MensaContent(
        state = MensaState(
            selectedSystem = viewModel.selectedSystem.collectAsStateWithLifecycle().value,
            meals = viewModel.meals.collectAsStateWithLifecycle().value,
            loading = viewModel.loading.collectAsStateWithLifecycle().value,
            error = viewModel.error.collectAsStateWithLifecycle().value,
            estimateError = viewModel.estimateError.collectAsStateWithLifecycle().value,
            loggedMealKeys = viewModel.loggedMealKeys.collectAsStateWithLifecycle().value,
            isWeekend = viewModel.isWeekend
        ),
        snackbarHostState = snackbarHostState,
        actions = MensaActions(
            onBack = onBack,
            onRefresh = { viewModel.refresh(force = true) },
            onSelectSystem = viewModel::selectSystem,
            onLog = viewModel::logToDiary
        )
    )
}

@Composable
fun MensaContent(
    state: MensaState,
    actions: MensaActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = stringResource(R.string.mensa_title),
                eyebrow = stringResource(R.string.mensa_today_lunch),
                onBack = actions.onBack,
                actions = {
                    CircleIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = actions.onRefresh,
                        enabled = !state.loading
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
            item(key = "systems") {
                SegmentedTabs(
                    options = listOf(stringResource(R.string.mensa_system_sd), stringResource(R.string.mensa_system_tech)),
                    selectedIndex = SYSTEMS.indexOf(state.selectedSystem).coerceAtLeast(0),
                    onSelect = { actions.onSelectSystem(SYSTEMS[it]) }
                )
            }
            if (state.loading) {
                item(key = "loading") {
                    Column {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.mensa_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            state.error?.let { message ->
                item(key = "error") { Notice(message, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
            }
            state.estimateError?.let { message ->
                item(key = "estimateError") {
                    Notice(
                        stringResource(R.string.mensa_estimate_prefix, "$message "),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                state.isWeekend && state.meals.isEmpty() -> item(key = "weekend") {
                    EmptyState(
                        icon = Icons.Outlined.EventBusy,
                        title = stringResource(R.string.mensa_weekend_title),
                        description = stringResource(R.string.mensa_weekend_desc)
                    )
                }
                state.meals.isEmpty() && !state.loading -> item(key = "none") {
                    EmptyState(
                        icon = Icons.Outlined.NoMeals,
                        title = stringResource(R.string.mensa_no_meals_title),
                        description = stringResource(R.string.mensa_no_meals_desc),
                        action = {
                            OutlinedButton(onClick = actions.onRefresh) { Text(stringResource(R.string.refresh)) }
                        }
                    )
                }
                else -> state.meals.groupBy { it.category }.forEach { (category, meals) ->
                    item(key = "cat_$category") {
                        Eyebrow(category, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                    }
                    items(meals, key = { "${it.id}" }) { meal ->
                        MealCard(
                            meal = meal,
                            logged = MensaViewModel.mealKey(meal) in state.loggedMealKeys,
                            onLog = { actions.onLog(meal) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, container: Color, content: Color) {
    Surface(color = container, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

@Composable
private fun MealCard(meal: MensaMealEntity, logged: Boolean, onLog: () -> Unit) {
    val data = dataColors()
    val (ratingLabel, ratingColor) = when (meal.rating) {
        MensaMealEntity.RATING_GREEN -> stringResource(R.string.rating_label_green) to data.good
        MensaMealEntity.RATING_YELLOW -> stringResource(R.string.rating_label_yellow) to data.okay
        MensaMealEntity.RATING_RED -> stringResource(R.string.rating_label_red) to data.bad
        else -> null to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SignalCard(contentPadding = 0.dp) {
        meal.photoUrl?.let { url ->
            Box {
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.mensa_food_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                if (ratingLabel != null) {
                    Pill(
                        ratingLabel,
                        color = ratingColor,
                        filled = true,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (meal.photoUrl == null && ratingLabel != null) {
                Pill(ratingLabel, color = ratingColor)
                Spacer(Modifier.height(8.dp))
            }
            Text(meal.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (meal.kcalMin != null && meal.kcalMax != null) {
                            stringResource(R.string.kcal_range, meal.kcalMin, meal.kcalMax)
                        } else {
                            stringResource(R.string.mensa_no_estimate)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    val details = listOfNotNull(
                        meal.grams?.let { "$it g" },
                        meal.proteinG?.let { stringResource(R.string.macro_protein_short, it.toInt()) },
                        meal.carbsG?.let { stringResource(R.string.macro_carbs_short, it.toInt()) },
                        meal.fatG?.let { stringResource(R.string.macro_fat_short, it.toInt()) }
                    )
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (logged) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mensa_logged_button))
                    }
                } else {
                    Button(
                        onClick = onLog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mensa_log_button))
                    }
                }
            }
            meal.allergens?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mensa_allergens, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
