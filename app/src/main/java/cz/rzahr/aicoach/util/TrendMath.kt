package cz.rzahr.aicoach.util

import kotlin.math.abs
import kotlin.math.pow

object TrendMath {

    data class FitResult(val a: Double, val b: Double, val c: Double) {
        fun evaluate(xNormalized: Double): Double =
            a * xNormalized * xNormalized + b * xNormalized + c
    }

    fun fitQuadratic(points: List<Pair<Double, Double>>): FitResult? {
        if (points.size < 3) return null
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val rangeX = maxX - minX
        if (rangeX <= 0.0) return null

        val xs = points.map { (it.first - minX) / rangeX }
        val ys = points.map { it.second }

        var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
        var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
        xs.forEachIndexed { i, x ->
            val y = ys[i]
            s1 += x
            s2 += x * x
            s3 += x.pow(3)
            s4 += x.pow(4)
            t0 += y
            t1 += y * x
            t2 += y * x * x
        }
        s0 = xs.size.toDouble()

        val matrix = arrayOf(
            doubleArrayOf(s0, s1, s2, t0),
            doubleArrayOf(s1, s2, s3, t1),
            doubleArrayOf(s2, s3, s4, t2)
        )
        val solution = solve3(matrix) ?: return null
        return FitResult(solution[0], solution[1], solution[2])
    }

    fun fitLinear(points: List<Pair<Double, Double>>): FitResult? {
        if (points.size < 2) return null
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val rangeX = maxX - minX
        if (rangeX <= 0.0) return null

        val xs = points.map { (it.first - minX) / rangeX }
        val ys = points.map { it.second }
        val n = xs.size.toDouble()

        var sx = 0.0; var sxx = 0.0; var sy = 0.0; var sxy = 0.0
        xs.forEachIndexed { i, x ->
            val y = ys[i]
            sx += x
            sxx += x * x
            sy += y
            sxy += y * x
        }
        val denominator = n * sxx - sx * sx
        if (abs(denominator) < 1e-12) return null
        val b = (n * sxy - sx * sy) / denominator
        val c = (sy - b * sx) / n
        return FitResult(0.0, b, c)
    }

    fun movingAverage(values: List<Double>, window: Int): List<Double> {
        if (window <= 1 || values.isEmpty()) return values
        return values.indices.map { i ->
            val from = maxOf(0, i - window + 1)
            var sum = 0.0
            for (k in from..i) sum += values[k]
            sum / (i - from + 1)
        }
    }

    private fun solve3(m: Array<DoubleArray>): DoubleArray? {
        val n = 3
        val a = Array(n) { m[it].clone() }
        for (col in 0 until n) {
            var pivotRow = col
            for (row in col + 1 until n) {
                if (abs(a[row][col]) > abs(a[pivotRow][col])) pivotRow = row
            }
            if (abs(a[pivotRow][col]) < 1e-12) return null
            if (pivotRow != col) {
                val tmp = a[pivotRow]
                a[pivotRow] = a[col]
                a[col] = tmp
            }
            for (row in col + 1 until n) {
                val factor = a[row][col] / a[col][col]
                for (k in col..n) a[row][k] -= factor * a[col][k]
            }
        }
        val result = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = a[row][n]
            for (k in row + 1 until n) sum -= a[row][k] * result[k]
            result[row] = sum / a[row][row]
        }
        return result
    }
}
