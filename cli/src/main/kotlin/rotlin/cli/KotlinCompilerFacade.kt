package rotlin.cli

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import rotlin.compiler.LineMap
import java.io.File
import java.nio.file.Path

/** A kotlinc diagnostic with its line number mapped back to the `.rot` source. */
data class MappedKtDiag(val isError: Boolean, val message: String, val rotLine: Int?)

/**
 * The only place in the codebase that talks to kotlin-compiler-embeddable.
 * Everything org.jetbrains.kotlin.* stays behind this wall.
 */
class KotlinCompilerFacade(private val userClasspath: List<Path>) {

    // Reused across calls so the JIT stays warm during `drop` reloads.
    private val compiler = K2JVMCompiler()

    /** Compiles [ktFile] into [outDir]; returns kotlinc diagnostics mapped through [lineMap]. */
    fun compile(ktFile: Path, outDir: Path, lineMap: LineMap): List<MappedKtDiag> {
        val collected = mutableListOf<MappedKtDiag>()
        val collector = object : MessageCollector {
            override fun clear() {}
            override fun hasErrors() = collected.any { it.isError }
            override fun report(
                severity: CompilerMessageSeverity,
                message: String,
                location: CompilerMessageSourceLocation?,
            ) {
                if (severity.isError || severity == CompilerMessageSeverity.WARNING ||
                    severity == CompilerMessageSeverity.STRONG_WARNING
                ) {
                    collected += MappedKtDiag(
                        isError = severity.isError,
                        message = message,
                        rotLine = location?.line?.let { lineMap.toRotLine(it) },
                    )
                }
            }
        }

        val args = K2JVMCompilerArguments().apply {
            freeArgs = listOf(ktFile.toAbsolutePath().toString())
            destination = outDir.toAbsolutePath().toString()
            classpath = userClasspath.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
            // The embeddable compiler has no "kotlin home" to find a stdlib in;
            // we pass the stdlib jar explicitly via classpath instead.
            noStdlib = true
            noReflect = true
            jvmTarget = "21"
            moduleName = "rotapp"
        }

        val exit = compiler.exec(collector, Services.EMPTY, args)
        if (exit == ExitCode.INTERNAL_ERROR) {
            collected += MappedKtDiag(true, "kotlin compiler internal error", null)
        }
        return collected
    }
}
