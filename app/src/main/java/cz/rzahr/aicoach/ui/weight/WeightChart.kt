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
import androidx.compose.ui.res.stringResource
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.theme.extendedColors
import cz.rzahr.aicoach.util.TrendMath
import cz.rzahr.aicoach.util.formatDate
import java.time.Instant
import java.util.Locale

@Composable
fun WeightChart(
    entries: List<WeightEntryEntity>,
    goalWeightKg: Double? = null,
    modifier: Modifier = Modifier
) {
    val sorted = remember(entries) { entries.sortedBy { it.timestamp } }

    if (sorted.isEmpty()) {
        Column(modifier.padding(vertical = 24.dp)) {
            Text("—", style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.weight_no_records),
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
                stringResource(R.string.weight_need_two),
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

    // Projekce k cíli: najdeme t > 1, kde trend protnutí cíl (max 2,5× horizontu dat)
    val projectionT = remember(trend, goalWeightKg) {
        val fit = trend
        val goal = goalWeightKg
        if (fit == null || goal == null) return@remember null
        var previousDiff = fit.evaluate(1.0) - goal
        if (kotlin.math.abs(previousDiff) < 0.01) return@remember null
        var t = 1.0
        while (t <= 2.5) {
            t += 0.02
            val diff = fit.evaluate(t) - goal
            if (diff * previousDiff <= 0) return@remember t
            previousDiff = diff
        }
        null
    }
    val domainMax = (projectionT?.let { maxOf(1.15, it * 1.04) }) ?: 1.0
    val projectionDate = remember(projectionT, sorted) {
        projectionT?.let { t ->
            val firstMs = sorted.first().timestamp
            val spanMs = sorted.last().timestamp - firstMs
            Instant.ofEpochMilli(firstMs + (spanMs * t).toLong()).toEpochMilli()
        }
    }

    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(sorted, goalWeightKg) {
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
            val domain = domainMax.toFloat()

            fun xPosRaw(index: Int): Float =
                padLeft + plotWidth * (index.toFloat() / (n - 1)) * (1f / domain)
            fun xPosT(t: Float): Float = padLeft + plotWidth * (t / domain)
            fun yPos(value: Double): Float =
                padTop + plotHeight * (1f - ((value - rawMin) / rawRange).toFloat())

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

            if (maValues.isNotEmpty()) {
                val visibleCount = (n * progress).toInt().coerceAtLeast(2).coerceAtMost(n)
                val path = Path()
                for (index in 0 until visibleCount) {
                    val point = Offset(xPosRaw(index), yPos(maValues[index]).coerceIn(padTop, padTop + plotHeight))
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

            if (trend != null && progress > 0f) {
                val steps = 80
                val visibleSteps = (steps * progress).toInt().coerceAtLeast(2)
                val line = Path()
                for (step in 0..visibleSteps) {
                    val t = step.toDouble() / steps
                    val point = Offset(xPosT(t.toFloat()), yPos(trend.evaluate(t)).coerceIn(padTop, padTop + plotHeight))
                    if (step == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
                }
                drawPath(line, trendColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

                if (progress > 0.98f) {
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(xPosT(visibleSteps / steps.toFloat()), padTop + plotHeight)
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

            // projekce k cíli — přerušovaná čára za posledním bodem
            if (projectionT != null && progress > 0.98f && trend != null) {
                val projPath = Path()
                var started = false
                var t = 1.0
                while (t <= projectionT + 1e-9) {
                    val point = Offset(xPosT(t.toFloat()), yPos(trend.evaluate(t)).coerceIn(padTop, padTop + plotHeight))
                    if (!started) {
                        projPath.moveTo(point.x, point.y)
                        started = true
                    } else {
                        projPath.lineTo(point.x, point.y)
                    }
                    t += 0.04
                }
                drawPath(
                    projPath,
                    trendColor.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 3f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                )
                val goalPoint = Offset(xPosT(projectionT.toFloat()), yPos(goalWeightKg!!))
                drawCircle(trendColor.copy(alpha = 0.9f), radius = 7f, center = goalPoint, style = Stroke(width = 3f))
                drawLine(
                    trendColor.copy(alpha = 0.5f),
                    start = Offset(padLeft, yPos(goalWeightKg)),
                    end = Offset(size.width - padX, yPos(goalWeightKg)),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )
            }

            val visiblePoints = (n * progress).toInt().coerceAtLeast(1)
            for (index in 0 until visiblePoints.coerceAtMost(n)) {
                drawCircle(rawColor, radius = 4f, center = Offset(xPosRaw(index), yPos(sorted[index].weightKg)))
            }

            val lastPoint = Offset(xPosRaw(n - 1), yPos(sorted.last().weightKg))
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
                projectionDate?.formatDate() ?: sorted.last().timestamp.formatDate(),
                style = MaterialTheme.typography.labelSmall,
                color = if (projectionDate != null) trendColor else labelColor,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        if (projectionT != null && goalWeightKg != null && projectionDate != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    " Při současném tempu ≈ ${projectionDate.formatDate()} dosáhneš " +
                        "${String.format(Locale.forLanguageTag("cs"), "%.1f", goalWeightKg)} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LegendItem(color = rawColor, label = stringResource(R.string.legend_measurements), isDot = true)
            LegendItem(color = trendColor, label = stringResource(R.string.legend_trend), isDot = false)
            LegendItem(color = maColor, label = stringResource(R.string.legend_ma7), isDot = false)
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
