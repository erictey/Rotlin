package rotlin.compiler

enum class TokenType {
    // single-word keywords
    TUNG, ALPHA, BETA, YEET, CLASS, NPC, IF, ELSE, GRIND, MOG, WHEN,
    INSIDE, TRUE, FALSE, NULL, SUMMON, PACKAGE, SPITS, MAYBE,
    OTHERWISE, DEADAHH, GAINS, LOSES, DIP, SKIP, AND, OR, NOT,
    IS, AINT, ATLEAST, ATMOST, THROUGH,
    VIBE, OVERRIDE, PRIVATE, THIS, CRASHOUT, TRY, CATCH, DOES, DROP,

    // multi-word phrases (fused by the lexer)
    IS_A, VIBES_WITH,

    // punctuation
    LPAREN, RPAREN, COMMA, COLON, DOT, SAFE_DOT, LBRACKET, RBRACKET,
    LBRACE, RBRACE,
    LT, GT, ASSIGN, ARROW, PLUS, MINUS, STAR, SLASH, PERCENT,

    // literals
    INT_LIT, DOUBLE_LIT, STRING_TMPL,

    IDENT, NEWLINE, EOF,

    // a symbol that is never legal Rotlin; carries the word-form suggestion
    BANNED,
}

sealed interface TmplPart {
    /** Raw source text including escape sequences, emitted verbatim. */
    data class Text(val raw: String) : TmplPart

    /** Sub-lexed contents of a `${...}` or `$name` interpolation. */
    data class Interp(val tokens: List<Token>) : TmplPart
}

data class Token(
    val type: TokenType,
    val text: String,
    val line: Int,
    val col: Int,
    val parts: List<TmplPart>? = null, // STRING_TMPL only
    val suggestion: String? = null,    // BANNED only
)

val KEYWORDS: Map<String, TokenType> = mapOf(
    "tung" to TokenType.TUNG,
    "alpha" to TokenType.ALPHA,
    "beta" to TokenType.BETA,
    "yeet" to TokenType.YEET,
    "class" to TokenType.CLASS,
    "npc" to TokenType.NPC,
    "if" to TokenType.IF,
    "else" to TokenType.ELSE,
    "grind" to TokenType.GRIND,
    "mog" to TokenType.MOG,
    "when" to TokenType.WHEN,
    "inside" to TokenType.INSIDE,
    "true" to TokenType.TRUE,
    "false" to TokenType.FALSE,
    "null" to TokenType.NULL,
    "summon" to TokenType.SUMMON,
    "package" to TokenType.PACKAGE,
    "spits" to TokenType.SPITS,
    "maybe" to TokenType.MAYBE,
    "otherwise" to TokenType.OTHERWISE,
    "deadahh" to TokenType.DEADAHH,
    "gains" to TokenType.GAINS,
    "loses" to TokenType.LOSES,
    "dip" to TokenType.DIP,
    "skip" to TokenType.SKIP,
    "and" to TokenType.AND,
    "or" to TokenType.OR,
    "not" to TokenType.NOT,
    "is" to TokenType.IS,
    "aint" to TokenType.AINT,
    "atleast" to TokenType.ATLEAST,
    "atmost" to TokenType.ATMOST,
    "through" to TokenType.THROUGH,
    "vibe" to TokenType.VIBE,
    "override" to TokenType.OVERRIDE,
    "private" to TokenType.PRIVATE,
    "this" to TokenType.THIS,
    "crashout" to TokenType.CRASHOUT,
    "try" to TokenType.TRY,
    "catch" to TokenType.CATCH,
    "does" to TokenType.DOES,
    "drop" to TokenType.DROP,
)

/** Words that can start a multi-word phrase; reserved on their own. */
val PHRASE_HEADS: Map<String, String> = mapOf(
    "vibes" to "vibes with",
)
