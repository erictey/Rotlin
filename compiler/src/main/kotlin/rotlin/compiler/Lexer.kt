package rotlin.compiler

data class LexResult(val tokens: List<Token>, val diagnostics: DiagnosticBag)

/**
 * Hand-written scanner. Newlines are tokens (statements are line-oriented);
 * consecutive newlines collapse to one. Strings and comments are protected -
 * keywords inside them never lex as keywords. Multi-word phrases (`is a`,
 * `vibes with`, `caught in 4k`) fuse via longest-match with rollback.
 * Symbols that are never legal Rotlin (`==`, `{`, `&&`, ...) lex as BANNED
 * tokens carrying the word-form suggestion for the parser to roast with.
 */
class Lexer(src: String) {

    private val src = src.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    private var pos = 0
    private var line = 1
    private var col = 1
    private val tokens = mutableListOf<Token>()
    private val diags = DiagnosticBag()

    fun lex(): LexResult {
        while (pos < src.length) {
            val c = src[pos]
            when {
                c == '\n' -> { emitNewline(); advance() }
                c == ' ' || c == '\t' -> advance()
                src.startsWith("//", pos) -> skipLineComment()
                src.startsWith("/*", pos) -> skipBlockComment()
                c == '"' -> lexString()
                c.isDigit() -> lexNumber()
                c.isLetter() || c == '_' -> lexWord()
                else -> lexSymbol()
            }
        }
        emit(TokenType.EOF, "")
        return LexResult(tokens, diags)
    }

    // ---- basics ---------------------------------------------------------

    private fun advance() {
        if (src[pos] == '\n') { line++; col = 1 } else col++
        pos++
    }

    private fun advanceBy(n: Int) = repeat(n) { advance() }

    private fun emit(type: TokenType, text: String, atLine: Int = line, atCol: Int = col,
                     parts: List<TmplPart>? = null, suggestion: String? = null) {
        tokens += Token(type, text, atLine, atCol, parts, suggestion)
    }

    private fun emitNewline() {
        if (tokens.isNotEmpty() && tokens.last().type != TokenType.NEWLINE) {
            emit(TokenType.NEWLINE, "\\n")
        }
    }

    // ---- comments -------------------------------------------------------

    private fun skipLineComment() {
        while (pos < src.length && src[pos] != '\n') advance()
    }

    private fun skipBlockComment() {
        val hadLine = line
        advanceBy(2)
        var depth = 1
        while (pos < src.length && depth > 0) {
            when {
                src.startsWith("/*", pos) -> { depth++; advanceBy(2) }
                src.startsWith("*/", pos) -> { depth--; advanceBy(2) }
                else -> advance()
            }
        }
        if (depth > 0) {
            diags.error("E_UNCLOSED_COMMENT", "this /* comment never ends", hadLine, 1,
                hint = "close it with */")
        }
        // a comment that swallowed newlines still terminates the statement
        if (line > hadLine) emitNewline()
    }

    // ---- numbers --------------------------------------------------------

    private fun lexNumber() {
        val startLine = line; val startCol = col
        val sb = StringBuilder()
        while (pos < src.length && src[pos].isDigit()) { sb.append(src[pos]); advance() }
        if (pos + 1 < src.length && src[pos] == '.' && src[pos + 1].isDigit()) {
            sb.append('.'); advance()
            while (pos < src.length && src[pos].isDigit()) { sb.append(src[pos]); advance() }
            emit(TokenType.DOUBLE_LIT, sb.toString(), startLine, startCol)
        } else {
            emit(TokenType.INT_LIT, sb.toString(), startLine, startCol)
        }
    }

    // ---- words + phrases ------------------------------------------------

    private data class Phrase(val words: List<String>, val type: TokenType)

    private val phrases = listOf(
        Phrase(listOf("caught", "in", "4k"), TokenType.CAUGHT_IN_4K),
        Phrase(listOf("vibes", "with"), TokenType.VIBES_WITH),
        Phrase(listOf("is", "an"), TokenType.IS_A),
        Phrase(listOf("is", "a"), TokenType.IS_A),
    )

