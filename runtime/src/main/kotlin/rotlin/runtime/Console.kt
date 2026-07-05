package rotlin.runtime

/** Rotlin's error type. Every crash is a skill issue. */
class SkillIssue(message: String) : RuntimeException(message)

fun yap() = println()
fun yap(x: Any?) = println(x)
fun whisper(x: Any?) = print(x)

/** Reads a line from the console; ghosted when input ends. */
fun listen(): String? = readLine()

/** Backing for the postfix `deadass` operator (`!!` with a better story). */
fun <T : Any> T?.deadass(): T =
    this ?: throw SkillIssue("you said deadass but it was ghosted. caught in 4k.")

// conversions — named after the types they convert INTO
fun lore(x: Any?): String = x.toString()
fun aura(x: Any?): Int? = if (x is Int) x else x?.toString()?.trim()?.toIntOrNull()
fun ratio(x: Any?): Double? = if (x is Double) x else x?.toString()?.trim()?.toDoubleOrNull()

// collection factories
fun <T> squad(vararg items: T): MutableList<T> = mutableListOf(*items)
fun <K, V> stash(): MutableMap<K, V> = mutableMapOf()
