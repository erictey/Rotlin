package rotlin.cli.commands

import rotlin.cli.ClasspathLocator
import rotlin.cli.KotlinCompilerFacade
import rotlin.cli.Runner
import rotlin.compiler.CompilerDriver
import rotlin.compiler.Diagnostic
import rotlin.compiler.LineMap
import rotlin.runtime.SkillIssue
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/** `rotlin cook app.rot` — compile and run a console program. */
class CookCommand(private val err: PrintStream = System.err) {

    fun run(file: Path): Int {
        if (!Files.exists(file)) {
            err.println("can't find `$file` - that file is ghosting us")
            return 1
        }
        val fileName = file.fileName.toString()
        val source = Files.readString(file, Charsets.UTF_8)

        val result = CompilerDriver.compile(source)
        result.diagnostics.all.forEach { renderDiag(fileName, it) }
        val output = result.output
        if (result.diagnostics.hasErrors || output == null) return 1

        val genDir = Files.createTempDirectory("rotcook")
        val ktFile = genDir.resolve("RotMain.kt")
        Files.writeString(ktFile, output.ktText, Charsets.UTF_8)
        val classesDir = genDir.resolve("classes")

        val ktDiags = KotlinCompilerFacade(ClasspathLocator.userClasspath())
            .compile(ktFile, classesDir, output.lineMap)
        var hadError = false
        for (d in ktDiags) {
            if (d.isError) hadError = true else continue // warnings from kotlinc are noise for kids
            val where = d.rotLine?.let { "[$fileName line $it] " } ?: ""
            err.println("${where}the cook failed: ${d.message.lineSequence().first()}")
        }
        if (hadError) return 1

        return try {
            Runner(listOf(classesDir)).runMain(output.mainClassFqn)
            0
        } catch (e: Throwable) {
            renderCrash(fileName, e, output.lineMap)
            1
        }
    }

    private fun renderDiag(fileName: String, d: Diagnostic) {
        err.println("[$fileName line ${d.line}] ${d.message}")
        d.hint?.let { err.println("  fix: $it") }
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
