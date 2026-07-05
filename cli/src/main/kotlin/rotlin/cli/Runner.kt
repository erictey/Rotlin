package rotlin.cli

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Runs a compiled Rotlin program in-process via a fresh URLClassLoader whose
 * parent is the CLI's own loader (so rotlin.runtime classes are shared -
 * required for DevHost statics to be visible to both sides).
 */
class Runner(private val classpath: List<Path>) {

    fun runMain(mainClassFqn: String) {
        val urls = classpath.map { it.toUri().toURL() }.toTypedArray()
        val loader = URLClassLoader(urls, Runner::class.java.classLoader)
        val cls = Class.forName(mainClassFqn, true, loader)
        val main = cls.methods.firstOrNull {
            it.name == "main" && it.parameterCount == 1 && it.parameterTypes[0] == Array<String>::class.java
        } ?: cls.methods.firstOrNull { it.name == "main" && it.parameterCount == 0 }
        ?: error("no main function found in $mainClassFqn")

        try {
            if (main.parameterCount == 1) main.invoke(null, arrayOf<String>()) else main.invoke(null)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
