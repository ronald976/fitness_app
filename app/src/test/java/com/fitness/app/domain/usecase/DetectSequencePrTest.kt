package com.fitness.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectSequencePrTest {

    private fun sets(vararg pairs: Pair<Double, Int>): List<WorkingSet> =
        pairs.map { WorkingSet(it.first, it.second) }

    @Test
    fun `same weight more total reps beats prior best sequence`() {
        // 80x8 80x7 80x7 (1760) → 80x8 80x8 80x8 (1920) is a session PR.
        val result = DetectPrUseCase.detectSequencePr(
            current = sets(80.0 to 8, 80.0 to 8, 80.0 to 8),
            priorSessions = listOf(sets(80.0 to 8, 80.0 to 7, 80.0 to 7))
        )
        assertTrue(result is PrResult.SessionVolumePr)
        result as PrResult.SessionVolumePr
        assertEquals(1920.0, result.totalVolumeKg, 0.0)
        assertEquals(1760.0, result.previousBestVolumeKg, 0.0)
        assertEquals(3, result.setCount)
    }

    @Test
    fun `higher volume does not beat a heavier single from history`() {
        // A lone 100x1 in history blocks volume PRs at 80 kg — not strictly better.
        val result = DetectPrUseCase.detectSequencePr(
            current = sets(80.0 to 8, 80.0 to 8, 80.0 to 8),
            priorSessions = listOf(
                sets(80.0 to 8, 80.0 to 7, 80.0 to 7),
                sets(100.0 to 1)
            )
        )
        assertEquals(PrResult.None, result)
    }

    @Test
    fun `equal volume is not a PR`() {
        val result = DetectPrUseCase.detectSequencePr(
            current = sets(80.0 to 8, 80.0 to 8),
            priorSessions = listOf(sets(80.0 to 8, 80.0 to 8))
        )
        assertEquals(PrResult.None, result)
    }

    @Test
    fun `only the best four sets count on both sides`() {
        // Six light back-off sets in the current session can't out-volume a solid prior 4.
        val result = DetectPrUseCase.detectSequencePr(
            current = sets(60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8),
            priorSessions = listOf(sets(60.0 to 9, 60.0 to 9, 60.0 to 9, 60.0 to 9))
        )
        assertEquals(PrResult.None, result)

        // But beating the prior top-4 with our top-4 (extra sets ignored) does count.
        val pr = DetectPrUseCase.detectSequencePr(
            current = sets(60.0 to 10, 60.0 to 10, 60.0 to 10, 60.0 to 10, 40.0 to 5),
            priorSessions = listOf(sets(60.0 to 9, 60.0 to 9, 60.0 to 9, 60.0 to 9))
        )
        assertTrue(pr is PrResult.SessionVolumePr)
        assertEquals(2400.0, (pr as PrResult.SessionVolumePr).totalVolumeKg, 0.0)
    }

    @Test
    fun `a single current set is never a sequence PR`() {
        val result = DetectPrUseCase.detectSequencePr(
            current = sets(80.0 to 8),
            priorSessions = listOf(sets(50.0 to 5, 50.0 to 5))
        )
        assertEquals(PrResult.None, result)
    }

    @Test
    fun `no prior sessions means nothing to beat`() {
        val result = DetectPrUseCase.detectSequencePr(
            current = sets(80.0 to 8, 80.0 to 8),
            priorSessions = emptyList()
        )
        assertEquals(PrResult.None, result)
    }
}
