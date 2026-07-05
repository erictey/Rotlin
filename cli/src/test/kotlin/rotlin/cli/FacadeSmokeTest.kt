package rotlin.cli

import rotlin.compiler.LineMap
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The canary: proves kotlin-compiler-embeddable can compile a file and that we
 * can run the result in-process. If the embeddable API ever churns, this is
 * the first test to break.
 */
class FacadeSmokeTest {

    @Test
    fun `embeddable compiler compiles and runs hello kotlin in process`() {
        val tmp = Files.createTempDirectory("rotsmoke")
        val kt = tmp.resolve("Hello.kt")
        kt.writeText(
            """
            @file:JvmName("HelloMain")
            fun main() { println("skibidi ok") }
            """.trimIndent()
        )
        val outDir = tmp.resolve("classes")

        val diags = KotlinCompilerFacade(ClasspathLocator.userClasspath())
            .compile(kt, outDir, LineMap.identity())
        assertTrue(diags.none { it.isError }, "kotlinc reported errors: $diags")

        val captured = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        try {
            Runner(listOf(outDir)).runMain("HelloMain")
        } finally {
            System.setOut(old)
        }
        assertEquals("skibidi ok", captured.toString(Charsets.UTF_8).trim())
    }
}
