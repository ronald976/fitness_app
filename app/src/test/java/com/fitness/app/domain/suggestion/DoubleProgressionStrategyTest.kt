package com.fitness.app.domain.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleProgressionStrategyTest {

    private val strategy = DoubleProgressionStrategy()
    private val target = TargetSpec(targetSets = 3, repLow = 5, repHigh = 8, weightIncrementKg = 2.5)

    @Test
    fun `no history returns placeholder at repLow`() {
        val s = strategy.suggest(target, emptyList())
        assertEquals(3, s.sets.size)
        assertEquals(5, s.sets.first().reps)
        assertEquals(0.0, s.sets.first().weightKg, 0.0)
        assertTrue(s.note.contains("No history"))
    }

    @Test
    fun `all sets at top of range triggers weight increase and floors rep drop at repLow`() {
        val prev = listOf(
            PreviousSet(60.0, 8),
            PreviousSet(60.0, 8),
            PreviousSet(60.0, 8)
        )
        val s = strategy.suggest(target, prev)
        assertTrue(s.sets.all { it.weightKg == 62.5 })
        assertTrue(s.sets.all { it.reps == 5 })
        assertTrue(s.note.contains("Progression"))
    }

    @Test
    fun `high rep progression drops four reps instead of resetting to repLow`() {
        val highRepTarget = TargetSpec(
            targetSets = 2,
            repLow = 10,
            repHigh = 20,
            weightIncrementKg = 2.5
        )
        val prev = listOf(
            PreviousSet(220.0, 60),
            PreviousSet(220.0, 58)
        )

        val s = strategy.suggest(highRepTarget, prev)

        assertTrue(s.sets.all { it.weightKg == 222.5 })
        assertTrue(s.sets.all { it.reps == 56 })
    }

    @Test
    fun `partial progress keeps weight and pushes plus one rep capped at repHigh`() {
        val prev = listOf(
            PreviousSet(60.0, 6),
            PreviousSet(60.0, 6),
            PreviousSet(60.0, 5)
        )
        val s = strategy.suggest(target, prev)
        assertEquals(listOf(60.0, 60.0, 60.0), s.sets.map { it.weightKg })
        // First set is exempt from the +1 push; only sets 2+ are asked to improve.
        assertEquals(listOf(6, 7, 6), s.sets.map { it.reps })
    }

    @Test
    fun `stall when all sets below repLow repeats the weight`() {
        val prev = listOf(
            PreviousSet(100.0, 3),
            PreviousSet(100.0, 3),
            PreviousSet(100.0, 2)
        )
        val s = strategy.suggest(target, prev)
        assertTrue(s.sets.all { it.weightKg == 100.0 })
        assertTrue(s.note.contains("Stall", ignoreCase = true))
    }

    @Test
    fun `cap keeps reps at repHigh when only some sets hit top`() {
        val prev = listOf(
            PreviousSet(80.0, 8),
            PreviousSet(80.0, 7),
            PreviousSet(80.0, 6)
        )
        val s = strategy.suggest(target, prev)
        // First set repeats last time's reps (exempt from +1); sets 2+ push +1 capped at 8.
        assertEquals(8, s.sets[0].reps)
        assertEquals(8, s.sets[1].reps)
        assertEquals(7, s.sets[2].reps)
    }

    @Test
    fun `sets-only target with no history yields mark-done suggestion`() {
        val absTarget = TargetSpec(targetSets = 3, repLow = 0, repHigh = 0, weightIncrementKg = 0.0)
        val s = strategy.suggest(absTarget, emptyList())
        assertEquals(3, s.sets.size)
        assertTrue(s.note.contains("Mark", ignoreCase = true))
        assertTrue(!s.note.contains("0–0"))
    }

    @Test
    fun `sets-only target with history still shows mark-done not progression text`() {
        val absTarget = TargetSpec(targetSets = 3, repLow = 0, repHigh = 0, weightIncrementKg = 0.0)
        val prev = listOf(PreviousSet(0.0, 0), PreviousSet(0.0, 0), PreviousSet(0.0, 0))
        val s = strategy.suggest(absTarget, prev)
        assertEquals(3, s.sets.size)
        assertTrue(s.note.contains("Mark", ignoreCase = true))
    }
}
