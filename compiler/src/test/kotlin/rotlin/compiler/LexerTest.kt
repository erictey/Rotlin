package rotlin.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import rotlin.compiler.TokenType.*

class LexerTest {

    private fun lex(src: String): LexResult = Lexer(src).lex()

    /** Token types with NEWLINE/EOF stripped, for compact assertions. */
    private fun types(src: String): List<TokenType> =
        lex(src).tokens.map { it.type }.filter { it != NEWLINE && it != EOF }

    @Test
    fun `keywords map to keyword tokens and other words stay identifiers`() {
        assertEquals(
            listOf(ALPHA, IDENT, ASSIGN, INT_LIT),
            types("alpha count = 5"),
        )
        assertEquals(
            listOf(BETA, IDENT, ASSIGN, TRUE),
            types("beta cool = true"),
        )
    }

    @Test
    fun `newlines are tokens and blank lines collapse`() {
        val toks = lex("alpha a = 1\n\n\nalpha b = 2\n").tokens
        val newlineRuns = toks.count { it.type == NEWLINE }
        assertEquals(2, newlineRuns, "consecutive newlines should collapse: $toks")
    }

    @Test
    fun `utf-8 BOM is ignored`() {
        // Notepad and PowerShell love to prepend BOMs; users will hit this
        assertEquals(listOf(ALPHA, IDENT, ASSIGN, INT_LIT), types("﻿alpha a = 1"))
    }

    @Test
    fun `crlf and lf lex identically`() {
        val lf = lex("alpha a = 1\nalpha b = 2\n").tokens.map { it.type }
        val crlf = lex("alpha a = 1\r\nalpha b = 2\r\n").tokens.map { it.type }
        assertEquals(lf, crlf)
    }

    @Test
    fun `line and column positions are 1-based and correct`() {
        val toks = lex("alpha a = 1\nyap(a)\n").tokens
        val yap = toks.first { it.text == "yap" }
        assertEquals(2, yap.line)
        assertEquals(1, yap.col)
    }

    @Test
    fun `keywords inside strings and comments never swap`() {
        val toks = lex("yap(\"tung if else\") // alpha beta comment\n").tokens
        val str = toks.first { it.type == STRING_TMPL }
        val text = (str.parts!!.single() as TmplPart.Text).raw
        assertEquals("tung if else", text)
        assertTrue(toks.none { it.type == ALPHA || it.type == BETA || it.type == IF })
    }

    @Test
    fun `nested block comments are skipped entirely`() {
        val types = types("alpha a /* outer /* nested tung */ still comment */ = 1")
        assertEquals(listOf(ALPHA, IDENT, ASSIGN, INT_LIT), types)
    }

    @Test
    fun `string template with simple dollar name interpolation`() {
        val tok = lex("\"count is \$count!\"").tokens.first { it.type == STRING_TMPL }
        val parts = tok.parts!!
        assertEquals(3, parts.size)
        assertEquals("count is ", (parts[0] as TmplPart.Text).raw)
        val interp = parts[1] as TmplPart.Interp
        assertEquals(listOf(IDENT), interp.tokens.map { it.type })
        assertEquals("count", interp.tokens.single().text)
        assertEquals("!", (parts[2] as TmplPart.Text).raw)
    }

    @Test
    fun `string template with braced expression sub-lexes word operators`() {
        val tok = lex("\"result \${a is b}\"").tokens.first { it.type == STRING_TMPL }
        val interp = tok.parts!!.filterIsInstance<TmplPart.Interp>().single()
        assertEquals(listOf(IDENT, IS, IDENT), interp.tokens.map { it.type })
    }

    @Test
    fun `escape sequences inside strings are preserved verbatim`() {
        val tok = lex("\"line\\none \\\"quoted\\\" \\\$notinterp\"").tokens.first { it.type == STRING_TMPL }
        val text = (tok.parts!!.single() as TmplPart.Text).raw
        assertEquals("line\\none \\\"quoted\\\" \\\$notinterp", text)
    }

    @Test
    fun `banned symbol operators lex as BANNED with word suggestion`() {
        fun bannedSuggestion(src: String): String? =
            lex(src).tokens.firstOrNull { it.type == BANNED }?.suggestion

        assertEquals("is", bannedSuggestion("a == b"))
        assertEquals("aint", bannedSuggestion("a != b"))
        assertEquals("atmost", bannedSuggestion("a <= b"))
        assertEquals("atleast", bannedSuggestion("a >= b"))
        assertEquals("and", bannedSuggestion("a && b"))
        assertEquals("or", bannedSuggestion("a || b"))
        assertEquals("deadahh", bannedSuggestion("a!!"))
        assertEquals("otherwise", bannedSuggestion("a ?: b"))
        assertEquals("not", bannedSuggestion("!a"))
    }

    @Test
    fun `braces lex as legal block tokens`() {
        assertEquals(listOf(LBRACE, IDENT, RBRACE), types("{ x }"))
    }

    @Test
    fun `semicolons are banned with a one-statement-per-line hint`() {
        val tok = lex("alpha a = 1; alpha b = 2").tokens.first { it.type == BANNED }
        assertEquals(";", tok.text)
    }

    @Test
    fun `lt and gt are legal tokens`() {
        assertEquals(
            listOf(IDENT, LT, IDENT, GT),
            types("squad<aura>"),
        )
        assertEquals(
            listOf(IDENT, LT, INT_LIT),
            types("a < 3"),
        )
    }

    @Test
    fun `numbers lex as int and double literals`() {
        assertEquals(listOf(INT_LIT), types("42"))
        assertEquals(listOf(DOUBLE_LIT), types("3.14"))
        assertEquals(listOf(INT_LIT, DOT, IDENT), types("42.toString"))
    }

    @Test
    fun `multi word phrases fuse into single tokens`() {
        assertEquals(listOf(CLASS, IDENT, IS_A, IDENT), types("class Dog is a Animal"))
        assertEquals(listOf(CLASS, IDENT, IS_A, IDENT), types("class Dog is an Animal"))
        assertEquals(listOf(IDENT, VIBES_WITH, IDENT), types("Dog vibes with Fetchable"))
    }

    @Test
    fun `is on its own lexes as the equality keyword`() {
        assertEquals(listOf(IDENT, IS, IDENT), types("a is b"))
        assertEquals(listOf(IDENT, IS, INT_LIT), types("x is 5"))
    }

    @Test
    fun `catch is a single-word keyword`() {
        assertEquals(listOf(CATCH, LPAREN, IDENT, RPAREN), types("catch (oops)"))
    }

    @Test
    fun `phrase tail words stay usable as plain identifiers`() {
        assertEquals(listOf(ALPHA, IDENT, ASSIGN, INT_LIT), types("alpha a = 1"))
        assertEquals(listOf(ALPHA, IDENT, ASSIGN, INT_LIT), types("alpha with = 2"))
        assertEquals(listOf(ALPHA, IDENT, ASSIGN, INT_LIT), types("alpha an = 3"))
    }

    @Test
    fun `broken phrase head produces a diagnostic with did-you-mean`() {
        val result = lex("alpha vibes = 1")
        assertTrue(result.diagnostics.hasErrors)
        val diag = result.diagnostics.all.first()
        assertTrue(diag.hint!!.contains("vibes with"), "hint was: ${diag.hint}")
    }

    @Test
    fun `safe dot and arrow lex as dedicated tokens`() {
        assertEquals(listOf(IDENT, SAFE_DOT, IDENT), types("user?.name"))
        assertEquals(listOf(INT_LIT, ARROW, IDENT), types("1 -> yap"))
    }
}
