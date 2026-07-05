package rotlin.compiler

data class CompileResult(val output: EmitOutput?, val diagnostics: DiagnosticBag)

object CompilerDriver {
    /** lex → parse → typecheck → emit. Emission skipped on errors. */
    fun compile(source: String): CompileResult {
        val lexed = Lexer(source).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        // the parser recovers well (banned symbols become the intended token),
        // so the checker runs even after parse errors - kids see everything at once
        TypeChecker(lexed.diagnostics).check(program)
        if (lexed.diagnostics.hasErrors) return CompileResult(null, lexed.diagnostics)
        return CompileResult(KotlinEmitter().emit(program), lexed.diagnostics)
    }
}
