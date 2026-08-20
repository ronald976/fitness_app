package com.fitness.app.domain.suggestion

/** One ordered slot in a session: what it trains, and how many working sets it is worth. */
data class FatigueSlot(val primaryMuscle: String, val workingSets: Int)

/**
 * Fatigue thresholds, in [SessionPosition.fatigueScore] units (1.0 = one extra same-muscle
 * working set beforehand, or four places later in the running order).
 */
object FatigueThresholds {
    /** Enough of a change to shave a rep off the ask — e.g. one 3-set same-muscle exercise
     *  now landing in front of this one. Moving a few slots later on its own doesn't count. */
    const val EASE = 1.5
    /** Enough to also refuse a weight jump — roughly two same-muscle exercises' worth. */
    const val EASE_HARD = 4.0
    /** Meaningfully fresher than last time — the opener can be a real set. */
    const val PUSH = -1.5
}

/**
 * Turn an ordered session into per-slot positions: each slot's index plus the working sets
 * every earlier slot already spent on the same primary muscle. Muscle names are compared
 * case-insensitively because customs are user-typed.
 */
fun sessionPositions(slots: List<FatigueSlot>): List<SessionPosition> =
    slots.mapIndexed { idx, slot ->
        SessionPosition(
            positionIdx = idx,
            priorSetsSameMuscle = slots.take(idx)
                .filter { it.primaryMuscle.equals(slot.primaryMuscle, ignoreCase = true) }
                .sumOf { it.workingSets }
        )
    }

/**
 * Re-aim a progression suggestion for where the exercise actually falls today.
 *
 * The base strategy compares today against the last time this lift was done and asks for a
 * little more. That is only fair if the two sit in comparable places in the session: pressing
 * after three sets of chest work is not the lift that was done fresh last week, and asking for
 * +1 rep on it just manufactures a failed set. So a slot that got harder gets a rep (or two,
 * plus the weight jump) taken back off, and one that got easier gets its settling-in set asked
 * to count.
 *
 * [previousTopWeightKg] is the heaviest weight in the history behind [base] — null when there
 * is no history, in which case there is nothing to re-aim and [base] passes through.
 */
fun adjustForFatigue(
    base: Suggestion,
    target: TargetSpec,
    previousTopWeightKg: Double?,
    context: FatigueContext
): Suggestion {
    // Nothing to compare against, or a sets-only entry (abs, planks) with no rep target.
    if (previousTopWeightKg == null) return base
    if (target.repLow == 0 && target.repHigh == 0) return base
    if (base.sets.isEmpty()) return base

    val delta = context.delta
    return when {
        delta >= FatigueThresholds.EASE_HARD -> {
            val isWeightJump = base.sets.any { it.weightKg > previousTopWeightKg }
            if (isWeightJump) {
                // Deep into the muscle's work is the wrong place to try a new top weight.
                // Repeat the known weight instead, one rep under the range's ceiling.
                Suggestion(
                    sets = base.sets.map {
                        SuggestedSet(
                            weightKg = previousTopWeightKg,
                            reps = (target.repHigh - 1).coerceAtLeast(target.repLow)
                        )
                    },
                    note = "Deeper in the session - holding ${formatKg(previousTopWeightKg)} kg"
                )
            } else {
                Suggestion(
                    sets = base.sets.map { it.easedBy(2, target) },
                    note = "Deeper in the session - 2 reps easier"
                )
            }
        }
        delta >= FatigueThresholds.EASE -> Suggestion(
            sets = base.sets.map { it.easedBy(1, target) },
            note = "Later than last time - 1 rep easier"
        )
        delta <= FatigueThresholds.PUSH -> Suggestion(
            // Fresher today, so set 1 loses its settling-in exemption.
            sets = base.sets.mapIndexed { idx, set ->
                if (idx == 0) set.copy(reps = (set.reps + 1).coerceAtMost(target.repHigh))
                else set
            },
            note = "Fresher than last time - push set 1"
        )
        else -> base
    }
}

private fun SuggestedSet.easedBy(reps: Int, target: TargetSpec): SuggestedSet =
    copy(reps = (this.reps - reps).coerceAtLeast(target.repLow))

private fun formatKg(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
