package rotlin.runtime

/**
 * The web DSL. Under `rotlin drop`, DevHost.current owns the socket and this
 * just hands over the SiteSpec; under `rotlin cook`, a standalone server
 * binds the port and blocks forever.
 */
fun site(port: Int, build: SiteBuilder.() -> Unit) {
    val spec = SiteBuilder().apply(build).toSpec(port)
    val host = DevHost.current
    if (host != null) host.install(spec) else StandaloneServer(spec).serveForever()
}

class SiteBuilder internal constructor() {
    private val pages = mutableListOf<PageSpec>()

    fun page(path: String, body: PageContext.() -> Unit) {
        pages += PageSpec(path, body)
    }

    internal fun toSpec(port: Int) = SiteSpec(port, pages.toList())
}

class SiteSpec internal constructor(val port: Int, val pages: List<PageSpec>)

class PageSpec internal constructor(val path: String, val body: PageContext.() -> Unit)

/**
 * Receiver for `page("/") bet ... periodt` blocks. Members shadow the console
 * top-levels - inside a page, `yap` writes a paragraph, not stdout.
 */
class PageContext internal constructor(private val pagePath: String) {
    internal val html = StringBuilder()
    internal val handlers = LinkedHashMap<String, () -> Unit>()
    private var ordinal = 0

    fun bigyap(x: Any?) {
        html.append("<h1>").append(Html.esc(x.toString())).append("</h1>\n")
    }

    fun yap(x: Any?) {
        html.append("<p>").append(Html.esc(x.toString())).append("</p>\n")
    }

    fun yap() {
        html.append("<br>\n")
    }

    fun pic(url: String) {
        html.append("<img src=\"").append(Html.escAttr(url)).append("\" alt=\"\">\n")
    }

    fun link(label: String, to: String) {
        html.append("<p><a href=\"").append(Html.escAttr(to)).append("\">")
            .append(Html.esc(label)).append("</a></p>\n")
    }

    fun smash(label: String, handler: () -> Unit) {
        val id = "$pagePath#${ordinal++}"
        handlers[id] = handler
        html.append("<form method=\"post\" action=\"/__rotlin/click?id=")
            .append(Html.urlEnc(id)).append("&back=").append(Html.urlEnc(pagePath)).append("\">")
            .append("<button>").append(Html.esc(label)).append("</button></form>\n")
    }
}

internal class RenderedPage(val fullHtml: String, val handlers: Map<String, () -> Unit>)

internal object PageRenderer {
    /** Runs the page lambda against a fresh context; null if no page matches [path]. */
    fun render(spec: SiteSpec, path: String, devMode: Boolean): RenderedPage? {
        val page = spec.pages.firstOrNull { it.path == path } ?: return null
        val ctx = PageContext(path)
        page.body(ctx)
        return RenderedPage(Html.shell(ctx.html.toString(), devMode), ctx.handlers)
    }
}
