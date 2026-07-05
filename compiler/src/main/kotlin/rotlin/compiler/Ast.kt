package rotlin.compiler

sealed interface Node {
    val line: Int
    val col: Int
}

// ---- types ----------------------------------------------------------------

sealed interface TypeRef : Node {
    data class Named(
        val name: String,
        val args: List<TypeRef>,
        override val line: Int,
        override val col: Int,
    ) : TypeRef

    data class Maybe(
        val inner: TypeRef,
        override val line: Int,
        override val col: Int,
    ) : TypeRef
}

// ---- program structure -----------------------------------------------------

data class Program(
    val hood: HoodDecl?,
    val summons: List<SummonDecl>,
    val items: List<Stmt>,
    override val line: Int = 1,
    override val col: Int = 1,
) : Node

data class HoodDecl(val path: String, override val line: Int, override val col: Int) : Node

data class SummonDecl(
    val path: String,
    val wildcard: Boolean,
    override val line: Int,
    override val col: Int,
) : Node

data class Param(
    val name: String,
    val type: TypeRef,
    override val line: Int,
    override val col: Int,
) : Node

/** `bet ... periodt`. [endLine] is the periodt line, where `}` is emitted. */
data class Block(
    val stmts: List<Stmt>,
    override val line: Int,
    override val col: Int,
    val endLine: Int,
) : Node

// ---- statements -------------------------------------------------------------

sealed interface Stmt : Node

/** [body] is null only for abstract signatures inside a `vibe`. */
data class FunDecl(
    val name: String,
    val params: List<Param>,
    val returnType: TypeRef?,
    val body: Block?,
    override val line: Int,
    override val col: Int,
    val gatekeep: Boolean = false,
    val remix: Boolean = false,
) : Stmt

data class VarDecl(
    val mutable: Boolean,
    val name: String,
    val declaredType: TypeRef?,
    val init: Expr,
    override val line: Int,
    override val col: Int,
    val gatekeep: Boolean = false,
) : Stmt

// ---- OOP ---------------------------------------------------------------------

enum class CtorParamKind { PLAIN, RIZZ, GYATT }

data class CtorParam(
    val kind: CtorParamKind,
    val gatekeep: Boolean,
    val name: String,
    val type: TypeRef,
    override val line: Int,
    override val col: Int,
) : Node

data class SuperRef(
    val name: String,
    val args: List<Expr>,
    override val line: Int,
    override val col: Int,
) : Node

/** `sigma Dog(rizz name: lore) is a Animal vibes with Fetchable bet ... periodt` */
data class SigmaDecl(
    val name: String,
    val ctorParams: List<CtorParam>,
    val superRef: SuperRef?,
    val vibes: List<String>,
    val members: List<Stmt>,
    val endLine: Int,
    override val line: Int,
    override val col: Int,
) : Stmt

/** `npc Config bet ... periodt` — a singleton. */
data class NpcDecl(
    val name: String,
    val members: List<Stmt>,
    val endLine: Int,
    override val line: Int,
    override val col: Int,
) : Stmt

/** `vibe Fetchable bet ... periodt` — an interface. */
data class VibeDecl(
    val name: String,
    val members: List<Stmt>,
    val endLine: Int,
    override val line: Int,
    override val col: Int,
) : Stmt

// ---- control flow ---------------------------------------------------------------

/** One `values -> body` arm; [values] null means the `bruh ->` default arm. */
data class VcBranch(
    val values: List<Expr>?,
    val body: Block,
    override val line: Int,
    override val col: Int,
) : Node

/** `vibecheck (x) bet ... periodt` — when/switch. */
data class VibecheckStmt(
    val subject: Expr,
    val branches: List<VcBranch>,
    val endLine: Int,
    override val line: Int,
    override val col: Int,
) : Stmt

/** `mog (item inside things) bet ... periodt` — for-in. */
data class MogStmt(
    val varName: String,
    val iterable: Expr,
    val body: Block,
    override val line: Int,
    override val col: Int,
) : Stmt

enum class AssignOp(val kotlin: String) { SET("="), GAINS("+="), LOSES("-=") }

data class Assign(
    val target: Expr,
    val op: AssignOp,
    val value: Expr,
    override val line: Int,
    override val col: Int,
) : Stmt

/** `sus (cond) bet ... periodt [bruh ...]`; [elseBranch] is a Block or a SusStmt. */
data class SusStmt(
    val cond: Expr,
    val thenBlock: Block,
    val elseBranch: Node?,
    override val line: Int,
    override val col: Int,
) : Stmt

data class GrindStmt(
    val cond: Expr,
    val body: Block,
    override val line: Int,
    override val col: Int,
) : Stmt

data class YeetStmt(val value: Expr?, override val line: Int, override val col: Int) : Stmt

data class DipStmt(override val line: Int, override val col: Int) : Stmt

data class SkipStmt(override val line: Int, override val col: Int) : Stmt

data class ExprStmt(val expr: Expr, override val line: Int, override val col: Int) : Stmt

/** `drop site on <port> bet ... periodt` — the one dedicated web statement. */
data class DropSiteStmt(
    val port: Expr,
    val block: Block,
    override val line: Int,
    override val col: Int,
) : Stmt

// ---- expressions -------------------------------------------------------------

sealed interface Expr : Node

data class IntLit(val text: String, override val line: Int, override val col: Int) : Expr
data class DoubleLit(val text: String, override val line: Int, override val col: Int) : Expr
data class BoolLit(val value: Boolean, override val line: Int, override val col: Int) : Expr
data class GhostedLit(override val line: Int, override val col: Int) : Expr
data class NameRef(val name: String, override val line: Int, override val col: Int) : Expr

/** `me` — this. */
data class MeRef(override val line: Int, override val col: Int) : Expr

/** `xs[i]` — indexing. */
data class IndexExpr(
    val receiver: Expr,
    val index: Expr,
    override val line: Int,
    override val col: Int,
) : Expr

sealed interface TmplNode {
    data class Text(val raw: String) : TmplNode
    data class Interp(val expr: Expr) : TmplNode
}

data class StringTmpl(
    val parts: List<TmplNode>,
    override val line: Int,
    override val col: Int,
) : Expr

/** [lambda] is the trailing block: `page("/") bet ... periodt` → `page("/") { ... }`. */
data class Call(
    val callee: Expr,
    val args: List<Expr>,
    override val line: Int,
    override val col: Int,
    val lambda: Block? = null,
) : Expr

data class MemberAccess(
    val receiver: Expr,
    val name: String,
    val safe: Boolean,
    override val line: Int,
    override val col: Int,
) : Expr

enum class BinOp(val kotlin: String) {
    ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%"),
    TWINS("=="), AINT("!="), CLEARS(">"), FLOPS("<"), ATLEAST(">="), ATMOST("<="),
    AND("&&"), OR("||"), THROUGH(".."), OTHERWISE("?:"),
}

data class Binary(
    val op: BinOp,
    val left: Expr,
    val right: Expr,
    override val line: Int,
    override val col: Int,
) : Expr

enum class UnaryOp(val kotlin: String) { NOT("!"), NEG("-") }

data class Unary(
    val op: UnaryOp,
    val operand: Expr,
    override val line: Int,
    override val col: Int,
) : Expr

/** postfix `deadass` — emitted as a runtime `.deadass()` call so the NPE roast is ours. */
data class DeadassExpr(val operand: Expr, override val line: Int, override val col: Int) : Expr
