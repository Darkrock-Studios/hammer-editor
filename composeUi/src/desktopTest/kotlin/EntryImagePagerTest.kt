import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_ADD_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_COUNTER_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_PAGER_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_REMOVE_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_SET_PRIMARY_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.EntryImagePager
import com.darkrockstudios.apps.hammer.common.encyclopedia.entryImagePageTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class EntryImagePagerTest {
	@get:Rule
	val compose = createComposeRule()

	private val images = listOf(4, 7, 9).map { id ->
		ViewEntry.EntryImage(id = id, name = "Image $id", path = "/missing/$id.png", hash = "h$id")
	}

	private var added = 0
	private val primaryRequests = mutableListOf<Int>()
	private val removeRequests = mutableListOf<Int>()

	private fun setPager(images: List<ViewEntry.EntryImage>, startIndex: Int = 0) {
		compose.setContent {
			var current by mutableIntStateOf(startIndex)
			EntryImagePager(
				entryName = "Alice",
				images = images,
				currentIndex = current,
				onShowIndex = { current = it },
				onAdd = { added++ },
				onFilesDropped = {},
				onSetPrimary = { primaryRequests += it },
				onRemove = { removeRequests += it },
				modifier = Modifier.size(width = 240.dp, height = 320.dp),
			)
		}
	}

	@Test
	fun `An entry without images offers to add one`() {
		setPager(emptyList())

		compose.onNodeWithTag(ENTRY_IMAGE_PAGER_TAG).assertDoesNotExist()
		compose.onNodeWithTag(ENTRY_IMAGE_ADD_TAG).performClick()

		assertEquals(1, added)
	}

	@Test
	fun `The primary image shows the count and can be removed but not made primary`() {
		setPager(images)

		compose.onNodeWithTag(entryImagePageTag(4)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("1 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_SET_PRIMARY_TAG).assertDoesNotExist()
		compose.onNodeWithTag(ENTRY_IMAGE_REMOVE_TAG).performClick()

		assertEquals(listOf(4), removeRequests)
	}

	@Test
	fun `A later image can be made primary`() {
		setPager(images, startIndex = 2)

		compose.onNodeWithTag(entryImagePageTag(9)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("3 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_SET_PRIMARY_TAG).performClick()

		assertEquals(listOf(9), primaryRequests)
	}

	@Test
	fun `Swiping moves to the next image`() {
		setPager(images)

		compose.onNodeWithTag(ENTRY_IMAGE_PAGER_TAG).performTouchInput { swipeLeft() }
		compose.waitForIdle()

		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("2 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_REMOVE_TAG).performClick()
		assertEquals(listOf(7), removeRequests)
	}

	@Test
	fun `A single image has no counter`() {
		setPager(images.take(1))

		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertDoesNotExist()
		compose.onNodeWithTag(ENTRY_IMAGE_ADD_TAG).performClick()
		assertEquals(1, added)
	}
}
