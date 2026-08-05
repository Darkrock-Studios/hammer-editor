import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val FIELD_TAG = "tag-field"

class HdHairlineTagFieldTest {

	@get:Rule
	val compose = createComposeRule()

	private lateinit var tags: List<String>

	private fun showField(suggestions: List<String>) {
		compose.setContent {
			var current by remember { mutableStateOf(emptyList<String>()) }
			tags = current
			Box(modifier = Modifier.size(600.dp, 300.dp)) {
				HdHairlineTagField(
					label = "TAGS",
					tags = current,
					onTagsChange = { current = it },
					suggestTags = { prefix -> suggestions.filter { it.startsWith(prefix) } },
					testTag = FIELD_TAG,
				)
			}
		}
	}

	@Test
	fun `picking a suggestion adds the suggested tag, not the typed prefix`() {
		showField(listOf("shorts", "shortstory"))

		compose.onNodeWithTag(FIELD_TAG).performTextInput("short")
		compose.onNodeWithText("shortstory").performClick()
		compose.waitForIdle()

		assertEquals(listOf("shortstory"), tags)
	}

	@Test
	fun `picking a suggestion leaves focus in the field`() {
		showField(listOf("shortstory"))

		compose.onNodeWithTag(FIELD_TAG).performTextInput("short")
		compose.onNodeWithText("shortstory").performClick()
		compose.waitForIdle()

		compose.onNodeWithTag(FIELD_TAG).assertIsFocused()
	}

	@Test
	fun `picking a suggestion keeps tags typed before it`() {
		showField(listOf("shortstory"))

		compose.onNodeWithTag(FIELD_TAG).performTextInput("novel short")
		compose.onNodeWithText("shortstory").performClick()
		compose.waitForIdle()

		assertEquals(listOf("novel", "shortstory"), tags)
	}
}
