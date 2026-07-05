package rotlin.compiler

/** Compact s-expression rendering of the AST for snapshot-style assertions. */
fun Node.sexp(): String = when (this) {
    is Program -> items.joinToString(" ") { it.sexp() }
    is HoodDecl -> "(hood $path)"
    is SummonDecl -> "(summon $path${if (wildcard) ".*" else ""})"
    is Param -> "$name: ${type.sexp()}"
    is TypeRef.Named -> if (args.isEmpty()) name else "$name<${args.joinToString(", ") { it.sexp() }}>"
    is TypeRef.Maybe -> "maybe ${inner.sexp()}"
    is Block -> "{${stmts.joinToString("; ") { it.sexp() }}}"
    is FunDecl -> buildString {
        append("(fun $name (${params.joinToString(", ") { it.sexp() }})")
        returnType?.let { append(": ${it.sexp()}") }
        append(" ${body.sexp()})")
    }
    is VarDecl -> buildString {
        append("(${if (mutable) "var" else "val"} $name")
        declaredType?.let { append(": ${it.sexp()}") }
        append(" ${init.sexp()})")
    }
    is Assign -> "(${op.kotlin} ${target.sexp()} ${value.sexp()})"
    is SusStmt -> buildString {
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
    is GhostedLit -> "null"
    is NameRef -> name
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
    is DeadassExpr -> "(deadass ${operand.sexp()})"
}
