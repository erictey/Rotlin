package rotlin.compiler

enum class Severity { ERROR, WARNING }

data class Diagnostic(
    val code: String,
    val message: String,
    val hint: String?,
    val line: Int,
    val col: Int,
    val severity: Severity = Severity.ERROR,
)

class DiagnosticBag {
    private val list = mutableListOf<Diagnostic>()

    val all: List<Diagnostic> get() = list
    val hasErrors: Boolean get() = list.any { it.severity == Severity.ERROR }

    fun error(code: String, message: String, line: Int, col: Int, hint: String? = null) {
        list += Diagnostic(code, message, hint, line, col, Severity.ERROR)
    }

    fun warning(code: String, message: String, line: Int, col: Int, hint: String? = null) {
        list += Diagnostic(code, message, hint, line, col, Severity.WARNING)
    }
}
