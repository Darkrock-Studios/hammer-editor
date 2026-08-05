import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import com.darkrockstudios.apps.hammer.common.projectselection.linkifyChangelog
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkifyChangelogTest {

	private fun linkify(text: String) = linkifyChangelog(text, SpanStyle())

	private fun AnnotatedString.urls(): List<String> =
		getLinkAnnotations(0, length)
			.mapNotNull { (it.item as? LinkAnnotation.Url)?.url }

	@Test
	fun `markdown link renders its label and carries the url`() {
		val result = linkify("- See [the docs](https://hammer.ink/docs) for more")

		assertEquals("- See the docs for more", result.text)
		assertEquals(listOf("https://hammer.ink/docs"), result.urls())
	}

	@Test
	fun `bare url links to itself`() {
		val result = linkify("Visit https://hammer.ink today")

		assertEquals("Visit https://hammer.ink today", result.text)
		assertEquals(listOf("https://hammer.ink"), result.urls())
	}

	@Test
	fun `trailing sentence punctuation stays outside the href`() {
		val result = linkify("Read https://hammer.ink/docs.")

		assertEquals("Read https://hammer.ink/docs.", result.text)
		assertEquals(listOf("https://hammer.ink/docs"), result.urls())
	}

	@Test
	fun `section tags are left untouched`() {
		val notes = "[New]\n- Ideas: capture ideas for new stories\n\n[Fix]\n- Text editor crash"
		val result = linkify(notes)

		assertEquals(notes, result.text)
		assertTrue(result.urls().isEmpty())
	}

	@Test
	fun `text with no links round-trips unchanged`() {
		val notes = "- Translations updated for German, Spanish, French"
		val result = linkify(notes)

		assertEquals(notes, result.text)
		assertTrue(result.urls().isEmpty())
	}

	@Test
	fun `markdown url containing parentheses is not truncated`() {
		val url = "https://en.wikipedia.org/wiki/Rich_Text_Format_(RTF)"
		val result = linkify("- See [the format]($url) for details")

		assertEquals("- See the format for details", result.text)
		assertEquals(listOf(url), result.urls())
	}

	@Test
	fun `bare url keeps its own balanced parentheses`() {
		val url = "https://en.wikipedia.org/wiki/Rich_Text_Format_(RTF)"
		val result = linkify("See $url today")

		assertEquals("See $url today", result.text)
		assertEquals(listOf(url), result.urls())
	}

	@Test
	fun `wrapping paren is not swallowed by a bare url`() {
		val result = linkify("(see https://hammer.ink)")

		assertEquals("(see https://hammer.ink)", result.text)
		assertEquals(listOf("https://hammer.ink"), result.urls())
	}

	@Test
	fun `balanced parens survive trailing sentence punctuation`() {
		val url = "https://example.com/a_(b)"
		val result = linkify("Read $url.")

		assertEquals("Read $url.", result.text)
		assertEquals(listOf(url), result.urls())
	}

	@Test
	fun `multiple links in one line are all captured`() {
		val result = linkify("[a](https://a.example) and https://b.example done")

		assertEquals("a and https://b.example done", result.text)
		assertEquals(listOf("https://a.example", "https://b.example"), result.urls())
	}
}