    private fun scanWordText(): String {
        val sb = StringBuilder()
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) {
            sb.append(src[pos]); advance()
        }
        return sb.toString()
    }

    private fun lexWord() {
        val startLine = line; val startCol = col
        val word = scanWordText()

        for (phrase in phrases.filter { it.words.first() == word }) {
            val save = Triple(pos, line, col)
            if (phrase.words.drop(1).all { expected -> matchNextWordSameLine(expected) }) {
                emit(phrase.type, phrase.words.joinToString(" "), startLine, startCol)
                return
            }
            pos = save.first; line = save.second; col = save.third
        }

        PHRASE_HEADS[word]?.let { full ->
            diags.error(
                "E_BROKEN_PHRASE", "`$word` on its own is not a thing", startLine, startCol,
                hint = "did you mean `$full`?",
            )
            emit(TokenType.IDENT, word, startLine, startCol)
            return
        }

        val kw = KEYWORDS[word]
        if (kw != null) emit(kw, word, startLine, startCol)
        else emit(TokenType.IDENT, word, startLine, startCol)
    }

    /** Skips spaces/tabs (same line only), then matches [expected] as a whole word. */
    private fun matchNextWordSameLine(expected: String): Boolean {
        while (pos < src.length && (src[pos] == ' ' || src[pos] == '\t')) advance()
        if (pos >= src.length || !(src[pos].isLetterOrDigit() || src[pos] == '_')) return false
        val word = scanWordText()
        return word == expected
    }

    // ---- strings --------------------------------------------------------

    private fun lexString() {
        val startLine = line; val startCol = col
        advance() // opening quote
        val parts = mutableListOf<TmplPart>()
        val text = StringBuilder()

        fun flushText() {
            if (text.isNotEmpty()) { parts += TmplPart.Text(text.toString()); text.clear() }
        }

        while (true) {
            if (pos >= src.length || src[pos] == '\n') {
                diags.error("E_UNCLOSED_STRING", "this string never closes", startLine, startCol,
                    hint = "add the closing \" before the end of the line")
                break
            }
            val c = src[pos]
            when {
                c == '"' -> { advance(); break }
                c == '\\' -> {
                    text.append(c); advance()
                    if (pos < src.length) { text.append(src[pos]); advance() }
                }
                c == '$' && pos + 1 < src.length && src[pos + 1] == '{' -> {
                    flushText()
                    parts += lexBracedInterpolation()
                }
                c == '$' && pos + 1 < src.length && (src[pos + 1].isLetter() || src[pos + 1] == '_') -> {
                    flushText()
                    val nameLine = line; val nameCol = col
                    advance() // $
                    val name = scanWordText()
                    val type = KEYWORDS[name] ?: TokenType.IDENT
                    parts += TmplPart.Interp(listOf(Token(type, name, nameLine, nameCol + 1)))
                }
                else -> { text.append(c); advance() }
            }
        }
        flushText()
        if (parts.isEmpty()) parts += TmplPart.Text("")
        emit(TokenType.STRING_TMPL, "", startLine, startCol, parts = parts)
    }

    /** Consumes `${ ... }` (balanced, string-aware) and sub-lexes the contents. */
    private fun lexBracedInterpolation(): TmplPart.Interp {
        val openLine = line; val openCol = col
        advanceBy(2) // ${
        val start = pos
        var depth = 1
        while (pos < src.length && depth > 0) {
            val c = src[pos]
            when {
                c == '"' -> skipNestedString()
                c == '{' -> { depth++; advance() }
                c == '}' -> { depth--; if (depth > 0) advance() }
                else -> advance()
            }
        }
        val inner = src.substring(start, pos)
        if (pos < src.length) advance() // closing }
        else diags.error("E_UNCLOSED_INTERP", "this \${ interpolation never closes", openLine, openCol,
            hint = "add the closing }")

        val sub = Lexer(inner).lex()
        sub.diagnostics.all.forEach { d ->
            diags.error(d.code, d.message, openLine + d.line - 1, if (d.line == 1) openCol + 2 + d.col - 1 else d.col, d.hint)
        }
        val innerTokens = sub.tokens
            .filter { it.type != TokenType.EOF && it.type != TokenType.NEWLINE }
            .map { t ->
                t.copy(
                    line = openLine + t.line - 1,
                    col = if (t.line == 1) openCol + 2 + t.col - 1 else t.col,
                )
            }
        return TmplPart.Interp(innerTokens)
    }

    private fun skipNestedString() {
        advance() // opening quote
        while (pos < src.length && src[pos] != '"' && src[pos] != '\n') {
            if (src[pos] == '\\') advance()
            if (pos < src.length) advance()
        }
        if (pos < src.length && src[pos] == '"') advance()
    }

    // ---- symbols --------------------------------------------------------

    private fun lexSymbol() {
        val startLine = line; val startCol = col

        fun banned(text: String, suggestion: String) {
            advanceBy(text.length)
            emit(TokenType.BANNED, text, startLine, startCol, suggestion = suggestion)
        }

        fun symbol(text: String, type: TokenType) {
            advanceBy(text.length)
            emit(type, text, startLine, startCol)
        }

        val two = if (pos + 1 < src.length) src.substring(pos, pos + 2) else ""
        when {
            two == "==" -> banned("==", "twins")
            two == "!=" -> banned("!=", "aint")
            two == "<=" -> banned("<=", "atmost")
            two == ">=" -> banned(">=", "atleast")
            two == "&&" -> banned("&&", "and")
            two == "||" -> banned("||", "or")
            two == "!!" -> banned("!!", "deadass")
            two == "?:" -> banned("?:", "otherwise")
            two == "?." -> symbol("?.", TokenType.SAFE_DOT)
            two == "->" -> symbol("->", TokenType.ARROW)
            else -> when (src[pos]) {
                '(' -> symbol("(", TokenType.LPAREN)
                ')' -> symbol(")", TokenType.RPAREN)
                ',' -> symbol(",", TokenType.COMMA)
                ':' -> symbol(":", TokenType.COLON)
                '.' -> symbol(".", TokenType.DOT)
                '[' -> symbol("[", TokenType.LBRACKET)
                ']' -> symbol("]", TokenType.RBRACKET)
                '<' -> symbol("<", TokenType.LT)
                '>' -> symbol(">", TokenType.GT)
                '=' -> symbol("=", TokenType.ASSIGN)
                '+' -> symbol("+", TokenType.PLUS)
                '-' -> symbol("-", TokenType.MINUS)
                '*' -> symbol("*", TokenType.STAR)
                '/' -> symbol("/", TokenType.SLASH)
                '%' -> symbol("%", TokenType.PERCENT)
                '{' -> banned("{", "bet")
                '}' -> banned("}", "periodt")
                ';' -> banned(";", "a new line")
                '!' -> banned("!", "not")
                '&' -> banned("&", "and")
                '|' -> banned("|", "or")
                '?' -> banned("?", "maybe")
                else -> {
                    diags.error("E_WEIRD_CHAR", "no clue what `${src[pos]}` is doing here", startLine, startCol,
                        hint = "delete it")
                    advance()
                }
            }
        }
    }
}
