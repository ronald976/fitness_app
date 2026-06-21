package com.fitness.app.domain.suggestion

import javax.inject.Inject

/**
 * Double-progression rule:
 *  - No prior working sets → empty-weight placeholder at repLow reps, "No history yet".
 *  - All prior working sets hit ≥ repHigh at weight W → suggest W + increment,
 *    dropping four reps from the best prior set and flooring at repLow.
 *  - Top prior set reps < repLow → stall: repeat same weight, same reps.
 *  - Otherwise → same weight, try lastReps + 1 per set, capped at repHigh.
 *    The first set is exempt from the +1 push (it doubles as the settling-in set);
 *    only sets 2+ are asked to improve.
 */
class DoubleProgressionStrategy @Inject constructor() : ProgressionStrategy {

    override fun suggest(target: TargetSpec, previous: List<PreviousSet>): Suggestion {
        // Sets-only / bodyweight-style entries (abs, plank, quick cables) don't have a
        // numeric rep target — skip the "0–0 reps" suggestion and prompt to mark sets done.
        val isSetsOnly = target.repLow == 0 && target.repHigh == 0
        if (previous.isEmpty()) {
            return Suggestion(
                sets = List(target.targetSets) { SuggestedSet(0.0, target.repLow) },
                note = if (isSetsOnly) "Mark each set done."
                       else "No history yet — aim for ${target.repLow}–${target.repHigh} reps."
            )
        }
        if (isSetsOnly) {
            return Suggestion(
                sets = List(target.targetSets) { SuggestedSet(0.0, 0) },
                note = "Mark each set done."
            )
        }

        val workingSets = previous
        val lastWeight = workingSets.first().weightKg
        val sameWeight = workingSets.all { it.weightKg == lastWeight }

        val hitTop = sameWeight && workingSets.all { it.reps >= target.repHigh }
        if (hitTop) {
            val newWeight = lastWeight + target.weightIncrementKg
            val lastReps = workingSets.maxOf { it.reps }
            val newReps = (lastReps - 4).coerceAtLeast(target.repLow)
            return Suggestion(
                sets = List(target.targetSets) { SuggestedSet(newWeight, newReps) },
                note = "Progression: +${formatKg(target.weightIncrementKg)} kg"
            )
        }

        val topReps = workingSets.maxOf { it.reps }
        if (topReps < target.repLow) {
            return Suggestion(
                sets = workingSets.map { SuggestedSet(it.weightKg, it.reps) }
                    .padTo(target.targetSets, SuggestedSet(lastWeight, target.repLow)),
                note = "Stall — repeat the weight."
            )
        }

        val suggested = workingSets.mapIndexed { i, set ->
            val reps = if (i == 0) set.reps else set.reps + 1
            SuggestedSet(set.weightKg, reps.coerceAtMost(target.repHigh))
        }.padTo(target.targetSets, SuggestedSet(lastWeight, target.repLow))

        return Suggestion(
            sets = suggested,
            note = "Push for +1 rep (cap ${target.repHigh})."
        )
    }

    private fun <T> List<T>.padTo(size: Int, filler: T): List<T> =
        if (this.size >= size) this.take(size) else this + List(size - this.size) { filler }

    private fun formatKg(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
