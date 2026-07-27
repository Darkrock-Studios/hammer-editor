package com.darkrockstudios.apps.hammer.frontend

import com.github.mustachejava.DefaultMustacheFactory
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders the real home page against the real English bundle. A missing message key renders as an
 * empty string rather than failing, so a mistyped key looks identical to a section with no copy.
 */
class HomePageRenderTest {

	private val factory = DefaultMustacheFactory("templates")
	private val bundle: ResourceBundle = ResourceBundle.getBundle("i18n.Messages", Locale.ENGLISH)

	private val messages: Map<String, String> =
		bundle.keys.asSequence().associateWith { bundle.getString(it) }

	private fun render(model: Map<String, Any> = emptyMap()): String {
		val writer = StringWriter()
		val full = mutableMapOf<String, Any>("msg" to messages).apply { putAll(model) }
		factory.compile("home.mustache").execute(writer, full).flush()
		return writer.toString()
	}

	/** Every `{{msg.some_key}}` the template asks for, across the page and its partials. */
	private fun referencedKeys(): Set<String> {
		val templates = listOf(
			"home.mustache",
			"header.mustache",
			"footer.mustache",
			"partials/community-callout.mustache",
		)
		return templates.flatMap { name ->
			val source = javaClass.getResource("/templates/$name")!!.readText()
			Regex("""\{\{\{?msg\.([a-zA-Z0-9_]+)}}}?""").findAll(source).map { it.groupValues[1] }
		}.toSet()
	}

	@Test
	fun `every message key the home page references exists in the bundle`() {
		val missing = referencedKeys().filterNot { messages.containsKey(it) }.sorted()

		assertEquals(emptyList(), missing, "Home page references message keys that do not exist")
	}

	@Test
	fun `the page renders its headline and the three rules`() {
		val html = render()

		assertTrue(html.contains(messages.getValue("home_masthead_title_first")), html)
		assertTrue(html.contains(messages.getValue("home_masthead_title_second")), html)
		assertTrue(html.contains(messages.getValue("home_rule_offline_title")), html)
		assertTrue(html.contains(messages.getValue("home_rule_files_title")), html)
		assertTrue(html.contains(messages.getValue("home_rule_safety_title")), html)
	}

	@Test
	fun `the publish warning shown in the app is the one shown on the page`() {
		val html = render()

		assertTrue(html.contains("First Publication Rights"), html)
	}

	@Test
	fun `no unresolved template variables survive a render`() {
		val html = render()

		assertEquals(emptyList(), Regex("""\{\{[^}]+}}""").findAll(html).map { it.value }.toList(), html)
	}

	@Test
	fun `the origin photo is omitted when the asset is absent`() {
		val html = render()

		assertTrue(html.contains(messages.getValue("home_origin_title")), html)
		assertTrue(!html.contains("origin-van-760.webp"), html)
	}

	@Test
	fun `the origin photo renders once the asset exists`() {
		val html = render(mapOf("hasOriginPhoto" to true))

		assertTrue(html.contains("origin-van-760.webp"), html)
		assertTrue(html.contains("origin-van-1520.webp"), html)
	}

	@Test
	fun `the server notice renders when the instance sets one`() {
		val notice = "Sync is offline on Sunday morning."
		val html = render(mapOf("serverMessage" to notice))

		assertTrue(html.contains(notice), html)
		assertTrue(html.contains(messages.getValue("home_servermessage")), html)
	}

	@Test
	fun `every screenshot the page shows carries alt text`() {
		val html = render()

		val imgs = Regex("""<img\b[^>]*>""").findAll(html).map { it.value }.toList()
		assertTrue(imgs.isNotEmpty(), html)
		val withoutAlt = imgs.filterNot { it.contains("alt=\"") && !it.contains("alt=\"\"") }
		assertEquals(emptyList(), withoutAlt, "Screenshots must describe themselves")
	}
}
