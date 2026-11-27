import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HD_DROPDOWN_SEARCH_TAG
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HdHairlineDropdownTest {

	@get:Rule
	val compose = createComposeRule()

	private val cultures = listOf(
		"American", "Brazilian", "Chinese", "Dutch", "English", "Filipino",
		"French", "German", "Greek", "Indian", "Iranian", "Irish",
	)
	private var selected by mutableStateOf("")
	private val search get() = compose.onNodeWithTag(HD_DROPDOWN_SEARCH_TAG)

	private fun openDropdown(options: List<String>, initial: String = options.first()) {
		selected = initial
		compose.setContent {
			Box(modifier = Modifier.size(600.dp, 600.dp)) {
				HdHairlineDropdown(options = options, selected = selected, onSelect = { selected = it }, label = { it })
			}
		}
		compose.onNodeWithText(initial).performClick()
	}

	@Test
	fun `a long list opens on a focused search field and still lists everything`() {
		openDropdown(cultures)

		search.assertIsFocused()
		cultures.drop(1).forEach { compose.onNodeWithText(it).assertExists() }
	}

	@Test
	fun `typing narrows the options to the labels containing it, whatever the case`() {
		openDropdown(cultures)
		search.performTextInput("ira")

		compose.onNodeWithText("Iranian").assertExists()
		compose.onNodeWithText("Irish").assertDoesNotExist()
		compose.onNodeWithText("Iranian").performClick()
		compose.runOnIdle { assertEquals("Iranian", selected) }
	}

	@Test
	fun `enter takes the first match`() {
		openDropdown(cultures)
		search.performTextInput("gr")
		search.performImeAction()

		compose.runOnIdle { assertEquals("Greek", selected) }
	}

	@Test
	fun `enter with nothing typed closes the menu and keeps the selection`() {
		openDropdown(cultures, initial = "Greek")
		search.performImeAction()

		search.assertDoesNotExist()
		compose.runOnIdle { assertEquals("Greek", selected) }
	}

	@Test
	fun `the arrow keys move through the matches and enter takes the one they are on`() {
		openDropdown(cultures)
		search.performTextInput("ir")
		search.performKeyInput {
			pressKey(Key.DirectionDown)
			pressKey(Key.DirectionUp)
			pressKey(Key.DirectionDown)
		}
		search.performImeAction()

		compose.runOnIdle { assertEquals("Irish", selected) }
	}

	@Test
	fun `an arrow key with nothing typed starts at the first option`() {
		openDropdown(cultures, initial = "Greek")
		search.performKeyInput { pressKey(Key.DirectionDown) }
		search.performImeAction()

		compose.runOnIdle { assertEquals("American", selected) }
	}

	@Test
	fun `nothing matching says so, and enter leaves the menu open`() {
		openDropdown(cultures)
		search.performTextInput("zz")
		search.performImeAction()

		compose.onNodeWithText("No match").assertExists()
		compose.runOnIdle { assertEquals("American", selected) }
	}

	@Test
	fun `typing without accents finds an accented label`() {
		openDropdown(cultures + "São Toméan")
		search.performTextInput("sao tome")

		compose.onNodeWithText("São Toméan").assertExists()
		compose.onNodeWithText("Brazilian").assertDoesNotExist()
	}

	@Test
	fun `the menu opens with the selected option in view`() {
		val many = (1..40).map { "Option $it" }
		openDropdown(many, initial = "Option 38")

		compose.onAllNodesWithText("Option 38")[1].assertIsDisplayed()
	}

	@Test
	fun `reopening starts with nothing typed`() {
		openDropdown(cultures)
		search.performTextInput("dutch")
		compose.onNodeWithText("Dutch").performClick()
		compose.onNodeWithText("Dutch").performClick()

		compose.onNodeWithText("Irish").assertExists()
	}

	@Test
	fun `a short list has no search field`() {
		openDropdown(cultures.take(4))

		search.assertDoesNotExist()
		compose.onNodeWithText("Dutch").assertExists()
	}
}
