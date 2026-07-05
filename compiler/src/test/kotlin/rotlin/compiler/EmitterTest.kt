package rotlin.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EmitterTest {

    private fun emit(src: String): EmitOutput {
        val lexed = Lexer(src).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        assertFalse(lexed.diagnostics.hasErrors, "unexpected errors: ${lexed.diagnostics.all}")
        return KotlinEmitter().emit(program)
    }

    @Test
    fun `full script emits line-aligned kotlin`() {
        val src = """
            summon kotlin.math.PI

            skibidi greet(name: lore) spits lore bet
            yeet "sup " + name
            periodt

            gyatt count = 0
            grind (count flops 3) bet
            count gains 1
            periodt
            yap(greet("bro"))
        """.trimIndent()

        val expected = """
            @file:JvmName("RotMain")
            import rotlin.runtime.*
            import kotlin.math.PI

            fun greet(name: String): String {
            return "sup " + name
            }

            var count = 0
            fun main() { while (count < 3) {
            count += 1
            }
            yap(greet("bro"))
            }
        """.trimIndent()

        assertEquals(expected, emit(src).ktText.trimEnd())
    }

    @Test
    fun `every rot line maps back through the line map`() {
        val src = "rizz a = 1\nyap(a)\n"
        val out = emit(src)
        // prelude is 2 lines: kt line 3 == rot line 1
        assertEquals(1, out.lineMap.toRotLine(3))
        assertEquals(2, out.lineMap.toRotLine(4))
        assertNull(out.lineMap.toRotLine(2))
    }

    @Test
    fun `hood joins package and runtime import on one line`() {
        val out = emit("hood my.pkg\nyap(1)\n")
        val lines = out.ktText.lines()
        assertEquals("@file:JvmName(\"RotMain\")", lines[0])
        assertEquals("", lines[1])
        assertEquals("package my.pkg; import rotlin.runtime.*", lines[2])
        assertEquals("my.pkg.RotMain", out.mainClassFqn)
    }

    @Test
    fun `word operators translate to kotlin operators`() {
        val out = emit("rizz ok = a twins b and c atleast d\nscore gains 5\nrizz r = 1 through 10\nrizz n = x otherwise 0\nrizz d = y deadass\n")
        val lines = out.ktText.lines()
        assertEquals("val ok = (a == b) && (c >= d)", lines[2])
        assertEquals("fun main() { score += 5", lines[3]) // first statement opens main inline
        assertEquals("val r = 1..10", lines[4])
        assertEquals("val n = x ?: 0", lines[5])
        assertEquals("val d = y.deadass()", lines[6])
        assertEquals("}", lines[7])
    }

    @Test
    fun `rotlin type names map to kotlin types`() {
        val out = emit("skibidi f(a: aura, r: ratio, s: lore, ok: fact, xs: squad<aura>, m: maybe lore) spits aura bet\nyeet a\nperiodt\n")
        assertEquals(
            "fun f(a: Int, r: Double, s: String, ok: Boolean, xs: MutableList<Int>, m: String?): Int {",
            out.ktText.lines()[2],
        )
    }

    @Test
    fun `identifiers that are kotlin keywords get backticks`() {
        val out = emit("rizz fun = 1\nyap(fun)\n")
        val lines = out.ktText.lines()
        assertEquals("val `fun` = 1", lines[2])
        assertEquals("fun main() { yap(`fun`)", lines[3])
    }

    @Test
    fun `sus bruh chain emits else on the shared line`() {
        val src = "sus (x twins 1) bet\nyap(\"one\")\nperiodt bruh bet\nyap(\"other\")\nperiodt\n"
        val out = emit(src)
        val lines = out.ktText.lines()
        assertEquals("fun main() { if (x == 1) {", lines[2])
        assertEquals("yap(\"one\")", lines[3])
        assertEquals("} else {", lines[4])
        assertEquals("yap(\"other\")", lines[5])
        assertEquals("}", lines[6])
        assertEquals("}", lines[7])
    }

    @Test
    fun `string templates emit with braced interpolation`() {
        val out = emit("yap(\"count \$count and \${a + b}!\")\n")
        assertEquals("fun main() { yap(\"count \${count} and \${a + b}!\")", out.ktText.lines()[2])
    }

    @Test
    fun `web dsl emits trailing lambdas and site call`() {
        val src = """
            gyatt score = 0

            drop site on 3000 bet
            page("/") bet
            bigyap("AURA CLICKER")
            yap("aura: ${'$'}score")
            smash("+1") does bet
            score gains 1
            periodt
            periodt
            periodt
        """.trimIndent()
        val out = emit(src)
        val lines = out.ktText.lines()
        assertEquals("var score = 0", lines[2])
        assertEquals("fun main() { site(3000) {", lines[4])
        assertEquals("page(\"/\") {", lines[5])
        assertEquals("bigyap(\"AURA CLICKER\")", lines[6])
        assertEquals("yap(\"aura: \${score}\")", lines[7])
        assertEquals("smash(\"+1\") {", lines[8])
        assertEquals("score += 1", lines[9])
        assertEquals("}", lines[10])
        assertEquals("}", lines[11])
        assertEquals("}", lines[12])
        assertEquals("}", lines[13])
    }

    @Test
    fun `ghosted emits null and based cringe emit booleans`() {
        val out = emit("rizz g = ghosted\nrizz t = based\nrizz f = cringe\n")
        val lines = out.ktText.lines()
        assertEquals("val g = null", lines[2])
        assertEquals("val t = true", lines[3])
        assertEquals("val f = false", lines[4])
    }
}
