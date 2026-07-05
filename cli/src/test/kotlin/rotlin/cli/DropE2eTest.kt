package rotlin.cli

import rotlin.cli.commands.DropCommand
import rotlin.runtime.DevHost
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DropE2eTest {

    private val diagBuf = ByteArrayOutputStream()

    @AfterTest
    fun tearDown() {
        DevHost.current?.stop()
        DevHost.current = null
    }

    private fun clickerSrc(title: String) = """
        gyatt score = 0

        drop site on 0 bet
            page("/") bet
                bigyap("$title")
                yap("aura: ${'$'}score")
                smash("+1") does bet
                    score gains 1
                periodt
            periodt
        periodt
    """.trimIndent()

    private fun awaitPort(timeoutMs: Long = 120_000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val port = DevHost.current?.boundPort ?: -1
            if (port > 0) return port
            Thread.sleep(100)
        }
        error("drop server never came up. diagnostics:\n$diagBuf")
    }

    private fun awaitBody(client: HttpClient, url: String, contains: String, timeoutMs: Long = 30_000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = ""
        while (System.currentTimeMillis() < deadline) {
            last = client.send(
                HttpRequest.newBuilder(URI(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).body()
            if (last.contains(contains)) return last
            Thread.sleep(250)
        }
        error("never saw `$contains`. last body:\n$last\ndiagnostics:\n$diagBuf")
    }

    @Test
    fun `full dopamine loop - serve click reload roast`() {
        val dir = Files.createTempDirectory("rotdrop-test")
        val file: Path = dir.resolve("app.rot")
        file.writeText(clickerSrc("AURA CLICKER"))

        Thread { DropCommand(err = PrintStream(diagBuf, true, Charsets.UTF_8)).run(file) }
            .apply { isDaemon = true; name = "drop-under-test"; start() }

        val port = awaitPort()
        val base = "http://127.0.0.1:$port"
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

        // 1. page serves
        val first = awaitBody(client, "$base/", "AURA CLICKER")
        assertContains(first, "aura: 0")
        assertContains(first, "<button>+1</button>")

        // 2. smash click bumps state (303 -> GET, so the body is the re-render)
        val afterClick = client.send(
            HttpRequest.newBuilder(URI("$base/__rotlin/click?id=%2F%230&back=%2F"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body()
        assertContains(afterClick, "aura: 1")

        // 3. SSE stream is live
        val events = LinkedBlockingQueue<String>()
        val sseResponse = client.send(
            HttpRequest.newBuilder(URI("$base/__rotlin/events")).GET().build(),
            HttpResponse.BodyHandlers.ofLines(),
        )
        Thread { sseResponse.body().forEach { events.put(it) } }
            .apply { isDaemon = true; start() }
        Thread.sleep(500) // let the watcher settle before we edit

        // 4. save a new version -> reload event + new content + fresh state
        file.writeText(clickerSrc("GLOW UP"))
        awaitEvent(events, "data: reload")
        val reloaded = awaitBody(client, "$base/", "GLOW UP")
        assertContains(reloaded, "aura: 0") // reload = fresh vibes

        // 5. save a broken version -> roast event, old site stays alive
        file.writeText("rizz = broken\n")
        awaitEvent(events, "event: aura")
        val stillUp = awaitBody(client, "$base/", "GLOW UP")
        assertContains(stillUp, "GLOW UP")
    }

    private fun awaitEvent(events: LinkedBlockingQueue<String>, contains: String, timeoutMs: Long = 60_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = events.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (line.contains(contains)) return
        }
        error("never saw SSE `$contains`. diagnostics:\n$diagBuf")
    }
}
