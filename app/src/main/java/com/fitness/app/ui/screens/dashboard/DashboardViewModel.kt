package com.fitness.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.app.data.db.dao.DashboardSetRow
import com.fitness.app.data.db.dao.TrainingDayRow
import com.fitness.app.data.repository.AppStateRepository
import com.fitness.app.data.repository.SessionRepository
import com.fitness.app.domain.usecase.DetectOutlierPrsUseCase
import com.fitness.app.domain.usecase.OutlierPrCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.max

// ── UI state models ────────────────────────────────────────────────────────

enum class TimeRange(val label: String, val months: Long?) {
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("All", null)
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedRange: TimeRange = TimeRange.ALL,
    val progression: List<ExerciseProgression> = emptyList(),
    val weeklyVolume: List<WeeklyVolume> = emptyList(),
    val trainingDays: List<LocalDate> = emptyList(),
    val totalSessions: Int = 0,
    val avgPerWeek: Double = 0.0,
    val sessionsThisWeek: Int = 0,
    val avgPerWeek4: Double = 0.0,
    val muscleBalance: Map<String, Int> = emptyMap(),
    val weeklyMuscleSets: Map<String, Double> = emptyMap(),
    val monthlyPushPull: List<MonthlyPushPull> = emptyList(),
    val exercisePrs: List<ExercisePrSummary> = emptyList(),
    val repRangeDistribution: Map<String, Int> = emptyMap(),
    val forecasts: List<ExerciseForecast> = emptyList(),
    val outlierCandidates: List<OutlierPrCandidate> = emptyList()
)

data class ExerciseProgression(
    val exerciseId: Long,
    val exerciseName: String,
    val dataPoints: List<ProgressionPoint>,
    val trendPoints: List<ProgressionPoint>,
    val prs: List<PrMarker>
)

data class ProgressionPoint(val date: LocalDate, val score: Double, val weightKg: Double = 0.0, val reps: Int = 0)

data class PrMarker(
    val date: LocalDate,
    val score: Double,
    val weightKg: Double,
    val reps: Int,
    val isRepPr: Boolean
)

data class WeeklyVolume(
    val weekStart: LocalDate,
    val tonnage: Double,
    val sets: Int,
    val tonnageByMuscle: Map<String, Double> = emptyMap()
)

data class MonthlyPushPull(
    val month: String,
    val pushSets: Int,
    val pullSets: Int,
    val ratio: Double
)

data class ExercisePrSummary(
    val exerciseId: Long,
    val exerciseName: String,
    val muscle: String,
    val bestWeight: Double,
    val bestReps: Int,
    val bestScore: Double,   // estimated 1RM (kg) of the best set
    val bestDate: LocalDate,
    val lastRepPr: PrMarker?,
    val lastWeightPr: PrMarker?,
    val lastVolumePr: VolumePrMarker?
)

data class VolumePrMarker(
    val date: LocalDate,
    val volume: Double,
    val sets: List<String>  // e.g. ["80kg×8", "80kg×7", "75kg×8"]
)

data class ExerciseForecast(
    val exerciseId: Long,
    val exerciseName: String,
    val status: String,       // "Progressing", "Plateau", "Declining"
    val changePct: Double,
    val earlierMed: Double,   // median e1RM (kg)
    val recentMed: Double,
    val projectedMed: Double,
    val projChangePct: Double,
    val earlierBestSet: String,  // e.g. "80kg × 8"
    val recentBestSet: String
)

/**
 * Epley estimated 1RM — the score used for progression, PR ranking and forecasts.
 * Unlike weight×reps it doesn't conflate intensity with volume, and the result
 * is directly interpretable as kilograms.
 */
internal fun e1rm(weightKg: Double, reps: Int): Double = weightKg * (1 + reps / 30.0)

