package rotlin.compiler

/**
 * Maps line numbers in emitted Kotlin back to `.rot` source lines.
 * The emitter keeps output line-aligned with the source, so the MVP
 * implementation is a constant offset; the interface exists so a future
 * multi-line desugaring can record real mappings without touching callers.
 */
interface LineMap {
    /** Returns the `.rot` line for [ktLine], or null if it falls in generated prelude. */
    fun toRotLine(ktLine: Int): Int?

    companion object {
        fun identity(): LineMap = object : LineMap {
            override fun toRotLine(ktLine: Int): Int? = ktLine
        }
    }
}
