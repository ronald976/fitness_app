package com.fitness.app.domain.usecase

import com.fitness.app.domain.usecase.DetectPrUseCase.Companion.detectSingleSetPr
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectSingleSetPrTest {

    @Test
    fun `more reps at a weight already lifted is a rep PR`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(80.0, 9),
            priors = listOf(WorkingSet(80.0, 8), WorkingSet(80.0, 7)),
            targetRepRange = null
        )
        assertEquals(PrResult.RepPr(80.0, 9, 8), result)
    }

    @Test
    fun `matching an old rep count at a lower weight is not a rep PR`() {
        // The reported bug: 100x10 already done, then 90x10 fired "new rep max" purely
        // because the best previous set at 90 kg happened to be 90x8.
        val result = detectSingleSetPr(
            logged = WorkingSet(90.0, 10),
            priors = listOf(WorkingSet(100.0, 10), WorkingSet(90.0, 8)),
            targetRepRange = null
        )
        assertEquals(PrResult.None, result)
    }

    @Test
    fun `beating a heavier set's rep count by one is still not a PR at the lower weight`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(90.0, 11),
            priors = listOf(WorkingSet(100.0, 12), WorkingSet(90.0, 8)),
            targetRepRange = null
        )
        assertEquals(PrResult.None, result)
    }

    @Test
    fun `out-repping every set at this weight and above is a rep PR`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(90.0, 13),
            priors = listOf(WorkingSet(100.0, 12), WorkingSet(90.0, 8)),
            targetRepRange = null
        )
        assertEquals(PrResult.RepPr(90.0, 13, 12), result)
    }

    @Test
    fun `heavier sets with fewer reps don't block a rep PR`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(80.0, 9),
            priors = listOf(WorkingSet(100.0, 3), WorkingSet(80.0, 8)),
            targetRepRange = null
        )
        assertEquals(PrResult.RepPr(80.0, 9, 8), result)
    }

    @Test
    fun `a brand new heaviest weight is a weight PR`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(105.0, 5),
            priors = listOf(WorkingSet(100.0, 8)),
            targetRepRange = null
        )
        assertEquals(PrResult.WeightPr(105.0, 5, 100.0), result)
    }

    @Test
    fun `a heavier squeeze below the target rep range is not a weight PR`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(105.0, 2),
            priors = listOf(WorkingSet(100.0, 8)),
            targetRepRange = 6..10
        )
        assertEquals(PrResult.None, result)
    }

    @Test
    fun `no prior sets means nothing to beat`() {
        val result = detectSingleSetPr(
            logged = WorkingSet(80.0, 8),
            priors = emptyList(),
            targetRepRange = null
        )
        assertEquals(PrResult.None, result)
    }
}