// ── ViewModel ──────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val sessionRepository: SessionRepository,
    private val detectOutlierPrs: DetectOutlierPrsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()

    // Raw query results cached so switching the time range doesn't hit the DB.
    private var rawSets: List<DashboardSetRow> = emptyList()
    private var rawDays: List<TrainingDayRow> = emptyList()
    private var rawOutliers: List<OutlierPrCandidate> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val userId = appStateRepository.observe().first()?.currentUserId ?: return@launch
            rawSets = sessionRepository.allSetsForDashboard(userId)
            rawDays = sessionRepository.trainingDays(userId)
            rawOutliers = detectOutlierPrs(userId)
            rebuild(_state.value.selectedRange)
        }
    }

    fun setRange(range: TimeRange) {
        if (range == _state.value.selectedRange) return
        viewModelScope.launch { rebuild(range) }
    }

    private suspend fun rebuild(range: TimeRange) {
        val zone = ZoneId.systemDefault()
        val built = withContext(Dispatchers.Default) {
            val cutoff = range.months?.let { LocalDate.now().minusMonths(it) }
            val sets = if (cutoff == null) rawSets else rawSets.filter {
                !Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate().isBefore(cutoff)
            }
            val days = if (cutoff == null) rawDays else rawDays.filter {
                !LocalDate.ofEpochDay(it.dayEpoch).isBefore(cutoff)
            }
            val dates = buildTrainingDays(days)

            DashboardUiState(
                isLoading = false,
                selectedRange = range,
                progression = buildProgression(sets, zone),
                weeklyVolume = buildWeeklyVolume(sets, zone),
                trainingDays = dates,
                totalSessions = dates.size,
                avgPerWeek = computeAvgPerWeek(dates),
                sessionsThisWeek = computeSessionsThisWeek(dates),
                avgPerWeek4 = computeAvg4Week(dates),
                muscleBalance = buildMuscleBalance(sets),
                weeklyMuscleSets = buildWeeklyMuscleSets(sets, zone),
                monthlyPushPull = buildMonthlyPushPull(sets, zone),
                exercisePrs = buildExercisePrs(sets, zone),
                repRangeDistribution = buildRepRangeDistribution(sets),
                // Forecasts always use full history — trend detection needs the
                // long view, and a 3-month window has no "earlier" period to
                // compare against.
                forecasts = buildForecasts(rawSets, zone),
                outlierCandidates = rawOutliers
            )
        }
        _state.update { built }
    }

    /** Apply the user's decision on an outlier candidate. exclude=true marks the set as
     *  excluded from PR queries; exclude=false just records a "Keep" decision so the set
     *  doesn't keep showing up in the review list. Both refresh the dashboard so the
     *  PR widgets reflect the change immediately. */
    fun resolveOutlier(setId: Long, exclude: Boolean) {
        viewModelScope.launch {
            sessionRepository.setOutlierFlags(setId, exclude = exclude, reviewed = true)
            load()
        }
    }

    // ── Progression ────────────────────────────────────────────────────

    private fun buildProgression(
        sets: List<DashboardSetRow>,
        zone: ZoneId
    ): List<ExerciseProgression> {
        val byExercise = sets.groupBy { it.exerciseId }
        return byExercise.mapNotNull { (exId, exSets) ->
            buildExerciseProgression(exId, exSets, zone, minSessions = 5)
        }.sortedByDescending { it.dataPoints.maxOfOrNull { p -> p.score } ?: 0.0 }
    }

    // ── Weekly volume ──────────────────────────────────────────────────

    private fun buildWeeklyVolume(sets: List<DashboardSetRow>, zone: ZoneId): List<WeeklyVolume> {
        data class SetDate(val date: LocalDate, val volume: Double, val muscle: String)

        val dated = sets.map {
            val d = Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate()
            SetDate(d, it.weightKg * it.reps, it.primaryMuscle)
        }
        val byWeek = dated.groupBy { it.date.with(DayOfWeek.MONDAY) }
        return byWeek.map { (weekStart, weekSets) ->
            WeeklyVolume(
                weekStart,
                weekSets.sumOf { it.volume },
                weekSets.size,
                weekSets.groupBy { it.muscle }.mapValues { (_, v) -> v.sumOf { s -> s.volume } }
            )
        }.sortedBy { it.weekStart }
    }

    // ── Frequency ──────────────────────────────────────────────────────

    private fun buildTrainingDays(days: List<TrainingDayRow>): List<LocalDate> =
        days.map { LocalDate.ofEpochDay(it.dayEpoch) }.distinct()

    private fun computeAvgPerWeek(dates: List<LocalDate>): Double {
        if (dates.size < 2) return dates.size.toDouble()
        val weeks = ChronoUnit.WEEKS.between(dates.first(), dates.last()).coerceAtLeast(1)
        return dates.size.toDouble() / weeks
    }

    private fun computeSessionsThisWeek(dates: List<LocalDate>): Int {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        return dates.count { !it.isBefore(monday) }
    }

    private fun computeAvg4Week(dates: List<LocalDate>): Double {
        val cutoff = LocalDate.now().minusDays(28)
        return dates.count { it.isAfter(cutoff) } / 4.0
    }

    // ── Muscle balance ──────────────────────────────────────────────────

    private fun buildMuscleBalance(sets: List<DashboardSetRow>): Map<String, Int> =
        sets.groupBy { it.primaryMuscle }
            .mapValues { (_, v) -> v.size }

    /**
     * Average hard sets per muscle per week over the last 4 trained weeks,
     * for comparison against the common 10-20 sets/week guideline.
     * Anchored to the latest logged session so stale data doesn't read as zero.
     */
    private fun buildWeeklyMuscleSets(
        sets: List<DashboardSetRow>,
        zone: ZoneId
    ): Map<String, Double> {
        val latest = sets.maxOfOrNull { it.sessionStartedAt } ?: return emptyMap()
        val end = Instant.ofEpochMilli(latest).atZone(zone).toLocalDate()
        val cutoff = end.minusDays(27)
        val recent = sets.filter {
            !Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate().isBefore(cutoff)
        }
        return recent.groupBy { it.primaryMuscle }.mapValues { (_, v) -> v.size / 4.0 }
    }

    private fun buildMonthlyPushPull(
        sets: List<DashboardSetRow>,
        zone: ZoneId
    ): List<MonthlyPushPull> {
        val pushMuscles = setOf("Chest", "Shoulders", "Arms")
        val pullMuscles = setOf("Back")

        data class Dated(val yearMonth: String, val muscle: String)

        val items = sets.map {
            val d = Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate()
            Dated("${d.year}-${d.monthValue.toString().padStart(2, '0')}", it.primaryMuscle)
        }
        val months = items.map { it.yearMonth }.distinct().sorted()
        return months.map { ym ->
            val monthItems = items.filter { it.yearMonth == ym }
            val push = monthItems.count { it.muscle in pushMuscles }
            val pull = monthItems.count { it.muscle in pullMuscles }
            MonthlyPushPull(ym, push, pull, if (pull > 0) push.toDouble() / pull else 0.0)
        }
    }

    // ── Exercise PRs ────────────────────────────────────────────────────

    private fun buildExercisePrs(
        sets: List<DashboardSetRow>,
        zone: ZoneId
    ): List<ExercisePrSummary> {
        val byExercise = sets.groupBy { it.exerciseId }
        return byExercise.mapNotNull { (exId, exSets) ->
            if (exSets.size < 3) return@mapNotNull null
            val name = exSets.first().exerciseName
            val muscle = exSets.first().primaryMuscle
            val sorted = exSets.sortedBy { it.completedAt }

            var bestScore = 0.0; var bestW = 0.0; var bestR = 0; var bestDate = LocalDate.MIN
            var lastRepPr: PrMarker? = null
            var lastWeightPr: PrMarker? = null
            val bestRepsAtWeight = mutableMapOf<Double, Int>()
            var maxWeight = 0.0

            for (s in sorted) {
                val date = Instant.ofEpochMilli(s.sessionStartedAt).atZone(zone).toLocalDate()
                val score = e1rm(s.weightKg, s.reps)
                if (score > bestScore) {
                    bestScore = score; bestW = s.weightKg; bestR = s.reps; bestDate = date
                }
                // Rep PR
                val prev = bestRepsAtWeight[s.weightKg] ?: 0
                if (prev > 0 && s.reps > prev) {
                    lastRepPr = PrMarker(date, score, s.weightKg, s.reps, true)
                }
                bestRepsAtWeight[s.weightKg] = max(prev, s.reps)
                // Weight PR
                if (s.weightKg > maxWeight && s.reps >= 3) {
                    if (maxWeight > 0) {
                        lastWeightPr = PrMarker(date, score, s.weightKg, s.reps, false)
                    }
                    maxWeight = s.weightKg
                }
            }

            // Volume PR: sum of weight×reps for first 4 sets per session-exercise
            var lastVolumePr: VolumePrMarker? = null
            var bestVolume = 0.0
            val bySession = sorted.groupBy { it.sessionStartedAt }
            for ((sessionTs, sessionSets) in bySession.entries.sortedBy { it.key }) {
                val date = Instant.ofEpochMilli(sessionTs).atZone(zone).toLocalDate()
                val capped = sessionSets.sortedBy { it.setIndex }.take(4)
                val volume = capped.sumOf { it.weightKg * it.reps }
                if (volume > bestVolume && bestVolume > 0) {
                    lastVolumePr = VolumePrMarker(
                        date, volume,
                        capped.map { "${it.weightKg.toInt()}kg×${it.reps}" }
                    )
                }
                if (volume > bestVolume) bestVolume = volume
            }

            ExercisePrSummary(exId, name, muscle, bestW, bestR, bestScore, bestDate,
                lastRepPr, lastWeightPr, lastVolumePr)
        }.sortedByDescending { it.bestScore }
    }

    // ── Forecast ────────────────────────────────────────────────────────

    private fun buildForecasts(
        sets: List<DashboardSetRow>,
        zone: ZoneId
    ): List<ExerciseForecast> {
        val byExercise = sets.groupBy { it.exerciseId }
        return byExercise.mapNotNull { (exId, exSets) ->
            val name = exSets.first().exerciseName
            val dated = exSets.map {
                val d = Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate()
                Triple(d, e1rm(it.weightKg, it.reps), it)
            }
            // Best set per session date (by estimated 1RM)
            val sessionBest = dated.groupBy { it.first }
                .mapValues { (_, v) -> v.maxByOrNull { it.second }!! }
                .toSortedMap()

            if (sessionBest.size < 8) return@mapNotNull null

            val dates = sessionBest.keys.toList()
            val cutoff = dates.last().minusDays(90)

            val recentEntries = sessionBest.filter { it.key >= cutoff }
            val earlierEntries = sessionBest.filter { it.key < cutoff }

            val recentValues = recentEntries.values.map { it.second }
            val earlierValues = earlierEntries.values.map { it.second }

            if (recentValues.size < 3 || earlierValues.size < 3) return@mapNotNull null

            val recentMed = median(recentValues)
            val earlierMed = median(earlierValues)
            val changePct = (recentMed - earlierMed) / max(earlierMed, 1.0) * 100

            // Find best set in each period for description
            val earlierBest = earlierEntries.values.maxByOrNull { it.second }?.third
            val recentBest = recentEntries.values.maxByOrNull { it.second }?.third
            val earlierDesc = earlierBest?.let { "${it.weightKg.toInt()}kg × ${it.reps}" } ?: ""
            val recentDesc = recentBest?.let { "${it.weightKg.toInt()}kg × ${it.reps}" } ?: ""

            val status = when {
                changePct > 5 -> "Progressing"
                changePct < -5 -> "Declining"
                else -> "Plateau"
            }

            // Log-curve projection fitted on the recent window only — a fit over
            // years of history is dominated by newbie gains and reads far too
            // optimistic. Fall back to all points when the window is thin.
            val windowStart = dates.last().minusDays(180)
            val fitEntries = sessionBest.entries.filter { it.key >= windowStart }
                .let { if (it.size >= 5) it else sessionBest.entries.toList() }
            val fitStart = fitEntries.first().key
            val t = fitEntries.map { ChronoUnit.DAYS.between(fitStart, it.key).toDouble() }
            val vals = fitEntries.map { it.value.second }
            val projected = try {
                val (a, b) = fitLog(t.toDoubleArray(), vals.toDoubleArray())
                val futureT = t.last() + 182
                if (changePct < -5) recentMed else a * ln(futureT + 1) + b
            } catch (_: Exception) { recentMed }

            val projChangePct = (projected - recentMed) / max(recentMed, 1.0) * 100

            ExerciseForecast(exId, name, status, changePct, earlierMed, recentMed, projected,
                projChangePct, earlierDesc, recentDesc)
        }.sortedByDescending { it.changePct }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    /** Simple least-squares fit for y = a * ln(x+1) + b. */
    private fun fitLog(x: DoubleArray, y: DoubleArray): Pair<Double, Double> {
        val n = x.size.toDouble()
        val lnx = x.map { ln(it + 1) }
        val sumLnx = lnx.sum()
        val sumY = y.sum()
        val sumLnxY = lnx.zip(y.toList()).sumOf { (a, b) -> a * b }
        val sumLnx2 = lnx.sumOf { it * it }
        val denom = n * sumLnx2 - sumLnx * sumLnx
        if (denom == 0.0) return 0.0 to (sumY / n)
        val a = (n * sumLnxY - sumLnx * sumY) / denom
        val b = (sumY - a * sumLnx) / n
        return a to b
    }
}

