package rotlin.cli

import rotlin.cli.commands.CookCommand
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CookE2eTest {

    private data class CookRun(val exitCode: Int, val stdout: String, val diag: String)

    private fun cook(src: String): CookRun {
        val file = Files.createTempDirectory("rotcook-test").resolve("app.rot")
        file.writeText(src)
        val diagBuf = ByteArrayOutputStream()
        val outBuf = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
        val code = try {
            CookCommand(err = PrintStream(diagBuf, true, Charsets.UTF_8)).run(file)
        } finally {
            System.setOut(oldOut)
        }
        return CookRun(code, outBuf.toString(Charsets.UTF_8), diagBuf.toString(Charsets.UTF_8))
    }

    @Test
    fun `hello example compiles and prints expected output`() {
        val src = Files.readString(Paths.get("..", "examples", "hello.rot"))
        val run = cook(src)
        assertEquals(0, run.exitCode, "diagnostics: ${run.diag}")
        val expected = listOf(
            "skibidi number 0",
            "skibidi number 1",
            "skibidi number 2",
            "based",
            "sup rizzlord",
            "PI is 3.141592653589793",
        ).joinToString(System.lineSeparator(), postfix = System.lineSeparator())
        assertEquals(expected, run.stdout)
    }

    @Test
    fun `rotlin parse error reports rot line and skips kotlinc`() {
        val run = cook("rizz a = 1\nsus (a == 1) bet\nyap(a)\nperiodt\n")
        assertNotEquals(0, run.exitCode)
        assertContains(run.diag, "line 2")
        assertContains(run.diag, "twins")
    }

    @Test
    fun `kotlinc-only error maps back to the rot line`() {
        // `nope` is undefined — phase 1 has no typechecker, so kotlinc catches it
        val run = cook("rizz a = 1\nyap(nope)\n")
        assertNotEquals(0, run.exitCode)
        assertContains(run.diag, "line 2")
    }

    @Test
    fun `deadass on ghosted crashes with caught in 4k and the rot line`() {
        val run = cook("rizz name: maybe lore = ghosted\nyap(name deadass)\n")
        assertNotEquals(0, run.exitCode)
        assertContains(run.diag, "caught in 4k")
        assertContains(run.diag, "line 2")
    }
}
