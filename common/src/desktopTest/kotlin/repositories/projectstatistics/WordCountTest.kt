package repositories.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.countWords
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WordCountTest {

	@Test
	fun `empty string is zero`() {
		assertEquals(0, countWords(""))
	}

	@Test
	fun `whitespace only is zero`() {
		assertEquals(0, countWords("   "))
		assertEquals(0, countWords("\n\n\t"))
	}

	@Test
	fun `single word is one`() {
		assertEquals(1, countWords("hello"))
	}

	@Test
	fun `two words separated by space`() {
		assertEquals(2, countWords("hello world"))
	}

	@Test
	fun `leading and trailing whitespace does not change count`() {
		assertEquals(2, countWords("  hello   world  "))
	}

	@Test
	fun `newline counts as a separator`() {
		assertEquals(4, countWords("line one\nline two"))
	}

	@Test
	fun `CRLF counts as a single separator`() {
		assertEquals(2, countWords("hello\r\nworld"))
	}

	@Test
	fun `mixed whitespace runs collapse to single separator`() {
		assertEquals(3, countWords("one \t two\n\n three"))
	}

	@Test
	fun `a non-breaking space separates words`() {
		assertEquals(2, countWords("hello world"))
	}
}