/**
 * Shared between the dashboard Progression tab and the per-exercise detail
 * screen: best e1RM set per session date, rolling-median trend, PR markers.
 */
internal fun buildExerciseProgression(
    exerciseId: Long,
    exSets: List<DashboardSetRow>,
    zone: ZoneId,
    minSessions: Int
): ExerciseProgression? {
    if (exSets.isEmpty()) return null
    val name = exSets.first().exerciseName
    val sorted = exSets.sortedBy { it.completedAt }

    // Best set per session-date
    val sessionBest = sorted.groupBy {
        Instant.ofEpochMilli(it.sessionStartedAt).atZone(zone).toLocalDate()
    }.mapValues { (_, v) -> v.maxByOrNull { s -> e1rm(s.weightKg, s.reps) }!! }.toSortedMap()

    if (sessionBest.size < minSessions) return null

    val points = sessionBest.map { (d, s) ->
        ProgressionPoint(d, e1rm(s.weightKg, s.reps), s.weightKg, s.reps)
    }

    // Rolling median trend (window 7)
    val scores = points.map { it.score }
    val trendValues = rollingMedian(scores, 7)
    val trendPoints = points.mapIndexed { i, p -> ProgressionPoint(p.date, trendValues[i]) }

    // PR detection
    val prs = mutableListOf<PrMarker>()
    val bestRepsAtWeight = mutableMapOf<Double, Int>()
    var maxWeight = 0.0
    for (s in sorted) {
        val date = Instant.ofEpochMilli(s.sessionStartedAt).atZone(zone).toLocalDate()
        val score = e1rm(s.weightKg, s.reps)
        val prev = bestRepsAtWeight[s.weightKg] ?: 0
        if (prev > 0 && s.reps > prev) {
            prs.add(PrMarker(date, score, s.weightKg, s.reps, isRepPr = true))
        }
        bestRepsAtWeight[s.weightKg] = max(prev, s.reps)
        if (s.weightKg > maxWeight && s.reps >= 3) {
            if (maxWeight > 0) {
                prs.add(PrMarker(date, score, s.weightKg, s.reps, isRepPr = false))
            }
            maxWeight = s.weightKg
        }
    }

    return ExerciseProgression(exerciseId, name, points, trendPoints, prs)
}

/** Shared with the per-exercise detail screen. */
internal fun buildRepRangeDistribution(sets: List<DashboardSetRow>): Map<String, Int> {
    var strength = 0; var power = 0; var hypertrophy = 0; var endurance = 0
    for (s in sets) {
        when {
            s.reps <= 5 -> strength++
            s.reps <= 8 -> power++
            s.reps <= 12 -> hypertrophy++
            else -> endurance++
        }
    }
    return mapOf(
        "1-5 Strength" to strength,
        "5-8 Power" to power,
        "8-12 Hypertrophy" to hypertrophy,
        "12+ Endurance" to endurance
    )
}

private fun rollingMedian(values: List<Double>, window: Int): List<Double> {
    val result = DoubleArray(values.size)
    for (i in values.indices) {
        val start = max(0, i - window / 2)
        val end = minOf(values.size, i + window / 2 + 1)
        val slice = values.subList(start, end).sorted()
        result[i] = slice[slice.size / 2]
    }
    return result.toList()
}
