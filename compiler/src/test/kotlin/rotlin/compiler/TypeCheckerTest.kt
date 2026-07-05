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
    fun `alpha cannot be reassigned`() {
        assertEquals(listOf("E_ALPHA_LOCKED"), errorCodes("alpha x = 5\nx = 6\n"))
    }

    @Test
    fun `beta reassignment must keep the type`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("beta x = 5\nx = \"nope\"\n"))
        assertClean("beta x = 5\nx = 6\n")
    }

    @Test
    fun `declared type must match initializer`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("alpha x: aura = \"words\"\n"))
    }

    @Test
    fun `gains needs numbers or lore`() {
        assertClean("beta s = 1\ns gains 2\n")
        assertClean("beta s = \"a\"\ns gains \"b\"\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("beta s = 1\ns gains \"b\"\n"))
    }

    // ---- names ----

    @Test
    fun `undefined name gets a did-you-mean`() {
        val diags = check("alpha score = 5\nyap(scor)\n")
        val err = diags.all.single { it.code == "E_UNRESOLVED" }
        assertTrue(err.hint!!.contains("score"), "hint was: ${err.hint}")
    }

    @Test
    fun `summoned names are trusted as interop`() {
        assertClean("summon kotlin.math.PI\nyap(PI)\n")
    }

    @Test
    fun `defining a function named like a runtime builtin warns`() {
        val diags = check("tung yap(x: lore) {\n}\n")
        assertTrue(diags.all.any { it.code == "W_SHADOW" })
    }

    // ---- functions ----

    @Test
    fun `call arity is checked`() {
        assertEquals(
            listOf("E_ARITY"),
            errorCodes("tung add(a: aura, b: aura) spits aura {\nyeet a + b\n}\nyap(add(1))\n"),
        )
    }

    @Test
    fun `argument types are checked`() {
        assertEquals(
            listOf("E_TYPE_MISMATCH"),
            errorCodes("tung add(a: aura, b: aura) spits aura {\nyeet a + b\n}\nyap(add(1, \"x\"))\n"),
        )
    }

    @Test
    fun `spits function must yeet on every path`() {
        assertEquals(
            listOf("E_MISSING_YEET"),
            errorCodes("tung f(x: aura) spits aura {\nif (x > 0) {\nyeet x\n}\n}\n"),
        )
        assertClean("tung f(x: aura) spits aura {\nif (x > 0) {\nyeet x\n} else {\nyeet 0\n}\n}\n")
    }

    @Test
    fun `yeet type must match spits`() {
        assertEquals(
            listOf("E_TYPE_MISMATCH"),
            errorCodes("tung f() spits aura {\nyeet \"words\"\n}\n"),
        )
    }

    // ---- null safety ----

    @Test
    fun `null needs a maybe type`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("beta name: lore = null\n"))
        assertEquals(listOf("E_NULL_INIT"), errorCodes("alpha x = null\n"))
        assertClean("alpha x: maybe lore = null\n")
    }

    @Test
    fun `deref of maybe without a check is unsafe`() {
        val diags = check("alpha name: maybe lore = listen()\nyap(name.length)\n")
        val err = diags.all.single { it.code == "E_NULL_UNSAFE" }
        assertTrue(err.hint!!.contains("aint null"))
        assertTrue(err.hint!!.contains("?."))
        assertTrue(err.hint!!.contains("deadahh"))
    }

    @Test
    fun `safe call and otherwise are fine on maybe`() {
        assertClean("alpha name: maybe lore = listen()\nyap(name?.length)\nyap(name otherwise \"anon\")\n")
    }

    @Test
    fun `otherwise unwraps the maybe`() {
        assertClean("alpha name: maybe lore = listen()\nalpha n = name otherwise \"anon\"\nyap(n.length)\n")
    }

    @Test
    fun `smart cast after aint null on an alpha`() {
        assertClean("alpha name: maybe lore = listen()\nif (name aint null) {\nyap(name.length)\n}\n")
    }

    @Test
    fun `is null narrows the else branch`() {
        assertClean(
            "alpha name: maybe lore = listen()\nif (name is null) {\nyap(\"missing\")\n} else {\nyap(name.length)\n}\n",
        )
    }

    @Test
    fun `beta does not smart cast and the hint says why`() {
        val diags = check("beta name: maybe lore = listen()\nif (name aint null) {\nyap(name.length)\n}\n")
        val err = diags.all.single { it.code == "E_NULL_UNSAFE" }
        assertTrue(err.hint!!.contains("beta"), "hint was: ${err.hint}")
    }

    @Test
    fun `deadahh on something never null warns`() {
        val diags = check("alpha s = \"x\"\nyap(s deadahh)\n")
        assertTrue(diags.all.any { it.code == "W_NEVER_NULL" })
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
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("if (1 and true) {\n}\n"))
    }

    @Test
    fun `comparisons need matching numbers`() {
        assertClean("if (1 > 0) {\n}\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("if (1 > \"a\") {\n}\n"))
    }

    // ---- control flow ----

    @Test
    fun `dip and skip outside a loop are errors`() {
        assertEquals(listOf("E_DIP_NOWHERE"), errorCodes("dip\n"))
        assertEquals(listOf("E_SKIP_NOWHERE"), errorCodes("skip\n"))
        assertClean("grind (true) {\ndip\n}\n")
    }

    // ---- oop ----

    @Test
    fun `constructor calls check arity and types`() {
        val cls = "class Dog(alpha name: lore) {\n}\n"
        assertEquals(listOf("E_ARITY"), errorCodes("${cls}alpha d = Dog()\n"))
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("${cls}alpha d = Dog(5)\n"))
        assertClean("${cls}alpha d = Dog(\"rex\")\nyap(d.name)\n")
    }

    @Test
    fun `this outside a class is an error`() {
        assertEquals(listOf("E_THIS_NOWHERE"), errorCodes("yap(this)\n"))
    }

    @Test
    fun `methods see ctor props through this`() {
        assertClean(
            "class Dog(alpha name: lore) {\ntung intro() spits lore {\nyeet \"i am \" + this.name\n}\n}\n",
        )
    }

    // ---- squads, stashes, mog ----

    @Test
    fun `squad elements infer and mixed squads error`() {
        assertClean("alpha xs = squad(1, 2, 3)\nyap(xs[0] + 1)\n")
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("alpha xs = squad(1, \"two\")\n"))
    }

    @Test
    fun `stash indexing returns a maybe`() {
        // m["k"] might be null - using it raw as a number is the teaching moment
        val diags = check("beta m: stash<lore, aura> = stash()\nalpha v = m[\"k\"]\nyap(v + 1)\n")
        assertTrue(diags.all.any { it.code == "E_MIXED_NUMBERS" || it.code == "E_TYPE_MISMATCH" })
        assertClean("beta m: stash<lore, aura> = stash()\nyap((m[\"k\"] otherwise 0) + 1)\n")
    }

    @Test
    fun `mog over a squad types the loop variable`() {
        assertClean("alpha xs = squad(1, 2)\nmog (x inside xs) {\nyap(x + 1)\n}\n")
        assertClean("mog (i inside 1 through 5) {\nyap(i * 2)\n}\n")
    }

    @Test
    fun `mog over something unloopable errors`() {
        assertEquals(listOf("E_TYPE_MISMATCH"), errorCodes("mog (x inside 5) {\n}\n"))
    }

    @Test
    fun `when branch values must match the subject`() {
        assertEquals(
            listOf("E_TYPE_MISMATCH"),
            errorCodes("alpha x = 5\nwhen (x) {\n\"one\" -> yap(1)\n}\n"),
        )
        assertClean("alpha x = 5\nwhen (x) {\n1 -> yap(1)\nelse -> yap(0)\n}\n")
    }

    // ---- exceptions ----

    @Test
    fun `try scopes the catch name to the catch block`() {
        assertClean("try {\nyap(1)\n} catch (oops) {\nyap(oops)\n}\n")
        assertEquals(
            listOf("E_UNRESOLVED"),
            errorCodes("try {\nyap(1)\n} catch (oops) {\nyap(1)\n}\nyap(oops)\n"),
        )
    }

    @Test
    fun `crashout counts as a returning path`() {
        assertClean(
            "tung f(x: aura) spits aura {\nif (x > 0) {\nyeet x\n} else {\ncrashout \"negative input\"\n}\n}\n",
        )
    }

    // ---- web dsl scoping ----

    @Test
    fun `names inside page lambdas are still checked`() {
        val diags = check(
            "alpha score = 5\ndrop site on 3000 {\npage(\"/\") {\nyap(scor)\n}\n}\n",
        )
        assertTrue(diags.all.any { it.code == "E_UNRESOLVED" })
    }

    @Test
    fun `checker still runs on a parse-recovered ast`() {
        // banned symbols recover to the intended token, so the checker can
        // still deliver type errors in the same run
        val lexed = Lexer("alpha score = 5\nif (scor == 5) {\n}\n").lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        TypeChecker(lexed.diagnostics).check(program)
        val codes = lexed.diagnostics.all.map { it.code }
        assertTrue("E_BANNED_SYMBOL" in codes, "codes: $codes")
        assertTrue("E_UNRESOLVED" in codes, "codes: $codes")
    }

    @Test
    fun `clean clicker program type checks`() {
        assertClean(
            "beta score = 0\ndrop site on 3000 {\npage(\"/\") {\nbigyap(\"AURA\")\nyap(\"aura: \$score\")\nsmash(\"+1\") does {\nscore gains 1\n}\n}\n}\n",
        )
    }
}
