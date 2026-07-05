package rotlin.compiler

enum class TokenType {
    // single-word keywords
    SKIBIDI, RIZZ, GYATT, YEET, SIGMA, NPC, SUS, BRUH, GRIND, MOG, VIBECHECK,
    INSIDE, BASED, CRINGE, GHOSTED, SUMMON, HOOD, BET, PERIODT, SPITS, MAYBE,
    OTHERWISE, DEADASS, GAINS, LOSES, DIP, SKIP, AND, OR, NOT,
    TWINS, AINT, CLEARS, FLOPS, ATLEAST, ATMOST, THROUGH,
    VIBE, REMIX, GATEKEEP, ME, CRASHOUT, FINNA, DOES, DROP,

    // multi-word phrases (fused by the lexer)
    IS_A, VIBES_WITH, CAUGHT_IN_4K,

    // punctuation
    LPAREN, RPAREN, COMMA, COLON, DOT, SAFE_DOT, LBRACKET, RBRACKET,
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
    "skibidi" to TokenType.SKIBIDI,
    "rizz" to TokenType.RIZZ,
    "gyatt" to TokenType.GYATT,
    "yeet" to TokenType.YEET,
    "sigma" to TokenType.SIGMA,
    "npc" to TokenType.NPC,
    "sus" to TokenType.SUS,
    "bruh" to TokenType.BRUH,
    "grind" to TokenType.GRIND,
    "mog" to TokenType.MOG,
    "vibecheck" to TokenType.VIBECHECK,
    "inside" to TokenType.INSIDE,
    "based" to TokenType.BASED,
    "cringe" to TokenType.CRINGE,
    "ghosted" to TokenType.GHOSTED,
    "summon" to TokenType.SUMMON,
    "hood" to TokenType.HOOD,
    "bet" to TokenType.BET,
    "periodt" to TokenType.PERIODT,
    "spits" to TokenType.SPITS,
    "maybe" to TokenType.MAYBE,
    "otherwise" to TokenType.OTHERWISE,
    "deadass" to TokenType.DEADASS,
    "gains" to TokenType.GAINS,
    "loses" to TokenType.LOSES,
    "dip" to TokenType.DIP,
    "skip" to TokenType.SKIP,
    "and" to TokenType.AND,
    "or" to TokenType.OR,
    "not" to TokenType.NOT,
    "twins" to TokenType.TWINS,
    "aint" to TokenType.AINT,
    "clears" to TokenType.CLEARS,
    "flops" to TokenType.FLOPS,
    "atleast" to TokenType.ATLEAST,
    "atmost" to TokenType.ATMOST,
    "through" to TokenType.THROUGH,
    "vibe" to TokenType.VIBE,
    "remix" to TokenType.REMIX,
    "gatekeep" to TokenType.GATEKEEP,
    "me" to TokenType.ME,
    "crashout" to TokenType.CRASHOUT,
    "finna" to TokenType.FINNA,
    "does" to TokenType.DOES,
    "drop" to TokenType.DROP,
)

/** Words that can start a multi-word phrase; reserved on their own. */
val PHRASE_HEADS: Map<String, String> = mapOf(
    "is" to "is a",
    "vibes" to "vibes with",
    "caught" to "caught in 4k",
)
