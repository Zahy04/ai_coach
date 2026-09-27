package cz.rzahr.aicoach.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Týdenní kalorický cíl (issue #3): týden = kalendářní Po–Ne,
 * cíl = 7× denní. Umožňuje kompenzaci mezi dny
 * (míň jeden den, víc jiný).
 *
 * Čisté funkce – testovatelné bez Androidu.
 */
object WeeklyGoal {

    /** Pondělí týdne, do kterého patří [date]. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(DayOfWeek.MONDAY)

    /** Kolik dní do konce týdne zbývá včetně [date] (Po → 7, Ne → 1). */
    fun daysLeftIncludingToday(date: LocalDate): Int =
        DayOfWeek.SUNDAY.value - date.dayOfWeek.value + 1

    /** Kolik kcal denně v průměru zbývá do konce týdne. */
    fun perDayLeft(remainingKcal: Int, daysLeft: Int): Int =
        if (daysLeft > 0) remainingKcal / daysLeft else 0

    /** Kolik kcal by k danému dni mělo být snědeno při rovnoměrném tempu (x-tý den → x × denní). */
    fun expectedByDay(date: LocalDate, dailyGoal: Int): Int =
        date.dayOfWeek.value * dailyGoal

    /** Pozice značky tempa na týdenním koláči 0..1 (očekáváno / týdenní cíl). */
    fun paceFraction(date: LocalDate, dailyGoal: Int, weeklyGoal: Int): Float =
        if (weeklyGoal > 0) expectedByDay(date, dailyGoal).toFloat() / weeklyGoal else 0f

    /** Začátek týdne (pondělí 00:00) v epoch-milisekundách – pro DAO dotazy. */
    fun weekStartMillis(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        weekStart(
            java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        ).atStartOfDay(zone).toInstant().toEpochMilli()
}
