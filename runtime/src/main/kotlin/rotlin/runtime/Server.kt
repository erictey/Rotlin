package rotlin.runtime

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.BindException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Routes GET renders and smash-button clicks for a swappable SiteSpec.
 * Handlers run under one lock — kids never meet a race condition.
 */
internal class SiteRouter(private val devMode: Boolean) {

    @Volatile
    var spec: SiteSpec? = null

    private val lock = Any()

    @Volatile
    private var liveHandlers: Map<String, () -> Unit> = emptyMap()

    fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            when {
                exchange.requestMethod == "POST" && path == "/__rotlin/click" -> click(exchange)
                exchange.requestMethod == "GET" -> get(exchange, path)
                else -> respond(exchange, 405, "text/plain; charset=utf-8", "405: nah")
            }
        } catch (e: Exception) {
            runCatching {
                respond(exchange, 500, "text/plain; charset=utf-8", "500: the server crashed out - ${e.message}")
            }
        } finally {
            exchange.close()
        }
    }

    private fun get(exchange: HttpExchange, path: String) {
        if (path == "/favicon.ico") {
            respond(exchange, 404, "text/plain; charset=utf-8", "")
            return
        }
        val s = spec ?: run {
            respond(exchange, 503, "text/plain; charset=utf-8", "site still cooking, refresh in a sec")
            return
        }
        val rendered = synchronized(lock) {
            PageRenderer.render(s, path, devMode)?.also { liveHandlers = it.handlers }
        }
        if (rendered == null) {
            respond(
                exchange, 404, "text/html; charset=utf-8",
                Html.shell("<h1>404</h1><p>this page is ghosting you</p>", devMode),
            )
        } else {
            respond(exchange, 200, "text/html; charset=utf-8", rendered.fullHtml)
        }
    }

    /** POST /__rotlin/click?id=...&back=... → run handler → 303 back (PRG, refresh-safe). */
    private fun click(exchange: HttpExchange) {
        val q = parseQuery(exchange.requestURI.rawQuery ?: "")
        val id = q["id"]
        var back = q["back"] ?: "/"
        if (!back.startsWith("/")) back = "/"
        if (id != null) synchronized(lock) { liveHandlers[id]?.invoke() } // stale id: no-op, self-heals on render
        exchange.responseHeaders.add("Location", back)
        exchange.sendResponseHeaders(303, -1)
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').filter { it.contains('=') }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
        }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
    }
}

/** Blocks forever serving [spec] — the `rotlin cook` path for web programs. */
internal class StandaloneServer(private val spec: SiteSpec) {

    fun serveForever(): Nothing {
        val router = SiteRouter(devMode = false).also { it.spec = spec }
        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", spec.port), 0)
        } catch (e: BindException) {
            throw SkillIssue("port ${spec.port} is taken - some other app is squatting there. try a different number.")
        }
        server.createContext("/", router::handle)
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        println("your site is LIVE: http://localhost:${spec.port} (ctrl+c to stop)")
        while (true) Thread.sleep(Long.MAX_VALUE)
    }
}
