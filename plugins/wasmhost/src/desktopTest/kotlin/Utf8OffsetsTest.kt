import com.darkrockstudios.apps.hammer.plugins.wasmhost.utf16Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Utf8OffsetsTest {
	@Test
	fun `byte offsets map to UTF-16 offsets across one to four byte characters`() {
		val text = "aé€😀b"
		assertEquals(listOf(0, 1, 2, 3, 5, 6), listOf(0, 1, 3, 6, 10, 11).map { utf16Offset(text, it) })
	}

	@Test
	fun `offsets inside a character or past the end have none`() {
		assertNull(utf16Offset("é", 1))
		assertNull(utf16Offset("😀", 2))
		assertNull(utf16Offset("ab", 3))
		assertNull(utf16Offset("ab", -1))
	}
}
