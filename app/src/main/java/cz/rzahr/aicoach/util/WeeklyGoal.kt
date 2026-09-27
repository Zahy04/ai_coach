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

    /** Začátek týdne (pondělí 00:00) v epoch-milisekundách – pro DAO dotazy. */
    fun weekStartMillis(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        weekStart(
            java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        ).atStartOfDay(zone).toInstant().toEpochMilli()
}
