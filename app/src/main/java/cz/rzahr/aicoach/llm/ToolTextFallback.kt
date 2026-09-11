package cz.rzahr.aicoach.llm

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Záchranná síť pro případ, kdy model místo skutečného function callingu
 * napíše tool jako prostý text, např.:
 * `Zapisuji: log_food(name="toastový chléb", quantity_g=180, calories=450, ...)`.
 *
 * Long-term strategie:
 * - primární cesta je vždy skutečný functionCall (ToolConfig AUTO, prompt, teplota),
 * - když model přesto simuluje textem, tento helper pseudo-volání
 *   (a) spolehlivě najde,
 *   (b) odstraní zobrazeného textu (sanitizace), aby se chat nezanášel
 *   a historie neučila model dalším simulacím,
 *   (c) při vysoké jistotě převede na skutečný [FunctionCall] a ten se
 *   normálně spustí přes [ToolExecutor] (rescue) – data se tedy zapíšou.
 *
 * Čistý Kotlin bez Android závislostí – kvůli jednotkovým testům.
 */
object ToolTextFallback {

    val knownTools: Set<String> = setOf(
        ToolSpecs.SAVE_WEIGHT,
        ToolSpecs.LOG_FOOD,
        ToolSpecs.LOG_WATER,
        ToolSpecs.LOG_WORKOUT,
        ToolSpecs.SAVE_FACT,
        ToolSpecs.DELETE_FACT
    )

    private val toolNamePattern = knownTools.joinToString("|") { Regex.escape(it) }
    private val toolOpenRegex = Regex("\\b($toolNamePattern)\\s*\\(")

    data class PseudoCall(
        val name: String,
        val argsText: String,
        val start: Int,
        val endExclusive: Int
    )

    /** Najde všechna `nástroj(...)` v textu, respektuje závorky v uvozovkách. */
    fun extractPseudoCalls(text: String): List<PseudoCall> {
        val out = mutableListOf<PseudoCall>()
        for (match in toolOpenRegex.findAll(text)) {
            val name = match.groupValues[1]
            val openParen = match.value.lastIndexOf('(').let { match.range.first + it }
            val close = findMatchingCloseParen(text, openParen) ?: continue
            val argsText = text.substring(openParen + 1, close)
            out.add(PseudoCall(name, argsText, match.range.first, close + 1))
        }
        return out
    }

    fun containsPseudoCall(text: String): Boolean = toolOpenRegex.containsMatchIn(text)

