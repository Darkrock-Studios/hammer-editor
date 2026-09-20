import com.darkrockstudios.apps.hammer.common.utils.addToDictionaryMenuItems
import com.darkrockstudios.texteditor.CharLineOffset
import com.darkrockstudios.texteditor.TextEditorRange
import com.darkrockstudios.texteditor.spellcheck.SpellCheckItem
import com.darkrockstudios.texteditor.spellcheck.api.Correction
import com.darkrockstudios.texteditor.state.WordSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class AddToDictionaryMenuTest {

	private val range = TextEditorRange(CharLineOffset(0, 0), CharLineOffset(0, 5))
	private fun word(text: String) = SpellCheckItem.MisspelledWord(WordSegment(text, range))

	@Test
	fun `a misspelled word gets one item that adds the normalized word`() {
		var added: String? = null
		val items = addToDictionaryMenuItems("Add") { added = it }(word(" Kvothe "))

		assertEquals(listOf("Add"), items.map { it.label })
		items.single().onClick()
		assertEquals("Kvothe", added)
	}

	@Test
	fun `a word the dictionary cannot store gets no item`() {
		val items = addToDictionaryMenuItems("Add") {}(word("a".repeat(65)))

		assertEquals(emptyList<String>(), items.map { it.label })
	}

	@Test
	fun `a sentence issue gets no item`() {
		val issue = SpellCheckItem.SentenceIssue(Correction(range, "in the", emptyList()))
		val items = addToDictionaryMenuItems("Add") {}(issue)

		assertEquals(emptyList<String>(), items.map { it.label })
	}
}
