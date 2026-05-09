package com.fitness.app.ui.components

import com.fitness.app.data.db.entities.SetLogEntity

/**
 * Format a list of sets from a single past session as a compact one-liner for display
 * on the active workout card ("Last: ...").
 *
 *   All sets at the same weight  → "89×8,8,8,6f"
 *   Mixed weights                → "60×10 70×8 80×6"
 *   "f" suffix means at-least-one set in the group was logged "to failure" (note contains "f")
 *
 * Sets with reps == 0 (placeholders for sets-only logging) and warmups are skipped.
 * Returns null when there is nothing meaningful to display.
 */
fun formatSetSummary(sets: List<SetLogEntity>): String? {
    val real = sets.asSequence()
        .filter { !it.isWarmup && it.reps > 0 }
        .sortedBy { it.setIndex }
        .toList()
    if (real.isEmpty()) return null

    val groups = mutableListOf<MutableList<SetLogEntity>>()
    for (set in real) {
        val last = groups.lastOrNull()
        if (last != null && last.first().weightKg == set.weightKg) last.add(set)
        else groups.add(mutableListOf(set))
    }

    return groups.joinToString(" ") { group ->
        val w = formatWeight(group.first().weightKg)
        val reps = group.joinToString(",") { it.reps.toString() }
        val anyFailure = group.any { isFailureNote(it.note) }
        val suffix = if (anyFailure) "f" else ""
        "${w}×$reps$suffix"
    }
}

private fun formatWeight(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private fun isFailureNote(note: String): Boolean {
    val n = note.trim().lowercase()
    return n == "f" || n == "to failure" || n.contains("failure")
}
