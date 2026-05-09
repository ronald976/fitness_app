package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.OutlierSetRow
import com.fitness.app.data.repository.SessionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.max

/**
 * One outlier candidate that the user should confirm or exclude.
 * The reason fields explain why we flagged it so the UI can render context.
 */
data class OutlierPrCandidate(
    val setId: Long,
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int,
    val date: LocalDate,
    val score: Double,
    val medianScore: Double,
    val reason: String
)

/**
 * Scans the user's logged sets and surfaces likely logging mistakes — sets whose
 * weight, reps, or combined score sit far above the user's actual ability for that
 * exercise (e.g. accidentally logging 80 kg × 20 instead of 80 kg × 8).
 *
 * Sets the user has already reviewed (kept or excluded) are skipped so we don't
 * re-prompt every time the dashboard loads.
 *
 * The thresholds here are intentionally conservative — we'd rather miss a real
 * outlier than nag the user about legitimate PRs. Tune in one place if needed.
 */
class DetectOutlierPrsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(userId: Long): List<OutlierPrCandidate> {
        val zone = ZoneId.systemDefault()
        val all = sessionRepository.allSetsForOutlierReview(userId)
        return all.groupBy { it.exerciseId }
            .flatMap { (_, sets) -> findOutliersFor(sets, zone) }
            .sortedByDescending { it.score / it.medianScore.coerceAtLeast(1.0) }
    }

    private fun findOutliersFor(
        sets: List<OutlierSetRow>,
        zone: ZoneId
    ): List<OutlierPrCandidate> {
        if (sets.size < MIN_SETS_FOR_BASELINE) return emptyList()

        val scored = sets.map { it to it.weightKg * it.reps }
        val medianScore = median(scored.map { it.second })
        if (medianScore <= 0.0) return emptyList()

        val p90Weight = percentile(sets.map { it.weightKg }, 0.9).coerceAtLeast(0.0)
        // Sets sorted by score desc — only the top few are PR-tier candidates.
        val topByScore = scored.sortedByDescending { it.second }.take(TOP_K_TO_FLAG).map { it.first }.toSet()

        // Median reps at each (rounded) weight so we can spot rep-outliers per weight bucket.
        val repsByWeight = sets.groupBy { roundWeight(it.weightKg) }
            .mapValues { (_, v) -> median(v.map { it.reps.toDouble() }) }

        return scored.mapNotNull { (s, score) ->
            if (s.prReviewed) return@mapNotNull null

            val medianRepsHere = repsByWeight[roundWeight(s.weightKg)] ?: medianScore
            val isScoreOutlier = (s in topByScore) && score > SCORE_RATIO * medianScore
            val isWeightOutlier = p90Weight > 0 && s.weightKg > WEIGHT_RATIO * p90Weight
            val isRepOutlier = medianRepsHere > 0 && s.reps > REPS_RATIO * medianRepsHere

            if (!isScoreOutlier && !isWeightOutlier && !isRepOutlier) return@mapNotNull null

            val reason = buildList<String> {
                if (isScoreOutlier) add("score ${score.toInt()} vs median ${medianScore.toInt()}")
                if (isWeightOutlier) add("weight ${s.weightKg.toInt()} kg vs typical ${p90Weight.toInt()} kg")
                if (isRepOutlier) add("${s.reps} reps vs typical ${medianRepsHere.toInt()}")
            }.joinToString(" · ")

            OutlierPrCandidate(
                setId = s.id,
                exerciseName = s.exerciseName,
                weightKg = s.weightKg,
                reps = s.reps,
                date = Instant.ofEpochMilli(s.completedAt).atZone(zone).toLocalDate(),
                score = score,
                medianScore = medianScore,
                reason = reason
            )
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    /** Round to 1 kg buckets so 79.5 / 80 / 80.5 share rep-baseline comparisons. */
    private fun roundWeight(w: Double): Long = max(0L, kotlin.math.round(w).toLong())

    private companion object {
        const val MIN_SETS_FOR_BASELINE = 8
        const val TOP_K_TO_FLAG = 3
        const val SCORE_RATIO = 1.8       // total score 80% above median
        const val WEIGHT_RATIO = 1.4      // weight 40% above 90th-percentile working weight
        const val REPS_RATIO = 2.0        // reps 2× the typical at that weight
    }
}
