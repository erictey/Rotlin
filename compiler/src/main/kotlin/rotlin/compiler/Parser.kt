package rotlin.compiler

import rotlin.compiler.TokenType.*

/**
 * Recursive descent, newline-sensitive (one statement per line), expressions
 * by precedence climbing that deliberately mirrors Kotlin's table so emitted
 * code keeps identical semantics.
 *
 * BANNED tokens from the lexer are normalized up front: each produces one
 * diagnostic and is replaced by the token the kid clearly meant, so parsing
 * recovers with maximum signal.
 */
class Parser(rawTokens: List<Token>, private val diags: DiagnosticBag) {

    private val tokens: List<Token> = normalize(rawTokens)
    private var idx = 0
    private var errorCount = 0

    private companion object {
        const val MAX_ERRORS = 20
        val BANNED_REPLACEMENTS: Map<String, TokenType> = mapOf(
            "twins" to TWINS, "aint" to AINT, "atmost" to ATMOST, "atleast" to ATLEAST,
            "and" to AND, "or" to OR, "deadass" to DEADASS, "otherwise" to OTHERWISE,
            "not" to NOT, "bet" to BET, "periodt" to PERIODT,
        )
    }

    private fun normalize(raw: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        for (t in raw) {
            if (t.type != BANNED) { out += t; continue }
            val suggestion = t.suggestion ?: "something else"
            when {
                t.text == ";" -> {
                    diags.error("E_SEMICOLON", "no semicolons in the hood", t.line, t.col,
                        hint = "one statement per line - just press enter")
                    if (out.isNotEmpty() && out.last().type != NEWLINE) {
                        out += Token(NEWLINE, "\\n", t.line, t.col)
                    }
                }
                BANNED_REPLACEMENTS.containsKey(suggestion) -> {
                    diags.error("E_BANNED_SYMBOL", "`${t.text}` is not a thing here, that's so 2020",
                        t.line, t.col, hint = "write `$suggestion` instead")
                    out += Token(BANNED_REPLACEMENTS.getValue(suggestion), suggestion, t.line, t.col)
                }
                else -> {
                    diags.error("E_BANNED_SYMBOL", "`${t.text}` is not Rotlin", t.line, t.col,
                        hint = "did you mean `$suggestion`?")
                }
            }
        }
        return out
    }

    // ---- token helpers --------------------------------------------------

    private fun peek(offset: Int = 0): Token = tokens[(idx + offset).coerceAtMost(tokens.lastIndex)]
    private fun at(type: TokenType): Boolean = peek().type == type
    private fun advance(): Token = peek().also { if (idx < tokens.lastIndex) idx++ }

    private fun expect(type: TokenType, what: String): Token? {
        if (at(type)) return advance()
        error("E_EXPECTED", "expected $what but got `${describe(peek())}`", peek())
        return null
    }

    private fun describe(t: Token): String = when (t.type) {
        NEWLINE -> "end of line"
        EOF -> "end of file"
        STRING_TMPL -> "a string"
        else -> t.text
    }

    private fun error(code: String, message: String, at: Token, hint: String? = null) {
        if (errorCount < MAX_ERRORS) {
            diags.error(code, message, at.line, at.col, hint)
            errorCount++
            if (errorCount == MAX_ERRORS) {
                diags.error("E_COOKED", "your code is COOKED - fix the first few and run it back",
                    at.line, at.col)
            }
        }
    }

    private fun skipNewlines() {
        while (at(NEWLINE)) advance()
    }

    /** Consumes end-of-statement: a newline, or lets `periodt`/EOF terminate. */
    private fun expectStmtEnd() {
        when {
            at(NEWLINE) -> advance()
            at(PERIODT) || at(EOF) || at(BRUH) -> {}
            else -> {
                error("E_STMT_END", "one statement per line - `${describe(peek())}` doesn't belong here",
                    peek(), hint = "press enter and put it on its own line")
                synchronize()
            }
        }
    }

    private fun synchronize() {
        while (!at(NEWLINE) && !at(EOF) && !at(PERIODT)) advance()
        if (at(NEWLINE)) advance()
    }

    // ---- program ---------------------------------------------------------

