package rotlin.cli.commands

import rotlin.cli.ClasspathLocator
import rotlin.cli.KotlinCompilerFacade
import rotlin.cli.Runner
import rotlin.cli.Term
import rotlin.compiler.CompilerDriver
import rotlin.compiler.Diagnostic
import rotlin.compiler.LineMap
import rotlin.compiler.RoastRenderer
import rotlin.runtime.SkillIssue
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/** `rotlin cook app.rot` - compile and run a console program. */
class CookCommand(private val err: PrintStream = System.err) {

    fun run(file: Path): Int {
        if (!Files.exists(file)) {
            err.println("can't find `$file` - that file is ghosting us")
            return 1
        }
        val fileName = file.fileName.toString()
        val source = Files.readString(file, Charsets.UTF_8)

        val result = CompilerDriver.compile(source)
        if (result.diagnostics.all.isNotEmpty()) {
            err.println(RoastRenderer.renderAll(fileName, source, result.diagnostics, Term.color))
            err.println()
        }
        val output = result.output
        if (result.diagnostics.hasErrors || output == null) {
            err.println(RoastRenderer.auraSummary(result.diagnostics, Term.color))
            return 1
        }

        val genDir = Files.createTempDirectory("rotcook")
        val ktFile = genDir.resolve("RotMain.kt")
        Files.writeString(ktFile, output.ktText, Charsets.UTF_8)
        val classesDir = genDir.resolve("classes")

        val ktErrors = KotlinCompilerFacade(ClasspathLocator.userClasspath())
            .compile(ktFile, classesDir, output.lineMap)
            .filter { it.isError }
        if (ktErrors.isNotEmpty()) {
            // our checker missed it; kotlinc is the backstop - map lines and render anyway
            val sourceLines = source.lines()
            for (d in ktErrors) {
                val diag = Diagnostic(
                    "E_KOTLINC", "the cook failed: ${d.message.lineSequence().first()}",
                    hint = null, line = d.rotLine ?: 0, col = 1,
                )
                err.println(RoastRenderer.render(fileName, sourceLines, diag, Term.color))
            }
            return 1
        }

        return try {
            Runner(listOf(classesDir)).runMain(output.mainClassFqn)
            0
        } catch (e: Throwable) {
            renderCrash(fileName, e, output.lineMap)
            1
        }
    }

    private fun renderCrash(fileName: String, e: Throwable, lineMap: LineMap) {
        val rotLine = e.stackTrace
            .firstOrNull { it.fileName == "RotMain.kt" }
            ?.let { lineMap.toRotLine(it.lineNumber) }
        val where = rotLine?.let { " at $fileName line $it" } ?: ""
        val label = if (e is SkillIssue) "SKILL ISSUE" else "SKILL ISSUE (${e.javaClass.simpleName})"
        err.println("$label$where: ${e.message ?: "no message, just vibes"}")
        if (e !is SkillIssue) err.println("  (caught in 4k)")
    }
}
