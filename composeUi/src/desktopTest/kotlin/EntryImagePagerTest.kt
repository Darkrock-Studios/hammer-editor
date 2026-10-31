import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntry
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_ADD_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_CAPTION_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_COUNTER_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_LIBRARY_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENTRY_IMAGE_MENU_TAG
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
	private var fromLibrary = 0
	private val opened = mutableListOf<Int>()
	private val primaryRequests = mutableListOf<Int>()
	private val removeRequests = mutableListOf<Int>()

	private fun setPager(
		images: List<ViewEntry.EntryImage>,
		startIndex: Int = 0,
		followSwipes: Boolean = true,
	) {
		compose.setContent {
			var current by remember { mutableIntStateOf(startIndex) }
			EntryImagePager(
				entryName = "Alice",
				images = images,
				currentIndex = current,
				onShowIndex = { if (followSwipes) current = it },
				onAdd = { added++ },
				onAddFromLibrary = { fromLibrary++ },
				onOpenImage = { opened += it },
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
		compose.onNodeWithTag(ENTRY_IMAGE_LIBRARY_TAG).performClick()

		assertEquals(1, added)
		assertEquals(1, fromLibrary)
	}

	@Test
	fun `The bar names the figure, with the counter only for several images`() {
		setPager(images.take(1))
		compose.onNodeWithTag(ENTRY_IMAGE_CAPTION_TAG).assertTextEquals("FIG. I")
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertDoesNotExist()
	}

	@Test
	fun `Images can be added from the library`() {
		setPager(images)

		compose.onNodeWithTag(ENTRY_IMAGE_MENU_TAG).performClick()
		compose.onNodeWithTag(ENTRY_IMAGE_LIBRARY_TAG).performClick()

		assertEquals(1, fromLibrary)
		assertEquals(0, added)
		compose.onNodeWithTag(ENTRY_IMAGE_LIBRARY_TAG).assertDoesNotExist()
	}

	@Test
	fun `Tapping an image opens it at its page`() {
		setPager(images, startIndex = 2)

		compose.onNodeWithTag(entryImagePageTag(9)).performClick()

		assertEquals(listOf(2), opened)
	}

	@Test
	fun `The primary image shows the count and can be removed but not made primary`() {
		setPager(images)

		compose.onNodeWithTag(entryImagePageTag(4)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("1 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_MENU_TAG).performClick()
		compose.onNodeWithTag(ENTRY_IMAGE_SET_PRIMARY_TAG).assertDoesNotExist()
		compose.onNodeWithTag(ENTRY_IMAGE_REMOVE_TAG).performClick()

		assertEquals(listOf(4), removeRequests)
	}

	@Test
	fun `A later image can be made primary`() {
		setPager(images, startIndex = 2)

		compose.onNodeWithTag(entryImagePageTag(9)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("3 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_MENU_TAG).performClick()
		compose.onNodeWithTag(ENTRY_IMAGE_SET_PRIMARY_TAG).performClick()

		assertEquals(listOf(9), primaryRequests)
	}

	@Test
	fun `Swiping moves to the next image`() {
		setPager(images)

		compose.onNodeWithTag(ENTRY_IMAGE_PAGER_TAG).performTouchInput { swipeLeft() }
		compose.waitForIdle()

		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("2 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_MENU_TAG).performClick()
		compose.onNodeWithTag(ENTRY_IMAGE_REMOVE_TAG).performClick()
		assertEquals(listOf(7), removeRequests)
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `Dragging with the mouse moves to the next image`() {
		setPager(images)

		compose.onNodeWithTag(ENTRY_IMAGE_PAGER_TAG).performMouseInput {
			press()
			repeat(10) { moveBy(Offset(-12f, 0f), delayMillis = 16) }
			release()
		}
		compose.waitForIdle()

		compose.onNodeWithTag(entryImagePageTag(7)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("2 / 3")
		assertEquals(emptyList(), opened)
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `A short mouse drag settles back on the same image`() {
		setPager(images)

		compose.onNodeWithTag(ENTRY_IMAGE_PAGER_TAG).performMouseInput {
			press()
			moveBy(Offset(-30f, 0f), delayMillis = 500)
			moveBy(Offset(0f, 0f), delayMillis = 500)
			release()
		}
		compose.waitForIdle()

		compose.onNodeWithTag(entryImagePageTag(4)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("1 / 3")
		assertEquals(emptyList(), opened)
	}

	@OptIn(ExperimentalTestApi::class)
	@Test
	fun `Clicking an image with the mouse opens it`() {
		setPager(images, startIndex = 1)

		compose.onNodeWithTag(entryImagePageTag(7)).performMouseInput { click() }

		assertEquals(listOf(1), opened)
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("2 / 3")
	}

	@Test
	fun `The bar acts on the current index, not the pager's page`() {
		setPager(images, followSwipes = false)

		compose.onNodeWithTag(ENTRY_IMAGE_PAGER_TAG).performTouchInput { swipeLeft() }
		compose.waitForIdle()

		compose.onNodeWithTag(entryImagePageTag(7)).assertIsDisplayed()
		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertTextEquals("1 / 3")
		compose.onNodeWithTag(ENTRY_IMAGE_MENU_TAG).performClick()
		compose.onNodeWithTag(ENTRY_IMAGE_SET_PRIMARY_TAG).assertDoesNotExist()
		compose.onNodeWithTag(ENTRY_IMAGE_REMOVE_TAG).performClick()
		assertEquals(listOf(4), removeRequests)
	}

	@Test
	fun `A single image has no counter`() {
		setPager(images.take(1))

		compose.onNodeWithTag(ENTRY_IMAGE_COUNTER_TAG).assertDoesNotExist()
		compose.onNodeWithTag(ENTRY_IMAGE_MENU_TAG).performClick()
		compose.onNodeWithTag(ENTRY_IMAGE_ADD_TAG).performClick()
		assertEquals(1, added)
	}
}
