import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchableListDialogContent
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projecthome.AuthorField
import com.darkrockstudios.apps.hammer.common.projectselection.SyncLogList
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class PickerAndLogScrollTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `Filtering a picker opened on a late selection shows the first match`() {
		val items = (0 until 40).map { "Match %02d".format(it) } + (0 until 40).map { "Other %02d".format(it) }
		var query by mutableStateOf("")
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				HdSearchableListDialogContent(
					title = "Pick",
					query = query,
					onQueryChange = { query = it },
					items = items,
					itemLabel = { it },
					onSelect = {},
					onDismissRequest = {},
					closeContentDescription = "Close",
					itemKey = { it },
					isSelected = { it == "Match 39" },
				)
			}
		}
		compose.onNodeWithText("Match 39").assertIsDisplayed()

		query = "Match"
		compose.waitForIdle()

		compose.onNodeWithText("Match 00").assertIsDisplayed()
	}

	@Test
	fun `A late save echo does not replace what is being typed`() {
		var initial by mutableStateOf("ab")
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				AuthorField(initial = initial, onChange = {})
			}
		}
		compose.onNodeWithText("ab").performClick()
		compose.onNodeWithText("ab").performTextInput("c")
		compose.onNodeWithText("abc").assertIsDisplayed()

		initial = "a"
		compose.waitForIdle()

		compose.onNodeWithText("abc").assertIsDisplayed()
	}

	@Test
	fun `The sync log follows new lines only while the reader is at the tail`() {
		var log by mutableStateOf(messages(0 until 50))
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				Box(Modifier.width(400.dp).height(300.dp)) {
					SyncLogList(log = log, showProjectName = false, horizontalPadding = 0.dp)
				}
			}
		}
		compose.onNodeWithText("line 49").assertIsDisplayed()

		log = log + messages(50 until 100)
		compose.waitForIdle()
		compose.onNodeWithText("line 99").assertIsDisplayed()

		compose.onNode(hasScrollToIndexAction()).performScrollToIndex(55)
		log = log + messages(100 until 101)
		compose.waitForIdle()

		compose.onNodeWithText("line 55").assertIsDisplayed()
	}

	private fun messages(range: IntRange): List<SyncLogMessage> = range.map {
		SyncLogMessage(
			message = "line $it",
			level = SyncLogLevel.INFO,
			projectName = null,
			timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000L + it * 1000L),
		)
	}
}
