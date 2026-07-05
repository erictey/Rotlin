package rotlin.runtime

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderTest {

    private fun clickerSpec(score: Int = 7): SiteSpec =
        SiteBuilder().apply {
            page("/") {
                bigyap("AURA CLICKER")
                yap("aura: $score")
                pic("https://cats.example/cat.png")
                smash("+1 aura") { }
                smash("-1 aura") { }
            }
        }.toSpec(3000)

    @Test
    fun `renders headings paragraphs images and buttons`() {
        val page = PageRenderer.render(clickerSpec(), "/", devMode = false)!!
        val html = page.fullHtml
        assertContains(html, "<h1>AURA CLICKER</h1>")
        assertContains(html, "<p>aura: 7</p>")
        assertContains(html, "<img src=\"https://cats.example/cat.png\"")
        assertContains(html, "<button>+1 aura</button>")
    }

    @Test
    fun `each smash gets a stable ordinal id and registers its handler`() {
        val page = PageRenderer.render(clickerSpec(), "/", devMode = false)!!
        assertEquals(setOf("/#0", "/#1"), page.handlers.keys)
        assertContains(page.fullHtml, "action=\"/__rotlin/click?id=%2F%230&back=%2F\"")
    }

    @Test
    fun `html in user text is escaped`() {
        val spec = SiteBuilder().apply {
            page("/") { yap("<script>alert('pwn')</script>") }
        }.toSpec(3000)
        val html = PageRenderer.render(spec, "/", devMode = false)!!.fullHtml
        assertFalse(html.contains("<script>alert"))
        assertContains(html, "&lt;script&gt;")
    }

    @Test
    fun `unknown path renders nothing`() {
        assertNull(PageRenderer.render(clickerSpec(), "/nope", devMode = false))
    }

    @Test
    fun `dev mode injects the reload script and normal mode does not`() {
        assertTrue(PageRenderer.render(clickerSpec(), "/", devMode = true)!!.fullHtml.contains("__rotlin/events"))
        assertFalse(PageRenderer.render(clickerSpec(), "/", devMode = false)!!.fullHtml.contains("__rotlin/events"))
    }

    @Test
    fun `page yap shadows console yap inside page blocks`() {
        // compile-level guarantee: PageContext.yap is a member, so inside the
        // lambda it wins over top-level rotlin.runtime.yap by receiver scoping
        val spec = SiteBuilder().apply { page("/") { yap(42) } }.toSpec(3000)
        val html = PageRenderer.render(spec, "/", devMode = false)!!.fullHtml
        assertContains(html, "<p>42</p>")
    }
}
