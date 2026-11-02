import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneMetadataPanelUi
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class SceneMetadataPanelUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val projectDef = fakeProjectDef()
	private val alice = EntryDef(projectDef, id = 3, type = EntryType.PERSON, name = "Alice")
	private val wonderland = EntryDef(projectDef, id = 7, type = EntryType.PLACE, name = "Wonderland")
	private val cheshireCat = EntryDef(projectDef, id = 9, type = EntryType.PERSON, name = "Cheshire Cat")

	private fun componentSetup(data: SceneMetadataPanel.State): SceneMetadataPanel {
		val component: SceneMetadataPanel = mockk(relaxed = true)

		val observer = slot<(SceneMetadataPanel.State) -> Unit>()
		val stateValue: Value<SceneMetadataPanel.State> = mockk()
		every { stateValue.subscribe(capture(observer)) } returns mockk<Cancellation>().apply {
			every { cancel() } just Runs
		}
		every { stateValue.value } returns data
		every { component.state } returns stateValue
		every { component.validateDraftName(any()) } returns true

		return component
	}

	private fun state(
		confirmedRefs: List<EntryDef> = emptyList(),
		dismissedRefs: List<EntryDef> = emptyList(),
	) = SceneMetadataPanel.State(
		sceneItem = SceneItem(projectDef, SceneItem.Type.Scene, id = 1, name = "Tea Party", order = 0),
		confirmedRefs = confirmedRefs,
		dismissedRefs = dismissedRefs,
	)

	@Test
	fun `Confirmed references render under their bucket labels`() {
		val component = componentSetup(state(confirmedRefs = listOf(wonderland, alice)))

		compose.setContent { SceneMetadataPanelUi(component = component, closeMetadata = {}) }

		val characters = compose.onNodeWithText("CHARACTERS").assertIsDisplayed().getUnclippedBoundsInRoot()
		val aliceChip = compose.onNodeWithText("Alice").assertIsDisplayed().getUnclippedBoundsInRoot()
		val locations = compose.onNodeWithText("LOCATIONS").assertIsDisplayed().getUnclippedBoundsInRoot()
		val wonderlandChip = compose.onNodeWithText("Wonderland").assertIsDisplayed().getUnclippedBoundsInRoot()

		assertTrue(characters.bottom <= aliceChip.top, "Alice sits under CHARACTERS")
		assertTrue(aliceChip.bottom <= locations.top, "LOCATIONS follows the Alice chip")
		assertTrue(locations.bottom <= wonderlandChip.top, "Wonderland sits under LOCATIONS")
	}

	@Test
	fun `Clicking a confirmed chip opens its entry`() {
		val component = componentSetup(state(confirmedRefs = listOf(alice, wonderland)))

		compose.setContent { SceneMetadataPanelUi(component = component, closeMetadata = {}) }

		compose.onNodeWithText("Wonderland").performClick()

		verify(exactly = 1) { component.navigateToEntry(wonderland) }
		verify(exactly = 0) { component.dismissReference(any()) }
	}

	@Test
	fun `A confirmed chip's action dismisses that reference`() {
		val component = componentSetup(state(confirmedRefs = listOf(alice, wonderland)))

		compose.setContent { SceneMetadataPanelUi(component = component, closeMetadata = {}) }

		// Buckets render Characters before Locations, so the second action is Wonderland's.
		compose.onAllNodesWithContentDescription("Dismiss")[1].performClick()

		verify(exactly = 1) { component.dismissReference(wonderland.id) }
		verify(exactly = 0) { component.navigateToEntry(any()) }
	}

	@Test
	fun `A dismissed reference shows in the Dismissed section and can be restored`() {
		val component = componentSetup(state(confirmedRefs = listOf(alice), dismissedRefs = listOf(cheshireCat)))

		compose.setContent { SceneMetadataPanelUi(component = component, closeMetadata = {}) }

		compose.onNodeWithText("Cheshire Cat").assertDoesNotExist()

		compose.onNodeWithText("DISMISSED").performScrollTo().performClick()

		compose.onNodeWithText("Cheshire Cat").performScrollTo().assertIsDisplayed()
		compose.onNodeWithContentDescription("Restore").performScrollTo().performClick()

		verify(exactly = 1) { component.restoreDismissedReference(cheshireCat.id) }
		verify(exactly = 0) { component.dismissReference(any()) }
	}
}