    fun parseProgram(): Program {
        skipNewlines()

        var hood: HoodDecl? = null
        if (at(HOOD)) {
            val t = advance()
            val path = parseDottedPath()
            hood = HoodDecl(path.first, t.line, t.col)
            expectStmtEnd(); skipNewlines()
        }

        val summons = mutableListOf<SummonDecl>()
        while (at(SUMMON)) {
            val t = advance()
            val (path, wildcard) = parseDottedPath()
            summons += SummonDecl(path, wildcard, t.line, t.col)
            expectStmtEnd(); skipNewlines()
        }

        val items = mutableListOf<Stmt>()
        skipNewlines()
        while (!at(EOF) && errorCount < MAX_ERRORS) {
            val before = idx
            parseStmt()?.let { items += it }
            if (idx == before && !at(EOF)) {
                error("E_UNEXPECTED", "`${describe(peek())}` doesn't belong here", peek())
                advance() // always make progress
            }
            skipNewlines()
        }
        return Program(hood, summons, items)
    }

    private fun parseDottedPath(): Pair<String, Boolean> {
        val parts = mutableListOf<String>()
        expect(IDENT, "a name")?.let { parts += it.text }
        var wildcard = false
        while (at(DOT)) {
            advance()
            when {
                at(STAR) -> { advance(); wildcard = true; break }
                at(IDENT) -> parts += advance().text
                else -> { error("E_EXPECTED", "expected a name after the dot", peek()); break }
            }
        }
        return parts.joinToString(".") to wildcard
    }

    // ---- statements -------------------------------------------------------

    private fun parseStmt(): Stmt? {
        skipNewlines()
        return when (peek().type) {
            SKIBIDI -> parseFunDecl()
            RIZZ, GYATT -> parseVarDecl()
            SUS -> parseSus()
            GRIND -> parseGrind()
            YEET -> parseYeet()
            DIP -> advance().let { val s = DipStmt(it.line, it.col); expectStmtEnd(); s }
            SKIP -> advance().let { val s = SkipStmt(it.line, it.col); expectStmtEnd(); s }
            SIGMA, NPC, VIBE, MOG, VIBECHECK, FINNA, CRASHOUT, DROP -> {
                error("E_NOT_YET", "`${peek().text}` isn't in this build yet - coming soon fr",
                    peek())
                synchronize(); null
            }
            SUMMON -> {
                error("E_LATE_SUMMON", "summons go at the top of the file", peek(),
                    hint = "move this line above everything else")
                synchronize(); null
            }
            EOF -> null
            else -> parseAssignOrExpr()
        }
    }

    private fun parseFunDecl(): Stmt? {
        val kw = advance()
        val name = expect(IDENT, "a function name after `skibidi`") ?: run { synchronize(); return null }
        expect(LPAREN, "`(` after the function name") ?: run { synchronize(); return null }
        val params = mutableListOf<Param>()
        skipNewlines()
        while (!at(RPAREN) && !at(EOF)) {
            val pname = expect(IDENT, "a parameter name") ?: break
            expect(COLON, "`:` and a type after `${pname.text}` (params need types, no cap)") ?: break
            val ptype = parseType() ?: break
            params += Param(pname.text, ptype, pname.line, pname.col)
            if (at(COMMA)) { advance(); skipNewlines() } else break
        }
        expect(RPAREN, "`)` to close the parameter list")
        val returnType = if (at(SPITS)) { advance(); parseType() } else null
        val body = parseBlock() ?: return null
        return FunDecl(name.text, params, returnType, body, kw.line, kw.col)
    }

    private fun parseVarDecl(): Stmt? {
        val kw = advance()
        val mutable = kw.type == GYATT
        val name = expect(IDENT, "a name after `${kw.text}`") ?: run { synchronize(); return null }
        var declared: TypeRef? = null
        if (at(COLON)) { advance(); declared = parseType() }
        expect(ASSIGN, "`=` and a starting value (every ${kw.text} needs one)") ?: run { synchronize(); return null }
        val init = parseExpression() ?: run { synchronize(); return null }
        expectStmtEnd()
        return VarDecl(mutable, name.text, declared, init, kw.line, kw.col)
    }

    private fun parseSus(): Stmt? {
        val kw = advance()
        expect(LPAREN, "`(` after `sus`")
        val cond = parseExpression() ?: run { synchronize(); return null }
        expect(RPAREN, "`)` after the condition")
        // `bruh` may close the then-block directly - no periodt needed before it
        val thenBlock = parseBlock(stopAtBruh = true) ?: return null

        var elseBranch: Node? = null
        val save = idx
        skipNewlines()
        if (at(BRUH)) {
            advance()
            elseBranch = if (at(SUS)) parseSus() else parseBlock()
        } else {
            idx = save
        }
        return SusStmt(cond, thenBlock, elseBranch, kw.line, kw.col)
    }

