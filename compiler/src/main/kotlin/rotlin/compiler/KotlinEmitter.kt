package rotlin.compiler

data class EmitOutput(val ktText: String, val lineMap: LineMap, val mainClassFqn: String)

/**
 * Line-anchored printer: every construct emits Kotlin on the same line as its
 * Rotlin source (plus a constant 2-line prelude), so kotlinc diagnostics AND
 * runtime stack traces map back to `.rot` lines by plain subtraction.
 *
 * Script-style top level: declarations before the first statement stay
 * top-level; from the first statement onward everything is wrapped into a
 * synthesized `fun main() {` opened inline on that statement's line and closed
 * on the line after the last one.
 */
class KotlinEmitter {

    private companion object {
        const val PRELUDE_LINES = 2

        val TYPE_MAP = mapOf(
            "aura" to "Int",
            "ratio" to "Double",
            "lore" to "String",
            "fact" to "Boolean",
            "squad" to "MutableList",
            "stash" to "MutableMap",
        )

        val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
            "in", "interface", "is", "null", "object", "package", "return", "super", "this",
            "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        )
    }

    private val sb = StringBuilder()
    private var currentLine = 1
    private var inClass = false
    private var inInterface = false

    fun emit(program: Program): EmitOutput {
        sb.append("@file:JvmName(\"RotMain\")\n")
        currentLine = 2

        if (program.hood != null) {
            // package must precede imports, so the runtime import rides the hood line
            padTo(program.hood.line + PRELUDE_LINES)
            write("package ${program.hood.path}; import rotlin.runtime.*")
        } else {
            write("import rotlin.runtime.*")
        }

        for (summon in program.summons) {
            padTo(summon.line + PRELUDE_LINES)
            write("import ${summon.path}${if (summon.wildcard) ".*" else ""}")
        }

        // split: leading declarations stay top-level, rest wraps into main()
        val firstStmtIdx = program.items.indexOfFirst {
            it !is FunDecl && it !is VarDecl && it !is SigmaDecl && it !is NpcDecl && it !is VibeDecl
        }
        val topLevel = if (firstStmtIdx == -1) program.items else program.items.take(firstStmtIdx)
        val mainBody = if (firstStmtIdx == -1) emptyList() else program.items.drop(firstStmtIdx)

        for (item in topLevel) emitStmt(item)

        if (mainBody.isNotEmpty()) {
            padTo(mainBody.first().line + PRELUDE_LINES)
            write("fun main() { ")
            for (item in mainBody) emitStmt(item)
            padTo(currentLine + 1)
            write("}")
        }

        sb.append('\n')
        val fqn = if (program.hood != null) "${program.hood.path}.RotMain" else "RotMain"
        return EmitOutput(sb.toString(), LineMap.offset(PRELUDE_LINES), fqn)
    }

    // ---- printer ----------------------------------------------------------

    private fun padTo(line: Int) {
        while (currentLine < line) {
            sb.append('\n')
            currentLine++
        }
    }

    private fun write(text: String) {
        sb.append(text)
    }

    /** Pads to [line]; if content already sits on it, separates with a space. */
    private fun anchor(line: Int) {
        if (line > currentLine) padTo(line)
        else if (sb.isNotEmpty() && sb.last() != '\n' && sb.last() != ' ') write(" ")
    }

    private fun escapeName(name: String): String =
        if (name in KOTLIN_KEYWORDS) "`$name`" else name

    // ---- statements --------------------------------------------------------

