package com.fitness.app.domain.suggestion

import javax.inject.Inject

/**
 * Double-progression rule:
 *  - No prior working sets → empty-weight placeholder at repLow reps, "No history yet".
 *  - All prior working sets hit ≥ repHigh at weight W → suggest (W + increment) × targetSets × repLow.
 *  - Top prior set reps < repLow → stall: repeat same weight, same reps.
 *  - Otherwise → same weight, try lastReps + 1 per set, capped at repHigh.
 */
class DoubleProgressionStrategy @Inject constructor() : ProgressionStrategy {

    override fun suggest(target: TargetSpec, previous: List<PreviousSet>): Suggestion {
        if (previous.isEmpty()) {
            return Suggestion(
                sets = List(target.targetSets) { SuggestedSet(0.0, target.repLow) },
                note = "No history yet — aim for ${target.repLow}–${target.repHigh} reps."
            )
        }

        val workingSets = previous
        val lastWeight = workingSets.first().weightKg
        val sameWeight = workingSets.all { it.weightKg == lastWeight }

        val hitTop = sameWeight && workingSets.all { it.reps >= target.repHigh }
        if (hitTop) {
            val newWeight = lastWeight + target.weightIncrementKg
            return Suggestion(
                sets = List(target.targetSets) { SuggestedSet(newWeight, target.repLow) },
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

        val suggested = workingSets.map {
            SuggestedSet(it.weightKg, (it.reps + 1).coerceAtMost(target.repHigh))
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
