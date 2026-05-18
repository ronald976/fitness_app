package com.fitness.app.data.xlsx

import android.util.Log
import com.fitness.app.data.db.FitnessDatabase
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import com.fitness.app.data.importer.ExerciseNameMapper
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.Row
import org.dhatim.fastexcel.reader.Sheet

/**
 * Reads a workbook produced by [XlsxExporter] (or the legacy build_xlsx.py output) and
 * populates Room with its sessions/exercises/sets. Consumes the `YYYY Sets` sheet(s) —
 * the Sessions/PRs/Progression sheets are derived and can be regenerated on export.
 *
 * Each row in the Sets sheet represents one logged set:
 *   Session#, Date, Weekday, Type, Time, Exercise, Set#, Weight (kg), Reps,
 *   Tag, Intensity %, PR, Notes
 */
class XlsxImporter @Inject constructor(private val db: FitnessDatabase) {

    data class ImportResult(val sessionsImported: Int, val rowsSkipped: Int)

    private data class ParsedSet(
        val weightKg: Double?,
        val reps: Int?,
        val note: String
    )

    private data class ParsedExercise(
        val rawName: String,
        val sets: MutableList<ParsedSet> = mutableListOf()
    )

    private data class ParsedSession(
        val date: LocalDate,
        val type: String,
        val exercises: MutableList<ParsedExercise> = mutableListOf()
    )

    suspend fun import(input: InputStream, userId: Long): ImportResult {
        val sessions = input.use { parse(it) }
        return ingest(sessions, userId)
    }

    private fun parse(input: InputStream): List<ParsedSession> {
        val parsed = mutableListOf<ParsedSession>()
        ReadableWorkbook(input).use { wb ->
            wb.sheets.forEach { sheet ->
                if (!sheet.name.contains("Sets", ignoreCase = true)) return@forEach
                parseSheet(sheet, parsed)
            }
        }
        return parsed.sortedBy { it.date }
    }

    private fun parseSheet(sheet: Sheet, out: MutableList<ParsedSession>) {
        // Key sessions within this sheet by (Session#, Date) so repeated rows land together.
        val byKey = linkedMapOf<Pair<Int, LocalDate>, ParsedSession>()
        val byKeyExercises = mutableMapOf<Pair<Int, LocalDate>, MutableMap<String, ParsedExercise>>()

        sheet.openStream().use { stream ->
            stream.skip(1) // header row
            stream.forEach { row ->
                val entry = parseRow(row) ?: return@forEach
                val key = entry.sessionNum to entry.date
                val session = byKey.getOrPut(key) {
                    ParsedSession(date = entry.date, type = entry.type).also { out.add(it) }
                }
                val exercises = byKeyExercises.getOrPut(key) { linkedMapOf() }
                val ex = exercises.getOrPut(entry.exerciseName) {
                    ParsedExercise(rawName = entry.exerciseName).also { session.exercises.add(it) }
                }
                ex.sets.add(ParsedSet(entry.weightKg, entry.reps, entry.note))
            }
        }
    }

    private data class RowEntry(
        val sessionNum: Int,
        val date: LocalDate,
        val type: String,
        val exerciseName: String,
        val weightKg: Double?,
        val reps: Int?,
        val note: String
    )

    private fun parseRow(row: Row): RowEntry? {
        val sessionNum = row.intCell(0) ?: return null
        val date = row.dateCell(1) ?: return null
        val type = row.stringCell(3) ?: ""
        val exerciseName = row.stringCell(5)?.trim().orEmpty()
        if (exerciseName.isEmpty()) return null
        val weight = row.doubleCell(7)
        val reps = row.intCell(8)
        val tag = row.stringCell(9).orEmpty().trim()
        val notes = row.stringCell(12).orEmpty().trim()
        val note = listOf(tag, notes).filter { it.isNotEmpty() }.joinToString("; ")
        return RowEntry(sessionNum, date, type, exerciseName, weight, reps, note)
    }

