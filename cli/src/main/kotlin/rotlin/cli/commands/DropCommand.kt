package rotlin.cli.commands

import rotlin.cli.ClasspathLocator
import rotlin.cli.KotlinCompilerFacade
import rotlin.cli.Runner
import rotlin.cli.Term
import rotlin.cli.Watcher
import rotlin.compiler.CompilerDriver
import rotlin.compiler.LineMap
import rotlin.compiler.RoastRenderer
import rotlin.runtime.DevHost
import rotlin.runtime.DevHostImpl
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * `rotlin drop app.rot` - serve the site, watch the file, hot reload on save.
 *
 * The DevHost owns the socket for the whole session; each successful rebuild
 * compiles into a fresh generation dir (Windows file locks), runs user main
 * in a fresh classloader, and swaps the SiteSpec. On errors the old site
 * stays live and the roast is pushed into the page via SSE.
 */
class DropCommand(private val err: PrintStream = System.err) {

    fun run(file: Path): Int {
        if (!Files.exists(file)) {
            err.println("can't find `$file` - that file is ghosting us")
            return 1
        }
        val fileName = file.fileName.toString()
        val facade = KotlinCompilerFacade(ClasspathLocator.userClasspath())
        val host = DevHostImpl()
        DevHost.current = host
        val session = Files.createTempDirectory("rotdrop")
        var generation = 0

        err.println("preheating the cooker (first cook is the slowest)...")
        runCatching {
            val warm = session.resolve("warmup.kt")
            Files.writeString(warm, "fun main() {}", Charsets.UTF_8)
            facade.compile(warm, session.resolve("warmup-classes"), LineMap.identity())
        }

        fun buildAndRun(): Boolean {
            val source = readWithRetry(file) ?: run {
                err.println("couldn't read $fileName - editor still saving?")
                return false
            }

            val result = CompilerDriver.compile(source)
            val output = result.output
            if (result.diagnostics.hasErrors || output == null) {
                val text = RoastRenderer.renderAll(fileName, source, result.diagnostics) +
                    "\n\n" + RoastRenderer.auraSummary(result.diagnostics)
                err.println(RoastRenderer.renderAll(fileName, source, result.diagnostics, Term.color))
                err.println(RoastRenderer.auraSummary(result.diagnostics, Term.color))
                host.broadcastRoast("MID CODE DETECTED\n$text")
                return false
            }

            generation++
            val genDir = session.resolve("gen-$generation")
            Files.createDirectories(genDir)
            val ktFile = genDir.resolve("RotMain.kt")
            Files.writeString(ktFile, output.ktText, Charsets.UTF_8)
            val classesDir = genDir.resolve("classes")

            val ktErrors = facade.compile(ktFile, classesDir, output.lineMap).filter { it.isError }
            if (ktErrors.isNotEmpty()) {
                val text = ktErrors.joinToString("\n") {
                    val where = it.rotLine?.let { l -> "[$fileName line $l] " } ?: ""
                    "${where}the cook failed: ${it.message.lineSequence().first()}"
                }
                err.println(text)
                host.broadcastRoast("MID CODE DETECTED\n$text")
                return false
            }

            return try {
                Runner(listOf(classesDir)).runMain(output.mainClassFqn)
                true
            } catch (e: Throwable) {
                val text = "SKILL ISSUE: ${e.message ?: e.javaClass.simpleName}"
                err.println(text)
                host.broadcastRoast(text)
                false
            }
        }

        if (buildAndRun()) {
            if (host.boundPort > 0) {
                err.println("DROPPED: http://localhost:${host.boundPort} - go look")
            } else {
                err.println("program ran but never dropped a site - did you write `drop site on 3000`?")
            }
        }
        err.println("watching $fileName - every save updates the page (ctrl+c to stop)")

        Watcher(file).watch {
            err.println("cooking...")
            if (buildAndRun()) {
                host.broadcastReload()
                err.println("reloaded, W")
            }
        }
    }

    private fun readWithRetry(file: Path): String? {
        repeat(5) { attempt ->
            try {
                return Files.readString(file, Charsets.UTF_8)
            } catch (_: java.io.IOException) {
                Thread.sleep(40L * (attempt + 1))
            }
        }
        return null
    }
}
