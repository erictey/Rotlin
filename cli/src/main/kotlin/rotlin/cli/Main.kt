package rotlin.cli

import rotlin.cli.commands.CookCommand
import rotlin.cli.commands.DropCommand
import java.nio.file.Paths
import kotlin.system.exitProcess

private const val USAGE = """
rotlin - the brainrot programming language

  rotlin cook <file.rot>   compile + run a console program
  rotlin drop <file.rot>   serve a web program with hot reload (soon)
  rotlin aura <file.rot>   check your code, get your aura score (soon)
"""

fun main(args: Array<String>) {
    if (args.size < 2) {
        println(USAGE.trim())
        exitProcess(if (args.isEmpty()) 0 else 1)
    }
    val file = Paths.get(args[1])
    val exit = when (args[0]) {
        "cook" -> CookCommand().run(file)
        "drop" -> DropCommand().run(file)
        else -> {
            println("unknown command `${args[0]}` - that's not a thing yet")
            println(USAGE.trim())
            1
        }
    }
    exitProcess(exit)
}
