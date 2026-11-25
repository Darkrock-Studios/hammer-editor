import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.encyclopedia.BrowseEntriesUi
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRIES_GRID_TAG
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private val keyboardEntries = listOf(
	EntryContent(id = 1, name = "Alice", type = EntryType.PERSON, text = "Curious.", tags = emptySet()),
	EntryContent(id = 2, name = "Bob", type = EntryType.PERSON, text = "Patient.", tags = emptySet()),
	EntryContent(id = 3, name = "The Burrow", type = EntryType.PLACE, text = "Deep.", tags = emptySet()),
)

private class KeyboardFakeBrowseEntries : BrowseEntries {
	private val defs = keyboardEntries.map { it.toDef(fakeProjectDef()) }
	override val state: Value<BrowseEntries.State> = MutableValue(BrowseEntries.State(entryDefs = defs))
	override val filterText: Value<String> = MutableValue("")
	override val tagIndex: Value<TagIndex> = MutableValue(TagIndex(emptyMap(), emptyMap()))
	override fun updateFilter(text: String?, type: EntryType?) {}
	override fun getFilteredEntries(): List<EntryDef> = defs
	override suspend fun loadEntryContent(entryDef: EntryDef): EntryContent = keyboardEntries.first { it.id == entryDef.id }
	override suspend fun loadEntryImage(entry: EntryContent): ImageFile? = null
	override fun addTagToSearch(tag: String) {}
	override fun clearFilterText() {}
}

class BrowseEntriesKeyboardTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private val viewed = mutableListOf<Int>()

	@OptIn(ExperimentalSharedTransitionApi::class)
	private fun show() {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.requiredSize(500.dp, 900.dp)) {
					SharedTransitionLayout {
						AnimatedVisibility(visible = true) {
							BrowseEntriesUi(
								component = KeyboardFakeBrowseEntries(),
								scope = scope,
								viewEntry = { viewed += it.id },
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
	fun `Enter opens the entry under the cursor`() {
		show()

		compose.onNodeWithTag(ENTRIES_GRID_TAG).requestFocus()
		compose.onNodeWithTag(ENTRIES_GRID_TAG).performKeyInput {
			pressKey(Key.DirectionDown)
			pressKey(Key.DirectionRight)
			pressKey(Key.Enter)
		}
		compose.waitForIdle()

		assertEquals(listOf(2), viewed)
	}
}
