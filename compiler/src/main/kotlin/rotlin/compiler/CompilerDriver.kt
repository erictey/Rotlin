package rotlin.compiler

data class CompileResult(val output: EmitOutput?, val diagnostics: DiagnosticBag)

object CompilerDriver {
    /** lex → parse → (typecheck: phase 3) → emit. Emission skipped on errors. */
    fun compile(source: String): CompileResult {
        val lexed = Lexer(source).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        if (lexed.diagnostics.hasErrors) return CompileResult(null, lexed.diagnostics)
        return CompileResult(KotlinEmitter().emit(program), lexed.diagnostics)
    }
}
