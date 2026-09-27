package cz.rzahr.aicoach.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testy týdenního kalorického cíle (issue #3).
 * Týden = kalendářní Po–Ne. 27. 9. 2026 je neděle, 21. 9. 2026 pondělí.
 */
class WeeklyGoalTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private val wednesday = LocalDate.of(2026, 9, 23)
    private val sunday = LocalDate.of(2026, 9, 27)

    @Test
    fun `weekStart vraci pondeli daneho tydne`() {
        assertEquals(monday, WeeklyGoal.weekStart(monday))
        assertEquals(monday, WeeklyGoal.weekStart(wednesday))
        assertEquals(monday, WeeklyGoal.weekStart(sunday))
    }

    @Test
    fun `daysLeftIncludingToday pocita dny do nedele vcetne dneska`() {
        assertEquals(7, WeeklyGoal.daysLeftIncludingToday(monday))
        assertEquals(5, WeeklyGoal.daysLeftIncludingToday(wednesday))
        assertEquals(1, WeeklyGoal.daysLeftIncludingToday(sunday))
    }

    @Test
    fun `perDayLeft deli zbytek poctem dni`() {
        assertEquals(1000, WeeklyGoal.perDayLeft(7000, 7))
        assertEquals(700, WeeklyGoal.perDayLeft(3500, 5))
        assertEquals(0, WeeklyGoal.perDayLeft(0, 3))
        assertEquals(0, WeeklyGoal.perDayLeft(3500, 0))
    }

    @Test
    fun `expectedByDay je x-ty den krat denni cil`() {
        assertEquals(2000, WeeklyGoal.expectedByDay(monday, 2000))
        assertEquals(6000, WeeklyGoal.expectedByDay(wednesday, 2000))
        assertEquals(14000, WeeklyGoal.expectedByDay(sunday, 2000))
    }

    @Test
    fun `paceFraction je ocekavano lomeno tydenni cil`() {
        assertEquals(3f / 7f, WeeklyGoal.paceFraction(wednesday, 2000, 14000), 0.001f)
        assertEquals(1f, WeeklyGoal.paceFraction(sunday, 2000, 14000), 0.001f)
        assertEquals(0f, WeeklyGoal.paceFraction(monday, 2000, 0), 0.001f)
    }

    @Test
    fun `weekStartMillis je pondeli 00_00 v dane zone`() {
        val zone = ZoneId.of("Europe/Prague")
        // Středa 12:00 → pondělí 00:00 téhož týdne.
        val wednesdayNoon = wednesday.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, WeeklyGoal.weekStartMillis(wednesdayNoon, zone))
    }
}
