import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.markdownPreview
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownPreviewTest {

	private val names = mapOf(3 to "Cheshire Cat")

	private fun preview(markdown: String, maxLength: Int? = null) =
		markdownPreview(markdown, maxLength, "Image", names::get)

	@Test
	fun `a block image becomes its name in italics`() {
		assertEquals(
			"Before\n\n*Cheshire Cat*\n\nAfter",
			preview("Before\n\n![image](hammer://image/3)\n\nAfter\n"),
		)
	}

	@Test
	fun `an image inside prose becomes its name in italics`() {
		assertEquals("The *Cheshire Cat* grins.", preview("The ![image](hammer://image/3) grins."))
		assertEquals("See *Cheshire Cat*.", preview("See ![x][c].\n\n[c]: hammer://image/3\n"))
	}

	@Test
	fun `an image never shows its alt text`() {
		assertEquals("*Cheshire Cat*", preview("![Old name](hammer://image/3)"))
		assertEquals("*Cheshire Cat*", preview("![](hammer://image/3)"))
	}

	@Test
	fun `an image whose id names no image uses the placeholder`() {
		assertEquals("*Image* *Image*", preview("![Old name](hammer://image/9) ![image](hammer://image/9)"))
		assertEquals("*Image*", markdownPreview("![a](hammer://image/3)", null, "Image") { "  " })
	}

	@Test
	fun `markdown characters in the name are escaped`() {
		val preview = markdownPreview("![image](hammer://image/3)", null, "Image") { "Cat_*1* [x]" }
		assertEquals("""*Cat\_\*1\* \[x\]*""", preview)
	}

	@Test
	fun `an ampersand in the name is escaped so it never starts an entity`() {
		val preview = markdownPreview("![image](hammer://image/3)", null, "Image") { "&copy; Cat" }
		assertEquals("""*\&copy; Cat*""", preview)
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
		val text = "Alice met the cat. ![image](hammer://image/3)"
		assertEquals("Alice met the cat. *Cheshire Cat*", preview(text, maxLength = 40))
	}

	@Test
	fun `truncation never splits a reference`() {
		val text = "word ".repeat(10) + "![image](hammer://image/3) and more words after it"
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
