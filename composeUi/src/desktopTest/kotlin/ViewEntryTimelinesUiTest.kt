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
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelinePlacementLink
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
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

	private class FakeViewEntry(initial: ViewEntry.State) : ViewEntry {
		override val state: Value<ViewEntry.State> = MutableValue(initial)
		override val renameSlot: Value<ChildSlot<*, RenameEntry>> = MutableValue(ChildSlot<Any, RenameEntry>())

		val shown = mutableListOf<TimelinePlacementLink>()

		override fun showTimelinePlacement(placement: TimelinePlacementLink) {
			shown += placement
		}

		private val saved get() = EntryResult(EntryContainer(state.value.content!!), EntryError.NONE)
		override suspend fun loadEntryContent(entryDef: EntryDef) = state.value.content!!
		override suspend fun deleteEntry(entryDef: EntryDef) = true
		override suspend fun updateEntry(name: String, text: String, tags: Set<String>) = saved
		override suspend fun importImage(path: String) = ImageAddResult.Ok
		override fun addImageFromLibrary() {}
		override suspend fun removeImage(id: Int) = true
		override suspend fun setPrimaryImage(id: Int) = true
		override fun showImageAt(index: Int) {}
		override fun showDeleteEntryDialog() {}
		override fun closeDeleteEntryDialog() {}
		override fun showRemoveImageConfirm(id: Int) {}
		override fun dismissRemoveImageConfirm() {}
		override fun showAddImageDialog() {}
		override fun closeAddImageDialog() {}
		override fun startNameEdit() {}
		override fun startTextEdit() {}
		override fun finishNameEdit() {}
		override fun finishTextEdit() {}
		override fun discardEdit() {}
		override fun confirmClose() {}
		override fun dismissConfirmClose() {}
		override fun removeTag(tag: String) {}
		override fun showGlobalSearchForTag(tag: String) {}
		override fun startTagAdd() {}
		override suspend fun addTags(tagInput: String) {}
		override fun endTagAdd() {}
		override fun startAliasAdd() {}
		override fun endAliasAdd() {}
		override suspend fun addAlias(alias: String) = saved
		override fun removeAlias(alias: String) {}
		override suspend fun setExcludeFromDictionary(exclude: Boolean) = saved
		override fun navigateToAppearance(appearance: ViewEntry.Appearance) {}
		override fun showRename() {}
		override fun dismissRename() {}
		override fun suggestTags(prefix: String, limit: Int): List<String> = emptyList()
	}

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