    private fun parseGrind(): Stmt? {
        val kw = advance()
        expect(LPAREN, "`(` after `grind`")
        val cond = parseExpression() ?: run { synchronize(); return null }
        expect(RPAREN, "`)` after the condition")
        val body = parseBlock() ?: return null
        return GrindStmt(cond, body, kw.line, kw.col)
    }

    private fun parseYeet(): Stmt {
        val kw = advance()
        val value = if (at(NEWLINE) || at(PERIODT) || at(EOF)) null else parseExpression()
        expectStmtEnd()
        return YeetStmt(value, kw.line, kw.col)
    }

    private fun parseAssignOrExpr(): Stmt? {
        val start = peek()
        val expr = parseExpression() ?: run { synchronize(); return null }
        val op = when (peek().type) {
            ASSIGN -> AssignOp.SET
            GAINS -> AssignOp.GAINS
            LOSES -> AssignOp.LOSES
            else -> null
        }
        if (op == null) {
            expectStmtEnd()
            return ExprStmt(expr, start.line, start.col)
        }
        val opTok = advance()
        if (expr !is NameRef && expr !is MemberAccess) {
            error("E_BAD_ASSIGN", "you can't assign to that", opTok,
                hint = "put a variable name on the left of `${opTok.text}`")
        }
        val value = parseExpression() ?: run { synchronize(); return null }
        expectStmtEnd()
        return Assign(expr, op, value, start.line, start.col)
    }

    // ---- blocks -----------------------------------------------------------

    private fun parseBlock(stopAtBruh: Boolean = false): Block? {
        skipNewlines()
        val bet = expect(BET, "`bet` to open the block") ?: run { synchronize(); return null }
        val stmts = mutableListOf<Stmt>()
        while (true) {
            skipNewlines()
            if (stopAtBruh && at(BRUH)) {
                // bruh closes this block; caller consumes it for the else branch
                return Block(stmts, bet.line, bet.col, peek().line)
            }
            if (at(PERIODT)) break
            if (at(EOF)) {
                error("E_UNCLOSED_BLOCK", "this `bet` never got its `periodt`", bet,
                    hint = "close the block with `periodt`")
                return Block(stmts, bet.line, bet.col, peek().line)
            }
            if (errorCount >= MAX_ERRORS) break
            val before = idx
            parseStmt()?.let { stmts += it }
            if (idx == before && !at(PERIODT) && !at(EOF)) advance() // always make progress
        }
        val periodt = advance()
        return Block(stmts, bet.line, bet.col, periodt.line)
    }

    // ---- types ------------------------------------------------------------

    private fun parseType(): TypeRef? {
        if (at(MAYBE)) {
            val t = advance()
            val inner = parseType() ?: return null
            return TypeRef.Maybe(inner, t.line, t.col)
        }
        val name = expect(IDENT, "a type name") ?: return null
        val args = mutableListOf<TypeRef>()
        if (at(LT)) {
            advance()
            while (true) {
                args += parseType() ?: break
                if (at(COMMA)) advance() else break
            }
            expect(GT, "`>` to close the type arguments")
        }
        return TypeRef.Named(name.text, args, name.line, name.col)
    }

    // ---- expressions --------------------------------------------------------

    fun parseExpression(): Expr? = parseOr()

    private fun parseInterp(tokens: List<Token>, line: Int, col: Int): Expr {
        if (tokens.isEmpty()) {
            error("E_EMPTY_INTERP", "this \${} is empty", Token(EOF, "", line, col))
            return StringTmpl(listOf(TmplNode.Text("")), line, col)
        }
        val sub = Parser(tokens + Token(EOF, "", line, col), diags)
        return sub.parseExpression() ?: StringTmpl(listOf(TmplNode.Text("")), line, col)
    }

