package com.fitness.app.domain.suggestion

data class TargetSpec(
    val targetSets: Int,
    val repLow: Int,
    val repHigh: Int,
    val weightIncrementKg: Double
)

data class PreviousSet(val weightKg: Double, val reps: Int)

data class SuggestedSet(val weightKg: Double, val reps: Int)

data class Suggestion(
    val sets: List<SuggestedSet>,
    val note: String
)

/**
 * Where an exercise sits inside a single session, and how much work the muscle it trains
 * already absorbed before it. Both together stand in for "how fresh were you when you did
 * this" — the same lift is a different lift opening the session versus closing it.
 */
data class SessionPosition(
    /** 0-based slot in the session's exercise order. */
    val positionIdx: Int = 0,
    /** Working sets already done in this session for the same primary muscle. */
    val priorSetsSameMuscle: Int = 0
) {
    /** Same-muscle volume dominates; raw position is a weaker, general-fatigue proxy. */
    val fatigueScore: Double
        get() = priorSetsSameMuscle + POSITION_WEIGHT * positionIdx

    companion object {
        const val POSITION_WEIGHT = 0.25
    }
}

/**
 * Today's slot compared against the slot the history being progressed from was set in.
 * Only the *difference* matters: a lift that has always been done fifth doesn't need
 * handicapping, but one moved from first to fifth does.
 */
data class FatigueContext(
    val today: SessionPosition = SessionPosition(),
    val previous: SessionPosition = SessionPosition()
) {
    /** Positive = more tired today than when the previous sets were logged. */
    val delta: Double get() = today.fatigueScore - previous.fatigueScore
}

interface ProgressionStrategy {
    fun suggest(
        target: TargetSpec,
        previous: List<PreviousSet>,
        context: FatigueContext = FatigueContext()
    ): Suggestion
}
