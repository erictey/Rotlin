package rotlin.compiler

/** Compact s-expression rendering of the AST for snapshot-style assertions. */
fun Node.sexp(): String = when (this) {
    is Program -> items.joinToString(" ") { it.sexp() }
    is PackageDecl -> "(package $path)"
    is SummonDecl -> "(summon $path${if (wildcard) ".*" else ""})"
    is Param -> "$name: ${type.sexp()}"
    is TypeRef.Named -> if (args.isEmpty()) name else "$name<${args.joinToString(", ") { it.sexp() }}>"
    is TypeRef.Maybe -> "maybe ${inner.sexp()}"
    is Block -> "{${stmts.joinToString("; ") { it.sexp() }}}"
    is FunDecl -> buildString {
        append("(")
        if (isPrivate) append("private ")
        if (isOverride) append("override ")
        append("fun $name (${params.joinToString(", ") { it.sexp() }})")
        returnType?.let { append(": ${it.sexp()}") }
        body?.let { append(" ${it.sexp()}") }
        append(")")
    }
    is ClassDecl -> buildString {
        append("(class $name")
        if (ctorParams.isNotEmpty()) {
            append(" (")
            append(ctorParams.joinToString(", ") { p ->
                buildString {
                    if (p.isPrivate) append("private ")
                    when (p.kind) {
                        CtorParamKind.ALPHA -> append("val ")
                        CtorParamKind.BETA -> append("var ")
                        CtorParamKind.PLAIN -> {}
                    }
                    append("${p.name}: ${p.type.sexp()}")
                }
            })
            append(")")
        }
        superRef?.let { append(" :${it.name}(${it.args.joinToString(", ") { a -> a.sexp() }})") }
        if (vibes.isNotEmpty()) append(" ~${vibes.joinToString(",")}")
        append(" {${members.joinToString("; ") { it.sexp() }}})")
    }
    is NpcDecl -> "(npc $name {${members.joinToString("; ") { it.sexp() }}})"
    is VibeDecl -> "(vibe $name {${members.joinToString("; ") { it.sexp() }}})"
    is WhenStmt -> buildString {
        append("(when ${subject.sexp()}")
        for (b in branches) {
            append(" [")
            append(b.values?.joinToString(", ") { it.sexp() } ?: "else")
            append(" -> ${b.body.sexp()}]")
        }
        append(")")
    }
    is MogStmt -> "(for $varName in ${iterable.sexp()} ${body.sexp()})"
    is TryStmt -> "(try ${tryBlock.sexp()} catch $catchName ${catchBlock.sexp()})"
    is CrashoutStmt -> "(throw ${value.sexp()})"
    is VarDecl -> buildString {
        append("(")
        if (isPrivate) append("private ")
        append("${if (mutable) "var" else "val"} $name")
        declaredType?.let { append(": ${it.sexp()}") }
        append(" ${init.sexp()})")
    }
    is Assign -> "(${op.kotlin} ${target.sexp()} ${value.sexp()})"
    is IfStmt -> buildString {
        append("(if ${cond.sexp()} ${thenBlock.sexp()}")
        elseBranch?.let { append(" else ${it.sexp()}") }
        append(")")
    }
    is GrindStmt -> "(while ${cond.sexp()} ${body.sexp()})"
    is YeetStmt -> if (value == null) "(return)" else "(return ${value.sexp()})"
    is DipStmt -> "(break)"
    is SkipStmt -> "(continue)"
    is ExprStmt -> expr.sexp()
    is DropSiteStmt -> "(drop-site ${port.sexp()} ${block.sexp()})"
    is IntLit -> text
    is DoubleLit -> text
    is BoolLit -> value.toString()
    is NullLit -> "null"
    is NameRef -> name
    is ThisRef -> "this"
    is IndexExpr -> "([] ${receiver.sexp()} ${index.sexp()})"
    is StringTmpl -> "(str ${parts.joinToString(" ") {
        when (it) {
            is TmplNode.Text -> "\"${it.raw}\""
            is TmplNode.Interp -> "(interp ${it.expr.sexp()})"
        }
    }})"
    is Call -> "(call ${callee.sexp()}${args.joinToString("") { " ${it.sexp()}" }}${lambda?.let { " ${it.sexp()}" } ?: ""})"
    is MemberAccess -> "(${if (safe) "?." else "."} ${receiver.sexp()} $name)"
    is Binary -> "(${op.kotlin} ${left.sexp()} ${right.sexp()})"
    is Unary -> "(${if (op == UnaryOp.NOT) "!" else "neg"} ${operand.sexp()})"
    is DeadahhExpr -> "(deadahh ${operand.sexp()})"
    else -> error("no sexp rendering for $this") // CtorParam/SuperRef/WhenBranch render inline
}
