package rotlin.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParserTest {

    private fun parse(src: String): Pair<Program, DiagnosticBag> {
        val lexed = Lexer(src).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        return program to lexed.diagnostics
    }

    private fun sexp(src: String): String {
        val (program, diags) = parse(src)
        assertFalse(diags.hasErrors, "unexpected parse errors: ${diags.all}")
        return program.sexp()
    }

    private fun exprSexp(src: String): String = sexp("yap($src)")
        .removePrefix("(call yap ").removeSuffix(")")

    // ---- declarations ----

    @Test
    fun `rizz and gyatt declarations`() {
        assertEquals("(val count 5)", sexp("rizz count = 5"))
        assertEquals("(var lives: aura 3)", sexp("gyatt lives: aura = 3"))
    }

    @Test
    fun `maybe types parse`() {
        assertEquals("(val name: maybe lore (str \"x\"))", sexp("rizz name: maybe lore = \"x\""))
    }

    @Test
    fun `skibidi function with params spits and body`() {
        assertEquals(
            "(fun add (a: aura, b: aura): aura {(return (+ a b))})",
            sexp("skibidi add(a: aura, b: aura) spits aura bet\n    yeet a + b\nperiodt"),
        )
    }

    @Test
    fun `generic type arguments parse in type position`() {
        assertEquals("(val xs: squad<aura> (call squad))", sexp("rizz xs: squad<aura> = squad()"))
    }

    // ---- expressions + precedence ----

    @Test
    fun `arithmetic precedence`() {
        assertEquals("(+ 1 (* 2 3))", exprSexp("1 + 2 * 3"))
        assertEquals("(* (+ 1 2) 3)", exprSexp("(1 + 2) * 3"))
    }

    @Test
    fun `word operators with logical precedence`() {
        assertEquals("(&& (== a b) (> c d))", exprSexp("a twins b and c clears d"))
        assertEquals("(|| a (&& b c))", exprSexp("a or b and c"))
    }

    @Test
    fun `otherwise binds tighter than comparison like kotlin elvis`() {
        assertEquals("(> (?: x 0) 5)", exprSexp("x otherwise 0 clears 5"))
    }

    @Test
    fun `through range operator`() {
        assertEquals("(.. 1 10)", exprSexp("1 through 10"))
    }

    @Test
    fun `unary not and negation`() {
        assertEquals("(|| (! a) (! b))", exprSexp("not a or not b"))
        assertEquals("(+ (neg 5) 3)", exprSexp("-5 + 3"))
    }

    @Test
    fun `member access chain with safe dot and deadass`() {
        assertEquals("(?. user name)", exprSexp("user?.name"))
        assertEquals("(deadass (. user name))", exprSexp("user.name deadass"))
        assertEquals("(call (. list add) 5)", exprSexp("list.add(5)"))
    }

    @Test
    fun `string template with interpolation parses inner expression`() {
        assertEquals(
            "(str \"sum \" (interp (+ a b)))",
            exprSexp("\"sum \${a + b}\""),
        )
    }

    // ---- statements ----

    @Test
    fun `sus bruh chains`() {
        assertEquals(
            "(if (== x 1) {(call yap (str \"one\"))} else {(call yap (str \"other\"))})",
            sexp("sus (x twins 1) bet\n    yap(\"one\")\nperiodt bruh bet\n    yap(\"other\")\nperiodt"),
        )
        assertEquals(
            "(if a {b} else (if c {d} else {e}))",
            sexp("sus (a) bet\nb\nperiodt bruh sus (c) bet\nd\nperiodt bruh bet\ne\nperiodt"),
        )
    }

    @Test
    fun `bare bruh closes the then block without periodt`() {
        assertEquals(
            "(if (== x 1) {(call yap 1)} else {(call yap 2)})",
            sexp("sus (x twins 1) bet\n    yap(1)\nbruh bet\n    yap(2)\nperiodt"),
        )
        assertEquals(
            "(if a {b} else (if c {d} else {e}))",
            sexp("sus (a) bet\nb\nbruh sus (c) bet\nd\nbruh bet\ne\nperiodt"),
        )
    }

    @Test
    fun `grind loop with dip and skip`() {
        assertEquals(
            "(while (< i 3) {(+= i 1); (break); (continue)})",
            sexp("grind (i flops 3) bet\n    i gains 1\n    dip\n    skip\nperiodt"),
        )
    }

    @Test
    fun `one line block`() {
        assertEquals("(if x {(call yap 1)})", sexp("sus (x) bet yap(1) periodt"))
    }

    @Test
    fun `assignment and compound assignment`() {
        assertEquals("(= x 5)", sexp("x = 5"))
        assertEquals("(+= score 100)", sexp("score gains 100"))
        assertEquals("(-= hp 25)", sexp("hp loses 25"))
    }

    @Test
    fun `summon and hood parse`() {
        val (program, diags) = parse("hood my.pkg\nsummon kotlin.math.PI\nsummon rot.web.*\nyap(1)")
        assertFalse(diags.hasErrors)
        assertEquals("my.pkg", program.hood?.path)
        assertEquals(listOf("kotlin.math.PI" to false, "rot.web" to true),
            program.summons.map { it.path to it.wildcard })
    }

    // ---- web DSL forms ----

    @Test
    fun `trailing bet block becomes call lambda`() {
        assertEquals(
            "(call page (str \"/\") {(call bigyap (str \"YO\"))})",
            sexp("page(\"/\") bet\n    bigyap(\"YO\")\nperiodt"),
        )
    }

    @Test
    fun `does before bet is optional sugar`() {
        assertEquals(
            "(call smash (str \"+1\") {(+= score 1)})",
            sexp("smash(\"+1\") does bet\n    score gains 1\nperiodt"),
        )
    }

    @Test
    fun `drop site on parses port and block`() {
        assertEquals(
            "(drop-site 3000 {(call page (str \"/\") {(call yap (str \"hi\"))})})",
            sexp("drop site on 3000 bet\n    page(\"/\") bet\n        yap(\"hi\")\n    periodt\nperiodt"),
        )
    }

    @Test
    fun `drop without site gives a helpful roast`() {
        val (_, diags) = parse("drop 3000 bet\nperiodt")
        assertTrue(diags.hasErrors)
        assertTrue(diags.all.any { it.hint!!.contains("drop site on") })
    }

    // ---- banned symbols recover with diagnostics ----

    @Test
    fun `banned equality symbol reports error but parses as twins`() {
        val (program, diags) = parse("sus (a == b) bet\nyap(1)\nperiodt")
        assertTrue(diags.hasErrors)
        assertTrue(diags.all.any { it.hint!!.contains("twins") })
        assertEquals("(if (== a b) {(call yap 1)})", program.sexp())
    }

    @Test
    fun `lt gt in expression position report error but parse as comparison`() {
        val (program, diags) = parse("sus (a < b) bet\nyap(1)\nperiodt")
        assertTrue(diags.hasErrors)
        assertTrue(diags.all.any { it.hint!!.contains("flops") })
        assertEquals("(if (< a b) {(call yap 1)})", program.sexp())
    }

    @Test
    fun `braces report error but parse as bet periodt`() {
        val (program, diags) = parse("sus (x) { yap(1) }")
        assertTrue(diags.hasErrors)
        assertEquals("(if x {(call yap 1)})", program.sexp())
    }

    // ---- error recovery ----

    @Test
    fun `parse error synchronizes and later statements still parse`() {
        val (program, diags) = parse("rizz = 5\nrizz ok = 1\n")
        assertTrue(diags.hasErrors)
        assertTrue(program.sexp().contains("(val ok 1)"))
    }
}
