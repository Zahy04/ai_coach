package cz.rzahr.aicoach.ui.weight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.WeightEntryEntity
import cz.rzahr.aicoach.ui.theme.Manrope
import cz.rzahr.aicoach.ui.theme.dataColors
import cz.rzahr.aicoach.util.TrendMath
import cz.rzahr.aicoach.util.formatDate
import cz.rzahr.aicoach.util.oneDecimal

/**
 * Graf vývoje váhy: body měření, 7denní průměr a trend (kvadratický, při
 * nevhodném tvaru lineární). Všechno na jedné časové ose – nepravidelná
 * vážení se kreslí tam, kam časově patří, ne rovnoměrně podle pořadí.
 */
@Composable
fun WeightChart(
    entries: List<WeightEntryEntity>,
    goalWeightKg: Double? = null,
    modifier: Modifier = Modifier
) {
    val sorted = remember(entries) { entries.sortedBy { it.timestamp } }
    if (sorted.size < 2) {
        Text(
            stringResource(if (sorted.isEmpty()) R.string.weight_no_records else R.string.weight_need_two),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 32.dp)
        )
        return
    }

    val pointColor = MaterialTheme.colorScheme.onSurface
    val trendColor = MaterialTheme.colorScheme.primary
    val maColor = dataColors().protein
    val goalColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val rawValues = remember(sorted) { sorted.map { it.weightKg } }
    val xs = remember(sorted) { TrendMath.timeFractions(sorted.map { it.timestamp }) }
    val rawMin = rawValues.min()
    val rawMax = rawValues.max()
    val rawRange = (rawMax - rawMin).coerceAtLeast(0.5)
    val lower = rawMin - rawRange * 0.12
    val range = rawRange * 1.24

    val trend = remember(sorted) {
        val points = sorted.map { it.timestamp.toDouble() to it.weightKg }
        val lowerBound = rawMin - rawRange * 0.25
        val upperBound = rawMax + rawRange * 0.25
        fun inBounds(fit: TrendMath.FitResult): Boolean = (0..60).all { fit.evaluate(it / 60.0) in lowerBound..upperBound }
        val quadratic = if (points.size >= 5) TrendMath.fitQuadratic(points) else null
        val linear = TrendMath.fitLinear(points)
        when {
            quadratic != null && inBounds(quadratic) -> quadratic
            linear != null && inBounds(linear) -> linear
            else -> null
        }
    }
    val maValues = remember(sorted) { TrendMath.movingAverage(rawValues, 7) }

    // Kdy trend protne cíl (jen pro text – extrapolace se nekreslí).
    val projectionDate = remember(trend, goalWeightKg, sorted) {
        val fit = trend ?: return@remember null
        val goal = goalWeightKg ?: return@remember null
        var previousDiff = fit.evaluate(1.0) - goal
        if (kotlin.math.abs(previousDiff) < 0.01) return@remember null
        var t = 1.0
        var hit: Double? = null
        while (t <= 2.5 && hit == null) {
            t += 0.02
            val diff = fit.evaluate(t) - goal
            if (diff * previousDiff <= 0) hit = t
            previousDiff = diff
        }
        hit?.let {
            val first = sorted.first().timestamp
            first + ((sorted.last().timestamp - first) * it).toLong()
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(sorted, goalWeightKg) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
    }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            val padLeft = 40.dp.toPx()
            val padRight = 8.dp.toPx()
            val padTop = 10.dp.toPx()
            val padBottom = 8.dp.toPx()
            val plotWidth = size.width - padLeft - padRight
            val plotHeight = size.height - padTop - padBottom
            val p = progress.value

            fun x(t: Double): Float = padLeft + plotWidth * t.toFloat()
            fun y(value: Double): Float =
                (padTop + plotHeight * (1f - ((value - lower) / range).toFloat())).coerceIn(padTop, padTop + plotHeight)

            // Mřížka se třemi popisky.
            listOf(0.0, 0.5, 1.0).forEach { fraction ->
                val value = rawMin + rawRange * fraction
                val yy = y(value)
                drawLine(gridColor, Offset(padLeft, yy), Offset(size.width - padRight, yy), strokeWidth = 1.dp.toPx())
                val measured = textMeasurer.measure(
                    value.oneDecimal(),
                    TextStyle(fontSize = 10.sp, color = labelColor, fontFamily = Manrope)
                )
                drawText(measured, topLeft = Offset(0f, (yy - measured.size.height / 2f).coerceAtLeast(0f)))
            }

            // Cíl jako přerušovaná vodorovná čára, jen když je v rozsahu osy.
            if (goalWeightKg != null && goalWeightKg in lower..(lower + range)) {
                val gy = y(goalWeightKg)
                drawLine(
                    goalColor,
                    Offset(padLeft, gy),
                    Offset(size.width - padRight, gy),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                )
            }

            // Trend s jemnou výplní.
            if (trend != null) {
                val steps = 80
                val visible = (steps * p).toInt().coerceAtLeast(1)
                val line = Path()
                for (step in 0..visible) {
                    val t = step.toDouble() / steps
                    val point = Offset(x(t), y(trend.evaluate(t)))
                    if (step == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(visible.toDouble() / steps), padTop + plotHeight)
                    lineTo(padLeft, padTop + plotHeight)
                    close()
                }
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        listOf(trendColor.copy(alpha = 0.18f), Color.Transparent),
                        startY = padTop,
                        endY = padTop + plotHeight
                    )
                )
                drawPath(line, trendColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }

            // 7denní průměr.
            val maPath = Path()
            maValues.forEachIndexed { index, value ->
                if (xs[index] > p) return@forEachIndexed
                val point = Offset(x(xs[index]), y(value))
                if (index == 0) maPath.moveTo(point.x, point.y) else maPath.lineTo(point.x, point.y)
            }
            drawPath(
                maPath,
                maColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            )

            // Měření.
            rawValues.forEachIndexed { index, value ->
                if (xs[index] <= p) {
                    drawCircle(pointColor, radius = 3.dp.toPx(), center = Offset(x(xs[index]), y(value)))
                }
            }
            if (p >= 1f) {
                val last = Offset(x(xs.last()), y(rawValues.last()))
                drawCircle(trendColor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = last)
                drawCircle(trendColor, radius = 5.dp.toPx(), center = last)
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sorted.first().timestamp.formatDate(), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(sorted.last().timestamp.formatDate(), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Legend(pointColor, stringResource(R.string.legend_measurements), dot = true)
            Legend(trendColor, stringResource(R.string.legend_trend), dot = false)
            Legend(maColor, stringResource(R.string.legend_ma7), dot = false)
        }

        if (projectionDate != null && goalWeightKg != null) {
            Text(
                stringResource(R.string.weight_projection, goalWeightKg.oneDecimal(), projectionDate.formatDate()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, dot: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = if (dot) 8.dp else 14.dp, height = if (dot) 8.dp else 3.dp)
                .background(color, CircleShape)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
