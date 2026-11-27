import com.darkrockstudios.apps.hammer.common.util.containsFolded
import com.darkrockstudios.apps.hammer.common.util.foldedForSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFoldingTest {

	@Test
	fun `case and Latin accents fold away`() {
		assertEquals("sao tome", "São Tomé".foldedForSearch())
		assertEquals("francais", "FRANÇAIS".foldedForSearch())
		assertEquals("lodz", "Łódź".foldedForSearch())
		assertEquals("strase", "Straße".foldedForSearch())
	}

	@Test
	fun `text with nothing to fold is only lower-cased`() {
		assertEquals("plain text 123!", "Plain Text 123!".foldedForSearch())
		assertEquals("море", "Море".foldedForSearch())
		assertEquals("", "".foldedForSearch())
	}

	@Test
	fun `a needle is found whichever side carries the accents`() {
		assertTrue("Português".containsFolded("portugues"))
		assertTrue("Portugues".containsFolded("PORTUGUÊS"))
		assertTrue("anything".containsFolded(""))
		assertFalse("Português".containsFolded("espanol"))
	}
}
