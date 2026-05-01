package com.fitness.app.data.importer

import com.fitness.app.data.db.FitnessDatabase
import com.fitness.app.data.db.dao.SessionWithExercises
import java.io.OutputStream
import java.io.PrintWriter
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Renders completed sessions as text in the same informal "27/12 pull / DL / 110x8 130x6 …"
 * shape that [LogImporter] parses, so an exported file can be re-imported losslessly for
 * the round-tripable cases (weight, reps, "to failure", and a few common note flags).
 */
class TextLogExporter @Inject constructor(private val db: FitnessDatabase) {

    data class ExportResult(val sessionsExported: Int, val setsExported: Int)

    suspend fun export(output: OutputStream, userId: Long): ExportResult {
        val sessions = db.sessionDao().allCompletedForExport(userId)
        return write(sessions, output)
    }

    companion object {

        fun write(sessions: List<SessionWithExercises>, output: OutputStream): ExportResult {
            val writer = PrintWriter(output.bufferedWriter())
            var totalSets = 0
            var lastYear: Int? = null

            for (sws in sessions) {
                val date = Instant.ofEpochMilli(sws.session.startedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                if (lastYear == null) {
                    writer.println(date.year.toString())
                    writer.println()
                } else if (lastYear != date.year) {
                    writer.println()
                    writer.println(date.year.toString())
                    writer.println()
                }
                lastYear = date.year

                val type = sws.session.sessionType?.takeIf { it.isNotBlank() } ?: ""
                writer.println("${date.dayOfMonth}/${date.monthValue} $type".trimEnd())

                for (ex in sws.exercises) {
                    val name = ex.sessionExercise.customLabel ?: ex.exercise.name
                    writer.println(name)

                    val sortedSets = ex.sets.sortedBy { it.setIndex }
                    if (sortedSets.isNotEmpty()) {
                        val tokens = sortedSets.map { s ->
                            val w = formatKg(s.weightKg)
                            val r = if (s.reps > 0) s.reps.toString() else ""
                            val flag = encodeNoteFlag(s.note)
                            "${w}x${r}${flag}"
                        }
                        writer.println(tokens.joinToString(" "))
                        totalSets += sortedSets.size
                    }
                }
                writer.println()
            }
            writer.flush()
            return ExportResult(sessionsExported = sessions.size, setsExported = totalSets)
        }

        private fun formatKg(v: Double): String =
            when {
                v <= 0.0 -> ""
                v % 1.0 == 0.0 -> v.toInt().toString()
                else -> v.toString()
            }

        private fun encodeNoteFlag(note: String): String {
            if (note.isBlank()) return ""
            val lc = note.lowercase()
            return when {
                "to failure" in lc -> "f"
                "blowout" in lc -> "+blowout"
                "partials" in lc -> "+partials"
                "paused" in lc -> "+paused"
                else -> {
                    val firstWord = note.trim().split(Regex("\\s+")).firstOrNull()
                        ?.replace(Regex("[^A-Za-z0-9]"), "")
                        ?.lowercase().orEmpty()
                    if (firstWord.isEmpty()) "" else "+$firstWord"
                }
            }
        }
    }
}
