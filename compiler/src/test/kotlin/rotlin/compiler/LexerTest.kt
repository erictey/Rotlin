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
            listOf(RIZZ, IDENT, ASSIGN, INT_LIT),
            types("rizz count = 5"),
        )
        assertEquals(
            listOf(GYATT, IDENT, ASSIGN, BASED),
            types("gyatt cool = based"),
        )
    }

    @Test
    fun `newlines are tokens and blank lines collapse`() {
        val toks = lex("rizz a = 1\n\n\nrizz b = 2\n").tokens
        val newlineRuns = toks.count { it.type == NEWLINE }
        assertEquals(2, newlineRuns, "consecutive newlines should collapse: $toks")
    }

    @Test
    fun `crlf and lf lex identically`() {
        val lf = lex("rizz a = 1\nrizz b = 2\n").tokens.map { it.type }
        val crlf = lex("rizz a = 1\r\nrizz b = 2\r\n").tokens.map { it.type }
        assertEquals(lf, crlf)
    }

    @Test
    fun `line and column positions are 1-based and correct`() {
        val toks = lex("rizz a = 1\nyap(a)\n").tokens
        val yap = toks.first { it.text == "yap" }
        assertEquals(2, yap.line)
        assertEquals(1, yap.col)
    }

    @Test
    fun `keywords inside strings and comments never swap`() {
        val toks = lex("yap(\"skibidi sus bruh\") // rizz gyatt comment\n").tokens
        val str = toks.first { it.type == STRING_TMPL }
        val text = (str.parts!!.single() as TmplPart.Text).raw
        assertEquals("skibidi sus bruh", text)
        assertTrue(toks.none { it.type == RIZZ || it.type == GYATT || it.type == SUS })
    }

    @Test
    fun `nested block comments are skipped entirely`() {
        val types = types("rizz a /* outer /* nested skibidi */ still comment */ = 1")
        assertEquals(listOf(RIZZ, IDENT, ASSIGN, INT_LIT), types)
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
        val tok = lex("\"result \${a twins b}\"").tokens.first { it.type == STRING_TMPL }
        val interp = tok.parts!!.filterIsInstance<TmplPart.Interp>().single()
        assertEquals(listOf(IDENT, TWINS, IDENT), interp.tokens.map { it.type })
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

        assertEquals("twins", bannedSuggestion("a == b"))
        assertEquals("aint", bannedSuggestion("a != b"))
        assertEquals("atmost", bannedSuggestion("a <= b"))
        assertEquals("atleast", bannedSuggestion("a >= b"))
        assertEquals("and", bannedSuggestion("a && b"))
        assertEquals("or", bannedSuggestion("a || b"))
        assertEquals("deadass", bannedSuggestion("a!!"))
        assertEquals("otherwise", bannedSuggestion("a ?: b"))
        assertEquals("not", bannedSuggestion("!a"))
        assertEquals("bet", bannedSuggestion("{"))
        assertEquals("periodt", bannedSuggestion("}"))
    }

    @Test
    fun `semicolons are banned with a one-statement-per-line hint`() {
        val tok = lex("rizz a = 1; rizz b = 2").tokens.first { it.type == BANNED }
        assertEquals(";", tok.text)
    }

    @Test
    fun `lt and gt are legal tokens for generics`() {
        assertEquals(
            listOf(IDENT, LT, IDENT, GT),
            types("squad<aura>"),
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
        assertEquals(listOf(SIGMA, IDENT, IS_A, IDENT), types("sigma Dog is a Animal"))
        assertEquals(listOf(SIGMA, IDENT, IS_A, IDENT), types("sigma Dog is an Animal"))
        assertEquals(listOf(IDENT, VIBES_WITH, IDENT), types("Dog vibes with Fetchable"))
        assertEquals(listOf(CAUGHT_IN_4K, LPAREN, IDENT, RPAREN), types("caught in 4k (oops)"))
    }

    @Test
    fun `phrase tail words stay usable as plain identifiers`() {
        assertEquals(listOf(RIZZ, IDENT, ASSIGN, INT_LIT), types("rizz a = 1"))
        assertEquals(listOf(RIZZ, IDENT, ASSIGN, INT_LIT), types("rizz with = 2"))
        assertEquals(listOf(RIZZ, IDENT, ASSIGN, INT_LIT), types("rizz an = 3"))
    }

    @Test
    fun `broken phrase head produces a diagnostic with did-you-mean`() {
        val result = lex("rizz vibes = 1")
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