    /**
     * Odstraní pseudo-volání z textu pro zobrazení a historii.
     * Fenced code bloky (```...```), které obsahují jen tool volání, odstraní celé,
     * aby nezbyly prázdné rámečky.
     */
    fun sanitizeModelText(text: String): String {
        if (text.isBlank() || !containsPseudoCall(text)) return text
        var out = stripToolCodeFences(text)
        // Odstraňovat odzadu, aby seděly indexy.
        extractPseudoCalls(out).sortedByDescending { it.start }.forEach { call ->
            out = out.removeRange(call.start, call.endExclusive)
        }
        return out
            // Zbylé osiřelé uvozovky/závorky po odstranění – jen jemný úklid.
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("(?m)^[ \\t]*,[ \\t]*$"), "")
            .trim()
    }

    /** Převede pseudo-volání na skutečný FunctionCall, nebo null při nízké jistotě. */
    fun toFunctionCall(call: PseudoCall): FunctionCall? {
        if (call.name !in knownTools) return null
        val args = parseArgs(call.argsText) ?: return null
        val functionCall = FunctionCall(name = call.name, args = args)
        return if (isHighConfidence(functionCall)) functionCall else null
    }

    /** Všechna vysoce jistá pseudo-volání v textu jako spustitelné FunctionCally. */
    fun extractExecutableCalls(text: String): List<FunctionCall> =
        extractPseudoCalls(text).mapNotNull { toFunctionCall(it) }

    // ---------- internals ----------

    private fun stripToolCodeFences(text: String): String {
        // ```...``` bloky (včetně ```json). Pokud vnitřek obsahuje tool pattern, smaž celý blok.
        val fence = Regex("```[a-zA-Z]*\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return fence.replace(text) { m ->
            val inner = m.groupValues[1]
            if (containsPseudoCall(inner)) "" else m.value
        }
    }

    private fun findMatchingCloseParen(text: String, openIndex: Int): Int? {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var escape = false
        var i = openIndex
        while (i < text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                i++
                continue
            }
            when {
                c == '\\' && (inSingle || inDouble) -> escape = true
                c == '"' && !inSingle -> inDouble = !inDouble
                c == '\'' && !inDouble -> inSingle = !inSingle
                !inSingle && !inDouble -> when (c) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return null
    }

    /** Parsuje `k=v, k2="..."` do JsonObject. Vrací null, když nelze nic rozumného vytěžit. */
    fun parseArgs(argsText: String): JsonObject? {
        if (argsText.isBlank()) return buildJsonObject { }
        val pairs = splitTopLevel(argsText, ',')
        var any = false
        val obj = buildJsonObject {
            pairs.forEach { segment ->
                val eq = indexOfTopLevelEquals(segment) ?: return@forEach
                val rawKey = segment.substring(0, eq).trim().trim('"', '\'', '„', '“')
                if (rawKey.isEmpty()) return@forEach
                val rawValue = segment.substring(eq + 1).trim().trimEnd(',', ' ')
                if (rawValue.isEmpty() || rawValue.equals("null", true)) return@forEach
                putJsonValue(rawKey, rawValue)
                any = true
            }
        }
        if (!any) return null
        // Prázdný objekt je validní jen pro log_water (default 250 ml).
        return obj
    }

    private fun splitTopLevel(text: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escape = false
        var depthParen = 0
        var depthBracket = 0
        var depthBrace = 0
        for (c in text) {
            if (escape) {
                cur.append(c)
                escape = false
                continue
            }
            when {
                c == '\\' && (inSingle || inDouble) -> {
                    cur.append(c)
                    escape = true
                }
                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    cur.append(c)
                }
                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    cur.append(c)
                }
                !inSingle && !inDouble -> when {
                    c == '(' -> {
                        depthParen++
                        cur.append(c)
                    }
                    c == ')' -> {
                        depthParen--
                        cur.append(c)
                    }
                    c == '[' -> {
                        depthBracket++
                        cur.append(c)
                    }
                    c == ']' -> {
                        depthBracket--
                        cur.append(c)
                    }
                    c == '{' -> {
                        depthBrace++
                        cur.append(c)
                    }
                    c == '}' -> {
                        depthBrace--
                        cur.append(c)
                    }
                    c == delimiter && depthParen == 0 && depthBracket == 0 && depthBrace == 0 -> {
                        out.add(cur.toString())
                        cur.clear()
                    }
                    else -> cur.append(c)
                }
                else -> cur.append(c)
            }
        }
        out.add(cur.toString())
        return out
    }

    private fun indexOfTopLevelEquals(segment: String): Int? {
        var inSingle = false
        var inDouble = false
        var escape = false
        segment.forEachIndexed { i, c ->
            if (escape) {
                escape = false
                return@forEachIndexed
            }
            when {
                c == '\\' && (inSingle || inDouble) -> escape = true
                c == '"' && !inSingle -> inDouble = !inDouble
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '=' && !inSingle && !inDouble -> return i
            }
        }
        return null
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonValue(
        key: String,
        rawValue: String
    ) {
        val v = rawValue.trim()
        // Quoted string (", ', české „ “).
        if (v.length >= 2 && ((v.startsWith('"') && v.endsWith('"')) ||
                    (v.startsWith('\'') && v.endsWith('\'')) ||
                    (v.startsWith('„') && (v.endsWith('“') || v.endsWith('"')))
                    )
        ) {
            val inner = v.substring(1, v.length - 1)
                .replace("\\\"", "\"")
                .replace("\\'", "'")
            put(key, inner)
            return
        }
        if (v.equals("true", true)) {
            put(key, true)
            return
        }
        if (v.equals("false", true)) {
            put(key, false)
            return
        }
        // Čísla: model občas píše desetinnou čárku (4,5) – bereme jako tečku,
        // ale jen ve tvaru číslice,číslice, aby se nerozbily seznamy.
        val normalized = if (Regex("^\\d+,\\d+$").matches(v)) v.replace(',', '.') else v
        normalized.toLongOrNull()?.let { put(key, it); return }
        normalized.toDoubleOrNull()?.let { put(key, it); return }
        // Fallback: holý řetězec bez uvozovek (např. name=ovesna_kase).
        put(key, v.trim('"', '\'', '„', '“'))
    }

    /** Spustit rescue jen při vysoké jistotě, ať nikdy nezapisujeme halucinace. */
    fun isHighConfidence(call: FunctionCall): Boolean {
        val args: JsonObject = call.args ?: JsonObject(emptyMap())
        fun str(key: String): String? =
            args[key]?.let { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
                ?.takeIf { it.isNotBlank() }

        return when (call.name.substringAfterLast(':')) {
            ToolSpecs.SAVE_WEIGHT -> {
                val num = args["weight_kg"]?.let { (it as? JsonPrimitive)?.content }
                    ?.replace(',', '.')?.toDoubleOrNull()
                num != null && num > 0 && num < 500
            }
            ToolSpecs.LOG_FOOD -> !str("name").isNullOrBlank()
            ToolSpecs.LOG_WATER -> {
                val amount = args["amount_ml"]?.let { (it as? JsonPrimitive)?.content }?.toIntOrNull()
                amount == null || (amount > 0 && amount <= 5000)
            }
            ToolSpecs.LOG_WORKOUT -> !str("name").isNullOrBlank()
            ToolSpecs.SAVE_FACT -> {
                val cat = str("category")?.uppercase()
                val okCat = cat in setOf("PREFERENCE", "DISLIKE", "DIET", "GOAL", "HABIT", "HEALTH", "OTHER")
                okCat && !str("content").isNullOrBlank()
            }
            ToolSpecs.DELETE_FACT -> {
                args["fact_id"]?.let { (it as? JsonPrimitive)?.content }?.toLongOrNull() != null
            }
            else -> false
        }
    }
}