    private suspend fun ingest(sessions: List<ParsedSession>, userId: Long): ImportResult {
        val seedByName = db.exerciseDao().getAll().associateBy { it.name }.toMutableMap()
        val exerciseCache = mutableMapOf<String, Long>()
        var rowsSkipped = 0
        var imported = 0

        for (session in sessions) {
            val resolved = session.exercises.mapNotNull { pe ->
                val match = ExerciseNameMapper.map(pe.rawName)
                if (match == null) {
                    rowsSkipped += pe.sets.size
                    return@mapNotNull null
                }
                val working = pe.sets.filter { (it.weightKg != null && it.weightKg > 0.0) || (it.reps != null && it.reps > 0) }
                if (working.isEmpty()) return@mapNotNull null
                resolveExerciseId(match, seedByName, exerciseCache) to working
            }
            if (resolved.isEmpty()) continue

            val startedAt = session.date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val sessionId = db.sessionDao().insertSession(
                SessionEntity(
                    userId = userId,
                    planDayId = null,
                    startedAt = startedAt,
                    completedAt = startedAt,
                    sessionType = session.type,
                    notes = ""
                )
            )
            resolved.forEachIndexed { orderIdx, (exerciseId, sets) ->
                val seId = db.sessionDao().insertSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        plannedExerciseId = null,
                        actualExerciseId = exerciseId,
                        orderIdx = orderIdx
                    )
                )
                sets.forEachIndexed { i, s ->
                    db.sessionDao().insertSetLog(
                        SetLogEntity(
                            sessionExerciseId = seId,
                            setIndex = i,
                            weightKg = s.weightKg ?: 0.0,
                            reps = s.reps ?: 0,
                            note = s.note,
                            completedAt = startedAt
                        )
                    )
                }
            }
            imported++
        }
        Log.i(TAG, "imported $imported sessions, skipped $rowsSkipped rows")
        return ImportResult(imported, rowsSkipped)
    }

    private suspend fun resolveExerciseId(
        match: ExerciseNameMapper.Match,
        seedByName: MutableMap<String, ExerciseEntity>,
        cache: MutableMap<String, Long>
    ): Long {
        val cacheKey = "${match.slug}|${match.variantSuffix ?: ""}"
        cache[cacheKey]?.let { return it }

        val canonicalName = SLUG_TO_NAME[match.slug] ?: match.slug.replace('_', ' ').replaceFirstChar { it.titlecase() }
        val baseExercise = seedByName[canonicalName]
            ?: run {
                val newId = db.exerciseDao().insert(
                    ExerciseEntity(
                        name = canonicalName,
                        primaryMuscle = "Other",
                        equipment = "Other",
                        isCustom = true
                    )
                )
                ExerciseEntity(
                    id = newId,
                    name = canonicalName,
                    primaryMuscle = "Other",
                    equipment = "Other",
                    isCustom = true
                ).also { seedByName[canonicalName] = it }
            }

        val resolvedId = if (match.variantSuffix == null) {
            baseExercise.id
        } else {
            val variantName = "${baseExercise.name} ${match.variantSuffix}"
            seedByName[variantName]?.id ?: run {
                val newId = db.exerciseDao().insert(
                    ExerciseEntity(
                        name = variantName,
                        primaryMuscle = baseExercise.primaryMuscle,
                        equipment = baseExercise.equipment,
                        notes = "Variant ${match.variantSuffix} imported from history",
                        isCustom = true
                    )
                )
                seedByName[variantName] = ExerciseEntity(
                    id = newId,
                    name = variantName,
                    primaryMuscle = baseExercise.primaryMuscle,
                    equipment = baseExercise.equipment,
                    notes = "Variant ${match.variantSuffix} imported from history",
                    isCustom = true
                )
                newId
            }
        }
        cache[cacheKey] = resolvedId
        return resolvedId
    }

    companion object {
        private const val TAG = "XlsxImporter"
    }
}

// --- fastexcel cell-accessor helpers ----------------------------------------

private fun Row.intCell(col: Int): Int? {
    val c = getCell(col) ?: return null
    val raw = c.rawValue ?: return null
    return raw.trim().toDoubleOrNull()?.toInt()
}

private fun Row.doubleCell(col: Int): Double? {
    val c = getCell(col) ?: return null
    val raw = c.rawValue ?: return null
    return raw.trim().toDoubleOrNull()
}

private fun Row.stringCell(col: Int): String? {
    val c = getCell(col) ?: return null
    if (c.rawValue == null) return null
    return c.asString()
}

