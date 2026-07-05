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

            tung greet(name: lore) spits lore {
            yeet "sup " + name
            }

            beta count = 0
            grind (count < 3) {
            count gains 1
            }
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
        val src = "alpha a = 1\nyap(a)\n"
        val out = emit(src)
        // prelude is 2 lines: kt line 3 == rot line 1
        assertEquals(1, out.lineMap.toRotLine(3))
        assertEquals(2, out.lineMap.toRotLine(4))
        assertNull(out.lineMap.toRotLine(2))
    }

    @Test
    fun `package joins kotlin package and runtime import on one line`() {
        val out = emit("package my.pkg\nyap(1)\n")
        val lines = out.ktText.lines()
        assertEquals("@file:JvmName(\"RotMain\")", lines[0])
        assertEquals("", lines[1])
        assertEquals("package my.pkg; import rotlin.runtime.*", lines[2])
        assertEquals("my.pkg.RotMain", out.mainClassFqn)
    }

    @Test
    fun `word operators translate to kotlin operators`() {
        val out = emit("alpha ok = a is b and c atleast d\nscore gains 5\nalpha r = 1 through 10\nalpha n = x otherwise 0\nalpha d = y deadahh\n")
        val lines = out.ktText.lines()
        assertEquals("val ok = (a == b) && (c >= d)", lines[2])
        assertEquals("fun main() { score += 5", lines[3]) // first statement opens main inline
        assertEquals("val r = 1..10", lines[4])
        assertEquals("val n = x ?: 0", lines[5])
        assertEquals("val d = y.deadahh()", lines[6])
        assertEquals("}", lines[7])
    }

    @Test
    fun `rotlin type names map to kotlin types`() {
        val out = emit("tung f(a: aura, r: ratio, s: lore, ok: fact, xs: squad<aura>, m: maybe lore) spits aura {\nyeet a\n}\n")
        assertEquals(
            "fun f(a: Int, r: Double, s: String, ok: Boolean, xs: MutableList<Int>, m: String?): Int {",
            out.ktText.lines()[2],
        )
    }

    @Test
    fun `identifiers that are kotlin keywords get backticks`() {
        val out = emit("alpha fun = 1\nyap(fun)\n")
        val lines = out.ktText.lines()
        assertEquals("val `fun` = 1", lines[2])
        assertEquals("fun main() { yap(`fun`)", lines[3])
    }

    @Test
    fun `if else chain emits else on the shared line`() {
        val src = "if (x is 1) {\nyap(\"one\")\n} else {\nyap(\"other\")\n}\n"
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
            beta score = 0

            drop site on 3000 {
            page("/") {
            bigyap("AURA CLICKER")
            yap("aura: ${'$'}score")
            smash("+1") does {
            score gains 1
            }
            }
            }
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
    fun `class emits open class with modifiers mapped`() {
        val src = """
            class Dog(alpha name: lore) is a Animal vibes with Fetchable {
            private beta barks = 0
            override tung speak() spits lore {
            yeet "woof: " + this.name
            }
            tung bark() {
            barks gains 1
            }
            }
        """.trimIndent()
        val lines = emit(src).ktText.lines()
        assertEquals("open class Dog(val name: String) : Animal(), Fetchable {", lines[2])
        assertEquals("private var barks = 0", lines[3])
        assertEquals("override fun speak(): String {", lines[4])
        assertEquals("return \"woof: \" + this.name", lines[5])
        assertEquals("}", lines[6])
        assertEquals("open fun bark() {", lines[7])
        assertEquals("barks += 1", lines[8])
        assertEquals("}", lines[9])
        assertEquals("}", lines[10])
    }

    @Test
    fun `npc emits object and vibe emits interface`() {
        val out = emit("npc Config {\nalpha port = 3000\n}\nvibe Fetchable {\ntung fetch() spits lore\n}\n")
        val lines = out.ktText.lines()
        assertEquals("object Config {", lines[2])
        assertEquals("val port = 3000", lines[3])
        assertEquals("}", lines[4])
        assertEquals("interface Fetchable {", lines[5])
        assertEquals("fun fetch(): String", lines[6])
        assertEquals("}", lines[7])
    }

    @Test
    fun `when emits kotlin when with inline and else branches`() {
        val src = "when (x) {\n1 -> yap(\"one\")\n2, 3 -> yap(\"few\")\nelse -> yap(\"nah\")\n}\n"
        val lines = emit(src).ktText.lines()
        assertEquals("fun main() { when (x) {", lines[2])
        assertEquals("1 -> yap(\"one\")", lines[3])
        assertEquals("2, 3 -> yap(\"few\")", lines[4])
        assertEquals("else -> yap(\"nah\")", lines[5])
        assertEquals("}", lines[6])
        assertEquals("}", lines[7])
    }

    @Test
    fun `mog emits for and index emits brackets`() {
        val src = "alpha xs = squad(1, 2, 3)\nmog (x inside xs) {\nyap(xs[0] + x)\n}\n"
        val lines = emit(src).ktText.lines()
        assertEquals("val xs = squad(1, 2, 3)", lines[2])
        assertEquals("fun main() { for (x in xs) {", lines[3])
        assertEquals("yap(xs[0] + x)", lines[4])
        assertEquals("}", lines[5])
        assertEquals("}", lines[6])
    }

    @Test
    fun `try emits try catch and crashout wraps in skill issue`() {
        val src = "try {\ncrashout \"nope\"\n} catch (oops) {\nyap(\"got: \" + oops)\n}\n"
        val lines = emit(src).ktText.lines()
        assertEquals("fun main() { try {", lines[2])
        assertEquals("throw SkillIssue(lore(\"nope\"))", lines[3])
        assertEquals("} catch (oops: Exception) {", lines[4])
        assertEquals("yap(\"got: \" + oops)", lines[5])
        assertEquals("}", lines[6])
        assertEquals("}", lines[7])
    }

    @Test
    fun `null and boolean literals emit as kotlin literals`() {
        val out = emit("alpha g = null\nalpha t = true\nalpha f = false\n")
        val lines = out.ktText.lines()
        assertEquals("val g = null", lines[2])
        assertEquals("val t = true", lines[3])
        assertEquals("val f = false", lines[4])
    }
}
