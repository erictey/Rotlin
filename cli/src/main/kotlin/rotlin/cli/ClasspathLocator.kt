package rotlin.cli

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Locates the jars/class-dirs a compiled Rotlin program needs on its classpath:
 * the rotlin runtime and kotlin-stdlib. Works both under `gradlew :cli:run`
 * (build/classes dirs) and under installDist (jars in lib).
 */
object ClasspathLocator {

    fun userClasspath(): List<Path> {
        val entries = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { Paths.get(it) }

        val stdlib = entries.filter { it.fileName.toString().startsWith("kotlin-stdlib") }
        check(stdlib.isNotEmpty()) {
            "could not locate kotlin-stdlib on the CLI classpath - broken installation?"
        }

        val runtime = entries.filter { p ->
            val name = p.fileName.toString()
            (name.startsWith("runtime") && name.endsWith(".jar")) ||
                p.any { it.toString() == "runtime" }
        }

        return (runtime + stdlib).distinct()
    }
}
