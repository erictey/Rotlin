package rotlin.compiler

import rotlin.compiler.RType.AuraT
import rotlin.compiler.RType.ClassT
import rotlin.compiler.RType.FactT
import rotlin.compiler.RType.GhostT
import rotlin.compiler.RType.LoreT
import rotlin.compiler.RType.MaybeT
import rotlin.compiler.RType.RangeT
import rotlin.compiler.RType.RatioT
import rotlin.compiler.RType.SquadT
import rotlin.compiler.RType.StashT
import rotlin.compiler.RType.UnitT
import rotlin.compiler.RType.UnknownT

/**
 * Two passes: collect signatures, then check bodies with a scope chain plus
 * flow facts for smart casts. Philosophy: this checker owns the kid-facing
 * errors; kotlinc is ground truth for anything it can't see (interop members
 * type as Unknown and stay silent).
 *
 * Smart casts narrow only *stable* names (rizz locals and params) — the same
 * rule as Kotlin, which matters because the emitted Kotlin must smart-cast
 * too, or kotlinc would reject code we approved.
 */
class TypeChecker(private val diags: DiagnosticBag) {

    // ---- symbols ----------------------------------------------------------

    private class VarSymbol(val name: String, val type: RType, val mutable: Boolean)

    private class Scope(val parent: Scope?) {
        private val vars = mutableMapOf<String, VarSymbol>()
        fun lookup(name: String): VarSymbol? = vars[name] ?: parent?.lookup(name)
        fun declaredHere(name: String): Boolean = vars.containsKey(name)
        fun declare(sym: VarSymbol) { vars[sym.name] = sym }
        fun allNames(): Set<String> = (parent?.allNames() ?: emptySet()) + vars.keys
    }

    private data class FunSig(val name: String, val params: List<Pair<String, RType>>, val returns: RType)

    private data class BuiltinSig(
        val minArgs: Int,
        val maxArgs: Int,
        val paramTypes: List<RType?>, // by position; null = anything goes
        val returns: RType,
        val takesLambda: Boolean = false,
    )

    private val builtins: Map<String, BuiltinSig> = mapOf(
        "yap" to BuiltinSig(0, 1, listOf(null), UnitT),
        "whisper" to BuiltinSig(1, 1, listOf(null), UnitT),
        "listen" to BuiltinSig(0, 0, emptyList(), MaybeT(LoreT)),
        "lore" to BuiltinSig(1, 1, listOf(null), LoreT),
        "aura" to BuiltinSig(1, 1, listOf(null), MaybeT(AuraT)),
        "ratio" to BuiltinSig(1, 1, listOf(null), MaybeT(RatioT)),
        // web dsl
        "site" to BuiltinSig(1, 1, listOf(AuraT), UnitT, takesLambda = true),
        "page" to BuiltinSig(1, 1, listOf(LoreT), UnitT, takesLambda = true),
        "bigyap" to BuiltinSig(0, 1, listOf(null), UnitT),
        "pic" to BuiltinSig(1, 1, listOf(LoreT), UnitT),
        "link" to BuiltinSig(2, 2, listOf(LoreT, LoreT), UnitT),
        "smash" to BuiltinSig(1, 1, listOf(LoreT), UnitT, takesLambda = true),
    )

    private val loreProps: Map<String, RType> = mapOf("length" to AuraT)

    private val loreMethods: Map<String, Pair<List<RType>, RType>> = mapOf(
        "uppercase" to (emptyList<RType>() to LoreT),
        "lowercase" to (emptyList<RType>() to LoreT),
        "reversed" to (emptyList<RType>() to LoreT),
        "contains" to (listOf<RType>(LoreT) to FactT),
    )

    private val typeNames = setOf("aura", "ratio", "lore", "fact", "squad", "stash")

    // ---- checker state ------------------------------------------------------

    private val userFuns = mutableMapOf<String, FunSig>()
    private val classes = mutableMapOf<String, List<Pair<String, RType>>>() // name -> ctor params
    private var loopDepth = 0
    private var currentFun: FunSig? = null
    private var currentClass: String? = null

    /** Member-function frames so methods can call their siblings bare. */
    private val funFrames = ArrayDeque<Map<String, FunSig>>()

    private fun lookupFun(name: String): FunSig? {
        for (frame in funFrames.asReversed()) frame[name]?.let { return it }
        return userFuns[name]
    }

    /** Overlay frames of narrowed types (smart casts). */
    private val facts = ArrayDeque<MutableMap<String, RType>>()

    /** Names whose narrowing was denied because they're gyatt — for the teaching roast. */
    private val deniedFacts = ArrayDeque<MutableSet<String>>()

