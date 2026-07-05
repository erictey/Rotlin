package rotlin.runtime

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpPrincipal
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI

/** Minimal in-memory HttpExchange so SiteRouter can be tested without sockets. */
class FakeExchange(private val method: String, uri: String) : HttpExchange() {
    private val uriObj = URI(uri)
    private val requestHeaders = Headers()
    private val responseHeaders = Headers()
    private val responseBuf = ByteArrayOutputStream()

    var status: Int = -1
        private set

    val responseText: String get() = responseBuf.toString(Charsets.UTF_8)

    override fun getRequestHeaders(): Headers = requestHeaders
    override fun getResponseHeaders(): Headers = responseHeaders
    override fun getRequestURI(): URI = uriObj
    override fun getRequestMethod(): String = method
    override fun getHttpContext(): HttpContext? = null
    override fun close() {}
    override fun getRequestBody(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun getResponseBody(): OutputStream = responseBuf
    override fun sendResponseHeaders(rCode: Int, responseLength: Long) { status = rCode }
    override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress("127.0.0.1", 54321)
    override fun getResponseCode(): Int = status
    override fun getLocalAddress(): InetSocketAddress = InetSocketAddress("127.0.0.1", 80)
    override fun getProtocol(): String = "HTTP/1.1"
    override fun getAttribute(name: String?): Any? = null
    override fun setAttribute(name: String?, value: Any?) {}
    override fun setStreams(i: InputStream?, o: OutputStream?) {}
    override fun getPrincipal(): HttpPrincipal? = null
}
