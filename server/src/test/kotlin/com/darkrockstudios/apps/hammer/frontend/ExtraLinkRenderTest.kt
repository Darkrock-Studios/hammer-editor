package com.darkrockstudios.apps.hammer.frontend

import com.github.mustachejava.DefaultMustacheFactory
import org.junit.jupiter.api.Test
import java.io.StringWriter
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renders the real header/footer partials so a mustache typo fails the build — an unknown section
 * name renders as nothing rather than erroring, so a broken template looks exactly like a
 * deployment that configured no extra links.
 */
class ExtraLinkRenderTest {

	private val factory = DefaultMustacheFactory("templates")

	private fun render(template: String, model: Map<String, Any>): String {
		val writer = StringWriter()
		factory.compile(template).execute(writer, model).flush()
		return writer.toString()
	}

	private fun link(
		url: String = "/blog",
		title: String = "Blog",
		icon: String = "fa-solid fa-blog",
		external: Boolean = false,
	) = mapOf("url" to url, "icon" to icon, "title" to title, "external" to external)

	@Test
	fun `header renders a configured extra link`() {
		val html = render("header.mustache", mapOf("headerExtraLinks" to listOf(link())))

		assertTrue(html.contains("""href="/blog""""), html)
		assertTrue(html.contains("""class="fa-solid fa-blog""""), html)
		assertTrue(html.contains("Blog"), html)
	}

	@Test
	fun `footer renders a configured extra link`() {
		val html = render(
			"footer.mustache",
			mapOf("hasFooterNav" to true, "footerExtraLinks" to listOf(link())),
		)

		assertTrue(html.contains("""href="/blog""""), html)
		assertTrue(html.contains("""class="fa-solid fa-blog""""), html)
	}

	@Test
	fun `an external link opens in a new tab with noopener`() {
		val html = render(
			"footer.mustache",
			mapOf(
				"hasFooterNav" to true,
				"footerExtraLinks" to listOf(link(url = "https://blog.example.com", external = true)),
			),
		)

		assertTrue(html.contains("""target="_blank""""), html)
		assertTrue(html.contains("""rel="noopener noreferrer""""), html)
	}

	@Test
	fun `a site-relative link stays in the tab`() {
		val html = render(
			"footer.mustache",
			mapOf("hasFooterNav" to true, "footerExtraLinks" to listOf(link())),
		)

		assertFalse(html.substringBefore("footer-social").contains("""target="_blank""""), html)
	}

	@Test
	fun `no extra links renders no nav entries`() {
		val header = render("header.mustache", mapOf("headerExtraLinks" to emptyList<Any>()))
		val footer = render("footer.mustache", mapOf("footerExtraLinks" to emptyList<Any>()))

		assertFalse(header.contains("header-nav__link\" title=\"Blog"), header)
		assertFalse(footer.contains("footer-nav"), footer)
	}

	@Test
	fun `titles are html escaped`() {
		val html = render(
			"footer.mustache",
			mapOf(
				"hasFooterNav" to true,
				"footerExtraLinks" to listOf(link(title = """<script>alert(1)</script>""")),
			),
		)

		assertFalse(html.contains("<script>alert(1)</script>"), html)
	}
}