    fun check(program: Program) {
        val global = Scope(null)
        for (s in program.summons) {
            if (!s.wildcard) {
                // interop names are trusted: they type as Unknown, kotlinc backstops
                global.declare(VarSymbol(s.path.substringAfterLast('.'), UnknownT, mutable = false))
            }
        }
        // pre-register classes, vibes and npcs so order doesn't matter
        for (item in program.items) when (item) {
            is SigmaDecl -> {
                classes[item.name] = item.ctorParams.map { it.name to resolveType(it.type) }
                if (item.name in builtins) {
                    diags.warning("W_SHADOW", "`${item.name}` is already a rotlin thing", item.line, item.col,
                        hint = "pick a different name")
                }
            }
            is VibeDecl -> classes[item.name] = emptyList() // constructible? no - but referencable
            is NpcDecl -> global.declare(VarSymbol(item.name, ClassT(item.name), mutable = false))
            else -> {}
        }
        collectFuns(program.items)
        facts.addLast(mutableMapOf())
        deniedFacts.addLast(mutableSetOf())
        for (item in program.items) checkStmt(item, global)
        facts.removeLast()
        deniedFacts.removeLast()
    }

    private fun collectFuns(stmts: List<Stmt>) {
        for (s in stmts) {
            if (s !is FunDecl) continue
            if (s.name in builtins) {
                diags.warning(
                    "W_SHADOW", "`${s.name}` is already a rotlin thing - your version hides it",
                    s.line, s.col, hint = "pick a different name",
                )
            }
            if (userFuns.containsKey(s.name)) {
                diags.error("E_DOUBLE_COOK", "`${s.name}` got cooked twice", s.line, s.col,
                    hint = "rename one of them")
            }
            userFuns[s.name] = FunSig(
                s.name,
                s.params.map { it.name to resolveType(it.type) },
                s.returnType?.let { resolveType(it) } ?: UnitT,
            )
        }
    }

    // ---- types ----------------------------------------------------------------

    private fun resolveType(ref: TypeRef): RType = when (ref) {
        is TypeRef.Maybe -> MaybeT(resolveType(ref.inner))
        is TypeRef.Named -> when (ref.name) {
            "aura" -> AuraT
            "ratio" -> RatioT
            "lore" -> LoreT
            "fact" -> FactT
            "squad" -> SquadT(ref.args.firstOrNull()?.let { resolveType(it) } ?: UnknownT)
            "stash" -> StashT(
                ref.args.getOrNull(0)?.let { resolveType(it) } ?: UnknownT,
                ref.args.getOrNull(1)?.let { resolveType(it) } ?: UnknownT,
            )
            else -> {
                didYouMean(ref.name, typeNames)?.let { guess ->
                    diags.warning("W_TYPE_GUESS", "`${ref.name}` isn't a rotlin type", ref.line, ref.col,
                        hint = "did you mean `$guess`?")
                }
                UnknownT // interop types pass through; kotlinc backstops
            }
        }
    }

    // ---- statements --------------------------------------------------------------

    private fun checkStmt(stmt: Stmt, scope: Scope) {
        when (stmt) {
            is FunDecl -> checkFunDecl(stmt, scope)
            is VarDecl -> checkVarDecl(stmt, scope)
            is Assign -> checkAssign(stmt, scope)
            is SusStmt -> checkSus(stmt, scope)
            is GrindStmt -> {
                requireFact(checkExpr(stmt.cond, scope), stmt.cond)
                loopDepth++
                checkBlock(stmt.body, scope)
                loopDepth--
            }
            is YeetStmt -> checkYeet(stmt, scope)
            is DipStmt -> if (loopDepth == 0) {
                diags.error("E_DIP_NOWHERE", "you can only dip out of a loop", stmt.line, stmt.col,
                    hint = "dip belongs inside grind (or mog)")
            }
            is SkipStmt -> if (loopDepth == 0) {
                diags.error("E_SKIP_NOWHERE", "you can only skip inside a loop", stmt.line, stmt.col,
                    hint = "skip belongs inside grind (or mog)")
            }
            is ExprStmt -> checkExpr(stmt.expr, scope)
            is SigmaDecl -> checkSigma(stmt, scope)
            is NpcDecl -> checkMembers(stmt.name, stmt.members, Scope(scope), scope)
            is VibeDecl -> checkMembers(stmt.name, stmt.members, Scope(scope), scope)
            is VibecheckStmt -> {
                val subjT = checkExpr(stmt.subject, scope)
                for (b in stmt.branches) {
                    b.values?.forEach { v ->
                        val vT = checkExpr(v, scope)
                        if (!assignable(vT, subjT) && !assignable(subjT, vT)) {
                            diags.error(
                                "E_TYPE_MISMATCH",
                                "this branch checks ${vT.display()} but the vibecheck is on ${subjT.display()}",
                                v.line, v.col, hint = "match the type you're vibechecking",
                            )
                        }
                    }
                    checkBlock(b.body, scope)
                }
            }
            is MogStmt -> {
                val iterT = checkExpr(stmt.iterable, scope)
                val elemT: RType = when (iterT) {
                    is SquadT -> iterT.elem
                    is RangeT -> AuraT
                    is UnknownT -> UnknownT
                    is StashT -> {
                        diags.error("E_TYPE_MISMATCH", "you can't mog a whole stash", stmt.iterable.line,
                            stmt.iterable.col, hint = "mog a squad or a range like `1 through 10`")
                        UnknownT
                    }
                    else -> {
                        diags.error("E_TYPE_MISMATCH", "you can't mog through ${iterT.display()}",
                            stmt.iterable.line, stmt.iterable.col,
                            hint = "mog wants a squad or a range like `1 through 10`")
                        UnknownT
                    }
                }
                val bodyScope = Scope(scope)
                bodyScope.declare(VarSymbol(stmt.varName, elemT, mutable = false))
                loopDepth++
                for (s in stmt.body.stmts) checkStmt(s, bodyScope)
                loopDepth--
            }
            is FinnaStmt -> {
                checkBlock(stmt.tryBlock, scope)
                val catchScope = Scope(scope)
                catchScope.declare(VarSymbol(stmt.catchName, UnknownT, mutable = false))
                for (s in stmt.catchBlock.stmts) checkStmt(s, catchScope)
            }
            is CrashoutStmt -> checkExpr(stmt.value, scope)
            is DropSiteStmt -> {
                val portT = checkExpr(stmt.port, scope)
                if (!assignable(portT, AuraT)) {
                    diags.error("E_TYPE_MISMATCH", "the port has to be an aura (a number), got ${portT.display()}",
                        stmt.port.line, stmt.port.col, hint = "like `drop site on 3000`")
                }
                checkBlock(stmt.block, scope)
            }
        }
    }

