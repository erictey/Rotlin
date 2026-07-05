package rotlin.runtime

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerGuardTest {

    @Test
    fun `grinding smash handler gets a 500 roast and the router survives`() {
        val spec = SiteBuilder().apply {
            page("/") {
                smash("go") { while (true) Thread.sleep(10) } // kid wrote an infinite loop
            }
        }.toSpec(0)

        val router = SiteRouter(devMode = false, handlerTimeoutMs = 150)
        router.spec = spec

        router.handle(FakeExchange("GET", "/")) // render registers the handler
        val click = FakeExchange("POST", "/__rotlin/click?id=%2F%230&back=%2F")
        router.handle(click)
        assertEquals(500, click.status)
        assertContains(click.responseText, "grinding")

        // router must not be frozen by the zombie handler
        val again = FakeExchange("GET", "/")
        router.handle(again)
        assertEquals(200, again.status)
    }

    @Test
    fun `dev host slides to a free port when the preferred one is taken`() {
        val blocker = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val taken = blocker.localPort
        val host = DevHostImpl()
        try {
            host.install(SiteBuilder().apply { page("/") { yap("x") } }.toSpec(taken))
            assertTrue(host.boundPort > 0, "server should have bound somewhere")
            assertTrue(host.boundPort != taken, "should have slid off the taken port")
        } finally {
            host.stop()
            blocker.close()
        }
    }
}
