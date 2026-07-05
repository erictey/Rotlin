package rotlin.cli

import rotlin.cli.commands.AuraCommand
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AuraE2eTest {

    private fun aura(src: String): Pair<Int, String> {
        val file = Files.createTempDirectory("rotaura-test").resolve("app.rot")
        file.writeText(src)
        val buf = ByteArrayOutputStream()
        val code = AuraCommand(out = PrintStream(buf, true, Charsets.UTF_8)).run(file)
        return code to buf.toString(Charsets.UTF_8)
    }

    @Test
    fun `two errors cost 200 aura and exit nonzero`() {
        val (code, out) = aura("yap(nope)\nyap(alsonope)\n")
        assertEquals(1, code)
        assertContains(out, "800 / 1000")
        assertContains(out, "who is `nope`??")
    }

    @Test
    fun `clean file gets the full W`() {
        val (code, out) = aura("rizz name = \"chat\"\nyap(\"sup \" + name)\n")
        assertEquals(0, code)
        assertContains(out, "+1000 aura")
        assertContains(out, "no cap")
    }
}