    private fun sigOf(fn: FunDecl): FunSig = FunSig(
        fn.name,
        fn.params.map { it.name to resolveType(it.type) },
        fn.returnType?.let { resolveType(it) } ?: UnitT,
    )

    private fun checkFunDecl(fn: FunDecl, scope: Scope) {
        // top-level funs are pre-collected; locals get registered on the way down
        val isMember = currentClass != null
        if (!isMember && !userFuns.containsKey(fn.name)) collectFuns(listOf(fn))
        val sig = if (isMember) sigOf(fn) else userFuns.getValue(fn.name)

        val body = fn.body ?: return // abstract vibe member: signature only

        val fnScope = Scope(scope)
        for ((i, p) in fn.params.withIndex()) {
            fnScope.declare(VarSymbol(p.name, sig.params[i].second, mutable = false))
        }
        val savedLoop = loopDepth
        val savedFun = currentFun
        loopDepth = 0
        currentFun = sig
        facts.addLast(mutableMapOf())
        deniedFacts.addLast(mutableSetOf())
        for (s in body.stmts) checkStmt(s, fnScope)
        facts.removeLast()
        deniedFacts.removeLast()
        currentFun = savedFun
        loopDepth = savedLoop

        if (sig.returns != UnitT && !blockReturns(body)) {
            diags.error(
                "E_MISSING_YEET",
                "`${fn.name}` promises to spit ${sig.returns.display()} but some path never yeets",
                fn.line, fn.col,
                hint = "make sure every branch ends with `yeet <value>`",
            )
        }
    }

    private fun checkSigma(decl: SigmaDecl, outer: Scope) {
        val classScope = Scope(outer)
        for (p in decl.ctorParams) {
            classScope.declare(VarSymbol(p.name, resolveType(p.type), p.kind == CtorParamKind.GYATT))
        }
        decl.superRef?.let { sup ->
            val args = sup.args.map { checkExpr(it, outer) }
            classes[sup.name]?.let { ctorParams ->
                if (args.size != ctorParams.size) {
                    diags.error("E_ARITY", "`${sup.name}` needs ${ctorParams.size} thing(s) to be built",
                        sup.line, sup.col)
                }
            }
        }
        checkMembers(decl.name, decl.members, classScope, outer)
    }

    private fun checkMembers(className: String, members: List<Stmt>, memberScope: Scope, outer: Scope) {
        val savedClass = currentClass
        currentClass = className
        funFrames.addLast(members.filterIsInstance<FunDecl>().associate { it.name to sigOf(it) })
        // properties first so every method can see them, regardless of order
        for (m in members) if (m is VarDecl) checkVarDecl(m, memberScope)
        for (m in members) if (m is FunDecl) checkFunDecl(m, memberScope)
        funFrames.removeLast()
        currentClass = savedClass
    }

    private fun blockReturns(block: Block): Boolean = block.stmts.any { stmtReturns(it) }

    private fun stmtReturns(stmt: Stmt): Boolean = when (stmt) {
        is YeetStmt -> true
        is CrashoutStmt -> true // throwing exits the function too
        is FinnaStmt -> blockReturns(stmt.tryBlock) && blockReturns(stmt.catchBlock)
        is SusStmt -> {
            val e = stmt.elseBranch
            when (e) {
                is Block -> blockReturns(stmt.thenBlock) && blockReturns(e)
                is SusStmt -> blockReturns(stmt.thenBlock) && stmtReturns(e)
                else -> false
            }
        }
        else -> false
    }

    private fun checkVarDecl(v: VarDecl, scope: Scope) {
        val initT = checkExpr(v.init, scope)
        val declared = v.declaredType?.let { resolveType(it) }
        val finalT: RType = when {
            declared != null -> {
                if (!assignable(initT, declared)) reportMismatch(initT, declared, v.init)
                declared
            }
            initT == GhostT -> {
                diags.error(
                    "E_GHOST_ONLY", "`${v.name}` starts ghosted so rotlin can't tell what it should be",
                    v.line, v.col,
                    hint = "give it a type: `${if (v.mutable) "gyatt" else "rizz"} ${v.name}: maybe lore = ghosted`",
                )
                UnknownT
            }
            else -> initT
        }
        if (scope.declaredHere(v.name)) {
            diags.error("E_DOUBLE_COOK", "`${v.name}` already exists here", v.line, v.col,
                hint = "pick a different name (or just assign: `${v.name} = ...`)")
        }
        scope.declare(VarSymbol(v.name, finalT, v.mutable))
        killFacts(v.name)
    }

