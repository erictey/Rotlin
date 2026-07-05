package rotlin.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeCheckerTest {

    private fun check(src: String): DiagnosticBag {
        val lexed = Lexer(src).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        assertTrue(!lexed.diagnostics.hasErrors, "parse should be clean first: ${lexed.diagnostics.all}")
        TypeChecker(lexed.diagnostics).check(program)
        return lexed.diagnostics
    }

    private fun errorCodes(src: String): List<String> =
        check(src).all.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun assertClean(src: String) {
        val diags = check(src)
        assertTrue(diags.all.none { it.severity == Severity.ERROR }, "expected clean, got: ${diags.all}")
    }

    // ---- inference + mutability ----

    @Test
    fun `rizz cannot be reassigned`() {
        assertEquals(listOf("E_RIZZ_LOCKED"), errorCodes("rizz x = 5\nx = 6\n"))
    }

    @Test
    fun `gyatt reassignment must keep the type`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("gyatt x = 5\nx = \"nope\"\n"))
        assertClean("gyatt x = 5\nx = 6\n")
    }

    @Test
    fun `declared type must match initializer`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("rizz x: aura = \"words\"\n"))
    }

    @Test
    fun `gains needs numbers or lore`() {
        assertClean("gyatt s = 1\ns gains 2\n")
        assertClean("gyatt s = \"a\"\ns gains \"b\"\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("gyatt s = 1\ns gains \"b\"\n"))
    }

    // ---- names ----

    @Test
    fun `undefined name gets a did-you-mean`() {
        val diags = check("rizz score = 5\nyap(scor)\n")
        val err = diags.all.single { it.code == "E_WHO_IS_THAT" }
        assertTrue(err.hint!!.contains("score"), "hint was: ${err.hint}")
    }

    @Test
    fun `summoned names are trusted as interop`() {
        assertClean("summon kotlin.math.PI\nyap(PI)\n")
    }

    @Test
    fun `defining a function named like a runtime builtin warns`() {
        val diags = check("skibidi yap(x: lore) bet\nperiodt\n")
        assertTrue(diags.all.any { it.code == "W_SHADOW" })
    }

    // ---- functions ----

    @Test
    fun `call arity is checked`() {
        assertEquals(
            listOf("E_ARITY"),
            errorCodes("skibidi add(a: aura, b: aura) spits aura bet\nyeet a + b\nperiodt\nyap(add(1))\n"),
        )
    }

    @Test
    fun `argument types are checked`() {
        assertEquals(
            listOf("E_TYPE_MISMATCH"),
            errorCodes("skibidi add(a: aura, b: aura) spits aura bet\nyeet a + b\nperiodt\nyap(add(1, \"x\"))\n"),
        )
    }

    @Test
    fun `spits function must yeet on every path`() {
        assertEquals(
            listOf("E_MISSING_YEET"),
            errorCodes("skibidi f(x: aura) spits aura bet\nsus (x clears 0) bet\nyeet x\nperiodt\nperiodt\n"),
        )
        assertClean("skibidi f(x: aura) spits aura bet\nsus (x clears 0) bet\nyeet x\nbruh bet\nyeet 0\nperiodt\nperiodt\n")
    }

    @Test
    fun `yeet type must match spits`() {
        assertEquals(
            listOf("E_TYPE_MISMATCH"),
            errorCodes("skibidi f() spits aura bet\nyeet \"words\"\nperiodt\n"),
        )
    }

    // ---- null safety ----

    @Test
    fun `ghosted needs a maybe type`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("gyatt name: lore = ghosted\n"))
        assertEquals(listOf("E_GHOST_ONLY"), errorCodes("rizz x = ghosted\n"))
        assertClean("rizz x: maybe lore = ghosted\n")
    }

    @Test
    fun `deref of maybe without a check is unsafe`() {
        val diags = check("rizz name: maybe lore = listen()\nyap(name.length)\n")
        val err = diags.all.single { it.code == "E_NULL_UNSAFE" }
        assertTrue(err.hint!!.contains("aint ghosted"))
        assertTrue(err.hint!!.contains("?."))
        assertTrue(err.hint!!.contains("deadass"))
    }

    @Test
    fun `safe call and otherwise are fine on maybe`() {
        assertClean("rizz name: maybe lore = listen()\nyap(name?.length)\nyap(name otherwise \"anon\")\n")
    }

    @Test
    fun `otherwise unwraps the maybe`() {
        assertClean("rizz name: maybe lore = listen()\nrizz n = name otherwise \"anon\"\nyap(n.length)\n")
    }

    @Test
    fun `smart cast after aint ghosted on a rizz`() {
        assertClean("rizz name: maybe lore = listen()\nsus (name aint ghosted) bet\nyap(name.length)\nperiodt\n")
    }

    @Test
    fun `twins ghosted narrows the else branch`() {
        assertClean(
            "rizz name: maybe lore = listen()\nsus (name twins ghosted) bet\nyap(\"ghosted\")\nbruh bet\nyap(name.length)\nperiodt\n",
        )
    }

    @Test
    fun `gyatt does not smart cast and the roast says why`() {
        val diags = check("gyatt name: maybe lore = listen()\nsus (name aint ghosted) bet\nyap(name.length)\nperiodt\n")
        val err = diags.all.single { it.code == "E_NULL_UNSAFE" }
        assertTrue(err.hint!!.contains("gyatt"), "hint was: ${err.hint}")
    }

    @Test
    fun `deadass on something never ghosted warns`() {
        val diags = check("rizz s = \"x\"\nyap(s deadass)\n")
        assertTrue(diags.all.any { it.code == "W_NEVER_GHOSTED" })
    }

    // ---- operators ----

    @Test
    fun `mixed number math is an error with a conversion hint`() {
        val diags = check("yap(1 + 2.5)\n")
        val err = diags.all.single { it.code == "E_MIXED_NUMBERS" }
        assertTrue(err.hint!!.contains("ratio("))
    }

    @Test
    fun `lore concat accepts anything on the right`() {
        assertClean("yap(\"score: \" + 5)\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("yap(5 + \"score\")\n"))
    }

    @Test
    fun `logic operators need facts`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("sus (1 and based) bet\nperiodt\n"))
    }

    @Test
    fun `comparisons need matching numbers`() {
        assertClean("sus (1 clears 0) bet\nperiodt\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("sus (1 clears \"a\") bet\nperiodt\n"))
    }

    // ---- control flow ----

    @Test
    fun `dip and skip outside a loop are errors`() {
        assertEquals(listOf("E_DIP_NOWHERE"), errorCodes("dip\n"))
        assertEquals(listOf("E_SKIP_NOWHERE"), errorCodes("skip\n"))
        assertClean("grind (based) bet\ndip\nperiodt\n")
    }

    // ---- oop ----

    @Test
    fun `constructor calls check arity and types`() {
        val cls = "sigma Dog(rizz name: lore) bet\nperiodt\n"
        assertEquals(listOf("E_ARITY"), errorCodes("${cls}rizz d = Dog()\n"))
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("${cls}rizz d = Dog(5)\n"))
        assertClean("${cls}rizz d = Dog(\"rex\")\nyap(d.name)\n")
    }

    @Test
    fun `me outside a sigma is an error`() {
        assertEquals(listOf("E_ME_NOWHERE"), errorCodes("yap(me)\n"))
    }

    @Test
    fun `methods see ctor props through me`() {
        assertClean(
            "sigma Dog(rizz name: lore) bet\nskibidi intro() spits lore bet\nyeet \"i am \" + me.name\nperiodt\nperiodt\n",
        )
    }

    // ---- squads, stashes, mog ----

    @Test
    fun `squad elements infer and mixed squads roast`() {
        assertClean("rizz xs = squad(1, 2, 3)\nyap(xs[0] + 1)\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("rizz xs = squad(1, \"two\")\n"))
    }

    @Test
    fun `stash indexing returns a maybe`() {
        // m["k"] might be ghosted - using it raw as a number is the teaching moment
        val diags = check("gyatt m: stash<lore, aura> = stash()\nrizz v = m[\"k\"]\nyap(v + 1)\n")
        assertTrue(diags.all.any { it.code == "E_MIXED_NUMBERS" || it.code == "E_TYPE_MISMATCH" })
        assertClean("gyatt m: stash<lore, aura> = stash()\nyap((m[\"k\"] otherwise 0) + 1)\n")
    }

    @Test
    fun `mog over a squad types the loop variable`() {
        assertClean("rizz xs = squad(1, 2)\nmog (x inside xs) bet\nyap(x + 1)\nperiodt\n")
        assertClean("mog (i inside 1 through 5) bet\nyap(i * 2)\nperiodt\n")
    }

    @Test
    fun `mog over something unloopable roasts`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("mog (x inside 5) bet\nperiodt\n"))
    }

    @Test
    fun `vibecheck branch values must match the subject`() {
        assertEquals(
            listOf("E_TYPE_MISMATCH"),
            errorCodes("rizz x = 5\nvibecheck (x) bet\n\"one\" -> yap(1)\nperiodt\n"),
        )
        assertClean("rizz x = 5\nvibecheck (x) bet\n1 -> yap(1)\nbruh -> yap(0)\nperiodt\n")
    }

    // ---- exceptions ----

    @Test
    fun `finna scopes the catch name to the catch block`() {
        assertClean("finna bet\nyap(1)\ncaught in 4k (oops) bet\nyap(oops)\nperiodt\n")
        assertEquals(
            listOf("E_WHO_IS_THAT"),
            errorCodes("finna bet\nyap(1)\ncaught in 4k (oops) bet\nyap(1)\nperiodt\nyap(oops)\n"),
        )
    }

    @Test
    fun `crashout counts as a returning path`() {
        assertClean(
            "skibidi f(x: aura) spits aura bet\nsus (x clears 0) bet\nyeet x\nbruh bet\ncrashout \"negative aura\"\nperiodt\nperiodt\n",
        )
    }

    // ---- web dsl scoping ----

    @Test
    fun `names inside page lambdas are still checked`() {
        val diags = check(
            "rizz score = 5\ndrop site on 3000 bet\npage(\"/\") bet\nyap(scor)\nperiodt\nperiodt\n",
        )
        assertTrue(diags.all.any { it.code == "E_WHO_IS_THAT" })
    }

    @Test
    fun `checker still runs on a parse-recovered ast`() {
        // banned symbols recover to the intended token, so the checker can
        // still deliver type errors in the same run
        val lexed = Lexer("rizz score = 5\nsus (scor == 5) bet\nperiodt\n").lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        TypeChecker(lexed.diagnostics).check(program)
        val codes = lexed.diagnostics.all.map { it.code }
        assertTrue("E_BANNED_SYMBOL" in codes, "codes: $codes")
        assertTrue("E_WHO_IS_THAT" in codes, "codes: $codes")
    }

    @Test
    fun `clean clicker program type checks`() {
        assertClean(
            "gyatt score = 0\ndrop site on 3000 bet\npage(\"/\") bet\nbigyap(\"AURA\")\nyap(\"aura: \$score\")\nsmash(\"+1\") does bet\nscore gains 1\nperiodt\nperiodt\nperiodt\n",
        )
    }
}