    private fun emitStmt(stmt: Stmt) {
        when (stmt) {
            is FunDecl -> {
                anchor(stmt.line + PRELUDE_LINES)
                val params = stmt.params.joinToString(", ") { "${escapeName(it.name)}: ${typeText(it.type)}" }
                val ret = stmt.returnType?.let { ": ${typeText(it)}" } ?: ""
                val mods = buildString {
                    if (stmt.gatekeep) append("private ")
                    when {
                        stmt.remix -> append("override ")
                        // all-open world: any class method can be remixed by a child
                        inClass && !inInterface -> append("open ")
                    }
                }
                write("${mods}fun ${escapeName(stmt.name)}($params)$ret")
                if (stmt.body != null) {
                    write(" {")
                    emitBlockBody(stmt.body)
                }
            }
            is VarDecl -> {
                anchor(stmt.line + PRELUDE_LINES)
                val kw = if (stmt.mutable) "var" else "val"
                val gate = if (stmt.gatekeep) "private " else ""
                val typed = stmt.declaredType?.let { ": ${typeText(it)}" } ?: ""
                write("$gate$kw ${escapeName(stmt.name)}$typed = ${exprText(stmt.init)}")
            }
            is SigmaDecl -> {
                anchor(stmt.line + PRELUDE_LINES)
                val ctor = if (stmt.ctorParams.isEmpty()) "" else "(" + stmt.ctorParams.joinToString(", ") { p ->
                    buildString {
                        if (p.gatekeep) append("private ")
                        when (p.kind) {
                            CtorParamKind.RIZZ -> append("val ")
                            CtorParamKind.GYATT -> append("var ")
                            CtorParamKind.PLAIN -> {}
                        }
                        append("${escapeName(p.name)}: ${typeText(p.type)}")
                    }
                } + ")"
                val supers = mutableListOf<String>()
                stmt.superRef?.let { s ->
                    supers += "${escapeName(s.name)}(${s.args.joinToString(", ") { exprText(it) }})"
                }
                supers += stmt.vibes.map { escapeName(it) }
                val heritage = if (supers.isEmpty()) "" else " : " + supers.joinToString(", ")
                write("open class ${escapeName(stmt.name)}$ctor$heritage {")
                val saved = inClass
                inClass = true
                for (m in stmt.members) emitStmt(m)
                inClass = saved
                anchor(stmt.endLine + PRELUDE_LINES)
                write("}")
            }
            is NpcDecl -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("object ${escapeName(stmt.name)} {")
                for (m in stmt.members) emitStmt(m)
                anchor(stmt.endLine + PRELUDE_LINES)
                write("}")
            }
            is VibeDecl -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("interface ${escapeName(stmt.name)} {")
                val savedClass = inClass
                val savedIface = inInterface
                inClass = true
                inInterface = true
                for (m in stmt.members) emitStmt(m)
                inClass = savedClass
                inInterface = savedIface
                anchor(stmt.endLine + PRELUDE_LINES)
                write("}")
            }
            is VibecheckStmt -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("when (${exprText(stmt.subject)}) {")
                for (b in stmt.branches) {
                    anchor(b.line + PRELUDE_LINES)
                    val head = b.values?.joinToString(", ") { exprText(it) } ?: "else"
                    val single = b.body.stmts.singleOrNull()
                    val inlineable = single != null && b.body.line == b.body.endLine &&
                        (single is ExprStmt || single is Assign || single is VarDecl ||
                            single is YeetStmt || single is DipStmt || single is SkipStmt)
                    if (inlineable) {
                        write("$head -> ${flatStmt(single!!)}")
                    } else {
                        write("$head -> {")
                        emitBlockBody(b.body)
                    }
                }
                anchor(stmt.endLine + PRELUDE_LINES)
                write("}")
            }
            is MogStmt -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("for (${escapeName(stmt.varName)} in ${exprText(stmt.iterable)}) {")
                emitBlockBody(stmt.body)
            }
            is Assign -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("${exprText(stmt.target)} ${stmt.op.kotlin} ${exprText(stmt.value)}")
            }
            is SusStmt -> emitSus(stmt)
            is GrindStmt -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("while (${exprText(stmt.cond)}) {")
                emitBlockBody(stmt.body)
            }
            is YeetStmt -> {
                anchor(stmt.line + PRELUDE_LINES)
                write(if (stmt.value == null) "return" else "return ${exprText(stmt.value)}")
            }
            is DipStmt -> { anchor(stmt.line + PRELUDE_LINES); write("break") }
            is SkipStmt -> { anchor(stmt.line + PRELUDE_LINES); write("continue") }
            is ExprStmt -> {
                val e = stmt.expr
                if (e is Call && e.lambda != null) {
                    anchor(stmt.line + PRELUDE_LINES)
                    write("${render(e.callee, parenthesize = true)}(${e.args.joinToString(", ") { exprText(it) }}) {")
                    emitBlockBody(e.lambda)
                } else {
                    anchor(stmt.line + PRELUDE_LINES)
                    write(exprText(e))
                }
            }
            is DropSiteStmt -> {
                anchor(stmt.line + PRELUDE_LINES)
                write("site(${exprText(stmt.port)}) {")
                emitBlockBody(stmt.block)
            }
        }
    }

    private fun emitSus(stmt: SusStmt) {
        anchor(stmt.line + PRELUDE_LINES)
        write("if (${exprText(stmt.cond)}) {")
        emitBlockBody(stmt.thenBlock)
        when (val e = stmt.elseBranch) {
            null -> {}
            is Block -> {
                anchor(e.line + PRELUDE_LINES)
                write("else {")
                emitBlockBody(e)
            }
            is SusStmt -> {
                anchor(e.line + PRELUDE_LINES)
                write("else ")
                // inline: emit the nested if on this same line
                write("if (${exprText(e.cond)}) {")
                emitBlockBody(e.thenBlock)
                when (val e2 = e.elseBranch) {
                    null -> {}
                    is Block -> { anchor(e2.line + PRELUDE_LINES); write("else {"); emitBlockBody(e2) }
                    is SusStmt -> { anchor(e2.line + PRELUDE_LINES); write("else "); emitSusInline(e2) }
                    else -> {}
                }
            }
            else -> {}
        }
    }

    private fun emitSusInline(stmt: SusStmt) {
        write("if (${exprText(stmt.cond)}) {")
        emitBlockBody(stmt.thenBlock)
        when (val e = stmt.elseBranch) {
            null -> {}
            is Block -> { anchor(e.line + PRELUDE_LINES); write("else {"); emitBlockBody(e) }
            is SusStmt -> { anchor(e.line + PRELUDE_LINES); write("else "); emitSusInline(e) }
            else -> {}
        }
    }

    /** Emits the statements of a block and its closing `}` on the periodt line. */
    private fun emitBlockBody(block: Block) {
        for (s in block.stmts) emitStmt(s)
        anchor(block.endLine + PRELUDE_LINES)
        write("}")
    }

    /** Best-effort single-line statement text, for inline when-branches and nested lambdas. */
    private fun flatStmt(stmt: Stmt): String = when (stmt) {
        is ExprStmt -> exprText(stmt.expr)
        is Assign -> "${exprText(stmt.target)} ${stmt.op.kotlin} ${exprText(stmt.value)}"
        is VarDecl -> "${if (stmt.mutable) "var" else "val"} ${escapeName(stmt.name)} = ${exprText(stmt.init)}"
        is YeetStmt -> if (stmt.value == null) "return" else "return ${exprText(stmt.value)}"
        is DipStmt -> "break"
        is SkipStmt -> "continue"
        else -> "Unit"
    }

    // ---- types ---------------------------------------------------------------

    private fun typeText(type: TypeRef): String = when (type) {
        is TypeRef.Named -> {
            val base = TYPE_MAP[type.name] ?: type.name
            if (type.args.isEmpty()) base
            else "$base<${type.args.joinToString(", ") { typeText(it) }}>"
        }
        is TypeRef.Maybe -> "${typeText(type.inner)}?"
    }

    // ---- expressions ------------------------------------------------------------

    private fun exprText(expr: Expr): String = render(expr, parenthesize = false)

    /** Operands wrap binaries/unaries in parens — cheap, same-line, semantics-proof. */
    private fun operand(expr: Expr): String = render(expr, parenthesize = true)

    private fun render(expr: Expr, parenthesize: Boolean): String = when (expr) {
        is IntLit -> expr.text
        is DoubleLit -> expr.text
        is BoolLit -> expr.value.toString()
        is GhostedLit -> "null"
        is NameRef -> escapeName(expr.name)
        is MeRef -> "this"
        is IndexExpr -> "${render(expr.receiver, parenthesize = true)}[${exprText(expr.index)}]"
        is StringTmpl -> buildString {
            append('"')
            for (part in expr.parts) {
                when (part) {
                    is TmplNode.Text -> append(part.raw)
                    is TmplNode.Interp -> append("\${").append(exprText(part.expr)).append("}")
                }
            }
            append('"')
        }
        is Call -> {
            val base = "${render(expr.callee, parenthesize = true)}(${expr.args.joinToString(", ") { exprText(it) }})"
            // a lambda-call nested inside an expression flattens to one line
            if (expr.lambda == null) base
            else "$base { ${expr.lambda.stmts.joinToString("; ") { flatStmt(it) }} }"
        }
        is MemberAccess -> "${render(expr.receiver, parenthesize = true)}${if (expr.safe) "?." else "."}${escapeName(expr.name)}"
        is DeadassExpr -> "${render(expr.operand, parenthesize = true)}.deadass()"
        is Unary -> {
            val text = "${expr.op.kotlin}${render(expr.operand, parenthesize = true)}"
            if (parenthesize) "($text)" else text
        }
        is Binary -> {
            val sep = if (expr.op == BinOp.THROUGH) "" else " "
            val text = "${operand(expr.left)}$sep${expr.op.kotlin}$sep${operand(expr.right)}"
            if (parenthesize) "($text)" else text
        }
    }
}