    private fun checkAssign(a: Assign, scope: Scope) {
        val valueT = checkExpr(a.value, scope)
        val target = a.target
        if (target is IndexExpr) {
            val recvT = checkExpr(target.receiver, scope)
            checkExpr(target.index, scope)
            when (recvT) {
                is SquadT -> if (!assignable(valueT, recvT.elem)) {
                    diags.error("E_TYPE_MISMATCH",
                        "this squad holds ${recvT.elem.display()}, not ${valueT.display()}",
                        a.line, a.col)
                }
                is StashT -> if (!assignable(valueT, recvT.value)) {
                    diags.error("E_TYPE_MISMATCH",
                        "this stash holds ${recvT.value.display()}, not ${valueT.display()}",
                        a.line, a.col)
                }
                else -> {}
            }
            return
        }
        if (target !is NameRef) {
            // member assignment: receiver still gets its checks; member typing is kotlinc's
            checkExpr(target, scope)
            return
        }
        val sym = scope.lookup(target.name)
        if (sym == null) {
            unknownName(target.name, target, scope)
            return
        }
        if (!sym.mutable) {
            diags.error(
                "E_RIZZ_LOCKED", "`${target.name}` is rizz - locked in, no takebacks",
                a.line, a.col,
                hint = "declare it `gyatt ${target.name} = ...` if it needs to change",
            )
        }
        when (a.op) {
            AssignOp.SET -> if (!assignable(valueT, sym.type)) reportMismatch(valueT, sym.type, a.value)
            AssignOp.GAINS -> when {
                sym.type == LoreT -> {} // lore gains anything (concat)
                sym.type == AuraT && assignable(valueT, AuraT) -> {}
                sym.type == RatioT && assignable(valueT, RatioT) -> {}
                sym.type is UnknownT -> {}
                else -> diags.error(
                    "E_TYPE_MISMATCH",
                    "`${target.name}` is ${sym.type.display()} - it can't gain ${valueT.display()}",
                    a.line, a.col, hint = "gains works on matching numbers, or lore gaining anything",
                )
            }
            AssignOp.LOSES -> when {
                sym.type == AuraT && assignable(valueT, AuraT) -> {}
                sym.type == RatioT && assignable(valueT, RatioT) -> {}
                sym.type is UnknownT -> {}
                else -> diags.error(
                    "E_TYPE_MISMATCH",
                    "`${target.name}` is ${sym.type.display()} - it can't lose ${valueT.display()}",
                    a.line, a.col, hint = "loses only works on matching numbers",
                )
            }
        }
        killFacts(target.name)
    }

    private fun checkSus(s: SusStmt, scope: Scope) {
        requireFact(checkExpr(s.cond, scope), s.cond)
        val flow = extractFacts(s.cond, scope)

        facts.addLast(flow.thenFacts.toMutableMap())
        deniedFacts.addLast(flow.thenDenied.toMutableSet())
        checkBlock(s.thenBlock, scope)
        facts.removeLast()
        deniedFacts.removeLast()

        when (val e = s.elseBranch) {
            null -> {}
            is Block -> {
                facts.addLast(flow.elseFacts.toMutableMap())
                deniedFacts.addLast(flow.elseDenied.toMutableSet())
                checkBlock(e, scope)
                facts.removeLast()
                deniedFacts.removeLast()
            }
            is SusStmt -> {
                facts.addLast(flow.elseFacts.toMutableMap())
                deniedFacts.addLast(flow.elseDenied.toMutableSet())
                checkStmt(e, scope)
                facts.removeLast()
                deniedFacts.removeLast()
            }
            else -> {}
        }
    }

    private fun checkBlock(block: Block, parent: Scope) {
        val scope = Scope(parent)
        for (s in block.stmts) checkStmt(s, scope)
    }

    private fun checkYeet(y: YeetStmt, scope: Scope) {
        val fn = currentFun
        if (fn == null) {
            diags.error("E_YEET_NOWHERE", "there's nothing to yeet out of here", y.line, y.col,
                hint = "yeet only works inside a skibidi function")
            y.value?.let { checkExpr(it, scope) }
            return
        }
        val valueT = y.value?.let { checkExpr(it, scope) } ?: UnitT
        when {
            fn.returns == UnitT && y.value != null -> diags.error(
                "E_TYPE_MISMATCH", "`${fn.name}` doesn't spit anything, but you're yeeting a value",
                y.line, y.col, hint = "add `spits ${valueT.display()}` to the function",
            )
            fn.returns != UnitT && y.value == null -> diags.error(
                "E_TYPE_MISMATCH", "`${fn.name}` spits ${fn.returns.display()} but this yeet is empty",
                y.line, y.col, hint = "yeet an actual ${fn.returns.display()}",
            )
            fn.returns != UnitT && !assignable(valueT, fn.returns) -> diags.error(
                "E_TYPE_MISMATCH",
                "`${fn.name}` spits ${fn.returns.display()} but you're yeeting ${valueT.display()}",
                y.line, y.col, hint = "match the spits type",
            )
            else -> {}
        }
    }

