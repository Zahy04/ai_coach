package cz.rzahr.aicoach.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.theme.EyebrowStyle
import cz.rzahr.aicoach.ui.theme.MetricStyle

/** Hlavička obrazovky: verzálkový nadtitulek, velký titulek, akce vpravo. */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    large: Boolean = onBack == null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(
                start = if (onBack != null) 4.dp else 20.dp,
                end = 12.dp,
                top = if (large) 14.dp else 6.dp,
                bottom = if (large) 10.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                title,
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(text.uppercase(), style = EyebrowStyle, color = color, modifier = modifier, maxLines = 1)
}

/** Karta s jemnou linkou místo stínu. */
@Composable
fun SignalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    border: Color? = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
        .clip(shape)
        .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
        .background(container)
        .then(
            if (onClick != null) {
                Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(contentPadding)
    Column(base, content = content)
}

/** Velké číslo s menší jednotkou na stejné účaří („1 420 kcal"). */
@Composable
fun MetricText(
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MetricStyle
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = style, color = color, modifier = Modifier.alignByBaseline())
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleSmall,
                color = color.copy(alpha = 0.6f),
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

/**
 * Vodorovný ukazatel plnění. Přes 100 % se dobarví celý a přetečení ukáže
 * tmavší koncovkou, aby „překročeno" bylo vidět i bez textu.
 */
@Composable
fun LinearMeter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 10.dp,
    marker: Float? = null,
    markerColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val animated by animateFloatAsState(
        targetValue = progress.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "meter"
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val filled = animated.coerceAtMost(1f) * size.width
        if (filled > 0f) {
            drawRoundRect(color, size = Size(filled.coerceAtLeast(size.height), size.height), cornerRadius = radius)
        }
        if (animated > 1f) {
            val overflow = ((animated - 1f) / animated).coerceIn(0.04f, 1f) * size.width
            drawRoundRect(
                Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(size.width - overflow, 0f),
                size = Size(overflow, size.height),
                cornerRadius = radius
            )
        }
        marker?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.let { m ->
            val x = m * size.width
            drawLine(markerColor, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
        }
    }
}

/**
 * Sloupcový graf týdne. [goal] kreslí přerušovanou čáru denního cíle,
 * dnešek je plnou barvou, ostatní dny tlumeně, budoucí dny jen jako stopa.
 */
@Composable
fun WeekBars(
    values: List<Int>,
    labels: List<String>,
    todayIndex: Int,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    goalColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            if (values.isEmpty()) return@Canvas
            val maxValue = maxOf(values.maxOrNull() ?: 0, goal, 1).toFloat() * 1.08f
            val gap = 10.dp.toPx()
            val barWidth = (size.width - gap * (values.size - 1)) / values.size
            val radius = CornerRadius(barWidth / 3.2f, barWidth / 3.2f)
            values.forEachIndexed { index, value ->
                val left = index * (barWidth + gap)
                drawRoundRect(track, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = radius)
                if (value > 0) {
                    val h = (size.height * (value / maxValue) * grow.value).coerceAtLeast(4.dp.toPx())
                    val barColor = when {
                        index == todayIndex -> color
                        index < todayIndex || todayIndex < 0 -> color.copy(alpha = 0.55f)
                        else -> color.copy(alpha = 0.25f)
                    }
                    drawRoundRect(
                        barColor,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius
                    )
                }
            }
            if (goal > 0) {
                val y = size.height - size.height * (goal / maxValue)
                drawLine(
                    goalColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == todayIndex) MaterialTheme.colorScheme.onSurface else labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Malý štítek (stav, kategorie, hodnocení). */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (filled) MaterialTheme.colorScheme.surface else color
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}

/** Kulaté tlačítko s ikonou a jemným obrysem (akce v hlavičkách). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surface,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/** Přepínač segmentů (období grafu, výběr menzy). */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Potvrzení nevratné akce (mazání fotky, historie chatu). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Svislá dělicí čárka mezi hodnotami v řádku. */
@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Plynulé objevení položek při prvním zobrazení obrazovky. */
@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 45L)
        alpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = (1f - alpha.value) * 24f
        }
    ) { content() }
}

/** Při stisku se prvek lehce zmenší – hmatová odezva bez stínů. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Klik s jemným „zmáčknutím". */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    pressScale(interaction).clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        onClick = onClick
    )
}
