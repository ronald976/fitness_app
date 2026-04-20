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

interface ProgressionStrategy {
    fun suggest(target: TargetSpec, previous: List<PreviousSet>): Suggestion
}