    // ---- flow facts -----------------------------------------------------------------

    private data class Flow(
        val thenFacts: Map<String, RType> = emptyMap(),
        val elseFacts: Map<String, RType> = emptyMap(),
        val thenDenied: Set<String> = emptySet(),
        val elseDenied: Set<String> = emptySet(),
    )

    private fun extractFacts(cond: Expr, scope: Scope): Flow = when {
        cond is Binary && cond.op == BinOp.AINT && cond.left is NameRef && cond.right is GhostedLit ->
            ghostFlow(cond.left, scope, narrowThen = true)
        cond is Binary && cond.op == BinOp.AINT && cond.right is NameRef && cond.left is GhostedLit ->
            ghostFlow(cond.right, scope, narrowThen = true)
        cond is Binary && cond.op == BinOp.TWINS && cond.left is NameRef && cond.right is GhostedLit ->
            ghostFlow(cond.left, scope, narrowThen = false)
        cond is Binary && cond.op == BinOp.TWINS && cond.right is NameRef && cond.left is GhostedLit ->
            ghostFlow(cond.right, scope, narrowThen = false)
        cond is Binary && cond.op == BinOp.AND -> {
            val l = extractFacts(cond.left, scope)
            val r = extractFacts(cond.right, scope)
            Flow(
                thenFacts = l.thenFacts + r.thenFacts, // both sides hold in the then-branch
                thenDenied = l.thenDenied + r.thenDenied,
            )
        }
        cond is Binary && cond.op == BinOp.OR -> {
            val l = extractFacts(cond.left, scope)
            val r = extractFacts(cond.right, scope)
            Flow(
                elseFacts = l.elseFacts + r.elseFacts, // neither holds in the else-branch
                elseDenied = l.elseDenied + r.elseDenied,
            )
        }
        cond is Unary && cond.op == UnaryOp.NOT -> {
            val inner = extractFacts(cond.operand, scope)
            Flow(inner.elseFacts, inner.thenFacts, inner.elseDenied, inner.thenDenied)
        }
        else -> Flow()
    }

    private fun ghostFlow(name: NameRef, scope: Scope, narrowThen: Boolean): Flow {
        val sym = scope.lookup(name.name) ?: return Flow()
        val inner = (sym.type as? MaybeT)?.inner ?: return Flow()
        return if (sym.mutable) {
            if (narrowThen) Flow(thenDenied = setOf(name.name)) else Flow(elseDenied = setOf(name.name))
        } else {
            if (narrowThen) Flow(thenFacts = mapOf(name.name to inner))
            else Flow(elseFacts = mapOf(name.name to inner))
        }
    }

    private fun factType(name: String): RType? {
        for (frame in facts.asReversed()) frame[name]?.let { return it }
        return null
    }

    private fun isDenied(name: String): Boolean = deniedFacts.any { name in it }

    private fun killFacts(name: String) {
        for (frame in facts) frame.remove(name)
    }

    // ---- expressions ---------------------------------------------------------------------

