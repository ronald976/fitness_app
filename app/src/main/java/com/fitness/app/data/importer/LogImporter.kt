package com.fitness.app.data.importer

import android.content.Context
import android.util.Log
import com.fitness.app.data.db.FitnessDatabase
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Parses the historical Gmail-style fitness log .txt files in assets/logs and imports them
 * as completed sessions for a given user. Exercise names are normalized via [ExerciseNameMapper].
 *
 * The parser is tolerant of the log's informal structure:
 *   - "27/12 pull" / "3/4 Upper A" header lines set the date and session type.
 *   - Each following line of text is an exercise name.
 *   - Lines of set tokens like "110x8 130x7 130x6" attach to the previous exercise.
 *   - Parenthetical notes, warm-up lines ("(skip)"), and PR footer blocks are skipped.
 */
class LogImporter(
    private val context: Context,
    private val db: FitnessDatabase
) {

    private data class ParsedSet(
        val weightKg: Double?,
        val reps: Int?,
        val isWarmup: Boolean,
        val note: String = ""
    )
    private data class ParsedExercise(
        val rawName: String,
        val sets: MutableList<ParsedSet> = mutableListOf(),
        var quickSets: Int? = null,
        var intensityPct: Double? = null,
        var isPr: Boolean = false,
        val notes: MutableList<String> = mutableListOf()
    )
    private data class ParsedSession(
        val date: LocalDate,
        val type: String,
        val exercises: MutableList<ParsedExercise> = mutableListOf(),
        val notes: MutableList<String> = mutableListOf()
    )

    companion object {
        private const val TAG = "LogImporter"
        private val LOG_FILES = listOf(
            "logs/2025_log_1.txt" to 2025,
            "logs/2025_log_2.txt" to 2025,
            "logs/2025_log_3.txt" to 2025,
            "logs/2026_log_1.txt" to 2026,
            "logs/2026_log_2.txt" to 2026
        )
        private val DATE_RE = Regex("""^(\d{1,2})/(\d{1,2})\s+(.+?)\s*$""")
        private val SET_TOKEN_RE = Regex(
            """^(\d+(?:\.\d+)?)?x(\d+)?([a-z]+)?(?:\+(\w+))?$""",
            RegexOption.IGNORE_CASE
        )
        private val YEAR_MARKER_RE = Regex("""^(20\d{2})\s*$""")
        private val TIME_RE = Regex("""^\d{1,2}:\d{2}\s*$""")
        private val PAREN_LINE_RE = Regex("""^\(.+\)\s*$""")
    }

    suspend fun importFor(userId: Long) {
        val sessions = LOG_FILES.flatMap { (assetPath, baseYear) ->
            runCatching { context.assets.open(assetPath).use { it.bufferedReader().readText() } }
                .map { parse(it, baseYear) }
                .getOrElse {
                    Log.w(TAG, "Could not read $assetPath: ${it.message}")
                    emptyList()
                }
        }.sortedBy { it.date }
            .filter { it.date <= LocalDate.now() }

        // Build (or reuse) exercises for every raw name we encounter.
        val exerciseCache = mutableMapOf<String, Long>() // key: slug|variantSuffix
        val seedByName = db.exerciseDao().getAll().associateBy { it.name }.toMutableMap()

        for (session in sessions) {
            val resolvedExercises = session.exercises.mapNotNull { pe ->
                val match = ExerciseNameMapper.map(pe.rawName)
                if (match == null) {
                    if (pe.sets.any { it.weightKg != null || it.reps != null }) {
                        Log.i(TAG, "skipping unmapped exercise: '${pe.rawName}' (${pe.sets.size} sets)")
                    }
                    return@mapNotNull null
                }
                // Keep non-warmup sets. Null-reps ("36x?") are stored as reps = 0.
                val working = pe.sets.filter { !it.isWarmup }
                if (working.isEmpty() && pe.quickSets == null) return@mapNotNull null

                val exerciseId = resolveExerciseId(match, seedByName, exerciseCache)
                Triple(exerciseId, working, pe.rawName)
            }

            if (resolvedExercises.isEmpty()) continue

            val startedAt = session.date
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val sessionId = db.sessionDao().insertSession(
                SessionEntity(
                    userId = userId,
                    planDayId = null,
                    startedAt = startedAt,
                    completedAt = startedAt,
                    sessionType = session.type,
                    notes = session.notes.joinToString("; ")
                )
            )

            resolvedExercises.forEachIndexed { orderIdx, (exerciseId, sets, rawName) ->
                val seId = db.sessionDao().insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        plannedExerciseId = null,
                        actualExerciseId = exerciseId,
                        orderIdx = orderIdx,
                        customLabel = rawName
                    )
                )
                if (sets.isNotEmpty()) {
                    sets.forEachIndexed { idx, s ->
                        db.sessionDao().insertSetLog(
                            SetLogEntity(
                                sessionExerciseId = seId,
                                setIndex = idx,
                                weightKg = s.weightKg ?: 0.0,
                                reps = s.reps ?: 0,
                                note = s.note,
                                completedAt = startedAt
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveExerciseId(
        match: ExerciseNameMapper.Match,
        seedByName: MutableMap<String, ExerciseEntity>,
        cache: MutableMap<String, Long>
    ): Long {
        val cacheKey = "${match.slug}|${match.variantSuffix ?: ""}"
        cache[cacheKey]?.let { return it }

        // Find the canonical seed exercise by mapping slug → name via the seed lookup.
        val baseExercise = seedByName.values.firstOrNull { SLUG_TO_NAME[match.slug] == it.name }
            ?: seedByName.values.firstOrNull { it.name == SLUG_TO_NAME[match.slug] }

        val id = if (match.variantSuffix == null) {
            baseExercise?.id ?: insertPlaceholder(match.slug, seedByName)
        } else {
            val variantName = "${SLUG_TO_NAME[match.slug] ?: match.slug} ${match.variantSuffix}"
            val existing = seedByName[variantName]
            if (existing != null) existing.id
            else {
                val base = baseExercise ?: insertPlaceholderEntity(match.slug, seedByName)
                val newId = db.exerciseDao().insert(
                    ExerciseEntity(
                        name = variantName,
                        primaryMuscle = base.primaryMuscle,
                        equipment = base.equipment,
                        notes = "Variant ${match.variantSuffix} imported from history",
                        isCustom = true
                    )
                )
                val inserted = ExerciseEntity(
                    id = newId,
                    name = variantName,
                    primaryMuscle = base.primaryMuscle,
                    equipment = base.equipment,
                    notes = "Variant ${match.variantSuffix} imported from history",
                    isCustom = true
                )
                seedByName[variantName] = inserted
                newId
            }
        }
        cache[cacheKey] = id
        return id
    }

    private suspend fun insertPlaceholder(
        slug: String,
        seedByName: MutableMap<String, ExerciseEntity>
    ): Long = insertPlaceholderEntity(slug, seedByName).id

    private suspend fun insertPlaceholderEntity(
        slug: String,
        seedByName: MutableMap<String, ExerciseEntity>
    ): ExerciseEntity {
        val name = SLUG_TO_NAME[slug] ?: slug.replace('_', ' ').replaceFirstChar { it.titlecase() }
        val id = db.exerciseDao().insert(
            ExerciseEntity(
                name = name,
                primaryMuscle = "Other",
                equipment = "Other",
                isCustom = true
            )
        )
        val inserted = ExerciseEntity(
            id = id,
            name = name,
            primaryMuscle = "Other",
            equipment = "Other",
            isCustom = true
        )
        seedByName[name] = inserted
        return inserted
    }

    // --- parser ---------------------------------------------------------------

    private val PCT_RE = Regex("""(\d+(?:\.\d+)?)\s*%""")
    private val PR_MARKERS = listOf("new pr", "pr match", "recent pr")

    private fun parse(text: String, baseYear: Int): List<ParsedSession> {
        val out = mutableListOf<ParsedSession>()
        var currentSession: ParsedSession? = null
        var currentExercise: ParsedExercise? = null
        var inPrFooter = false
        var currentYear = baseYear
        var prevDate: LocalDate? = null
        var pendingTime: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (inPrFooter) {
                // PR footer — skip entirely; we only want dated sessions.
                continue
            }
            if (line.isEmpty()) continue
            if (line.startsWith("PRs") || line.all { it == '_' }) {
                inPrFooter = true
                continue
            }

            val yearMatch = YEAR_MARKER_RE.matchEntire(line)
            if (yearMatch != null) {
                currentYear = yearMatch.groupValues[1].toInt()
                prevDate = null
                continue
            }

            if (TIME_RE.matches(line)) {
                pendingTime = line.trim()
                continue
            }

            val dateMatch = DATE_RE.matchEntire(line)
            if (dateMatch != null) {
                val a = dateMatch.groupValues[1].toInt()
                val b = dateMatch.groupValues[2].toInt()
                val type = dateMatch.groupValues[3].trim()
                val date = resolveDate(currentYear, a, b, prevDate)
                currentYear = date.year
                prevDate = date
                currentSession = ParsedSession(date = date, type = type)
                currentExercise = null
                // Attach pending time as a session note on the first exercise (rare)
                if (pendingTime != null) {
                    currentSession.notes += "Time: $pendingTime"
                    pendingTime = null
                }
                out.add(currentSession)
                continue
            }

            if (PAREN_LINE_RE.matches(line)) {
                val content = line.removePrefix("(").removeSuffix(")").trim()
                if (currentExercise != null) {
                    applyParenthetical(currentExercise!!, content)
                } else if (currentSession != null) {
                    currentSession!!.notes += content
                }
                continue
            }

            // Strip trailing "(...)", e.g. "(94%)" or "(new PR!)"
            var trailingParen: String? = null
            val tokens = stripTrailingParen(line) { trailingParen = it }
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            // If every token parses as a set token and we have an exercise, attach them.
            if (currentExercise != null && tokens.all { isSetToken(it) }) {
                tokens.forEach { tok -> parseSetToken(tok)?.let(currentExercise!!.sets::add) }
                if (trailingParen != null) applyParenthetical(currentExercise!!, trailingParen!!)
                continue
            }

            if (currentSession == null) continue

            // Otherwise treat as an exercise header. Strip trailing "xN" shortcut (e.g. "Abs x3").
            val (name, quickSets) = splitQuickSetShortcut(line)
            val ex = ParsedExercise(rawName = name, quickSets = quickSets)
            currentSession!!.exercises.add(ex)
            currentExercise = ex
        }

        return out
    }

    private fun stripTrailingParen(line: String, onParen: ((String) -> Unit)? = null): String {
        val idx = line.indexOf('(')
        if (idx == -1 || !line.trim().endsWith(")")) return line
        val parenContent = line.substring(idx + 1, line.lastIndexOf(')')).trim()
        onParen?.invoke(parenContent)
        return line.substring(0, idx).trim()
    }

    private fun splitQuickSetShortcut(line: String): Pair<String, Int?> {
        val m = Regex("""^(.+?)\s*x(\d+)\s*$""").matchEntire(line) ?: return line to null
        val name = m.groupValues[1].trim()
        if (name.isEmpty()) return line to null
        return name to m.groupValues[2].toInt()
    }

    private fun applyParenthetical(ex: ParsedExercise, content: String) {
        val pctMatch = PCT_RE.find(content)
        if (pctMatch != null) {
            ex.intensityPct = pctMatch.groupValues[1].toDouble()
        }
        val lc = content.lowercase()
        if (PR_MARKERS.any { it in lc } || ("pr" in lc && "!" in lc)) {
            ex.isPr = true
        }
        // Keep raw text as note unless it's a pure percentage marker.
        if (!(pctMatch != null && pctMatch.value.trim() == content.trim())) {
            ex.notes += content
        }
    }

    private fun isSetToken(tok: String): Boolean {
        if (!tok.lowercase().contains('x')) return false
        if (!tok.any { it.isDigit() }) return false
        return SET_TOKEN_RE.matches(tok)
    }

    private fun parseSetToken(tok: String): ParsedSet? {
        val m = SET_TOKEN_RE.matchEntire(tok) ?: return null
        val weight = m.groupValues[1].toDoubleOrNull()
        val reps = m.groupValues[2].toIntOrNull()
        val letterTag = m.groupValues[3].lowercase()
        val plusMarker = m.groupValues[4].lowercase()
        if (weight == null && reps == null) return null

        val parts = mutableListOf<String>()
        when (letterTag) {
            "" -> {}
            "f" -> parts += "to failure"
            else -> parts += letterTag
        }
        if (plusMarker.isNotEmpty()) {
            val readable = when (plusMarker) {
                "bo", "blowout" -> "blowout"
                "partials" -> "partials"
                else -> plusMarker
            }
            parts += "+ $readable"
        }
        if (reps == null) parts += "incomplete"

        return ParsedSet(
            weightKg = weight,
            reps = reps,
            isWarmup = false,
            note = parts.joinToString("; ")
        )
    }

    /** Resolve "a/b" (usually d/m, occasionally m/d) by picking the candidate closest to prevDate. */
    private fun resolveDate(year: Int, a: Int, b: Int, prev: LocalDate?): LocalDate {
        val candidates = buildList {
            for (yr in (year - 1)..(year + 1)) {
                for ((d, m) in listOf(a to b, b to a)) {
                    runCatching { LocalDate.of(yr, m, d) }.getOrNull()?.let(::add)
                }
            }
        }.distinct()
        if (candidates.isEmpty()) return prev ?: LocalDate.of(year, 1, 1)
        if (prev == null) {
            return runCatching { LocalDate.of(year, b, a) }.getOrNull() ?: candidates.first()
        }
        val yearPenaltyDays = 300
        return candidates.minBy {
            Math.abs(java.time.temporal.ChronoUnit.DAYS.between(prev, it)) +
                yearPenaltyDays * Math.abs(it.year - year)
        }
    }
}

private val SLUG_TO_NAME: Map<String, String> = mapOf(
    "bench_press" to "Barbell Bench Press",
    "dumbbell_bench_press" to "Dumbbell Bench Press",
    "machine_chest_press" to "Machine Chest Press",
    "incline_bench_press" to "Incline Barbell Bench Press",
    "incline_db_press" to "Incline Dumbbell Press",
    "push_up" to "Push-up",
    "cable_fly" to "Cable Fly",
    "pec_deck" to "Pec Deck",
    "dumbbell_fly" to "Dumbbell Fly",
    "dip" to "Parallel Bar Dip",
    "deadlift" to "Barbell Deadlift",
    "trap_bar_deadlift" to "Trap Bar Deadlift",
    "barbell_row" to "Barbell Row",
    "pendlay_row" to "Pendlay Row",
    "dumbbell_row" to "Dumbbell Row",
    "chest_supported_row" to "Chest-Supported Row",
    "lat_pulldown" to "Lat Pulldown",
    "pull_up" to "Pull-up",
    "chin_up" to "Chin-up",
    "seated_cable_row" to "Seated Cable Row",
    "face_pull" to "Face Pull",
    "back_squat" to "Barbell Back Squat",
    "front_squat" to "Front Squat",
    "leg_press" to "Leg Press",
    "hack_squat" to "Hack Squat",
    "romanian_deadlift" to "Romanian Deadlift",
    "bulgarian_split_squat" to "Bulgarian Split Squat",
    "walking_lunge" to "Walking Lunge",
    "leg_extension" to "Leg Extension",
    "leg_curl" to "Leg Curl",
    "calf_raise" to "Standing Calf Raise",
    "seated_calf_raise" to "Seated Calf Raise",
    "hip_thrust" to "Barbell Hip Thrust",
    "overhead_press" to "Barbell Overhead Press",
    "seated_db_press" to "Seated Dumbbell Press",
    "machine_shoulder_press" to "Machine Shoulder Press",
    "lateral_raise" to "Dumbbell Lateral Raise",
    "cable_lateral_raise" to "Cable Lateral Raise",
    "rear_delt_fly" to "Rear Delt Fly",
    "shrug" to "Barbell Shrug",
    "barbell_curl" to "Barbell Curl",
    "ez_bar_curl" to "EZ Bar Curl",
    "dumbbell_curl" to "Dumbbell Curl",
    "hammer_curl" to "Hammer Curl",
    "tricep_pushdown" to "Cable Tricep Pushdown",
    "skullcrusher" to "Skullcrusher",
    "overhead_tricep_ext" to "Overhead Tricep Extension",
    "plank" to "Plank",
    "hanging_leg_raise" to "Hanging Leg Raise",
    "hip_adductor" to "Hip Adductor"
)
