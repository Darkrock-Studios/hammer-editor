import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.darkrockstudios.apps.hammer.common.help.HELP_ALL_TOPICS_TAG
import com.darkrockstudios.apps.hammer.common.help.HELP_PLAYLIST_URL
import com.darkrockstudios.apps.hammer.common.help.HELP_WATCH_ALL_TAG
import com.darkrockstudios.apps.hammer.common.help.HelpScreen
import com.darkrockstudios.apps.hammer.common.help.HelpDialogContent
import com.darkrockstudios.apps.hammer.common.help.HelpSection
import com.darkrockstudios.apps.hammer.common.help.HelpTopic
import com.darkrockstudios.apps.hammer.common.help.HelpAllTopicsContent
import com.darkrockstudios.apps.hammer.common.help.helpTopicTag
import com.darkrockstudios.apps.hammer.common.help.helpVideoTag
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class HelpUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val opened = mutableListOf<String>()
	private val uriHandler = object : UriHandler {
		override fun openUri(uri: String) {
			opened += uri
		}
	}

	private fun show(content: @Composable () -> Unit) {
		compose.setContent {
			CompositionLocalProvider(LocalUriHandler provides uriHandler) { content() }
		}
	}

	private fun text(topic: HelpTopic) = runBlocking { getString(topic.title) }

	@Test
	fun `Global help lists every section and every topic`() {
		show { HelpAllTopicsContent(onClose = {}) }

		HelpSection.entries.forEach { section ->
			compose.onNodeWithText(runBlocking { getString(section.title) }).assertExists()
		}
		HelpTopic.entries.forEach { topic ->
			compose.onNodeWithTag(helpTopicTag(topic)).assertExists()
			compose.onNodeWithText(text(topic)).assertExists()
		}
	}

	@Test
	fun `Watch the video opens the topic's YouTube video`() {
		show { HelpAllTopicsContent(onClose = {}) }

		compose.onNodeWithTag(helpVideoTag(HelpTopic.Notes)).performScrollTo().performClick()

		assertEquals(listOf("https://www.youtube.com/watch?v=1HVm853lin4"), opened)
	}

	@Test
	fun `A topic without a video has no video link`() {
		show { HelpAllTopicsContent(onClose = {}) }

		compose.onNodeWithTag(helpTopicTag(HelpTopic.Timelines)).assertExists()
		compose.onAllNodesWithTag(helpVideoTag(HelpTopic.Timelines)).assertCountEquals(0)
	}

	@Test
	fun `Watch all tutorials opens the playlist`() {
		show { HelpAllTopicsContent(onClose = {}) }

		compose.onNodeWithTag(HELP_WATCH_ALL_TAG).performClick()

		assertEquals(listOf(HELP_PLAYLIST_URL), opened)
	}

	@Test
	fun `Scenes help shows only its four topics`() {
		show { HelpDialogContent(screen = HelpScreen.Scenes, onClose = {}) }

		val shown = HelpTopic.entries.filter {
			compose.onAllNodesWithTag(helpTopicTag(it)).fetchSemanticsNodes().isNotEmpty()
		}
		assertEquals(HelpScreen.Scenes.topics, shown)
	}

	@Test
	fun `All help topics lists every topic in the dialog`() {
		show { HelpDialogContent(screen = HelpScreen.Scenes, onClose = {}) }

		compose.onNodeWithTag(HELP_ALL_TOPICS_TAG).performClick()

		HelpTopic.entries.forEach { compose.onNodeWithTag(helpTopicTag(it)).assertExists() }
		compose.onAllNodesWithTag(HELP_ALL_TOPICS_TAG).assertCountEquals(0)
	}
}