    private fun checkExpr(expr: Expr, scope: Scope): RType = when (expr) {
        is IntLit -> AuraT
        is DoubleLit -> RatioT
        is BoolLit -> FactT
        is GhostedLit -> GhostT
        is StringTmpl -> {
            expr.parts.filterIsInstance<TmplNode.Interp>().forEach { checkExpr(it.expr, scope) }
            LoreT
        }
        is NameRef -> {
            val sym = scope.lookup(expr.name)
            when {
                sym != null -> factType(expr.name) ?: sym.type
                lookupFun(expr.name) != null || expr.name in builtins -> {
                    diags.error("E_WHO_IS_THAT", "`${expr.name}` is a function - you have to call it",
                        expr.line, expr.col, hint = "add parentheses: `${expr.name}(...)`")
                    UnknownT
                }
                expr.name in classes -> {
                    diags.error("E_WHO_IS_THAT", "`${expr.name}` is a blueprint, not a value",
                        expr.line, expr.col, hint = "build one: `${expr.name}(...)`")
                    UnknownT
                }
                else -> {
                    unknownName(expr.name, expr, scope)
                    UnknownT
                }
            }
        }
        is MeRef -> {
            val cls = currentClass
            if (cls == null) {
                diags.error("E_ME_NOWHERE", "`me` only exists inside a sigma or npc", expr.line, expr.col,
                    hint = "outside a class there is no me, only vibes")
                UnknownT
            } else ClassT(cls)
        }
        is IndexExpr -> {
            val recvT = checkExpr(expr.receiver, scope)
            val idxT = checkExpr(expr.index, scope)
            when (recvT) {
                is SquadT -> {
                    if (!assignable(idxT, AuraT)) {
                        diags.error("E_TYPE_MISMATCH", "squad positions are auras, got ${idxT.display()}",
                            expr.index.line, expr.index.col, hint = "squads count from 0")
                    }
                    recvT.elem
                }
                is StashT -> {
                    if (!assignable(idxT, recvT.key)) {
                        diags.error("E_TYPE_MISMATCH",
                            "this stash uses ${recvT.key.display()} keys, got ${idxT.display()}",
                            expr.index.line, expr.index.col)
                    }
                    MaybeT(recvT.value) // the key might not be there - might be ghosted
                }
                is MaybeT -> {
                    diags.error("E_NULL_UNSAFE", "that might be ghosted - you can't index a ghost",
                        expr.line, expr.col,
                        hint = "check `aint ghosted` first, or use `otherwise`")
                    UnknownT
                }
                is UnknownT -> UnknownT
                else -> {
                    diags.error("E_TYPE_MISMATCH", "you can't [index] into ${recvT.display()}",
                        expr.line, expr.col, hint = "only squads and stashes take []")
                    UnknownT
                }
            }
        }
        is Unary -> when (expr.op) {
            UnaryOp.NOT -> {
                requireFact(checkExpr(expr.operand, scope), expr.operand)
                FactT
            }
            UnaryOp.NEG -> {
                val t = checkExpr(expr.operand, scope)
                if (t == AuraT || t == RatioT || t is UnknownT) t
                else {
                    diags.error("E_TYPE_MISMATCH", "you can't negate ${t.display()}", expr.line, expr.col,
                        hint = "minus only works on numbers")
                    UnknownT
                }
            }
        }
        is Binary -> checkBinary(expr, scope)
        is DeadassExpr -> {
            val t = checkExpr(expr.operand, scope)
            when (t) {
                is MaybeT -> t.inner
                is GhostT -> {
                    diags.warning("W_ALWAYS_GHOSTED", "that's literally always ghosted - this WILL crash",
                        expr.line, expr.col, hint = "give it a real value first")
                    UnknownT
                }
                is UnknownT -> UnknownT
                else -> {
                    diags.warning("W_NEVER_GHOSTED", "that was never ghosted - deadass does nothing here",
                        expr.line, expr.col, hint = "you can just delete the deadass")
                    t
                }
            }
        }
        is MemberAccess -> checkMemberAccess(expr, scope, argTypes = null)
        is Call -> checkCall(expr, scope)
    }

    private fun checkBinary(b: Binary, scope: Scope): RType {
        val l = checkExpr(b.left, scope)
        val r = checkExpr(b.right, scope)

        fun mixed(): RType {
            diags.error("E_MIXED_NUMBERS", "aura and ratio don't mix raw", b.line, b.col,
                hint = "wrap one side: `ratio(x)` makes it a ratio")
            return UnknownT
        }

        fun numeric(op: String): RType = when {
            l is UnknownT || r is UnknownT -> UnknownT
            l == AuraT && r == AuraT -> AuraT
            l == RatioT && r == RatioT -> RatioT
            (l == AuraT && r == RatioT) || (l == RatioT && r == AuraT) -> mixed()
            else -> {
                diags.error("E_TYPE_MISMATCH", "`$op` needs numbers, got ${l.display()} and ${r.display()}",
                    b.line, b.col, hint = "aura(x) or ratio(x) can convert lore to numbers")
                UnknownT
            }
        }

        return when (b.op) {
            BinOp.ADD -> when {
                l == LoreT -> LoreT // lore + anything = bigger lore
                l is UnknownT || r is UnknownT -> UnknownT
                (l == AuraT || l == RatioT) && r == LoreT -> {
                    diags.error("E_TYPE_MISMATCH", "number + lore doesn't cook", b.line, b.col,
                        hint = "put the lore first, or wrap: `lore(x) + ...`")
                    UnknownT
                }
                else -> numeric("+")
            }
            BinOp.SUB -> numeric("-")
            BinOp.MUL -> numeric("*")
            BinOp.DIV -> numeric("/")
            BinOp.MOD -> numeric("%")
            BinOp.CLEARS, BinOp.FLOPS, BinOp.ATLEAST, BinOp.ATMOST -> {
                when {
                    l is UnknownT || r is UnknownT -> {}
                    l == AuraT && r == AuraT -> {}
                    l == RatioT && r == RatioT -> {}
                    l == LoreT && r == LoreT -> {}
                    (l == AuraT && r == RatioT) || (l == RatioT && r == AuraT) -> mixed()
                    else -> diags.error(
                        "E_TYPE_MISMATCH",
                        "can't compare ${l.display()} against ${r.display()}",
                        b.line, b.col, hint = "compare matching types",
                    )
                }
                FactT
            }
            BinOp.TWINS, BinOp.AINT -> FactT
            BinOp.AND, BinOp.OR -> {
                requireFact(l, b.left)
                requireFact(r, b.right)
                FactT
            }
            BinOp.THROUGH -> {
                if (!assignable(l, AuraT) || !assignable(r, AuraT)) {
                    diags.error("E_TYPE_MISMATCH", "through needs auras on both sides", b.line, b.col,
                        hint = "like `1 through 10`")
                }
                RangeT
            }
            BinOp.OTHERWISE -> when (l) {
                is MaybeT -> {
                    if (r !is UnknownT && !assignable(r, l.inner) && r !is MaybeT) {
                        diags.error(
                            "E_TYPE_MISMATCH",
                            "the backup value is ${r.display()} but the real one is ${l.inner.display()}",
                            b.line, b.col, hint = "make the otherwise value match",
                        )
                    }
                    if (r is MaybeT) MaybeT(l.inner) else l.inner
                }
                is GhostT -> r
                is UnknownT -> UnknownT
                else -> {
                    diags.warning("W_NEVER_GHOSTED", "the left side is never ghosted - otherwise does nothing",
                        b.line, b.col, hint = "you can delete everything from `otherwise` on")
                    l
                }
            }
        }
    }

