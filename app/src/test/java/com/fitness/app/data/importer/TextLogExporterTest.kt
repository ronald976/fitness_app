package com.fitness.app.data.importer

import com.fitness.app.data.db.dao.SessionExerciseWithSets
import com.fitness.app.data.db.dao.SessionWithExercises
import com.fitness.app.data.db.entities.ExerciseEntity
import com.fitness.app.data.db.entities.SessionEntity
import com.fitness.app.data.db.entities.SessionExerciseEntity
import com.fitness.app.data.db.entities.SetLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId

class TextLogExporterTest {

    @Test
    fun `single session writes year, date header, exercise, and set tokens`() {
        val date = LocalDate.of(2025, 12, 27)
        val session = mkSession(
            date = date,
            type = "pull",
            exercises = listOf(
                ExerciseBlock(
                    name = "Barbell Deadlift",
                    sets = listOf(
                        Set(110.0, 8, ""),
                        Set(130.0, 6, ""),
                        Set(130.0, 8, ""),
                        Set(130.0, 10, "")
                    )
                ),
                ExerciseBlock(
                    name = "Lat Pulldown",
                    sets = listOf(
                        Set(89.0, 7, "to failure"),
                        Set(82.0, 8, ""),
                        Set(82.0, 5, "plus blowout")
                    )
                )
            )
        )
        val text = render(listOf(session))

        val lines = text.lines()
        assertEquals("2025", lines[0])
        assertTrue("date header missing", lines.any { it == "27/12 pull" })
        assertTrue("first exercise missing", lines.any { it == "Barbell Deadlift" })
        assertTrue("set tokens missing",
            lines.any { it == "110x8 130x6 130x8 130x10" })
        assertTrue("second exercise missing", lines.any { it == "Lat Pulldown" })
        assertTrue("note flag 'f' / blowout missing",
            lines.any { it == "89x7f 82x8 82x5+blowout" })
    }

    @Test
    fun `decimal weights and bodyweight render cleanly`() {
        val text = render(listOf(mkSession(
            date = LocalDate.of(2025, 1, 5),
            type = "push",
            exercises = listOf(
                ExerciseBlock("Pull-up", listOf(Set(0.0, 10, ""), Set(0.0, 8, ""))),
                ExerciseBlock("Incline Dumbbell Press", listOf(Set(22.5, 10, "")))
            )
        )))
        assertTrue("BW token missing", text.contains("x10 x8"))
        assertTrue("decimal weight not preserved", text.contains("22.5x10"))
    }

    @Test
    fun `year header only emitted when year changes`() {
        val sessions = listOf(
            mkSession(LocalDate.of(2025, 12, 1), "push", listOf(
                ExerciseBlock("Bench", listOf(Set(80.0, 5, "")))
            )),
            mkSession(LocalDate.of(2025, 12, 8), "pull", listOf(
                ExerciseBlock("Row", listOf(Set(70.0, 8, "")))
            )),
            mkSession(LocalDate.of(2026, 1, 3), "push", listOf(
                ExerciseBlock("Bench", listOf(Set(82.5, 5, "")))
            ))
        )
        val text = render(sessions)
        val firstYear = text.indexOf("2025")
        val secondYear = text.indexOf("2026")
        assertTrue("2025 marker missing", firstYear == 0)
        assertTrue("2026 marker missing", secondYear > firstYear)
        // Sessions in same year should not re-emit the year header
        val count2025 = Regex("(?m)^2025\\s*$").findAll(text).count()
        assertEquals(1, count2025)
    }

    @Test
    fun `tokens parse with LogImporter set-token regex`() {
        val text = render(listOf(mkSession(
            date = LocalDate.of(2025, 4, 10),
            type = "push",
            exercises = listOf(
                ExerciseBlock("Bench", listOf(
                    Set(100.0, 8, ""),
                    Set(100.0, 7, "to failure"),
                    Set(80.0, 5, "paused")
                ))
            )
        )))
        val lines = text.lines()
        val benchIdx = lines.indexOf("Bench")
        assertTrue("Bench header missing in:\n$text", benchIdx >= 0)
        val tokens = lines[benchIdx + 1].split(" ")
        val regex = Regex("""^(\d+(?:\.\d+)?)?x(\d+)?([a-z]+)?(?:\+(\w+))?$""", RegexOption.IGNORE_CASE)
        for (tok in tokens) {
            assertTrue("token '$tok' should match importer regex", regex.matches(tok))
        }
    }

    @Test
    fun `report counts sessions and sets`() {
        val sessions = listOf(
            mkSession(LocalDate.of(2025, 6, 1), "push", listOf(
                ExerciseBlock("Bench", listOf(Set(80.0, 5, ""), Set(80.0, 5, "")))
            )),
            mkSession(LocalDate.of(2025, 6, 3), "pull", listOf(
                ExerciseBlock("Row", listOf(Set(70.0, 8, ""), Set(70.0, 8, ""), Set(70.0, 8, "")))
            ))
        )
        val out = ByteArrayOutputStream()
        val res = TextLogExporter.write(sessions, out)
        assertEquals(2, res.sessionsExported)
        assertEquals(5, res.setsExported)
    }

    // ── helpers ────────────────────────────────────────────────────────

    private data class Set(val weight: Double, val reps: Int, val note: String)
    private data class ExerciseBlock(val name: String, val sets: List<Set>)

    private fun mkSession(
        date: LocalDate,
        type: String,
        exercises: List<ExerciseBlock>
    ): SessionWithExercises {
        val startedAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessionEntity = SessionEntity(
            id = 1,
            userId = 1,
            planDayId = null,
            startedAt = startedAt,
            completedAt = startedAt,
            sessionType = type,
            notes = ""
        )
        val sews = exercises.mapIndexed { i, block ->
            val ex = ExerciseEntity(
                id = (i + 1).toLong(),
                name = block.name,
                primaryMuscle = "x",
                equipment = "x"
            )
            val se = SessionExerciseEntity(
                id = (i + 1).toLong(),
                sessionId = 1L,
                plannedExerciseId = null,
                actualExerciseId = ex.id,
                orderIdx = i,
                customLabel = null
            )
            val sets = block.sets.mapIndexed { idx, s ->
                SetLogEntity(
                    id = idx.toLong() + 1 + i * 100,
                    sessionExerciseId = se.id,
                    setIndex = idx,
                    weightKg = s.weight,
                    reps = s.reps,
                    note = s.note,
                    completedAt = startedAt
                )
            }
            SessionExerciseWithSets(sessionExercise = se, exercise = ex, sets = sets)
        }
        return SessionWithExercises(session = sessionEntity, exercises = sews)
    }

    private fun render(sessions: List<SessionWithExercises>): String {
        val out = ByteArrayOutputStream()
        TextLogExporter.write(sessions, out)
        return out.toByteArray().toString(Charsets.UTF_8)
    }
}
