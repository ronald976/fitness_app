package com.fitness.app.domain.parsing

/**
 * A single parsed set token like "80x8", "92.5x8f", or reps-only "x8" / "x8f".
 * [weightKg] is null when the token omits weight (reps-only) — callers fill it in via
 * [SetTokenParser.resolveWeights].
 */
data class SetToken(
    val weightKg: Double?,
    val reps: Int,
    val toFailure: Boolean
)

/**
 * Pure parsing for the workout quick-entry grammars, shared by the two call sites:
 *  - the per-exercise Quick log dialog (a line of set tokens) via [parseQuickLog]
 *  - the mid-workout quick-add field (name + optional trailing tokens) via [parseQuickAdd]
 *
 * Kept free of Android/Room deps so it can be unit-tested in isolation.
 */
object SetTokenParser {

    // Weight is optional; reps required; optional trailing "f" = to failure.
    private val TOKEN_RE = Regex("""^(\d+(?:\.\d+)?)?x(\d+)(f?)$""", RegexOption.IGNORE_CASE)

    /** Upper bound on "xN" placeholder sets — a typo like "x40" shouldn't spray 40 rows. */
    private const val MAX_PLACEHOLDER_SETS = 20

    /** Parse one whitespace-free token, or null if it isn't a set token. */
    fun parseToken(token: String): SetToken? {
        val m = TOKEN_RE.matchEntire(token.trim()) ?: return null
        val reps = m.groupValues[2].toIntOrNull() ?: return null
        val weight = m.groupValues[1].ifEmpty { null }?.toDoubleOrNull()
        return SetToken(weight, reps, m.groupValues[3].equals("f", ignoreCase = true))
    }

    /** Parse a whitespace-separated line of set tokens (Quick log dialog). Non-tokens are dropped. */
    fun parseLine(text: String): List<SetToken> =
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull(::parseToken)

    sealed interface QuickLog {
        /** A lone reps-only token ("x4") — N sets ticked off with no weight/reps recorded. */
        data class Placeholder(val count: Int) : QuickLog
        /** Real sets to log, one per token. */
        data class Sets(val tokens: List<SetToken>) : QuickLog
    }

    /**
     * Quick log dialog grammar. A lone reps-only token means "I did N sets, don't ask me for
     * numbers" — the same shorthand the quick-add field uses ("abs x3"). Two or more tokens are
     * always real sets, so "x8 x8" still means two 8-rep sets at the running weight.
     */
    fun parseQuickLog(text: String): QuickLog? {
        val tokens = parseLine(text)
        if (tokens.isEmpty()) return null
        placeholderCount(tokens)?.let { return QuickLog.Placeholder(it) }
        return QuickLog.Sets(tokens)
    }

    sealed interface QuickAdd {
        /** "name xN" — a single reps-only trailing token means N empty placeholder sets. */
        data class Placeholder(val name: String, val count: Int) : QuickAdd
        /** "name" followed by set tokens — the parsed sets (empty for a bare name add). */
        data class Sets(val name: String, val tokens: List<SetToken>) : QuickAdd
    }

    /**
     * Quick-add field grammar: peel trailing set tokens off the end; the remaining leading
     * words are the exercise name. A single reps-only token (e.g. "cables x6", "abs x3") keeps
     * the historical "N placeholder sets" meaning; anything else is treated as real sets, so
     * "leg press 200x10" is one logged set — not 10 empty sets on an exercise named "leg press 200".
     */
    fun parseQuickAdd(text: String): QuickAdd? {
        val s = text.trim()
        if (s.isEmpty()) return null
        val parts = s.split(Regex("\\s+"))

        val tokens = mutableListOf<SetToken>()
        var splitIdx = parts.size
        for (i in parts.indices.reversed()) {
            val t = parseToken(parts[i]) ?: break
            tokens.add(0, t)
            splitIdx = i
        }
        val name = parts.take(splitIdx).joinToString(" ").trim()
        if (name.isEmpty()) return null

        // Back-compat: a lone reps-only, non-failure token means N placeholder sets.
        placeholderCount(tokens)?.let { return QuickAdd.Placeholder(name, it) }
        return QuickAdd.Sets(name, tokens.toList())
    }

    /** N for a lone reps-only, non-failure token ("x4"), else null. Shared by both grammars so
     *  the shorthand means the same thing wherever it's typed. */
    private fun placeholderCount(tokens: List<SetToken>): Int? {
        val only = tokens.singleOrNull() ?: return null
        if (only.weightKg != null || only.toFailure) return null
        return only.reps.coerceIn(1, MAX_PLACEHOLDER_SETS)
    }

    /**
     * Fill in missing weights for a token list. A null weight resolves to the most recent
     * earlier token's resolved weight, else [fallbackKg], else 0.0.
     */
    fun resolveWeights(tokens: List<SetToken>, fallbackKg: Double?): List<SetToken> {
        var last: Double? = null
        return tokens.map { t ->
            val w = t.weightKg ?: last ?: fallbackKg ?: 0.0
            last = w
            t.copy(weightKg = w)
        }
    }
}