private fun Row.dateCell(col: Int): LocalDate? {
    val c = getCell(col) ?: return null
    return runCatching { c.asDate()?.toLocalDate() }.getOrNull()
        ?: runCatching {
            val raw = c.rawValue ?: return@runCatching null
            // Excel serial date number
            val serial = raw.toDoubleOrNull() ?: return@runCatching null
            LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
        }.getOrNull()
}

// Mirrors the slug→canonical map in LogImporter so XlsxImporter can reuse the same
// canonical names without dragging the full object.
private val SLUG_TO_NAME: Map<String, String> = mapOf(
    "bench_press" to "Barbell Bench Press",
    "smith_bench_press" to "Smith Machine Bench Press",
    "dumbbell_bench_press" to "Dumbbell Bench Press",
    "machine_chest_press" to "Machine Chest Press",
    "incline_bench_press" to "Incline Barbell Bench Press",
    "incline_smith_press" to "Incline Smith Machine Press",
    "incline_db_press" to "Incline Dumbbell Press",
    "push_up" to "Push-up",
    "cable_fly" to "Cable Fly",
    "pec_deck" to "Pec Deck",
    "dumbbell_fly" to "Dumbbell Fly",
    "dip" to "Parallel Bar Dip",
    "deadlift" to "Barbell Deadlift",
    "trap_bar_deadlift" to "Trap Bar Deadlift",
    "barbell_row" to "Barbell Row",
    "smith_row" to "Smith Machine Row",
    "pendlay_row" to "Pendlay Row",
    "dumbbell_row" to "Dumbbell Row",
    "chest_supported_row" to "Chest-Supported Row",
    "lat_pulldown" to "Lat Pulldown",
    "unilateral_lat_pulldown" to "Unilateral Lat Pulldown",
    "pull_up" to "Pull-up",
    "chin_up" to "Chin-up",
    "assisted_pull_up" to "Assisted Pull-up",
    "assisted_chin_up" to "Assisted Chin-up",
    "seated_cable_row" to "Seated Cable Row",
    "face_pull" to "Face Pull",
    "back_squat" to "Barbell Back Squat",
    "front_squat" to "Front Squat",
    "leg_press" to "Leg Press (Machine)",
    "leg_press_free_weight" to "Leg Press (Free Weight)",
    "smith_squat" to "Smith Machine Squat",
    "hack_squat" to "Hack Squat",
    "romanian_deadlift" to "Romanian Deadlift",
    "bulgarian_split_squat" to "Bulgarian Split Squat",
    "walking_lunge" to "Walking Lunge",
    "leg_extension" to "Leg Extension",
    "leg_curl" to "Leg Curl",
    "calf_raise" to "Calf Raise (Machine)",
    "calf_raise_free_weight" to "Calf Raise (Free Weight)",
    "seated_calf_raise" to "Seated Calf Raise",
    "hip_thrust" to "Barbell Hip Thrust",
    "smith_hip_thrust" to "Smith Machine Hip Thrust",
    "overhead_press" to "Barbell Overhead Press",
    "smith_overhead_press" to "Smith Machine Overhead Press",
    "seated_db_press" to "Seated Dumbbell Press",
    "machine_shoulder_press" to "Machine Shoulder Press",
    "lateral_raise" to "Dumbbell Lateral Raise",
    "cable_lateral_raise" to "Cable Lateral Raise",
    "rear_delt_fly" to "Rear Delt Fly",
    "cable_rear_delt_fly" to "Cable Rear Delt Fly",
    "shrug" to "Barbell Shrug",
    "dumbbell_shrug" to "Dumbbell Shrug",
    "barbell_curl" to "Barbell Curl",
    "ez_bar_curl" to "EZ Bar Curl",
    "dumbbell_curl" to "Dumbbell Curl",
    "cable_bicep_curl" to "Cable Bicep Curl",
    "hammer_curl" to "Hammer Curl",
    "tricep_pushdown" to "Cable Tricep Pushdown",
    "skullcrusher" to "Skullcrusher",
    "overhead_tricep_ext" to "Overhead Tricep Extension",
    "cable_overhead_tricep_ext" to "Cable Overhead Tricep Extension",
    "barbell_overhead_tricep_ext" to "Barbell Overhead Tricep Extension",
    "dumbbell_misc" to "Dumbbell Misc",
    "plank" to "Plank",
    "hanging_leg_raise" to "Hanging Leg Raise",
    "abs" to "Abs"
)
