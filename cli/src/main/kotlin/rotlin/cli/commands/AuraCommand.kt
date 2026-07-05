package rotlin.cli.commands

import rotlin.cli.Term
import rotlin.compiler.Lexer
import rotlin.compiler.Parser
import rotlin.compiler.RoastRenderer
import rotlin.compiler.TypeChecker
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/** `rotlin aura app.rot` - type-check only, report diagnostics and the aura score. */
class AuraCommand(private val out: PrintStream = System.out) {

    fun run(file: Path): Int {
        if (!Files.exists(file)) {
            out.println("can't find `$file` - no such file")
            return 1
        }
        val fileName = file.fileName.toString()
        val source = Files.readString(file, Charsets.UTF_8)

        val lexed = Lexer(source).lex()
        val program = Parser(lexed.tokens, lexed.diagnostics).parseProgram()
        TypeChecker(lexed.diagnostics).check(program)

        val diags = lexed.diagnostics
        if (diags.all.isNotEmpty()) {
            out.println(RoastRenderer.renderAll(fileName, source, diags, Term.color))
            out.println()
        }
        out.println(RoastRenderer.auraSummary(diags, Term.color))
        return if (diags.hasErrors) 1 else 0
    }
}