    /** Shared by plain member access and member calls; [argTypes] non-null for calls. */
    private fun checkMemberAccess(m: MemberAccess, scope: Scope, argTypes: List<RType>?): RType {
        val receiverT = checkExpr(m.receiver, scope)
        val receiverName = (m.receiver as? NameRef)?.name

        if (receiverT is MaybeT && !m.safe) {
            val who = receiverName?.let { "`$it`" } ?: "that"
            val gyattNote = if (receiverName != null && isDenied(receiverName)) {
                "$who is gyatt, so the vibe check doesn't stick (it could flip any moment) - make it rizz, or stash it in a rizz first. or: "
            } else ""
            diags.error(
                "E_NULL_UNSAFE",
                "$who might be ghosted rn - you can't just grab `.${m.name}` off a ghost",
                m.line, m.col,
                hint = gyattNote +
                    "3 ways out: check first `sus (${receiverName ?: "it"} aint ghosted) bet ... periodt`, " +
                    "go safe `?.${m.name}`, or swear on it `${receiverName ?: "it"} deadass`",
            )
            return memberType(m.receiver, (receiverT).inner, m.name, argTypes, m)
        }

        if (m.safe && receiverT !is MaybeT && receiverT !is UnknownT && receiverT !is GhostT) {
            diags.warning("W_NEVER_GHOSTED", "that's never ghosted - the `?.` can just be `.`",
                m.line, m.col)
        }

        val baseT = when (receiverT) {
            is MaybeT -> receiverT.inner // safe access
            is GhostT -> return UnknownT
            else -> receiverT
        }
        val resultT = memberType(m.receiver, baseT, m.name, argTypes, m)
        return if (m.safe && receiverT is MaybeT && resultT !is UnknownT) MaybeT(resultT) else resultT
    }

    private fun memberType(receiver: Expr, receiverT: RType, member: String, argTypes: List<RType>?, at: Node): RType =
        when (receiverT) {
            is LoreT -> when {
                argTypes == null -> loreProps[member] ?: UnknownT
                else -> {
                    val sig = loreMethods[member]
                    if (sig != null && argTypes.size == sig.first.size) sig.second else UnknownT
                }
            }
            is SquadT -> if (member == "size") AuraT else UnknownT // full table lands in phase 4
            else -> UnknownT // interop members: kotlinc backstops
        }

