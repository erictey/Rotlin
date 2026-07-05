package rotlin.runtime

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Static hook the CLI sets before running user code in drop mode. Shared
 * across classloader generations because the runtime lives in the parent
 * loader - so a re-run of user main lands its SiteSpec in the same host.
 */
object DevHost {
    @Volatile
    var current: DevHostImpl? = null
}

/**
 * Owns the HTTP socket for the whole drop session; user code only produces
 * SiteSpecs. The socket never rebinds across reloads, so the browser
 * connection survives every save.
 */
class DevHostImpl {

    private val router = SiteRouter(devMode = true)
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private val sseClients = CopyOnWriteArrayList<OutputStream>()

    var boundPort: Int = -1
        private set

    /** Swaps in a freshly built site; binds the server socket on first call. */
    fun install(spec: SiteSpec) {
        router.spec = spec
        synchronized(this) {
            if (server == null) start(spec.port)
        }
    }

    private fun start(preferredPort: Int) {
        val candidates = if (preferredPort == 0) listOf(0) else (preferredPort..preferredPort + 10).toList()
        var s: HttpServer? = null
        for (p in candidates) {
            try {
                s = HttpServer.create(InetSocketAddress("127.0.0.1", p), 0)
                if (p != preferredPort) println("port $preferredPort is taken - sliding to $p")
                break
            } catch (_: java.net.BindException) {
                // squatter on this port, try the next one
            }
        }
        if (s == null) {
            throw SkillIssue(
                "ports $preferredPort through ${preferredPort + 10} are ALL taken - close something and run it back",
            )
        }
        val ex = Executors.newFixedThreadPool(4) { r -> Thread(r).apply { isDaemon = true } }
        s.createContext("/") { exchange ->
            if (exchange.requestURI.path == "/__rotlin/events") handleSse(exchange)
            else router.handle(exchange)
        }
        s.executor = ex
        s.start()
        server = s
        executor = ex
        boundPort = s.address.port
        startHeartbeat()
    }

    /** Keeps the exchange open forever; the browser's EventSource hangs off it. */
    private fun handleSse(exchange: HttpExchange) {
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(200, 0)
        val os = exchange.responseBody
        try {
            os.write(": yo\n\n".toByteArray(Charsets.UTF_8))
            os.flush()
            sseClients += os
        } catch (_: IOException) {
            runCatching { exchange.close() }
        }
    }

    fun broadcastReload() = broadcast(null, "reload")

    fun broadcastRoast(text: String) = broadcast("aura", text)

    private fun broadcast(event: String?, payload: String) {
        val sb = StringBuilder()
        if (event != null) sb.append("event: ").append(event).append('\n')
        payload.lines().forEach { sb.append("data: ").append(it).append('\n') }
        sb.append('\n')
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        for (os in sseClients) {
            try {
                os.write(bytes)
                os.flush()
            } catch (_: IOException) {
                sseClients.remove(os)
                runCatching { os.close() }
            }
        }
    }

    private fun startHeartbeat() {
        Thread {
            while (server != null) {
                Thread.sleep(15_000)
                val ping = ": ping\n\n".toByteArray(Charsets.UTF_8)
                for (os in sseClients) {
                    try { os.write(ping); os.flush() }
                    catch (_: IOException) { sseClients.remove(os); runCatching { os.close() } }
                }
            }
        }.apply { isDaemon = true; name = "rotlin-sse-heartbeat"; start() }
    }

    fun stop() {
        server?.stop(0)
        executor?.shutdownNow()
        server = null
    }
}
