package rotlin.cli

object Term {
    /** ANSI color only on a real console and when NO_COLOR isn't set. */
    val color: Boolean
        get() = System.console() != null && System.getenv("NO_COLOR") == null
}
