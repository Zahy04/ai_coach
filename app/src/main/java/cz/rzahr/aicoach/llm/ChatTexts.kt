package cz.rzahr.aicoach.llm

import cz.rzahr.aicoach.util.Locales

/** Krátké texty, které klienti vkládají přímo do odpovědi (bez přístupu ke Context). */
internal object ChatTexts {

    fun done(): String = if (Locales.isCzech()) "Hotovo. ✅" else "Done. ✅"

    fun truncated(): String =
        if (Locales.isCzech()) "\n\n_(Odpověď přerušil výpadek spojení – může být neúplná.)_"
        else "\n\n_(The reply was cut off by a network error – it may be incomplete.)_"
}
