import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.markdownPreview
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownPreviewTest {

	private fun preview(markdown: String, maxLength: Int? = null) = markdownPreview(markdown, maxLength, "Image")

	@Test
	fun `a block image becomes its alt text in italics`() {
		assertEquals(
			"Before\n\n*Cheshire Cat*\n\nAfter",
			preview("Before\n\n![Cheshire Cat](hammer://image/3)\n\nAfter\n"),
		)
	}

	@Test
	fun `an image inside prose becomes its alt text in italics`() {
		assertEquals("The *Cheshire Cat* grins.", preview("The ![Cheshire Cat](hammer://image/3) grins."))
	}

	@Test
	fun `markdown characters in the alt text are escaped`() {
		assertEquals("""*Cat \*sleeping\* \[1\]*""", preview("![Cat *sleeping* [1]](hammer://image/3)"))
	}

	@Test
	fun `an image without alt text uses the placeholder`() {
		assertEquals("*Image*", preview("![](hammer://image/3)"))
		assertEquals("A *Image* b", preview("A ![  ](hammer://image/3) b"))
	}

	@Test
	fun `short text is only trimmed`() {
		assertEquals("Short **bold** text", preview("Short **bold** text\n", maxLength = 256))
		assertEquals("x".repeat(256), preview("x".repeat(256), maxLength = 256))
	}

	@Test
	fun `no length limit never truncates`() {
		val long = "word ".repeat(500).trim()
		assertEquals(long, preview(long))
	}

	@Test
	fun `truncation measures the text after images are replaced`() {
		val text = "Alice met the cat. ![Cheshire Cat](hammer://image/3)"
		assertEquals("Alice met the cat. *Cheshire Cat*", preview(text, maxLength = 40))
	}

	@Test
	fun `truncation never splits a reference`() {
		val text = "word ".repeat(10) + "![Cheshire Cat](hammer://image/3) and more words after it"
		for (maxLength in 20..text.length) {
			val result = preview(text, maxLength)
			assertFalse(result.contains("hammer://"), "maxLength $maxLength: $result")
			assertFalse(result.contains("!["), "maxLength $maxLength: $result")
			assertFalse(result.contains("*Cheshire…"), "maxLength $maxLength: $result")
			assertTrue(result.length <= maxLength, "maxLength $maxLength: $result")
		}
	}

	@Test
	fun `truncation cuts at whitespace and ends in an ellipsis`() {
		assertEquals("alpha beta…", preview("alpha beta gamma delta", maxLength = 13))
		assertEquals("alpha beta gamma…", preview("alpha beta gamma delta", maxLength = 17))
	}

	@Test
	fun `truncation keeps a link whole`() {
		assertEquals("See…", preview("See [the long link text](https://e.com) now", maxLength = 20))
	}

	@Test
	fun `a single overlong word is cut at the limit`() {
		val result = preview("x".repeat(300), maxLength = 256)
		assertEquals("x".repeat(255) + "…", result)
	}
}
