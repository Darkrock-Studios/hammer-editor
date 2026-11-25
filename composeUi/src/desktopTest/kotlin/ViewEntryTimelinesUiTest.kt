import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelinePlacementLink
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.encyclopedia.ViewEntryUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.timeline.TIMELINE_PLACEMENTS_TAG
import com.darkrockstudios.apps.hammer.common.timeline.timelinePlacementChipTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ViewEntryTimelinesUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val content = EntryContent(id = 3, name = "The Long Silence", type = EntryType.EVENT, text = "Quiet.", tags = emptySet())
	private val onMain = TimelinePlacementLink(eventId = 7, timelineName = "Main", label = "The evening")
	private val onBackstory = TimelinePlacementLink(eventId = 12, timelineName = "Backstory")

	private fun entry(timelines: List<TimelinePlacementLink>) = FakeViewEntry(
		ViewEntry.State(
			entryDef = EntryDef(fakeProjectDef(), id = content.id, type = content.type, name = content.name),
			content = content,
			timelines = timelines,
		)
	)

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun show(component: ViewEntry) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.requiredSize(900.dp, 700.dp)) {
					SharedTransitionLayout {
						AnimatedVisibility(visible = true) {
							ViewEntryUi(
								component = component,
								scope = scope,
								rootSnackbar = rememberRootSnackbarHostState(),
								closeEntry = {},
								sharedTransitionScope = this@SharedTransitionLayout,
								animatedVisibilityScope = this@AnimatedVisibility,
							)
						}
					}
				}
			}
		}
		compose.waitForIdle()
	}

	@Test
	fun `An entry on no timeline has no Timelines zone`() {
		show(entry(timelines = emptyList()))

		compose.onNodeWithText("Appears In", substring = true, ignoreCase = true).assertExists()
		compose.onNodeWithTag(TIMELINE_PLACEMENTS_TAG).assertDoesNotExist()
	}

	@Test
	fun `An entry on two timelines shows a chip for each and a chip opens its placement`() {
		val component = entry(timelines = listOf(onMain, onBackstory))
		show(component)

		compose.onNodeWithTag(TIMELINE_PLACEMENTS_TAG).performScrollTo().assertIsDisplayed()
		compose.onNodeWithText("Main · The evening").assertIsDisplayed()
		compose.onNodeWithTag(timelinePlacementChipTag(onBackstory.eventId)).assertIsDisplayed()

		compose.onNodeWithText("Backstory").performScrollTo().performClick()

		assertEquals(listOf(onBackstory), component.shown)
	}
}
