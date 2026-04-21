package com.fitness.app.data.xlsx

import com.fitness.app.data.db.FitnessDatabase
import com.fitness.app.data.db.dao.SessionWithExercises
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet

/**
 * Renders the current Room contents as an xlsx workbook shaped like `data/build_xlsx.py`'s
 * output: per-year `Sets` and `Sessions` sheets plus aggregate `PRs` and `Progression`
 * sheets. Only completed sessions for the given user are included.
 */
class XlsxExporter @Inject constructor(private val db: FitnessDatabase) {

    data class ExportResult(val sessionsExported: Int, val setsExported: Int)

    suspend fun export(output: OutputStream, userId: Long): ExportResult {
        val sessions = db.sessionDao().allCompletedForExport(userId)
        val rows = sessions.flatMap { toRows(it) }
        val byYear = rows.groupBy { it.date.year }.toSortedMap()

        Workbook(output, "Fitness", "1.0").use { wb ->
            for ((year, yearRows) in byYear) {
                writeSetsSheet(wb.newWorksheet("$year Sets"), yearRows)
            }
            val sessionRollups = sessions.map { sessionRollup(it) }
            for ((year, yearSessions) in sessionRollups.groupBy { it.date.year }.toSortedMap()) {
                writeSessionsSheet(wb.newWorksheet("$year Sessions"), yearSessions)
            }
            writePrsSheet(wb.newWorksheet("PRs"), rows)
            writeProgressionSheet(wb.newWorksheet("Progression"), rows)
            wb.finish()
        }

        return ExportResult(sessionsExported = sessions.size, setsExported = rows.size)
    }

    // ---- row model --------------------------------------------------------

    private data class SetRow(
        val sessionId: Long,
        val sessionOrdinal: Int,
        val date: LocalDate,
        val sessionType: String,
        val timeStr: String,
        val exerciseName: String,
        val setIndex: Int,
        val weightKg: Double,
        val reps: Int,
        val note: String
    )

    private data class SessionRollup(
        val ordinal: Int,
        val date: LocalDate,
        val sessionType: String,
        val timeStr: String,
        val exerciseCount: Int,
        val totalSets: Int,
        val notes: String
    )

    private fun toRows(sws: SessionWithExercises): List<SetRow> {
        val instant = Instant.ofEpochMilli(sws.session.startedAt).atZone(ZoneId.systemDefault())
        val date = instant.toLocalDate()
        val time = instant.format(DateTimeFormatter.ofPattern("HH:mm"))
        val type = sws.session.sessionType ?: ""
        val sortedExercises = sws.exercises.sortedBy { it.sessionExercise.orderIdx }
        val rows = mutableListOf<SetRow>()
        for (ex in sortedExercises) {
            val sortedSets = ex.sets.sortedBy { it.setIndex }
            for (s in sortedSets) {
                rows += SetRow(
                    sessionId = sws.session.id,
                    sessionOrdinal = 0, // filled after global sort
                    date = date,
                    sessionType = type,
                    timeStr = time,
                    exerciseName = ex.exercise.name,
                    setIndex = s.setIndex + 1,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    note = s.note
                )
            }
        }
        return rows
    }

    private fun sessionRollup(sws: SessionWithExercises): SessionRollup {
        val instant = Instant.ofEpochMilli(sws.session.startedAt).atZone(ZoneId.systemDefault())
        return SessionRollup(
            ordinal = 0,
            date = instant.toLocalDate(),
            sessionType = sws.session.sessionType ?: "",
            timeStr = instant.format(DateTimeFormatter.ofPattern("HH:mm")),
            exerciseCount = sws.exercises.size,
            totalSets = sws.exercises.sumOf { it.sets.size },
            notes = sws.session.notes
        )
    }

    // ---- Sets sheet -------------------------------------------------------

