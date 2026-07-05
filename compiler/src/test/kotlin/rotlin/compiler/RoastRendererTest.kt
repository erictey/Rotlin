package rotlin.compiler

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoastRendererTest {

    private fun diagsFor(src: String): DiagnosticBag {
        val lexed = Lexer(src).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        if (!lexed.diagnostics.hasErrors) TypeChecker(lexed.diagnostics).check(program)
        return lexed.diagnostics
    }

    @Test
    fun `renders header excerpt caret fix and vibe line`() {
        val src = "rizz score = 5\nyap(scor)\n"
        val diags = diagsFor(src)
        val out = RoastRenderer.renderAll("app.rot", src, diags)

        assertContains(out, "[app.rot line 2]")
        assertContains(out, "who is `scor`??")
        assertContains(out, "2 | yap(scor)")
        assertContains(out, "^")
        assertContains(out, "fix: did you mean `score`?")
        assertContains(out, ">>") // the vibe line
    }

    @Test
    fun `caret sits under the offending column`() {
        val src = "rizz score = 5\nyap(scor)\n"
        val diags = diagsFor(src)
        val out = RoastRenderer.renderAll("app.rot", src, diags)
        val lines = out.lines()
        val excerptIdx = lines.indexOfFirst { it.contains("2 | yap(scor)") }
        val caretLine = lines[excerptIdx + 1]
        val codeCol = lines[excerptIdx].indexOf("yap(scor)")
        // `scor` starts at source col 5 -> 4 chars into the excerpt text
        assertEquals(codeCol + 4, caretLine.indexOf('^'), "caret line was: `$caretLine`")
    }

    @Test
    fun `rendering is deterministic`() {
        val src = "yap(nope)\n"
        val a = RoastRenderer.renderAll("app.rot", src, diagsFor(src))
        val b = RoastRenderer.renderAll("app.rot", src, diagsFor(src))
        assertEquals(a, b)
    }

    @Test
    fun `aura math deducts 100 per error and 25 per warning`() {
        val src = "rizz s = \"x\"\nyap(s deadass)\nyap(nope)\nyap(alsonope)\nyap(third)\n"
        val diags = diagsFor(src)
        assertEquals(3, diags.all.count { it.severity == Severity.ERROR })
        assertEquals(1, diags.all.count { it.severity == Severity.WARNING })
        assertEquals(1000 - 300 - 25, RoastRenderer.auraScore(diags))
        assertContains(RoastRenderer.auraSummary(diags), "675 / 1000")
    }

    @Test
    fun `clean compile is a W`() {
        val diags = diagsFor("yap(\"hi\")\n")
        assertTrue(diags.all.isEmpty())
        assertContains(RoastRenderer.auraSummary(diags), "+1000 aura")
        assertContains(RoastRenderer.auraSummary(diags), "no cap")
    }

    @Test
    fun `aura never goes below zero`() {
        val src = (1..15).joinToString("") { "yap(nope$it)\n" }
        assertEquals(0, RoastRenderer.auraScore(diagsFor(src)))
    }
}