    private fun checkCall(call: Call, scope: Scope): RType {
        val argTypes = call.args.map { checkExpr(it, scope) }
        val callee = call.callee

        fun checkLambdaBlock() {
            call.lambda?.let { lambda ->
                // a lambda is a new function context: no loop, no yeet target, no
                // carried smart casts (handlers run later, facts may be stale)
                val savedLoop = loopDepth
                val savedFun = currentFun
                loopDepth = 0
                currentFun = null
                facts.addLast(mutableMapOf())
                deniedFacts.addLast(mutableSetOf())
                checkBlock(lambda, scope)
                facts.removeLast()
                deniedFacts.removeLast()
                currentFun = savedFun
                loopDepth = savedLoop
            }
        }

        if (callee is NameRef) {
            val name = callee.name

            // squad(...) and stash() are generic factories - special-cased
            if (name == "squad" && scope.lookup(name) == null) {
                checkLambdaBlock()
                val distinct = argTypes.filter { it !is UnknownT && it !is GhostT }.distinct()
                return when {
                    distinct.size > 1 -> {
                        diags.error("E_TYPE_MISMATCH",
                            "squads don't mix - this one has ${distinct.joinToString(" and ") { it.display() }}",
                            call.line, call.col, hint = "keep every member the same type")
                        SquadT(UnknownT)
                    }
                    else -> SquadT(distinct.firstOrNull() ?: UnknownT)
                }
            }
            if (name == "stash" && scope.lookup(name) == null) {
                checkLambdaBlock()
                if (argTypes.isNotEmpty()) {
                    diags.error("E_ARITY", "a stash starts empty", call.line, call.col,
                        hint = "make it then fill it: `gyatt m: stash<lore, aura> = stash()` then `m[\"key\"] = 1`")
                }
                return StashT(UnknownT, UnknownT)
            }

            // constructor call
            classes[name]?.let { ctorParams ->
                checkLambdaBlock()
                if (argTypes.size != ctorParams.size) {
                    diags.error("E_ARITY", "`$name` needs ${ctorParams.size} thing(s) to be built, you gave ${argTypes.size}",
                        call.line, call.col,
                        hint = "it wants: ${ctorParams.joinToString(", ") { "${it.first}: ${it.second.display()}" }}")
                } else {
                    for ((i, at) in argTypes.withIndex()) {
                        val (pname, ptype) = ctorParams[i]
                        if (!assignable(at, ptype)) {
                            diags.error("E_TYPE_MISMATCH", "`$pname` wants ${ptype.display()} but got ${at.display()}",
                                call.args[i].line, call.args[i].col)
                        }
                    }
                }
                return ClassT(name)
            }

            lookupFun(name)?.let { sig ->
                if (call.lambda != null) {
                    diags.error("E_NO_LAMBDA", "`$name` doesn't take a block", call.line, call.col,
                        hint = "remove the bet ... periodt after the call")
                    checkLambdaBlock()
                }
                if (argTypes.size != sig.params.size) {
                    diags.error(
                        "E_ARITY",
                        "`$name` needs ${sig.params.size} thing(s), you gave ${argTypes.size}",
                        call.line, call.col,
                        hint = "it wants: ${sig.params.joinToString(", ") { "${it.first}: ${it.second.display()}" }}",
                    )
                } else {
                    for ((i, at) in argTypes.withIndex()) {
                        val (pname, ptype) = sig.params[i]
                        if (!assignable(at, ptype)) {
                            diags.error(
                                "E_TYPE_MISMATCH",
                                "`$pname` wants ${ptype.display()} but got ${at.display()}",
                                call.args[i].line, call.args[i].col,
                                hint = "convert it or pass the right thing",
                            )
                        }
                    }
                }
                return sig.returns
            }
            builtins[name]?.let { sig ->
                if (scope.lookup(name) == null) { // a local variable can shadow a builtin
                    if (argTypes.size < sig.minArgs || argTypes.size > sig.maxArgs) {
                        diags.error("E_ARITY", "`$name` takes ${sig.minArgs}..${sig.maxArgs} thing(s), you gave ${argTypes.size}",
                            call.line, call.col)
                    } else {
                        for ((i, at) in argTypes.withIndex()) {
                            val want = sig.paramTypes.getOrNull(i) ?: continue
                            if (!assignable(at, want)) {
                                diags.error("E_TYPE_MISMATCH", "`$name` wants ${want.display()} here, got ${at.display()}",
                                    call.args[i].line, call.args[i].col)
                            }
                        }
                    }
                    if (call.lambda != null && !sig.takesLambda) {
                        diags.error("E_NO_LAMBDA", "`$name` doesn't take a block", call.line, call.col,
                            hint = "remove the bet ... periodt after the call")
                    }
                    checkLambdaBlock()
                    return sig.returns
                }
            }
            val sym = scope.lookup(name)
            if (sym != null) {
                checkLambdaBlock()
                return UnknownT // calling something interop-ish; kotlinc backstops
            }
            unknownName(name, callee, scope, calling = true)
            checkLambdaBlock()
            return UnknownT
        }

        if (callee is MemberAccess) {
            checkLambdaBlock()
            return checkMemberAccess(callee, scope, argTypes)
        }

        checkExpr(callee, scope)
        checkLambdaBlock()
        return UnknownT
    }

    // ---- shared helpers ------------------------------------------------------------

    private fun requireFact(t: RType, at: Node) {
        if (t != FactT && t !is UnknownT && t !is GhostT) {
            diags.error("E_TYPE_MISMATCH", "that needs to be a fact (based or cringe), got ${t.display()}",
                at.line, at.col, hint = "comparisons like `x clears 5` give you facts")
        }
    }

    private fun reportMismatch(from: RType, to: RType, at: Node) {
        val hint = when {
            to is MaybeT || from !is GhostT -> "expected ${to.display()}"
            else -> "expected ${to.display()}"
        }
        val extra = if (from is GhostT && to !is MaybeT) {
            " - only `maybe` types can hold ghosted"
        } else ""
        diags.error("E_TYPE_MISMATCH", "can't stuff ${from.display()} into ${to.display()}$extra",
            at.line, at.col, hint = if (from is GhostT) "declare it `maybe ${to.display()}`" else hint)
    }

    private fun unknownName(name: String, at: Node, scope: Scope, calling: Boolean = false) {
        val candidates = scope.allNames() + userFuns.keys + builtins.keys + classes.keys
        val guess = didYouMean(name, candidates)
        diags.error(
            "E_WHO_IS_THAT",
            "who is `$name`?? never heard of them",
            at.line, at.col,
            hint = guess?.let { "did you mean `$it`?" }
                ?: if (calling) "define it first: `skibidi $name(...) bet ... periodt`"
                else "declare it first: `rizz $name = ...`",
        )
    }

    private fun didYouMean(name: String, candidates: Collection<String>): String? =
        candidates
            .filter { it != name }
            .map { it to levenshtein(name.lowercase(), it.lowercase()) }
            .filter { it.second <= 2 && it.second < it.first.length }
            .minByOrNull { it.second }
            ?.first

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return prev[b.length]
    }
}
