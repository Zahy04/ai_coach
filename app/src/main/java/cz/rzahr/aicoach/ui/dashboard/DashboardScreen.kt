package cz.rzahr.aicoach.ui.dashboard

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.FactEntity
import cz.rzahr.aicoach.ui.components.GlowProgressRing
import cz.rzahr.aicoach.ui.components.MiniBarChart
import cz.rzahr.aicoach.ui.components.SectionHeader
import cz.rzahr.aicoach.ui.components.StatTile
import cz.rzahr.aicoach.ui.components.StaggeredItem
import cz.rzahr.aicoach.ui.components.TonalIcon
import cz.rzahr.aicoach.ui.components.bounceClick
import cz.rzahr.aicoach.ui.components.heroGradient
import cz.rzahr.aicoach.ui.navigation.Routes
import cz.rzahr.aicoach.ui.theme.extendedColors
import cz.rzahr.aicoach.util.formatDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onWeeklySummary: () -> Unit,
    onOpenTab: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val todayCalories by viewModel.todayCalories.collectAsStateWithLifecycle()
    val todayProtein by viewModel.todayProtein.collectAsStateWithLifecycle()
    val calorieGoal by viewModel.calorieGoal.collectAsStateWithLifecycle()
    val proteinGoal by viewModel.proteinGoal.collectAsStateWithLifecycle()
    val latestWeight by viewModel.latestWeight.collectAsStateWithLifecycle()
    val weightDelta by viewModel.weightDelta.collectAsStateWithLifecycle()
    val facts by viewModel.facts.collectAsStateWithLifecycle()
    val recentWorkouts by viewModel.recentWorkouts.collectAsStateWithLifecycle()
    val workoutsTotal by viewModel.workoutsTotal.collectAsStateWithLifecycle()
    val photoCount by viewModel.photoCount.collectAsStateWithLifecycle()
    val weekCalories by viewModel.weekCalories.collectAsStateWithLifecycle()
    val today by viewModel.todayDate.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val waterToday by viewModel.waterToday.collectAsStateWithLifecycle()
    val waterGoal by viewModel.waterGoal.collectAsStateWithLifecycle()
    val goalWeightKg by viewModel.goalWeightKg.collectAsStateWithLifecycle()
    var editFact by remember { mutableStateOf<FactEntity?>(null) }
    var editFactText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Nastavení")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StaggeredItem(index = 0) {
                HeroCard(
                    date = today,
                    calories = todayCalories,
                    calorieGoal = calorieGoal,
                    protein = todayProtein.toInt(),
                    proteinGoal = proteinGoal,
                    weekCalories = weekCalories
                )
            }

            StaggeredItem(index = 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Filled.MonitorWeight,
                        iconContainer = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.stat_weight),
                        value = latestWeight?.let {
                            String.format(Locale.forLanguageTag("cs"), "%.1f kg", it.weightKg)
                        } ?: "—",
                        subtitle = buildString {
                            val goal = goalWeightKg
                            val current = latestWeight?.weightKg
                            if (goal != null && current != null) {
                                val remaining = kotlin.math.abs(current - goal)
                                append(
                                    stringResource(
                                        R.string.stat_weight_goal_left,
                                        goal, remaining
                                    )
                                )
                            } else {
                                weightDelta?.let { delta ->
                                    val sign = if (delta > 0) "+" else ""
                                    append(sign)
                                    append(String.format(Locale.forLanguageTag("cs"), "%.1f", delta))
                                    append(" kg")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTab(Routes.WEIGHT) }
                    )
                    StatTile(
                        icon = Icons.Filled.PhotoLibrary,
                        iconContainer = extendedColors().protein,
                        title = "Fotky",
                        value = "$photoCount",
                        subtitle = stringResource(R.string.photos_many),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTab(Routes.PHOTOS) }
                    )
                }
            }

            StaggeredItem(index = 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Filled.FitnessCenter,
                        iconContainer = extendedColors().calories,
                        title = stringResource(R.string.stat_workouts),
                        value = "$workoutsTotal",
                        subtitle = stringResource(R.string.workouts_total_subtitle),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Filled.Lightbulb,
                        iconContainer = extendedColors().success,
                        title = stringResource(R.string.stat_notes),
                        value = "${facts.size}",
                        subtitle = stringResource(R.string.notes_subtitle),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            StaggeredItem(index = 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f)) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TonalIcon(
                                Icons.Filled.LocalFireDepartment,
                                container = extendedColors().calories.copy(alpha = 0.22f),
                                tint = extendedColors().calories
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("$streakDays", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    if (streakDays == 1) "den v řadě" else if (streakDays in 2..4) "dny v řadě" else "dní v řadě",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TonalIcon(
                                    Icons.Filled.WaterDrop,
                                    container = extendedColors().protein.copy(alpha = 0.22f),
                                    tint = extendedColors().protein
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("$waterToday ml", style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        "/ $waterGoal ml",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { viewModel.addWater(250) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+250 ml")
                            }
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.section_badges))
            StaggeredItem(index = 4) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(badges) { badge ->
                        BadgeChip(badge)
                    }
                }
            }

            StaggeredItem(index = 5) {
                WeeklySummaryCard(onClick = onWeeklySummary)
            }

            SectionHeader(stringResource(R.string.facts_section))
            StaggeredItem(index = 6) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (facts.isEmpty()) {
                            Text(
                                stringResource(R.string.facts_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            facts.take(6).forEachIndexed { index, fact ->
                                FactRow(
                                    fact = fact,
                                    onEdit = {
                                        editFact = fact
                                        editFactText = fact.content
                                    },
                                    onDelete = { viewModel.deleteFact(fact.id) }
                                )
                                if (index < minOf(5, facts.size - 1)) Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.workouts_section))
            StaggeredItem(index = 7) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (recentWorkouts.isEmpty()) {
                            Text(
                                stringResource(R.string.workouts_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            recentWorkouts.forEachIndexed { index, workout ->
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(workout.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        buildString {
                                            append(workout.timestamp.formatDateTime())
                                            workout.durationMinutes?.let { append(" · $it min") }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (index < recentWorkouts.size - 1) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    editFact?.let { fact ->
        AlertDialog(
            onDismissRequest = { editFact = null },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editFactText,
                    onValueChange = { editFactText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateFact(fact.id, editFactText)
                    editFact = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteFact(fact.id)
                        editFact = null
                    }) { Text("Smazat", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editFact = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}

@Composable
private fun HeroCard(
    date: LocalDate,
    calories: Int,
    calorieGoal: Int,
    protein: Int,
    proteinGoal: Int,
    weekCalories: List<Int>
) {
    val ext = extendedColors()
    val animatedCalories by animateIntAsState(
        targetValue = calories,
        animationSpec = tween(durationMillis = 900),
        label = "countCalories"
    )
    val animatedProtein by animateIntAsState(
        targetValue = protein,
        animationSpec = tween(durationMillis = 900),
        label = "countProtein"
    )
    // Popisky přesně k datům, která nese weekCalories: posledních 7 dní končících
    // dneškem (date). Dřív tu bylo natvrdo Po–Ne, takže dny neseděly.
    val weekDayLabels = remember(date) {
        val shortDay = DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("cs"))
        (6 downTo 0).map { offset ->
            date.minusDays(offset.toLong()).format(shortDay).replaceFirstChar { it.uppercase() }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroGradient())
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    date.formatDayHeaderCapitalized(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Text(
                stringResource(R.string.dashboard_today_overview),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlowProgressRing(
                    progress = if (calorieGoal > 0) calories.toFloat() / calorieGoal else 0f,
                    value = "$animatedCalories",
                    label = stringResource(R.string.ring_kcal_goal, calorieGoal),
                    ringColor = ext.calories
                )
                GlowProgressRing(
                    progress = if (proteinGoal > 0) protein.toFloat() / proteinGoal else 0f,
                    value = "$animatedProtein g",
                    label = stringResource(R.string.ring_protein_goal, proteinGoal),
                    ringColor = ext.protein
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.dashboard_calories_week_label),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            MiniBarChart(
                values = weekCalories,
                barColor = ext.calories,
                todayIndex = weekCalories.lastIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekDayLabels.forEach { dayLabel ->
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.width(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklySummaryCard(onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TonalIcon(Icons.Filled.AutoAwesome)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.weekly_summary_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.weekly_summary_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FactRow(
    fact: FactEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val color = factCategoryColor(fact.category)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            SurfaceChip(
                text = factCategoryLabel(fact.category),
                color = color,
                onClick = { menuExpanded = true }
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                    menuExpanded = false
                    onEdit()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                    menuExpanded = false
                    onDelete()
                })
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(fact.content, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SurfaceChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    val clickableModifier = if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onClick)
    } else {
        Modifier
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .then(clickableModifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
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
private fun BadgeChip(badge: Badge) {
    val accent = if (badge.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = if (badge.earned) 0.18f else 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (badge.earned) Icons.Filled.AutoAwesome else Icons.Filled.Lock,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            badgeLabel(badge.id),
            style = MaterialTheme.typography.labelLarge,
            color = if (badge.earned) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun factCategoryLabel(category: String): String = when (category) {
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
    val ext = extendedColors()
    return when (category) {
        FactEntity.CATEGORY_PREFERENCE -> MaterialTheme.colorScheme.primary
        FactEntity.CATEGORY_GOAL -> ext.protein
        FactEntity.CATEGORY_DIET -> ext.carbs
        FactEntity.CATEGORY_HEALTH -> ext.success
        FactEntity.CATEGORY_DISLIKE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun LocalDate.formatDayHeaderCapitalized(): String =
    format(java.time.format.DateTimeFormatter.ofPattern("EEEE d. M. yyyy"))
        .replaceFirstChar { it.uppercase() }
