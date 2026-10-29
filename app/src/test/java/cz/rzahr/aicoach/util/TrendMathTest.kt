package cz.rzahr.aicoach.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendMathTest {

    @Test
    fun `nepravidelna mereni lezi na ose podle casu`() {
        val day = 24L * 60 * 60 * 1000
        // Čtyři vážení v prvním týdnu, páté po měsíci: dřív se kreslila rovnoměrně
        // (0, 0.25, 0.5, 0.75, 1), zatímco trend běžel po časové ose.
        val fractions = TrendMath.timeFractions(listOf(0, day, 2 * day, 3 * day, 30 * day))
        assertEquals(listOf(0.0, 1 / 30.0, 2 / 30.0, 3 / 30.0, 1.0), fractions)
    }

    @Test
    fun `stejny cas a jediny bod nepadaji`() {
        assertEquals(listOf(0.0, 0.5, 1.0), TrendMath.timeFractions(listOf(5, 5, 5)))
        assertEquals(listOf(0.0), TrendMath.timeFractions(listOf(5)))
        assertEquals(emptyList<Double>(), TrendMath.timeFractions(emptyList()))
    }
}
