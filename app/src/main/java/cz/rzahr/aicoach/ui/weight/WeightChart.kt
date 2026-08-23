package cz.rzahr.aicoach.ui.weight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.ui.theme.extendedColors
import cz.rzahr.aicoach.util.TrendMath
import cz.rzahr.aicoach.util.formatDate
import java.util.Locale

@Composable
fun WeightChart(
    entries: List<WeightEntryEntity>,
    modifier: Modifier = Modifier
) {
    val sorted = remember(entries) { entries.sortedBy { it.timestamp } }

    if (sorted.isEmpty()) {
        Column(modifier.padding(vertical = 24.dp)) {
            Text("—", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Zatím žádné záznamy váhy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    if (sorted.size == 1) {
        Column(modifier.padding(vertical = 24.dp)) {
            Text(
                String.format(Locale.forLanguageTag("cs"), "%.1f kg", sorted.first().weightKg),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Pro graf jsou potřeba alespoň 2 měření.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val ext = extendedColors()
    val rawColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trendColor = MaterialTheme.colorScheme.primary
    val maColor = ext.protein
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val rawValues = remember(sorted) { sorted.map { it.weightKg } }
    val rawMin = rawValues.min()
    val rawMax = rawValues.max()
    val rawRange = (rawMax - rawMin).coerceAtLeast(0.5)

    val trend = remember(sorted) {
        val points = sorted.map { it.timestamp.toDouble() to it.weightKg }
        val lowerBound = rawMin - rawRange * 0.25
        val upperBound = rawMax + rawRange * 0.25
        fun samplesInBounds(fit: TrendMath.FitResult): Boolean =
            (0..60).all { step ->
                val v = fit.evaluate(step / 60.0)
                v in lowerBound..upperBound
            }
        val quadratic = if (points.size >= 5) TrendMath.fitQuadratic(points) else null
        val linear = TrendMath.fitLinear(points)
        when {
            quadratic != null && samplesInBounds(quadratic) -> quadratic
            linear != null && samplesInBounds(linear) -> linear
            else -> null
        }
    }
    val maValues = remember(sorted) { TrendMath.movingAverage(rawValues, 7) }

    // animace "vykreslení" grafu po načtení / změně dat
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(sorted) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(1f, animationSpec = tween(850, easing = FastOutSlowInEasing))
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by pulse.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseR"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(800)),
        label = "pulseA"
    )

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val padLeft = 46f
            val padX = 16f
            val padTop = 12f
            val padBottom = 8f
            val plotWidth = size.width - padLeft - padX
            val plotHeight = size.height - padTop - padBottom
            val n = sorted.size
            val progress = drawProgress.value

            fun xPos(index: Int): Float = padLeft + plotWidth * index / (n - 1)
            fun yPos(value: Double): Float =
                padTop + plotHeight * (1f - ((value - rawMin) / rawRange).toFloat())

            fun yPosClamped(value: Double): Float =
                yPos(value).coerceIn(padTop, padTop + plotHeight)

            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = padTop + plotHeight * fraction
                drawLine(gridColor.copy(alpha = 0.6f), Offset(padLeft, y), Offset(size.width - padX, y), strokeWidth = 1f)
                val gridValue = rawMax - rawRange * fraction
                val measured = textMeasurer.measure(
                    String.format(Locale.forLanguageTag("cs"), "%.1f", gridValue),
                    TextStyle(fontSize = 10.sp, color = labelColor)
                )
                drawText(
                    measured,
                    topLeft = Offset(0f, (y - measured.size.height / 2).coerceAtLeast(0f))
                )
            }

            // klouzavý průměr — přerušovaná čára, animovaně se objevuje
            if (maValues.isNotEmpty()) {
                val visibleCount = (n * progress).toInt().coerceAtLeast(2).coerceAtMost(n)
                val path = Path()
                for (index in 0 until visibleCount) {
                    val point = Offset(xPos(index), yPosClamped(maValues[index]))
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path,
                    maColor,
                    style = Stroke(
                        width = 3f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                )
            }

            // kvadratický/lineární trend s gradientní výplní
            if (trend != null && progress > 0f) {
                val steps = 80
                val visibleSteps = (steps * progress).toInt().coerceAtLeast(2)
                val line = Path()
                for (step in 0..visibleSteps) {
                    val t = step.toDouble() / steps
                    val x = padLeft + plotWidth * t.toFloat()
                    val y = yPosClamped(trend.evaluate(t))
                    if (step == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                drawPath(line, trendColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

                if (progress > 0.98f) {
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(padLeft + plotWidth * visibleSteps / steps.toFloat(), padTop + plotHeight)
                        lineTo(padLeft, padTop + plotHeight)
                        close()
                    }
                    drawPath(
                        fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(trendColor.copy(alpha = 0.22f), Color.Transparent),
                            startY = padTop,
                            endY = padTop + plotHeight
                        )
                    )
                }
            }

            // měření — postupně problikávají
            val visiblePoints = (n * progress).toInt().coerceAtLeast(1)
            for (index in 0 until visiblePoints.coerceAtMost(n)) {
                drawCircle(
                    rawColor,
                    radius = 4f,
                    center = Offset(xPos(index), yPos(sorted[index].weightKg))
                )
            }

            // pulzující bod na nejnovějším měření
            val lastPoint = Offset(xPos(n - 1), yPos(sorted.last().weightKg))
            drawCircle(trendColor.copy(alpha = pulseAlpha), radius = pulseRadius, center = lastPoint)
            drawCircle(trendColor, radius = 5.5f, center = lastPoint)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                sorted.first().timestamp.formatDate(),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(start = 46.dp)
            )
            Text(
                sorted.last().timestamp.formatDate(),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LegendItem(color = rawColor, label = "Měření", isDot = true)
            LegendItem(color = trendColor, label = "Trend", isDot = false)
            LegendItem(color = maColor, label = "7denní průměr", isDot = false)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, isDot: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = if (isDot) 8.dp else 16.dp, height = 8.dp)
                .background(color, CircleShape)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
