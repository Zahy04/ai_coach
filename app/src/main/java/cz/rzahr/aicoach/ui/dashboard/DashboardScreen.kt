package cz.rzahr.aicoach.ui.dashboard

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.FactEntity
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.data.db.entity.WorkoutEntryEntity
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.ui.components.LinearMeter
import cz.rzahr.aicoach.ui.components.MetricText
import cz.rzahr.aicoach.ui.components.Pill
import cz.rzahr.aicoach.ui.components.SectionTitle
import cz.rzahr.aicoach.ui.components.SignalCard
import cz.rzahr.aicoach.ui.components.StaggeredItem
import cz.rzahr.aicoach.ui.components.VerticalHairline
import cz.rzahr.aicoach.ui.components.WeekBars
import cz.rzahr.aicoach.ui.navigation.Routes
import cz.rzahr.aicoach.ui.theme.dataColors
import cz.rzahr.aicoach.ui.theme.inverseDataColors
import cz.rzahr.aicoach.util.Locales
import cz.rzahr.aicoach.util.WeeklyGoal
import cz.rzahr.aicoach.util.formatDate
import cz.rzahr.aicoach.util.grouped
import cz.rzahr.aicoach.util.oneDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Vše, co obrazovka Dnes zobrazuje – oddělené od ViewModelu kvůli náhledům a testům. */
data class DashboardState(
    val today: LocalDate,
    val todayCalories: Int,
    val calorieGoal: Int,
    val todayProtein: Int,
    val proteinGoal: Int,
    val weekCalories: List<Int>,
    val weekTotal: Int,
    val weeklyGoal: Int,
    val waterToday: Int,
    val waterGoal: Int,
    val latestWeight: WeightEntryEntity?,
    val weightDelta: Double?,
    val goalWeightKg: Double?,
    val streakDays: Int,
    val workoutsTotal: Int,
    val recentWorkouts: List<WorkoutEntryEntity>,
    val photoCount: Int,
    val badges: List<Badge>,
    val facts: List<FactEntity>
)

data class DashboardActions(
    val onOpenSettings: () -> Unit = {},
    val onWeeklySummary: () -> Unit = {},
    val onOpenTab: (String) -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onAddWater: (Int) -> Unit = {},
    val onEditFact: (FactEntity, String) -> Unit = { _, _ -> },
    val onDeleteFact: (FactEntity) -> Unit = {}
)

@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onWeeklySummary: () -> Unit,
    onOpenTab: (String) -> Unit,
    onOpenChat: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state = DashboardState(
        today = viewModel.todayDate.collectAsStateWithLifecycle().value,
        todayCalories = viewModel.todayCalories.collectAsStateWithLifecycle().value,
        calorieGoal = viewModel.calorieGoal.collectAsStateWithLifecycle().value,
        todayProtein = viewModel.todayProtein.collectAsStateWithLifecycle().value.toInt(),
        proteinGoal = viewModel.proteinGoal.collectAsStateWithLifecycle().value,
        weekCalories = viewModel.weekCalories.collectAsStateWithLifecycle().value,
        weekTotal = viewModel.weekCaloriesTotal.collectAsStateWithLifecycle().value,
        weeklyGoal = viewModel.weeklyGoal.collectAsStateWithLifecycle().value,
        waterToday = viewModel.waterToday.collectAsStateWithLifecycle().value,
        waterGoal = viewModel.waterGoal.collectAsStateWithLifecycle().value,
        latestWeight = viewModel.latestWeight.collectAsStateWithLifecycle().value,
        weightDelta = viewModel.weightDelta.collectAsStateWithLifecycle().value,
        goalWeightKg = viewModel.goalWeightKg.collectAsStateWithLifecycle().value,
        streakDays = viewModel.streakDays.collectAsStateWithLifecycle().value,
        workoutsTotal = viewModel.workoutsTotal.collectAsStateWithLifecycle().value,
        recentWorkouts = viewModel.recentWorkouts.collectAsStateWithLifecycle().value,
        photoCount = viewModel.photoCount.collectAsStateWithLifecycle().value,
        badges = viewModel.badges.collectAsStateWithLifecycle().value,
        facts = viewModel.facts.collectAsStateWithLifecycle().value
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    DashboardContent(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = DashboardActions(
            onOpenSettings = onOpenSettings,
            onWeeklySummary = onWeeklySummary,
            onOpenTab = onOpenTab,
            onOpenChat = onOpenChat,
            onAddWater = viewModel::addWater,
            onEditFact = { fact, text -> viewModel.updateFact(fact.id, text) },
            onDeleteFact = { fact ->
                viewModel.deleteFact(fact)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        context.getString(R.string.fact_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreFact(fact)
                }
            }
        )
    )
}