    private fun parseOr(): Expr? {
        var left = parseAnd() ?: return null
        while (at(OR)) {
            val t = advance()
            val right = parseAnd() ?: return left
            left = Binary(BinOp.OR, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseAnd(): Expr? {
        var left = parseEquality() ?: return null
        while (at(AND)) {
            val t = advance()
            val right = parseEquality() ?: return left
            left = Binary(BinOp.AND, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseEquality(): Expr? {
        var left = parseComparison() ?: return null
        while (at(TWINS) || at(AINT)) {
            val t = advance()
            val op = if (t.type == TWINS) BinOp.TWINS else BinOp.AINT
            val right = parseComparison() ?: return left
            left = Binary(op, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseComparison(): Expr? {
        var left = parseElvis() ?: return null
        while (true) {
            val op = when (peek().type) {
                CLEARS -> BinOp.CLEARS
                FLOPS -> BinOp.FLOPS
                ATLEAST -> BinOp.ATLEAST
                ATMOST -> BinOp.ATMOST
                GT -> {
                    error("E_BANNED_SYMBOL", "`>` between values is not Rotlin", peek(),
                        hint = "write `clears` instead")
                    BinOp.CLEARS
                }
                LT -> {
                    error("E_BANNED_SYMBOL", "`<` between values is not Rotlin", peek(),
                        hint = "write `flops` instead")
                    BinOp.FLOPS
                }
                else -> return left
            }
            val t = advance()
            val right = parseElvis() ?: return left
            left = Binary(op, left, right, t.line, t.col)
        }
    }

    private fun parseElvis(): Expr? {
        val left = parseRange() ?: return null
        if (at(OTHERWISE)) {
            val t = advance()
            val right = parseElvis() ?: return left // right-associative, like Kotlin's ?:
            return Binary(BinOp.OTHERWISE, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseRange(): Expr? {
        val left = parseAdditive() ?: return null
        if (at(THROUGH)) {
            val t = advance()
            val right = parseAdditive() ?: return left
            return Binary(BinOp.THROUGH, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseAdditive(): Expr? {
        var left = parseMultiplicative() ?: return null
        while (at(PLUS) || at(MINUS)) {
            val t = advance()
            val op = if (t.type == PLUS) BinOp.ADD else BinOp.SUB
            val right = parseMultiplicative() ?: return left
            left = Binary(op, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseMultiplicative(): Expr? {
        var left = parseUnary() ?: return null
        while (at(STAR) || at(SLASH) || at(PERCENT)) {
            val t = advance()
            val op = when (t.type) {
                STAR -> BinOp.MUL
                SLASH -> BinOp.DIV
                else -> BinOp.MOD
            }
            val right = parseUnary() ?: return left
            left = Binary(op, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseUnary(): Expr? {
        if (at(NOT)) {
            val t = advance()
            val operand = parseUnary() ?: return null
            return Unary(UnaryOp.NOT, operand, t.line, t.col)
        }
        if (at(MINUS)) {
            val t = advance()
            val operand = parseUnary() ?: return null
            return Unary(UnaryOp.NEG, operand, t.line, t.col)
        }
        return parsePostfix()
    }

    private fun parsePostfix(): Expr? {
        var expr = parsePrimary() ?: return null
        while (true) {
            expr = when (peek().type) {
                LPAREN -> {
                    val t = advance()
                    val args = mutableListOf<Expr>()
                    skipNewlines()
                    while (!at(RPAREN) && !at(EOF)) {
                        args += parseExpression() ?: break
                        if (at(COMMA)) { advance(); skipNewlines() } else break
                    }
                    expect(RPAREN, "`)` to close the call")
                    Call(expr, args, t.line, t.col)
                }
                DOT -> {
                    val t = advance()
                    val name = expect(IDENT, "a name after `.`") ?: return expr
                    MemberAccess(expr, name.text, safe = false, t.line, t.col)
                }
                SAFE_DOT -> {
                    val t = advance()
                    val name = expect(IDENT, "a name after `?.`") ?: return expr
                    MemberAccess(expr, name.text, safe = true, t.line, t.col)
                }
                DEADASS -> {
                    val t = advance()
                    DeadassExpr(expr, t.line, t.col)
                }
                else -> return expr
            }
        }
    }

    private fun parsePrimary(): Expr? {
        val t = peek()
        return when (t.type) {
            INT_LIT -> { advance(); IntLit(t.text, t.line, t.col) }
            DOUBLE_LIT -> { advance(); DoubleLit(t.text, t.line, t.col) }
            BASED -> { advance(); BoolLit(true, t.line, t.col) }
            CRINGE -> { advance(); BoolLit(false, t.line, t.col) }
            GHOSTED -> { advance(); GhostedLit(t.line, t.col) }
            IDENT -> { advance(); NameRef(t.text, t.line, t.col) }
            STRING_TMPL -> {
                advance()
                val parts = t.parts.orEmpty().map { part ->
                    when (part) {
                        is TmplPart.Text -> TmplNode.Text(part.raw)
                        is TmplPart.Interp -> TmplNode.Interp(parseInterp(part.tokens, t.line, t.col))
                    }
                }
                StringTmpl(parts, t.line, t.col)
            }
            LPAREN -> {
                advance()
                val inner = parseExpression()
                expect(RPAREN, "`)` to close the parenthesis")
                inner
            }
            else -> {
                error("E_EXPECTED_EXPR", "expected a value here but got `${describe(t)}`", t)
                null
            }
        }
    }
}
