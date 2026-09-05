import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineWordListField
import com.darkrockstudios.apps.hammer.common.spellcheck.normalizeDictionaryWord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val FIELD_TAG = "word-list-field"

@OptIn(ExperimentalTestApi::class)
class HdHairlineWordListFieldTest {

	@get:Rule
	val compose = createComposeRule()

	private lateinit var words: List<String>

	private fun showField(initial: List<String> = emptyList()) {
		compose.setContent {
			var current by remember { mutableStateOf(initial) }
			words = current
			Box(modifier = Modifier.size(600.dp, 300.dp)) {
				HdHairlineWordListField(
					label = "PROJECT DICTIONARY",
					words = current,
					onAdd = { current = current + it },
					onRemove = { current = current - it },
					parseInput = ::normalizeDictionaryWord,
					testTag = FIELD_TAG,
				)
			}
		}
	}

	@Test
	fun `space commits the typed word`() {
		showField()

		compose.onNodeWithTag(FIELD_TAG).performTextInput("Kvothe ")
		compose.waitForIdle()

		assertEquals(listOf("Kvothe"), words)
	}

	@Test
	fun `enter commits the typed word`() {
		showField()

		compose.onNodeWithTag(FIELD_TAG).performTextInput("Denna")
		compose.onNodeWithTag(FIELD_TAG).performKeyInput { pressKey(Key.Enter) }
		compose.waitForIdle()

		assertEquals(listOf("Denna"), words)
	}

	@Test
	fun `a word already in the list is not added twice`() {
		showField(listOf("Kvothe"))

		compose.onNodeWithTag(FIELD_TAG).performTextInput("Kvothe ")
		compose.waitForIdle()

		assertEquals(listOf("Kvothe"), words)
	}

	@Test
	fun `input the parser rejects is not committed`() {
		showField()

		compose.onNodeWithTag(FIELD_TAG).performTextInput("a".repeat(65) + " ")
		compose.waitForIdle()

		assertEquals(emptyList<String>(), words)
	}

	@Test
	fun `a pasted list commits the complete words and keeps the partial one as draft`() {
		showField()

		compose.onNodeWithTag(FIELD_TAG).performTextInput("Kvothe Denna")
		compose.waitForIdle()
		assertEquals(listOf("Kvothe"), words)

		compose.onNodeWithTag(FIELD_TAG).performKeyInput { pressKey(Key.Enter) }
		compose.waitForIdle()
		assertEquals(listOf("Kvothe", "Denna"), words)
	}

	@Test
	fun `a word already present in another case is not added`() {
		showField(listOf("Kvothe"))

		compose.onNodeWithTag(FIELD_TAG).performTextInput("kvothe ")
		compose.waitForIdle()

		assertEquals(listOf("Kvothe"), words)
	}

	@Test
	fun `a rejected word stays in the draft after a separator`() {
		showField()
		val tooLong = "a".repeat(65)

		compose.onNodeWithTag(FIELD_TAG).performTextInput("$tooLong ")
		compose.waitForIdle()

		assertEquals(emptyList<String>(), words)
		compose.onNodeWithTag(FIELD_TAG).assertTextEquals(tooLong)
	}

	@Test
	fun `backspace on an empty draft removes the last word`() {
		showField(listOf("Kvothe", "Denna"))

		compose.onNodeWithTag(FIELD_TAG).performClick()
		compose.onNodeWithTag(FIELD_TAG).performKeyInput { pressKey(Key.Backspace) }
		compose.waitForIdle()

		assertEquals(listOf("Kvothe"), words)
	}
}