@Composable
fun DashboardContent(
    state: DashboardState,
    actions: DashboardActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    animate: Boolean = true
) {
    var editFact by remember { mutableStateOf<FactEntity?>(null) }
    var editFactText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = greeting(),
                eyebrow = state.today.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", appLocale())),
                actions = {
                    if (state.streakDays > 0) {
                        Pill(
                            text = "${state.streakDays}",
                            color = MaterialTheme.colorScheme.primary,
                            icon = Icons.Filled.LocalFireDepartment
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    CircleIconButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        onClick = actions.onOpenSettings
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Stagger(0, animate) { EnergyHero(state, animate) }
            Stagger(1, animate) { WeekCard(state) }
            Stagger(2, animate) {
                Row(
                    Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WaterCard(state, actions.onAddWater, Modifier.weight(1f).fillMaxSize())
                    WeightCard(state, { actions.onOpenTab(Routes.WEIGHT) }, Modifier.weight(1f).fillMaxSize())
                }
            }
            Stagger(3, animate) {
                StatStrip(state, onOpenPhotos = { actions.onOpenTab(Routes.PHOTOS) })
            }
            Stagger(4, animate) { WeeklyRecapCard(actions.onWeeklySummary) }
            if (state.recentWorkouts.isNotEmpty()) {
                SectionTitle(stringResource(R.string.workouts_section))
                RecentWorkouts(state.recentWorkouts)
            }
            SectionTitle(stringResource(R.string.section_badges))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(state.badges, key = { it.id }) { badge ->
                    Pill(
                        text = badgeLabel(badge.id),
                        color = if (badge.earned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = if (badge.earned) Icons.Filled.AutoAwesome else Icons.Filled.Lock
                    )
                }
            }
            SectionTitle(stringResource(R.string.facts_section))
            FactsCard(
                facts = state.facts,
                onOpenChat = actions.onOpenChat,
                onFactClick = {
                    editFact = it
                    editFactText = it.content
                }
            )
        }
    }

    editFact?.let { fact ->
        AlertDialog(
            onDismissRequest = { editFact = null },
            title = { Text(factCategoryLabel(fact.category)) },
            text = {
                OutlinedTextField(
                    value = editFactText,
                    onValueChange = { editFactText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onEditFact(fact, editFactText)
                        editFact = null
                    },
                    enabled = editFactText.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        actions.onDeleteFact(fact)
                        editFact = null
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editFact = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}

@Composable
private fun Stagger(index: Int, animate: Boolean, content: @Composable () -> Unit) {
    if (animate) StaggeredItem(index, content) else content()
}

@Composable
private fun greeting(): String {
    val hour = LocalTime.now().hour
    return stringResource(
        when (hour) {
            in 4..10 -> R.string.greeting_morning
            in 11..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )
}

private fun appLocale(): Locale = Locale.forLanguageTag(Locales.currentTag())

/** Inkoustová karta: kalorie dne jako jedno velké číslo a pod ním bílkoviny. */
@Composable
private fun EnergyHero(state: DashboardState, animate: Boolean) {
    val onInk = MaterialTheme.colorScheme.inverseOnSurface
    val data = inverseDataColors()
    val shown by animateIntAsState(
        targetValue = state.todayCalories,
        animationSpec = tween(if (animate) 900 else 0),
        label = "kcal"
    )
    val remaining = state.calorieGoal - state.todayCalories
    SignalCard(container = MaterialTheme.colorScheme.inverseSurface, border = null, contentPadding = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.dashboard_energy_today), color = onInk.copy(alpha = 0.65f), modifier = Modifier.weight(1f))
            Pill(
                text = if (remaining >= 0) {
                    stringResource(R.string.kcal_left, remaining.grouped())
                } else {
                    stringResource(R.string.kcal_over, (-remaining).grouped())
                },
                color = if (remaining >= 0) data.good else data.bad
            )
        }
        Spacer(Modifier.height(10.dp))
        MetricText(value = shown.grouped(), unit = stringResource(R.string.kcal_of_goal, state.calorieGoal.grouped()), color = onInk)
        Spacer(Modifier.height(14.dp))
        LinearMeter(
            progress = if (state.calorieGoal > 0) state.todayCalories / state.calorieGoal.toFloat() else 0f,
            color = data.calories,
            track = onInk.copy(alpha = 0.12f),
            height = 12.dp
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.protein_label),
                style = MaterialTheme.typography.labelLarge,
                color = onInk,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.grams_of_goal, state.todayProtein, state.proteinGoal),
                style = MaterialTheme.typography.labelMedium,
                color = onInk.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearMeter(
            progress = if (state.proteinGoal > 0) state.todayProtein / state.proteinGoal.toFloat() else 0f,
            color = data.protein,
            track = onInk.copy(alpha = 0.12f),
            height = 6.dp
        )
    }
}

@Composable
private fun WeekCard(state: DashboardState) {
    val data = dataColors()
    val expected = WeeklyGoal.expectedByDay(state.today, state.calorieGoal)
    val onPace = state.weekTotal <= expected
    val weekStart = WeeklyGoal.weekStart(state.today)
    val dayFormat = DateTimeFormatter.ofPattern("EEEEE", appLocale())
    val labels = (0..6).map { weekStart.plusDays(it.toLong()).format(dayFormat).uppercase() }
    val remaining = state.weeklyGoal - state.weekTotal
    val daysLeft = WeeklyGoal.daysLeftIncludingToday(state.today)

    SignalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.this_week), modifier = Modifier.weight(1f))
            Pill(
                text = stringResource(if (onPace) R.string.pace_on_track else R.string.pace_ahead),
                color = if (onPace) data.good else data.okay
            )
        }
        Spacer(Modifier.height(8.dp))
        MetricText(
            value = state.weekTotal.grouped(),
            unit = stringResource(R.string.kcal_of_goal, state.weeklyGoal.grouped()),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(14.dp))
        WeekBars(
            values = state.weekCalories,
            labels = labels,
            todayIndex = state.today.dayOfWeek.value - 1,
            goal = state.calorieGoal,
            color = data.calories
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (remaining >= 0) {
                stringResource(
                    R.string.dashboard_weekly_remaining,
                    remaining.grouped(),
                    daysLeft,
                    WeeklyGoal.perDayLeft(remaining, daysLeft).grouped()
                )
            } else {
                stringResource(R.string.dashboard_weekly_over, (-remaining).grouped())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WaterCard(state: DashboardState, onAddWater: (Int) -> Unit, modifier: Modifier) {
    val water = dataColors().water
    SignalCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = water, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Eyebrow(stringResource(R.string.water_title))
        }
        Spacer(Modifier.height(10.dp))
        MetricText(
            value = state.waterToday.grouped(),
            unit = "ml",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            stringResource(R.string.of_value_ml, state.waterGoal.grouped()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        LinearMeter(
            progress = if (state.waterGoal > 0) state.waterToday / state.waterGoal.toFloat() else 0f,
            color = water,
            height = 6.dp
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = { onAddWater(WATER_STEP_ML) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("+$WATER_STEP_ML ml", maxLines = 1)
        }
    }
}

@Composable
private fun WeightCard(state: DashboardState, onClick: () -> Unit, modifier: Modifier) {
    SignalCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.stat_weight), modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        val latest = state.latestWeight
        MetricText(
            value = latest?.weightKg?.oneDecimal() ?: "—",
            unit = if (latest != null) "kg" else null,
            style = MaterialTheme.typography.headlineMedium
        )
        val goal = state.goalWeightKg
        val delta = state.weightDelta
        Text(
            when {
                latest != null && goal != null ->
                    stringResource(R.string.weight_to_goal, kotlin.math.abs(latest.weightKg - goal).oneDecimal(), goal.oneDecimal())
                delta != null -> stringResource(R.string.weight_since_last, (if (delta > 0) "+" else "") + delta.oneDecimal())
                latest == null -> stringResource(R.string.weight_no_records)
                else -> latest.timestamp.formatDate()
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (latest != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            Text(
                latest.timestamp.formatDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tři drobné statistiky v jednom pruhu – série, tréninky, fotky. */
@Composable
private fun StatStrip(state: DashboardState, onOpenPhotos: () -> Unit) {
    SignalCard(contentPadding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell(
                icon = Icons.Filled.LocalFireDepartment,
                tint = MaterialTheme.colorScheme.primary,
                value = "${state.streakDays}",
                label = pluralStringResource(R.plurals.streak_days, state.streakDays),
                modifier = Modifier.weight(1f)
            )
            VerticalHairline(Modifier.padding(vertical = 14.dp))
            StatCell(
                icon = Icons.Filled.FitnessCenter,
                tint = dataColors().protein,
                value = "${state.workoutsTotal}",
                label = stringResource(R.string.stat_workouts),
                modifier = Modifier.weight(1f)
            )
            VerticalHairline(Modifier.padding(vertical = 14.dp))
            StatCell(
                icon = Icons.Filled.PhotoLibrary,
                tint = MaterialTheme.colorScheme.secondary,
                value = "${state.photoCount}",
                label = stringResource(R.string.stat_photos),
                modifier = Modifier.weight(1f),
                onClick = onOpenPhotos
            )
        }
    }
}

@Composable
private fun StatCell(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WeeklyRecapCard(onClick: () -> Unit) {
    SignalCard(
        container = MaterialTheme.colorScheme.primaryContainer,
        border = null,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.weekly_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.weekly_summary_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RecentWorkouts(workouts: List<WorkoutEntryEntity>) {
    SignalCard(contentPadding = 0.dp) {
        workouts.forEachIndexed { index, workout ->
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(workout.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        workout.timestamp.formatDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                workout.durationMinutes?.let {
                    Text("$it min", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (index < workouts.lastIndex) {
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

@Composable
private fun FactsCard(
    facts: List<FactEntity>,
    onOpenChat: () -> Unit,
    onFactClick: (FactEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SignalCard {
        if (facts.isEmpty()) {
            Text(
                stringResource(R.string.facts_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenChat) { Text(stringResource(R.string.dashboard_talk_to_coach)) }
            return@SignalCard
        }
        val shown = if (expanded) facts else facts.take(FACTS_COLLAPSED)
        shown.forEachIndexed { index, fact ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onFactClick(fact) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Pill(factCategoryLabel(fact.category), factCategoryColor(fact.category))
                Spacer(Modifier.width(10.dp))
                Text(
                    fact.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 3.dp)
                )
            }
            if (index < shown.lastIndex) Spacer(Modifier.height(2.dp))
        }
        if (facts.size > FACTS_COLLAPSED) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) stringResource(R.string.show_less)
                    else stringResource(R.string.show_all_count, facts.size)
                )
            }
        }
    }
}

@Composable
private fun badgeLabel(id: String): String = when (id) {
    Badge.ID_STREAK_3 -> stringResource(R.string.badge_streak_3)
    Badge.ID_STREAK_7 -> stringResource(R.string.badge_streak_7)
    Badge.ID_STREAK_30 -> stringResource(R.string.badge_streak_30)
    Badge.ID_WEIGHINS_10 -> stringResource(R.string.badge_weighins_10)
    Badge.ID_FOODS_50 -> stringResource(R.string.badge_foods_50)
    Badge.ID_PHOTOS_20 -> stringResource(R.string.badge_photos_20)
    else -> id
}

@Composable
fun factCategoryLabel(category: String): String = when (category) {
    FactEntity.CATEGORY_PREFERENCE -> stringResource(R.string.fact_preference)
    FactEntity.CATEGORY_DISLIKE -> stringResource(R.string.fact_dislike)
    FactEntity.CATEGORY_DIET -> stringResource(R.string.fact_diet)
    FactEntity.CATEGORY_GOAL -> stringResource(R.string.fact_goal)
    FactEntity.CATEGORY_HABIT -> stringResource(R.string.fact_habit)
    FactEntity.CATEGORY_HEALTH -> stringResource(R.string.fact_health)
    else -> stringResource(R.string.fact_other)
}

@Composable
private fun factCategoryColor(category: String): Color {
    val data = dataColors()
    return when (category) {
        FactEntity.CATEGORY_PREFERENCE -> MaterialTheme.colorScheme.primary
        FactEntity.CATEGORY_GOAL -> data.protein
        FactEntity.CATEGORY_DIET -> data.carbs
        FactEntity.CATEGORY_HEALTH -> data.good
        FactEntity.CATEGORY_DISLIKE -> data.bad
        FactEntity.CATEGORY_HABIT -> data.water
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private const val WATER_STEP_ML = 250
private const val FACTS_COLLAPSED = 5
