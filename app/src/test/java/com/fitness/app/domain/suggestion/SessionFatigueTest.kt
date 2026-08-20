package com.fitness.app.domain.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFatigueTest {

    private val strategy: ProgressionStrategy = DoubleProgressionStrategy()
    private val target = TargetSpec(targetSets = 3, repLow = 5, repHigh = 8, weightIncrementKg = 2.5)

    // ── sessionPositions ────────────────────────────────────────────────

    @Test
    fun `first slot has no prior work`() {
        val positions = sessionPositions(
            listOf(
                FatigueSlot("Chest", 3),
                FatigueSlot("Back", 3)
            )
        )
        assertEquals(SessionPosition(0, 0), positions[0])
        assertEquals(SessionPosition(1, 0), positions[1])
    }

    @Test
    fun `same-muscle sets accumulate, other muscles do not`() {
        val positions = sessionPositions(
            listOf(
                FatigueSlot("Chest", 4),
                FatigueSlot("Back", 3),
                FatigueSlot("Chest", 3),
                FatigueSlot("Chest", 2)
            )
        )
        assertEquals(0, positions[0].priorSetsSameMuscle)
        assertEquals(0, positions[1].priorSetsSameMuscle)
        assertEquals(4, positions[2].priorSetsSameMuscle)
        assertEquals(7, positions[3].priorSetsSameMuscle)
    }

    @Test
    fun `muscle names match case-insensitively`() {
        val positions = sessionPositions(
            listOf(FatigueSlot("chest", 3), FatigueSlot("Chest", 3))
        )
        assertEquals(3, positions[1].priorSetsSameMuscle)
    }

    // ── order sensitivity end to end ────────────────────────────────────

    private val prev = listOf(
        PreviousSet(60.0, 6),
        PreviousSet(60.0, 6),
        PreviousSet(60.0, 5)
    )

    @Test
    fun `same slot as last time keeps the plain progression`() {
        val slot = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0)
        val s = strategy.suggest(target, prev, FatigueContext(today = slot, previous = slot))
        assertEquals(listOf(6, 7, 6), s.sets.map { it.reps })
        assertTrue(s.note.contains("Push for +1 rep"))
    }

    @Test
    fun `moving an exercise later behind same-muscle work eases the ask`() {
        val s = strategy.suggest(
            target,
            prev,
            FatigueContext(
                today = SessionPosition(positionIdx = 2, priorSetsSameMuscle = 3),
                previous = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0)
            )
        )
        // Same weight, but every set asks a rep less than the unadjusted 6/7/6.
        assertEquals(listOf(60.0, 60.0, 60.0), s.sets.map { it.weightKg })
        assertEquals(listOf(5, 6, 5), s.sets.map { it.reps })
        assertTrue(s.note.contains("easier"))
    }

    @Test
    fun `deep fatigue cancels the weight jump and holds the known weight`() {
        val topOfRange = listOf(
            PreviousSet(60.0, 8),
            PreviousSet(60.0, 8),
            PreviousSet(60.0, 8)
        )
        val fresh = strategy.suggest(target, topOfRange)
        assertTrue("baseline should progress", fresh.sets.all { it.weightKg == 62.5 })

        val tired = strategy.suggest(
            target,
            topOfRange,
            FatigueContext(
                today = SessionPosition(positionIdx = 4, priorSetsSameMuscle = 6),
                previous = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0)
            )
        )
        assertTrue(tired.sets.all { it.weightKg == 60.0 })
        assertTrue(tired.sets.all { it.reps == 7 })
        assertTrue(tired.note.contains("holding"))
    }

    @Test
    fun `moving an exercise earlier lets the opening set count`() {
        val s = strategy.suggest(
            target,
            prev,
            FatigueContext(
                today = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0),
                previous = SessionPosition(positionIdx = 2, priorSetsSameMuscle = 3)
            )
        )
        // Set 1 loses its settling-in exemption; sets 2+ keep the base push.
        assertEquals(listOf(7, 7, 6), s.sets.map { it.reps })
        assertTrue(s.note.contains("Fresher"))
    }

    @Test
    fun `an exercise that has always been last is not handicapped`() {
        val slot = SessionPosition(positionIdx = 5, priorSetsSameMuscle = 9)
        val s = strategy.suggest(target, prev, FatigueContext(today = slot, previous = slot))
        assertEquals(listOf(6, 7, 6), s.sets.map { it.reps })
    }

    @Test
    fun `eased reps never fall below repLow`() {
        val atFloor = listOf(PreviousSet(60.0, 5), PreviousSet(60.0, 5), PreviousSet(60.0, 5))
        val s = strategy.suggest(
            target,
            atFloor,
            FatigueContext(
                today = SessionPosition(positionIdx = 4, priorSetsSameMuscle = 6),
                previous = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0)
            )
        )
        assertTrue(s.sets.all { it.reps >= target.repLow })
    }

    @Test
    fun `no history is left alone whatever the ordering`() {
        val s = strategy.suggest(
            target,
            emptyList(),
            FatigueContext(
                today = SessionPosition(positionIdx = 6, priorSetsSameMuscle = 9),
                previous = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0)
            )
        )
        assertTrue(s.note.contains("No history"))
        assertEquals(target.repLow, s.sets.first().reps)
    }

    @Test
    fun `sets-only entries are left alone whatever the ordering`() {
        val absTarget = TargetSpec(targetSets = 3, repLow = 0, repHigh = 0, weightIncrementKg = 0.0)
        val s = strategy.suggest(
            absTarget,
            listOf(PreviousSet(0.0, 0), PreviousSet(0.0, 0)),
            FatigueContext(
                today = SessionPosition(positionIdx = 6, priorSetsSameMuscle = 9),
                previous = SessionPosition(positionIdx = 0, priorSetsSameMuscle = 0)
            )
        )
        assertTrue(s.note.contains("Mark", ignoreCase = true))
    }
}
