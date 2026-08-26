package cz.rzahr.aicoach.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Aktuální jazyk aplikace — respektuje ruční přepínač v Nastavení
 * (AppCompatDelegate), případně spadne na systémový locale.
 */
object Locales {

    fun currentTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val tag = appLocales.takeIf { !it.isEmpty }?.get(0)?.language
        if (!tag.isNullOrBlank()) return tag.lowercase()
        return Locale.getDefault().language.lowercase()
    }

    fun isCzech(): Boolean = currentTag() == "cs"
}
