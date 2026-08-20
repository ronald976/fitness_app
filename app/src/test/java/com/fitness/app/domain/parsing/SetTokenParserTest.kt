package com.fitness.app.domain.parsing

import com.fitness.app.domain.parsing.SetTokenParser.QuickAdd
import com.fitness.app.domain.parsing.SetTokenParser.QuickLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTokenParserTest {

    // ── parseToken ──────────────────────────────────────────────────────

    @Test
    fun `weighted token parses weight and reps`() {
        val t = SetTokenParser.parseToken("80x8")!!
        assertEquals(80.0, t.weightKg!!, 0.0)
        assertEquals(8, t.reps)
        assertTrue(!t.toFailure)
    }

    @Test
    fun `decimal weight parses`() {
        val t = SetTokenParser.parseToken("92.5x8")!!
        assertEquals(92.5, t.weightKg!!, 0.0)
        assertEquals(8, t.reps)
    }

    @Test
    fun `failure suffix sets toFailure`() {
        val t = SetTokenParser.parseToken("80x6f")!!
        assertTrue(t.toFailure)
        assertEquals(6, t.reps)
    }

    @Test
    fun `reps-only token has null weight`() {
        val t = SetTokenParser.parseToken("x6")!!
        assertNull(t.weightKg)
        assertEquals(6, t.reps)
        assertTrue(!t.toFailure)
    }

    @Test
    fun `reps-only failure token`() {
        val t = SetTokenParser.parseToken("x8f")!!
        assertNull(t.weightKg)
        assertEquals(8, t.reps)
        assertTrue(t.toFailure)
    }

    @Test
    fun `garbage tokens do not parse`() {
        assertNull(SetTokenParser.parseToken("bench"))
        assertNull(SetTokenParser.parseToken("8"))
        assertNull(SetTokenParser.parseToken("x"))
        assertNull(SetTokenParser.parseToken("80x"))
    }

    @Test
    fun `parseLine drops non-tokens and keeps sets`() {
        val line = SetTokenParser.parseLine("80x8 x8f junk 70x6")
        assertEquals(3, line.size)
        assertEquals(80.0, line[0].weightKg!!, 0.0)
        assertNull(line[1].weightKg)
        assertEquals(70.0, line[2].weightKg!!, 0.0)
    }

    // ── parseQuickLog ───────────────────────────────────────────────────

    @Test
    fun `lone reps-only token logs that many blank sets`() {
        assertEquals(QuickLog.Placeholder(4), SetTokenParser.parseQuickLog("x4"))
        assertEquals(QuickLog.Placeholder(3), SetTokenParser.parseQuickLog("  x3  "))
    }

    @Test
    fun `quick log placeholder count is clamped`() {
        assertEquals(QuickLog.Placeholder(20), SetTokenParser.parseQuickLog("x99"))
    }

    @Test
    fun `two reps-only tokens stay real sets`() {
        val parsed = SetTokenParser.parseQuickLog("x8 x8") as QuickLog.Sets
        assertEquals(2, parsed.tokens.size)
        assertNull(parsed.tokens[0].weightKg)
    }

    @Test
    fun `weighted lone token stays a real set`() {
        val parsed = SetTokenParser.parseQuickLog("80x8") as QuickLog.Sets
        assertEquals(1, parsed.tokens.size)
        assertEquals(80.0, parsed.tokens[0].weightKg!!, 0.0)
    }

    @Test
    fun `lone failure token stays a real set`() {
        val parsed = SetTokenParser.parseQuickLog("x8f") as QuickLog.Sets
        assertEquals(1, parsed.tokens.size)
        assertTrue(parsed.tokens[0].toFailure)
    }

    @Test
    fun `quick log with no tokens is null`() {
        assertNull(SetTokenParser.parseQuickLog(""))
        assertNull(SetTokenParser.parseQuickLog("bench press"))
    }

    // ── parseQuickAdd ───────────────────────────────────────────────────

    @Test
    fun `lone reps-only token is a placeholder`() {
        assertEquals(QuickAdd.Placeholder("cables", 6), SetTokenParser.parseQuickAdd("cables x6"))
        assertEquals(QuickAdd.Placeholder("abs", 3), SetTokenParser.parseQuickAdd("abs x3"))
    }

    @Test
    fun `placeholder count is clamped to 1_20`() {
        assertEquals(QuickAdd.Placeholder("abs", 20), SetTokenParser.parseQuickAdd("abs x99"))
    }

    @Test
    fun `weighted final token is a single set, not a placeholder (regression)`() {
        val parsed = SetTokenParser.parseQuickAdd("leg press 200x10")
        assertTrue(parsed is QuickAdd.Sets)
        parsed as QuickAdd.Sets
        assertEquals("leg press", parsed.name)
        assertEquals(1, parsed.tokens.size)
        assertEquals(200.0, parsed.tokens[0].weightKg!!, 0.0)
        assertEquals(10, parsed.tokens[0].reps)
    }

    @Test
    fun `multiple weighted tokens become multiple sets`() {
        val parsed = SetTokenParser.parseQuickAdd("leg press 200x10 200x8 200x8") as QuickAdd.Sets
        assertEquals("leg press", parsed.name)
        assertEquals(3, parsed.tokens.size)
    }

    @Test
    fun `two reps-only tokens are sets, not a placeholder`() {
        val parsed = SetTokenParser.parseQuickAdd("bench x8 x8") as QuickAdd.Sets
        assertEquals("bench", parsed.name)
        assertEquals(2, parsed.tokens.size)
    }

    @Test
    fun `lone reps-only failure token is a single set`() {
        val parsed = SetTokenParser.parseQuickAdd("bench x8f") as QuickAdd.Sets
        assertEquals(1, parsed.tokens.size)
        assertTrue(parsed.tokens[0].toFailure)
    }

    @Test
    fun `bare name with no tokens is an empty sets add`() {
        val parsed = SetTokenParser.parseQuickAdd("leg press") as QuickAdd.Sets
        assertEquals("leg press", parsed.name)
        assertTrue(parsed.tokens.isEmpty())
    }

    @Test
    fun `empty or token-only input returns null`() {
        assertNull(SetTokenParser.parseQuickAdd(""))
        assertNull(SetTokenParser.parseQuickAdd("   "))
        assertNull(SetTokenParser.parseQuickAdd("x6"))
    }

    // ── resolveWeights ──────────────────────────────────────────────────

    @Test
    fun `reps-only tokens inherit previous token weight`() {
        val tokens = SetTokenParser.parseLine("80x8 x8f x7")
        val resolved = SetTokenParser.resolveWeights(tokens, fallbackKg = null)
        assertEquals(listOf(80.0, 80.0, 80.0), resolved.map { it.weightKg })
        assertTrue(resolved[1].toFailure)
    }

    @Test
    fun `leading reps-only token uses fallback`() {
        val tokens = SetTokenParser.parseLine("x8 x8")
        val resolved = SetTokenParser.resolveWeights(tokens, fallbackKg = 60.0)
        assertEquals(listOf(60.0, 60.0), resolved.map { it.weightKg })
    }

    @Test
    fun `no weight and no fallback resolves to zero`() {
        val tokens = SetTokenParser.parseLine("x8")
        val resolved = SetTokenParser.resolveWeights(tokens, fallbackKg = null)
        assertEquals(0.0, resolved[0].weightKg!!, 0.0)
    }
}
