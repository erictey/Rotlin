package rotlin.compiler

/**
 * Terminal rendering for diagnostics: header, source excerpt with caret, and
 * a fix hint - plain compiler language throughout. Aura math lives here too.
 * All output is ASCII - Windows conhost mangles anything fancier.
 */
object RoastRenderer {

    private const val START_AURA = 1000
    private const val ERROR_COST = 100
    private const val WARNING_COST = 25

    private object Ansi {
        const val RESET = "[0m"
        const val RED = "[31m"
        const val YELLOW = "[33m"
        const val CYAN = "[36m"
        const val MAGENTA = "[35m"
        const val DIM = "[2m"
        const val BOLD = "[1m"
    }

    fun render(fileName: String, sourceLines: List<String>, d: Diagnostic, color: Boolean = false): String {
        fun paint(code: String, text: String) = if (color) "$code$text${Ansi.RESET}" else text

        val headColor = if (d.severity == Severity.ERROR) Ansi.RED else Ansi.YELLOW
        val label = if (d.severity == Severity.ERROR) "error" else "warning"
        val sb = StringBuilder()
        sb.append(paint(Ansi.BOLD + headColor, "[$fileName line ${d.line}] $label: ${d.message}"))

        val src = sourceLines.getOrNull(d.line - 1)
        if (src != null) {
            val lineNo = d.line.toString()
            val prefix = "    $lineNo | "
            sb.append('\n').append(paint(Ansi.DIM, "$prefix$src"))
            if (d.col in 1..src.length + 1) {
                val caretPad = " ".repeat(prefix.length + d.col - 1)
                sb.append('\n').append(paint(headColor, "$caretPad^"))
            }
        }

        d.hint?.let { sb.append('\n').append(paint(Ansi.CYAN, "  fix: $it")) }
        return sb.toString()
    }

    fun renderAll(fileName: String, source: String, diags: DiagnosticBag, color: Boolean = false): String {
        val lines = source.lines()
        return diags.all
            .sortedWith(compareBy({ it.line }, { it.col }))
            .joinToString("\n\n") { render(fileName, lines, it, color) }
    }

    fun auraScore(diags: DiagnosticBag): Int {
        val errors = diags.all.count { it.severity == Severity.ERROR }
        val warnings = diags.all.count { it.severity == Severity.WARNING }
        return (START_AURA - errors * ERROR_COST - warnings * WARNING_COST).coerceAtLeast(0)
    }

    fun auraSummary(diags: DiagnosticBag, color: Boolean = false): String {
        fun paint(code: String, text: String) = if (color) "$code$text${Ansi.RESET}" else text
        val errors = diags.all.count { it.severity == Severity.ERROR }
        val warnings = diags.all.count { it.severity == Severity.WARNING }
        return when {
            errors == 0 && warnings == 0 ->
                paint(Ansi.BOLD + Ansi.MAGENTA, "+1000 aura. Compiled with no errors and no warnings.")
            errors == 0 ->
                paint(Ansi.YELLOW, "aura check: ${auraScore(diags)} / 1000 - compiled with $warnings warning(s)")
            else ->
                paint(Ansi.RED, "aura check: ${auraScore(diags)} / 1000 - $errors error(s) to fix")
        }
    }
}
