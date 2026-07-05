package rotlin.compiler

import rotlin.compiler.TokenType.*

/**
 * Recursive descent, newline-sensitive (one statement per line), expressions
 * by precedence climbing that deliberately mirrors Kotlin's table so emitted
 * code keeps identical semantics.
 *
 * BANNED tokens from the lexer are normalized up front: each produces one
 * diagnostic and is replaced by the token that was clearly meant, so parsing
 * recovers with maximum signal.
 */
class Parser(rawTokens: List<Token>, private val diags: DiagnosticBag) {

    private val tokens: List<Token> = normalize(rawTokens)
    private var idx = 0
    private var errorCount = 0

    private companion object {
        const val MAX_ERRORS = 20
        val BANNED_REPLACEMENTS: Map<String, TokenType> = mapOf(
            "is" to IS, "aint" to AINT, "atmost" to ATMOST, "atleast" to ATLEAST,
            "and" to AND, "or" to OR, "deadahh" to DEADAHH, "otherwise" to OTHERWISE,
            "not" to NOT,
        )
    }

    private fun normalize(raw: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        for (t in raw) {
            if (t.type != BANNED) { out += t; continue }
            val suggestion = t.suggestion ?: "something else"
            when {
                t.text == ";" -> {
                    diags.error("E_SEMICOLON", "semicolons are not used in Rotlin", t.line, t.col,
                        hint = "one statement per line - just press enter")
                    if (out.isNotEmpty() && out.last().type != NEWLINE) {
                        out += Token(NEWLINE, "\\n", t.line, t.col)
                    }
                }
                BANNED_REPLACEMENTS.containsKey(suggestion) -> {
                    diags.error("E_BANNED_SYMBOL", "`${t.text}` is not valid Rotlin syntax",
                        t.line, t.col, hint = "write `$suggestion` instead")
                    out += Token(BANNED_REPLACEMENTS.getValue(suggestion), suggestion, t.line, t.col)
                }
                else -> {
                    diags.error("E_BANNED_SYMBOL", "`${t.text}` is not valid Rotlin syntax", t.line, t.col,
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
                diags.error("E_TOO_MANY_ERRORS", "too many errors - fix the first few and recompile",
                    at.line, at.col)
            }
        }
    }

    private fun skipNewlines() {
        while (at(NEWLINE)) advance()
    }

    /** Consumes end-of-statement: a newline, or lets `}`/EOF terminate. */
    private fun expectStmtEnd() {
        when {
            at(NEWLINE) -> advance()
            at(RBRACE) || at(EOF) || at(ELSE) -> {}
            else -> {
                error("E_STMT_END", "one statement per line - `${describe(peek())}` doesn't belong here",
                    peek(), hint = "press enter and put it on its own line")
                synchronize()
            }
        }
    }

    private fun synchronize() {
        while (!at(NEWLINE) && !at(EOF) && !at(RBRACE)) advance()
        if (at(NEWLINE)) advance()
    }

    // ---- program ---------------------------------------------------------

    fun parseProgram(): Program {
        skipNewlines()

        var pkg: PackageDecl? = null
        if (at(PACKAGE)) {
            val t = advance()
            val path = parseDottedPath()
            pkg = PackageDecl(path.first, t.line, t.col)
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
        return Program(pkg, summons, items)
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
            TUNG -> parseFunDecl()
            ALPHA, BETA -> parseVarDecl()
            IF -> parseIf()
            GRIND -> parseGrind()
            YEET -> parseYeet()
            DIP -> advance().let { val s = DipStmt(it.line, it.col); expectStmtEnd(); s }
            SKIP -> advance().let { val s = SkipStmt(it.line, it.col); expectStmtEnd(); s }
            DROP -> parseDropSite()
            CLASS -> parseClass()
            NPC -> parseNpc()
            VIBE -> parseVibe()
            WHEN -> parseWhen()
            MOG -> parseMog()
            TRY -> parseTry()
            CRASHOUT -> parseCrashout()
            PRIVATE, OVERRIDE -> {
                error("E_MODIFIER_NOWHERE", "`${peek().text}` only makes sense inside a class/npc/vibe",
                    peek(), hint = "move this into a class body")
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

    private fun parseFunDecl(
        isPrivate: Boolean = false,
        isOverride: Boolean = false,
        allowNoBody: Boolean = false,
    ): Stmt? {
        val kw = advance()
        val name = expect(IDENT, "a function name after `tung`") ?: run { synchronize(); return null }
        expect(LPAREN, "`(` after the function name") ?: run { synchronize(); return null }
        val params = mutableListOf<Param>()
        skipNewlines()
        while (!at(RPAREN) && !at(EOF)) {
            val pname = expect(IDENT, "a parameter name") ?: break
            expect(COLON, "`:` and a type after `${pname.text}` (parameters need explicit types)") ?: break
            val ptype = parseType() ?: break
            params += Param(pname.text, ptype, pname.line, pname.col)
            if (at(COMMA)) { advance(); skipNewlines() } else break
        }
        expect(RPAREN, "`)` to close the parameter list")
        val returnType = if (at(SPITS)) { advance(); parseType() } else null
        if (allowNoBody && !at(LBRACE)) {
            expectStmtEnd()
            return FunDecl(name.text, params, returnType, null, kw.line, kw.col, isPrivate, isOverride)
        }
        val body = parseBlock() ?: return null
        return FunDecl(name.text, params, returnType, body, kw.line, kw.col, isPrivate, isOverride)
    }

    private fun parseVarDecl(isPrivate: Boolean = false): Stmt? {
        val kw = advance()
        val mutable = kw.type == BETA
        val name = expect(IDENT, "a name after `${kw.text}`") ?: run { synchronize(); return null }
        var declared: TypeRef? = null
        if (at(COLON)) { advance(); declared = parseType() }
        expect(ASSIGN, "`=` and an initial value (every ${kw.text} needs one)") ?: run { synchronize(); return null }
        val init = parseExpression() ?: run { synchronize(); return null }
        expectStmtEnd()
        return VarDecl(mutable, name.text, declared, init, kw.line, kw.col, isPrivate)
    }

    // ---- oop ---------------------------------------------------------------

    private fun parseClass(): Stmt? {
        val kw = advance()
        val name = expect(IDENT, "a name after `class`") ?: run { synchronize(); return null }

        val ctorParams = mutableListOf<CtorParam>()
        if (at(LPAREN)) {
            advance(); skipNewlines()
            while (!at(RPAREN) && !at(EOF)) {
                var priv = false
                var kind = CtorParamKind.PLAIN
                loop@ while (true) {
                    when (peek().type) {
                        PRIVATE -> { advance(); priv = true }
                        ALPHA -> { advance(); kind = CtorParamKind.ALPHA }
                        BETA -> { advance(); kind = CtorParamKind.BETA }
                        else -> break@loop
                    }
                }
                val pn = expect(IDENT, "a constructor parameter name") ?: break
                expect(COLON, "`:` and a type for `${pn.text}`") ?: break
                val pt = parseType() ?: break
                ctorParams += CtorParam(kind, priv, pn.text, pt, pn.line, pn.col)
                skipNewlines()
                if (at(COMMA)) { advance(); skipNewlines() } else break
            }
            expect(RPAREN, "`)` to close the constructor")
        }

        var superRef: SuperRef? = null
        if (at(IS_A)) {
            val t = advance()
            val sn = expect(IDENT, "the parent after `is a`") ?: run { synchronize(); return null }
            val args = mutableListOf<Expr>()
            if (at(LPAREN)) {
                advance()
                while (!at(RPAREN) && !at(EOF)) {
                    args += parseExpression() ?: break
                    if (at(COMMA)) advance() else break
                }
                expect(RPAREN, "`)` to close the parent's arguments")
            }
            superRef = SuperRef(sn.text, args, t.line, t.col)
        }

        val vibes = mutableListOf<String>()
        if (at(VIBES_WITH)) {
            advance()
            while (true) {
                vibes += (expect(IDENT, "a vibe name after `vibes with`") ?: break).text
                if (at(COMMA)) advance() else break
            }
        }

        val (members, endLine) = parseMemberBlock("class ${name.text}") ?: return null
        return ClassDecl(name.text, ctorParams, superRef, vibes, members, endLine, kw.line, kw.col)
    }

    private fun parseNpc(): Stmt? {
        val kw = advance()
        val name = expect(IDENT, "a name after `npc`") ?: run { synchronize(); return null }
        val (members, endLine) = parseMemberBlock("npc ${name.text}") ?: return null
        return NpcDecl(name.text, members, endLine, kw.line, kw.col)
    }

    private fun parseVibe(): Stmt? {
        val kw = advance()
        val name = expect(IDENT, "a name after `vibe`") ?: run { synchronize(); return null }
        val (members, endLine) = parseMemberBlock("vibe ${name.text}", allowAbstract = true) ?: return null
        return VibeDecl(name.text, members, endLine, kw.line, kw.col)
    }

    /** Parses `{ ... }` allowing only alpha/beta/tung members (with modifiers). */
    private fun parseMemberBlock(owner: String, allowAbstract: Boolean = false): Pair<List<Stmt>, Int>? {
        skipNewlines()
        val lbrace = expect(LBRACE, "`{` to open $owner") ?: run { synchronize(); return null }
        val members = mutableListOf<Stmt>()
        while (true) {
            skipNewlines()
            if (at(RBRACE)) break
            if (at(EOF)) {
                error("E_UNCLOSED_BLOCK", "$owner is missing its closing `}`", lbrace,
                    hint = "close it with `}`")
                return members to peek().line
            }
            var priv = false
            var over = false
            while (at(PRIVATE) || at(OVERRIDE)) {
                if (at(PRIVATE)) priv = true else over = true
                advance()
            }
            when (peek().type) {
                ALPHA, BETA -> parseVarDecl(isPrivate = priv)?.let { members += it }
                TUNG -> parseFunDecl(isPrivate = priv, isOverride = over, allowNoBody = allowAbstract)?.let { members += it }
                else -> {
                    error("E_CLASS_MEMBER", "only alpha, beta and tung declarations can be members of $owner", peek(),
                        hint = "put other code inside a tung method")
                    synchronize()
                }
            }
        }
        val rbrace = advance()
        return members to rbrace.line
    }

    // ---- control flow --------------------------------------------------------

    private fun parseWhen(): Stmt? {
        val kw = advance()
        expect(LPAREN, "`(` after `when`")
        val subject = parseExpression() ?: run { synchronize(); return null }
        expect(RPAREN, "`)` after the value")
        skipNewlines()
        expect(LBRACE, "`{` to open the when") ?: run { synchronize(); return null }

        val branches = mutableListOf<WhenBranch>()
        while (true) {
            skipNewlines()
            if (at(RBRACE) || at(EOF)) break
            val startTok = peek()

            val values: List<Expr>?
            if (at(ELSE)) {
                advance()
                values = null
            } else {
                val vs = mutableListOf<Expr>()
                while (true) {
                    vs += parseExpression() ?: break
                    if (at(COMMA)) advance() else break
                }
                if (vs.isEmpty()) { synchronize(); continue }
                values = vs
            }
            if (expect(ARROW, "`->` after the value(s)") == null) { synchronize(); continue }
            if (!at(LBRACE) && peek().type in setOf(IF, GRIND, MOG, WHEN, CLASS, NPC, VIBE, DROP)) {
                error("E_BRANCH_BRACE", "multi-line branches need their own block", peek(),
                    hint = "write `-> {` and close with `}`")
                synchronize(); continue
            }
            val body: Block = if (at(LBRACE)) {
                parseBlock() ?: continue
            } else {
                val s = parseStmt() ?: continue
                Block(listOf(s), s.line, s.col, s.line)
            }
            branches += WhenBranch(values, body, startTok.line, startTok.col)
        }
        val endTok = if (at(RBRACE)) advance() else peek()
        return WhenStmt(subject, branches, endTok.line, kw.line, kw.col)
    }

    private fun parseTry(): Stmt? {
        val kw = advance()
        // `catch` may close the try block directly, mirroring how `else` closes an if
        val tryBlock = parseBlock(stopAtToken = CATCH) ?: return null
        skipNewlines()
        if (expect(CATCH, "`catch (name)` after the try block") == null) {
            synchronize(); return null
        }
        expect(LPAREN, "`(` after `catch`")
        val name = expect(IDENT, "a name for the caught error, like `(oops)`") ?: run { synchronize(); return null }
        expect(RPAREN, "`)` after the name")
        val catchBlock = parseBlock() ?: return null
        return TryStmt(tryBlock, name.text, catchBlock, kw.line, kw.col)
    }

    private fun parseCrashout(): Stmt {
        val kw = advance()
        val value = parseExpression() ?: StringTmpl(listOf(TmplNode.Text("crashout")), kw.line, kw.col)
        expectStmtEnd()
        return CrashoutStmt(value, kw.line, kw.col)
    }

    private fun parseMog(): Stmt? {
        val kw = advance()
        expect(LPAREN, "`(` after `mog`")
        val v = expect(IDENT, "a loop variable - like `mog (item inside things)`") ?: run { synchronize(); return null }
        expect(INSIDE, "`inside` after `${v.text}`") ?: run { synchronize(); return null }
        val iterable = parseExpression() ?: run { synchronize(); return null }
        expect(RPAREN, "`)` to close the mog")
        val body = parseBlock() ?: return null
        return MogStmt(v.text, iterable, body, kw.line, kw.col)
    }

    private fun parseIf(): Stmt? {
        val kw = advance()
        expect(LPAREN, "`(` after `if`")
        val cond = parseExpression() ?: run { synchronize(); return null }
        expect(RPAREN, "`)` after the condition")
        // `else` may close the then-block directly - no `}` needed before it
        val thenBlock = parseBlock(stopAtToken = ELSE) ?: return null

        var elseBranch: Node? = null
        val save = idx
        skipNewlines()
        if (at(ELSE)) {
            advance()
            elseBranch = if (at(IF)) parseIf() else parseBlock()
        } else {
            idx = save
        }
        return IfStmt(cond, thenBlock, elseBranch, kw.line, kw.col)
    }

    private fun parseDropSite(): Stmt? {
        val kw = advance()
        if (!(at(IDENT) && peek().text == "site")) {
            error("E_DROP_WHAT", "drop what?", peek(),
                hint = "it goes `drop site on 3000 { ... }`")
            synchronize(); return null
        }
        advance() // site
        if (!(at(IDENT) && peek().text == "on")) {
            error("E_DROP_WHAT", "where does the site go?", peek(),
                hint = "it goes `drop site on 3000 { ... }`")
            synchronize(); return null
        }
        advance() // on
        val port = parseExpression() ?: run { synchronize(); return null }
        val block = parseBlock() ?: return null
        return DropSiteStmt(port, block, kw.line, kw.col)
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
        val value = if (at(NEWLINE) || at(RBRACE) || at(EOF)) null else parseExpression()
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
        if (expr !is NameRef && expr !is MemberAccess && expr !is IndexExpr) {
            error("E_BAD_ASSIGN", "you can't assign to that", opTok,
                hint = "put a variable name on the left of `${opTok.text}`")
        }
        val value = parseExpression() ?: run { synchronize(); return null }
        expectStmtEnd()
        return Assign(expr, op, value, start.line, start.col)
    }

    // ---- blocks -----------------------------------------------------------

    private fun parseBlock(stopAtToken: TokenType? = null): Block? {
        skipNewlines()
        val lbrace = expect(LBRACE, "`{` to open the block") ?: run { synchronize(); return null }
        val stmts = mutableListOf<Stmt>()
        while (true) {
            skipNewlines()
            if (stopAtToken != null && at(stopAtToken)) {
                // else / catch closes this block; caller consumes it
                return Block(stmts, lbrace.line, lbrace.col, peek().line)
            }
            if (at(RBRACE)) break
            if (at(EOF)) {
                error("E_UNCLOSED_BLOCK", "this `{` is missing its closing `}`", lbrace,
                    hint = "close the block with `}`")
                return Block(stmts, lbrace.line, lbrace.col, peek().line)
            }
            if (errorCount >= MAX_ERRORS) break
            val before = idx
            parseStmt()?.let { stmts += it }
            if (idx == before && !at(RBRACE) && !at(EOF)) advance() // always make progress
        }
        val rbrace = advance()
        return Block(stmts, lbrace.line, lbrace.col, rbrace.line)
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
        while (at(IS) || at(AINT)) {
            val t = advance()
            val op = if (t.type == IS) BinOp.IS else BinOp.AINT
            val right = parseComparison() ?: return left
            left = Binary(op, left, right, t.line, t.col)
        }
        return left
    }

    private fun parseComparison(): Expr? {
        var left = parseElvis() ?: return null
        while (true) {
            val op = when (peek().type) {
                GT -> BinOp.GT
                LT -> BinOp.LT
                ATLEAST -> BinOp.ATLEAST
                ATMOST -> BinOp.ATMOST
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
                        skipNewlines() // the closing `)` may sit on its own line
                        if (at(COMMA)) { advance(); skipNewlines() } else break
                    }
                    expect(RPAREN, "`)` to close the call")
                    // trailing lambda: `page("/") { ... }`, `smash("x") does { ... }`
                    var lambda: Block? = null
                    if (at(DOES)) { advance(); lambda = parseBlock() }
                    else if (at(LBRACE)) lambda = parseBlock()
                    Call(expr, args, t.line, t.col, lambda)
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
                DEADAHH -> {
                    val t = advance()
                    DeadahhExpr(expr, t.line, t.col)
                }
                LBRACKET -> {
                    val t = advance()
                    val index = parseExpression() ?: return expr
                    expect(RBRACKET, "`]` to close the index")
                    IndexExpr(expr, index, t.line, t.col)
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
            TRUE -> { advance(); BoolLit(true, t.line, t.col) }
            FALSE -> { advance(); BoolLit(false, t.line, t.col) }
            NULL -> { advance(); NullLit(t.line, t.col) }
            THIS -> { advance(); ThisRef(t.line, t.col) }
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
