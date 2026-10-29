package cz.rzahr.aicoach.util

import java.util.Locale

/** 14000 → „14 000" (cs) / „14,000" (en) podle jazyka aplikace. */
fun Int.grouped(): String = String.format(Locale.forLanguageTag(Locales.currentTag()), "%,d", this)

/** 78.45 → „78,5" (cs) / „78.5" (en). */
fun Double.oneDecimal(): String = String.format(Locale.forLanguageTag(Locales.currentTag()), "%.1f", this)
