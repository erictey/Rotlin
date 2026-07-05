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
    fun `alpha and beta declarations`() {
        assertEquals("(val count 5)", sexp("alpha count = 5"))
        assertEquals("(var lives: aura 3)", sexp("beta lives: aura = 3"))
    }

    @Test
    fun `maybe types parse`() {
        assertEquals("(val name: maybe lore (str \"x\"))", sexp("alpha name: maybe lore = \"x\""))
    }

    @Test
    fun `tung function with params spits and body`() {
        assertEquals(
            "(fun add (a: aura, b: aura): aura {(return (+ a b))})",
            sexp("tung add(a: aura, b: aura) spits aura {\n    yeet a + b\n}"),
        )
    }

    @Test
    fun `generic type arguments parse in type position`() {
        assertEquals("(val xs: squad<aura> (call squad))", sexp("alpha xs: squad<aura> = squad()"))
    }

    // ---- expressions + precedence ----

    @Test
    fun `arithmetic precedence`() {
        assertEquals("(+ 1 (* 2 3))", exprSexp("1 + 2 * 3"))
        assertEquals("(* (+ 1 2) 3)", exprSexp("(1 + 2) * 3"))
    }

    @Test
    fun `word and symbol operators with logical precedence`() {
        assertEquals("(&& (== a b) (> c d))", exprSexp("a is b and c > d"))
        assertEquals("(|| a (&& b c))", exprSexp("a or b and c"))
    }

    @Test
    fun `otherwise binds tighter than comparison like kotlin elvis`() {
        assertEquals("(> (?: x 0) 5)", exprSexp("x otherwise 0 > 5"))
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
    fun `member access chain with safe dot and deadahh`() {
        assertEquals("(?. user name)", exprSexp("user?.name"))
        assertEquals("(deadahh (. user name))", exprSexp("user.name deadahh"))
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
    fun `if else chains`() {
        assertEquals(
            "(if (== x 1) {(call yap (str \"one\"))} else {(call yap (str \"other\"))})",
            sexp("if (x is 1) {\n    yap(\"one\")\n} else {\n    yap(\"other\")\n}"),
        )
        assertEquals(
            "(if a {b} else (if c {d} else {e}))",
            sexp("if (a) {\nb\n} else if (c) {\nd\n} else {\ne\n}"),
        )
    }

    @Test
    fun `bare else closes the then block without a brace`() {
        assertEquals(
            "(if (== x 1) {(call yap 1)} else {(call yap 2)})",
            sexp("if (x is 1) {\n    yap(1)\nelse {\n    yap(2)\n}"),
        )
        assertEquals(
            "(if a {b} else (if c {d} else {e}))",
            sexp("if (a) {\nb\nelse if (c) {\nd\nelse {\ne\n}"),
        )
    }

    @Test
    fun `grind loop with dip and skip`() {
        assertEquals(
            "(while (< i 3) {(+= i 1); (break); (continue)})",
            sexp("grind (i < 3) {\n    i gains 1\n    dip\n    skip\n}"),
        )
    }

    @Test
    fun `one line block`() {
        assertEquals("(if x {(call yap 1)})", sexp("if (x) { yap(1) }"))
    }

    @Test
    fun `assignment and compound assignment`() {
        assertEquals("(= x 5)", sexp("x = 5"))
        assertEquals("(+= score 100)", sexp("score gains 100"))
        assertEquals("(-= hp 25)", sexp("hp loses 25"))
    }

    @Test
    fun `summon and package parse`() {
        val (program, diags) = parse("package my.pkg\nsummon kotlin.math.PI\nsummon rot.web.*\nyap(1)")
        assertFalse(diags.hasErrors)
        assertEquals("my.pkg", program.pkg?.path)
        assertEquals(listOf("kotlin.math.PI" to false, "rot.web" to true),
            program.summons.map { it.path to it.wildcard })
    }

    // ---- web DSL forms ----

    @Test
    fun `trailing brace block becomes call lambda`() {
        assertEquals(
            "(call page (str \"/\") {(call bigyap (str \"YO\"))})",
            sexp("page(\"/\") {\n    bigyap(\"YO\")\n}"),
        )
    }

    @Test
    fun `does before the block is optional sugar`() {
        assertEquals(
            "(call smash (str \"+1\") {(+= score 1)})",
            sexp("smash(\"+1\") does {\n    score gains 1\n}"),
        )
    }

    @Test
    fun `drop site on parses port and block`() {
        assertEquals(
            "(drop-site 3000 {(call page (str \"/\") {(call yap (str \"hi\"))})})",
            sexp("drop site on 3000 {\n    page(\"/\") {\n        yap(\"hi\")\n    }\n}"),
        )
    }

    @Test
    fun `drop without site gives a helpful hint`() {
        val (_, diags) = parse("drop 3000 {\n}")
        assertTrue(diags.hasErrors)
        assertTrue(diags.all.any { it.hint!!.contains("drop site on") })
    }

    // ---- banned symbols recover with diagnostics ----

    @Test
    fun `banned equality symbol reports error but parses as is`() {
        val (program, diags) = parse("if (a == b) {\nyap(1)\n}")
        assertTrue(diags.hasErrors)
        assertTrue(diags.all.any { it.hint!!.contains("is") })
        assertEquals("(if (== a b) {(call yap 1)})", program.sexp())
    }

    @Test
    fun `lt and gt are legal comparison operators`() {
        val (program, diags) = parse("if (a < b) {\nyap(1)\n}")
        assertFalse(diags.hasErrors, "unexpected errors: ${diags.all}")
        assertEquals("(if (< a b) {(call yap 1)})", program.sexp())
        assertEquals("(> x 5)", exprSexp("x > 5"))
    }

    @Test
    fun `old block words are plain identifiers now`() {
        assertEquals("(val bet 1)", sexp("alpha bet = 1"))
        assertEquals("(val periodt 2)", sexp("alpha periodt = 2"))
    }

    // ---- oop ----

    @Test
    fun `class with ctor props inheritance and vibes`() {
        assertEquals(
            "(class Dog (val name: lore, age: aura) :Animal() ~Fetchable,Petable " +
                "{(override fun speak (): lore {(return (str \"woof\"))})})",
            sexp(
                "class Dog(alpha name: lore, age: aura) is a Animal vibes with Fetchable, Petable {\n" +
                    "override tung speak() spits lore {\nyeet \"woof\"\n}\n}",
            ),
        )
    }

    @Test
    fun `class with super constructor args`() {
        assertEquals(
            "(class Puppy :Dog((str \"rex\")) {})",
            sexp("class Puppy() is a Dog(\"rex\") {\n}"),
        )
    }

    @Test
    fun `npc and vibe declarations`() {
        assertEquals("(npc Config {(val port 3000)})", sexp("npc Config {\nalpha port = 3000\n}"))
        assertEquals(
            "(vibe Fetchable {(fun fetch (): lore)})",
            sexp("vibe Fetchable {\ntung fetch() spits lore\n}"),
        )
    }

    @Test
    fun `private members and this references`() {
        assertEquals(
            "(class A {(private val secret 5); (fun expose (): aura {(return (. this secret))})})",
            sexp("class A() {\nprivate alpha secret = 5\ntung expose() spits aura {\nyeet this.secret\n}\n}"),
        )
    }

    // ---- control flow ----

    @Test
    fun `when with values lists and else default`() {
        assertEquals(
            "(when x [1 -> {(call yap (str \"one\"))}] [2, 3 -> {(call yap (str \"few\"))}] " +
                "[else -> {(call yap (str \"many\"))}])",
            sexp("when (x) {\n1 -> yap(\"one\")\n2, 3 -> yap(\"few\")\nelse -> yap(\"many\")\n}"),
        )
    }

    @Test
    fun `when branch can be a block`() {
        assertEquals(
            "(when x [1 -> {(call yap 1); (call yap 2)}])",
            sexp("when (x) {\n1 -> {\nyap(1)\nyap(2)\n}\n}"),
        )
    }

    @Test
    fun `mog over squads and ranges`() {
        assertEquals(
            "(for item in (call squad 1 2) {(call yap item)})",
            sexp("mog (item inside squad(1, 2)) {\nyap(item)\n}"),
        )
        assertEquals(
            "(for i in (.. 1 3) {(call yap i)})",
            sexp("mog (i inside 1 through 3) {\nyap(i)\n}"),
        )
    }

    @Test
    fun `catch closes the try block like else`() {
        assertEquals(
            "(try {(call risky)} catch oops {(call yap oops)})",
            sexp("try {\nrisky()\ncatch (oops) {\nyap(oops)\n}"),
        )
        assertEquals(
            "(try {(call risky)} catch oops {(call yap oops)})",
            sexp("try {\nrisky()\n} catch (oops) {\nyap(oops)\n}"),
        )
    }

    @Test
    fun `crashout parses a throw`() {
        assertEquals("(throw (str \"bad moment\"))", sexp("crashout \"bad moment\""))
    }

    @Test
    fun `indexing parses and can be assigned`() {
        assertEquals("([] xs 0)", exprSexp("xs[0]"))
        assertEquals("(= ([] xs 0) 5)", sexp("xs[0] = 5"))
    }

    // ---- error recovery ----

    @Test
    fun `parse error synchronizes and later statements still parse`() {
        val (program, diags) = parse("alpha = 5\nalpha ok = 1\n")
        assertTrue(diags.hasErrors)
        assertTrue(program.sexp().contains("(val ok 1)"))
    }
}