    private fun writeSetsSheet(ws: Worksheet, rows: List<SetRow>) {
        val headers = listOf(
            "Session#", "Date", "Weekday", "Type", "Time",
            "Exercise", "Set#", "Weight (kg)", "Reps", "Note"
        )
        writeHeader(ws, headers)

        // Assign session ordinals within this year's rows, stable by date+sessionId.
        val ordinalByKey = rows.asSequence()
            .map { it.sessionId }
            .distinct()
            .withIndex()
            .associate { (i, id) -> id to (i + 1) }

        rows.forEachIndexed { idx, r ->
            val row = idx + 1
            val fill = sessionFill(r.sessionType)
            val sOrd = ordinalByKey[r.sessionId] ?: 0
            ws.value(row, 0, sOrd.toLong())
            ws.value(row, 1, r.date.toString())
            ws.value(row, 2, r.date.dayOfWeek.name.substring(0, 3).lowercase().replaceFirstChar { it.uppercase() })
            ws.value(row, 3, r.sessionType)
            ws.value(row, 4, r.timeStr)
            ws.value(row, 5, r.exerciseName)
            ws.value(row, 6, r.setIndex.toLong())
            ws.value(row, 7, r.weightKg)
            ws.value(row, 8, r.reps.toLong())
            ws.value(row, 9, r.note)
            if (fill != null) {
                ws.range(row, 0, row, headers.lastIndex).style().fillColor(fill).set()
            }
        }
        ws.freezePane(0, 1)
        autoSize(ws, headers.size)
    }

    // ---- Sessions sheet ---------------------------------------------------

    private fun writeSessionsSheet(ws: Worksheet, sessions: List<SessionRollup>) {
        val headers = listOf(
            "Session#", "Date", "Weekday", "Type", "Time", "Exercises", "Total Sets", "Notes"
        )
        writeHeader(ws, headers)
        sessions.forEachIndexed { idx, s ->
            val row = idx + 1
            ws.value(row, 0, (idx + 1).toLong())
            ws.value(row, 1, s.date.toString())
            ws.value(row, 2, s.date.dayOfWeek.name.substring(0, 3).lowercase().replaceFirstChar { it.uppercase() })
            ws.value(row, 3, s.sessionType)
            ws.value(row, 4, s.timeStr)
            ws.value(row, 5, s.exerciseCount.toLong())
            ws.value(row, 6, s.totalSets.toLong())
            ws.value(row, 7, s.notes)
            sessionFill(s.sessionType)?.let { fill ->
                ws.range(row, 0, row, headers.lastIndex).style().fillColor(fill).set()
            }
        }
        ws.freezePane(0, 1)
        autoSize(ws, headers.size)
    }

    // ---- PRs sheet --------------------------------------------------------

    private fun writePrsSheet(ws: Worksheet, rows: List<SetRow>) {
        val byExercise = rows.groupBy { it.exerciseName }
        val years = rows.map { it.date.year }.distinct().sorted()
        val headers = listOf("Exercise") + years.map { it.toString() }
        writeHeader(ws, headers)

        val exerciseNames = byExercise.keys.sorted()
        exerciseNames.forEachIndexed { idx, name ->
            val row = idx + 1
            ws.value(row, 0, name)
            val perYearBest = byExercise.getValue(name).groupBy { it.date.year }
            years.forEachIndexed { yi, year ->
                val best = perYearBest[year]
                    ?.filter { it.weightKg > 0 && it.reps > 0 }
                    ?.maxByOrNull { epley1rm(it.weightKg, it.reps) }
                ws.value(row, yi + 1, best?.let { "${fmtWeight(it.weightKg)}x${it.reps}" } ?: "")
            }
        }
        ws.freezePane(1, 1)
        autoSize(ws, headers.size)
    }

    // ---- Progression sheet ------------------------------------------------

    private fun writeProgressionSheet(ws: Worksheet, rows: List<SetRow>) {
        data class QKey(val year: Int, val quarter: Int) : Comparable<QKey> {
            override fun compareTo(other: QKey): Int =
                compareValuesBy(this, other, { it.year }, { it.quarter })
        }
        fun quarter(d: LocalDate) = QKey(d.year, (d.monthValue - 1) / 3 + 1)

        val numeric = rows.filter { it.weightKg > 0 && it.reps > 0 }
        val byExercise = numeric.groupBy { it.exerciseName }
        val qualifying = byExercise.filterValues { it.map { r -> r.date }.toSet().size > PROG_MIN_DAYS }
        if (qualifying.isEmpty()) return

        val quarters = numeric.map { quarter(it.date) }.toSortedSet().toList()
        val headers = listOf("Exercise", "Days") + quarters.map { "${it.year} Q${it.quarter}" }
        writeHeader(ws, headers)

        val sortedNames = qualifying.keys.sorted()
        sortedNames.forEachIndexed { idx, name ->
            val row = idx + 1
            val exRows = qualifying.getValue(name)
            val days = exRows.map { it.date }.toSet().size
            ws.value(row, 0, name)
            ws.value(row, 1, days.toLong())

            val bestByQ: Map<QKey, SetRow> = exRows
                .groupBy { quarter(it.date) }
                .mapValues { (_, rs) -> rs.maxBy { epley1rm(it.weightKg, it.reps) } }

            var runningMax = 0.0
            quarters.forEachIndexed { qi, q ->
                val col = qi + 2
                val entry = bestByQ[q]
                if (entry == null) {
                    ws.value(row, col, "n/a")
                    ws.style(row, col).fillColor("F2F2F2").italic().set()
                } else {
                    val rm = epley1rm(entry.weightKg, entry.reps)
                    when {
                        runningMax == 0.0 || rm > runningMax * (1 + PROG_MATCH_TOL) -> {
                            ws.value(row, col, "${fmtWeight(entry.weightKg)}x${entry.reps}")
                            ws.style(row, col).fillColor("C6EFCE").bold().fontColor("006100").set()
                        }
                        rm >= runningMax * (1 - PROG_MATCH_TOL) -> {
                            ws.value(row, col, "${fmtWeight(entry.weightKg)}x-")
                            ws.style(row, col).fillColor("FFEB9C").bold().fontColor("9C5700").set()
                        }
                        else -> {
                            ws.value(row, col, "${fmtWeight(entry.weightKg)}x${entry.reps}")
                            ws.style(row, col).fillColor("FFC7CE").bold().fontColor("9C0006").set()
                        }
                    }
                    if (rm > runningMax) runningMax = rm
                }
            }
        }
        ws.freezePane(2, 1)
        autoSize(ws, headers.size)
    }

    // ---- helpers ----------------------------------------------------------

    private fun writeHeader(ws: Worksheet, headers: List<String>) {
        headers.forEachIndexed { i, label -> ws.value(0, i, label) }
        ws.range(0, 0, 0, headers.lastIndex)
            .style()
            .fillColor("305496")
            .fontColor("FFFFFF")
            .bold()
            .horizontalAlignment("center")
            .set()
    }

    private fun sessionFill(sessionType: String): String? {
        val s = sessionType.lowercase()
        return SESSION_COLORS.entries.firstOrNull { s.contains(it.key) }?.value
    }

    private fun autoSize(ws: Worksheet, numCols: Int) {
        for (c in 0 until numCols) ws.width(c, 16.0)
    }

    private fun epley1rm(w: Double, r: Int): Double = w * (1.0 + r / 30.0)

    private fun fmtWeight(w: Double): String =
        if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

    companion object {
        private const val PROG_MIN_DAYS = 10
        private const val PROG_MATCH_TOL = 0.005
        private val SESSION_COLORS = linkedMapOf(
            "pull" to "DCE6F1",
            "push" to "FCE4D6",
            "legs" to "E2EFDA",
            "upper" to "FFF2CC",
            "lower" to "EDEDED"
        )
    }
}
