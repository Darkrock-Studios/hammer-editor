import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HD_COACH_MARK_TAG
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCoachMarkHost
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdCoachMarkAnchor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdCoachMarkAnchors
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HdCoachMarkTest {

	@get:Rule
	val compose = createComposeRule()

	private val scenes = listOf("Harbor at dawn", "The customs house", "A letter unsent", "Night market")
	private var dismissals = 0
	private var dismissedFrom = -1

	private fun show(activeScene: String?, dark: Boolean = false, anchorLast: Boolean = false) {
		var active by mutableStateOf(activeScene)
		compose.setContent {
			AppTheme(settings = GlobalSettings(projectsDirectory = "/projects"), useDarkTheme = dark) {
				val anchors = rememberHdCoachMarkAnchors()
				HdCoachMarkHost(
					anchors = anchors,
					activeKey = active,
					title = "Swipe a scene right",
					body = "Rename, move, archive, or delete it from the menu that opens.",
					dismissLabel = "Got it",
					onDismiss = {
						dismissals++
						active = null
					},
					modifier = Modifier.testTag(HOST).size(width = 360.dp, height = 420.dp)
						.background(MaterialTheme.colorScheme.background),
				) {
					Column {
						if (anchorLast) Box(Modifier.size(width = 360.dp, height = 220.dp))
						scenes.forEach { name ->
							Text(
								text = name,
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurface,
								modifier = Modifier
									.fillMaxWidth()
									.hdCoachMarkAnchor(anchors, name)
									.testTag(name)
									.padding(horizontal = 24.dp, vertical = 12.dp),
							)
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
						}
					}
				}
			}
		}
	}

	@Test
	fun `the callout sits below the element it points at`() {
		show(activeScene = "The customs house")

		val row = compose.onNodeWithTag("The customs house").getBoundsInRoot()
		val callout = compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed().getBoundsInRoot()

		assertTrue(callout.top >= row.bottom)
		writeRender("coach-mark-light")
	}

	@Test
	fun `the callout moves above an element near the bottom`() {
		show(activeScene = "Night market", anchorLast = true, dark = true)

		val row = compose.onNodeWithTag("Night market").getBoundsInRoot()
		val callout = compose.onNodeWithTag(HD_COACH_MARK_TAG).getBoundsInRoot()

		assertTrue(callout.bottom <= row.top)
		writeRender("coach-mark-dark-above")
	}

	@Test
	fun `the callout leads with its title`() {
		show(activeScene = "Harbor at dawn")

		compose.onNodeWithTag(HD_COACH_MARK_TAG).onChildren().onFirst().assertTextEquals("Swipe a scene right")
	}

	@Test
	fun `nothing is shown when no mark is active`() {
		show(activeScene = null)

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	@Test
	fun `the dismiss button closes the mark`() {
		show(activeScene = "Harbor at dawn")

		compose.onNodeWithText("Got it").performClick()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
		assertEquals(1, dismissals)
	}

	@Test
	fun `tapping the dimmed area closes the mark, tapping the callout does not`() {
		show(activeScene = "Harbor at dawn")

		compose.onNodeWithTag(HD_COACH_MARK_TAG).performTouchInput { click(Offset(4f, 4f)) }
		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed()

		compose.onNodeWithTag(HOST).performTouchInput { click(Offset(width - 4f, height - 4f)) }
		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	/** A mark over one tappable, draggable row; a change of [generation] recomposes the host with a new onDismiss. */
	private fun showInteractiveTarget(onTap: () -> Unit, onDrag: (Float) -> Unit, generation: () -> Int = { 0 }) {
		compose.setContent {
			AppTheme(settings = GlobalSettings(projectsDirectory = "/projects"), useDarkTheme = false) {
				val anchors = rememberHdCoachMarkAnchors()
				val gen = generation()
				var active by remember { mutableStateOf<String?>("row") }
				HdCoachMarkHost(
					anchors = anchors,
					activeKey = active,
					title = "Title",
					body = "Body",
					dismissLabel = "Got it",
					onDismiss = {
						dismissals++
						dismissedFrom = gen
						active = null
					},
					modifier = Modifier.testTag(HOST).size(width = 360.dp, height = 420.dp),
				) {
					Column {
						Box(Modifier.size(width = 360.dp, height = 60.dp))
						Box(
							Modifier
								.size(width = 360.dp, height = 48.dp)
								.hdCoachMarkAnchor(anchors, "row")
								.testTag("row")
								.draggable(rememberDraggableState(onDrag), Orientation.Horizontal)
								.clickable(onClick = onTap)
						)
					}
				}
			}
		}
	}

	@Test
	fun `the highlighted element still takes taps and drags, and the mark stays`() {
		var taps = 0
		var dragged = 0f
		showInteractiveTarget(onTap = { taps++ }, onDrag = { dragged += it })

		compose.onNodeWithTag("row").performClick()
		compose.onNodeWithTag("row").performTouchInput { swipeRight() }

		assertEquals(1, taps)
		assertTrue(dragged > 0f)
		assertEquals(0, dismissals)
		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed()
	}

	@Test
	fun `a tap on the dimmed area survives a recomposition mid press`() {
		var generation by mutableStateOf(0)
		showInteractiveTarget(onTap = {}, onDrag = {}, generation = { generation })

		compose.onNodeWithTag(HOST).performTouchInput { down(Offset(width - 4f, height - 4f)) }
		generation++
		compose.waitForIdle()
		compose.onNodeWithTag(HOST).performTouchInput { up() }

		assertEquals(1, dismissals)
		assertEquals(1, dismissedFrom)
		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	@Test
	fun `a mark whose element leaves composition is withdrawn`() {
		var anchorPresent by mutableStateOf(true)
		compose.setContent {
			AppTheme(settings = GlobalSettings(projectsDirectory = "/projects"), useDarkTheme = false) {
				val anchors = rememberHdCoachMarkAnchors()
				HdCoachMarkHost(
					anchors = anchors,
					activeKey = "row",
					title = "Title",
					body = "Body",
					dismissLabel = "Got it",
					onDismiss = {},
					modifier = Modifier.size(width = 360.dp, height = 420.dp),
				) {
					if (anchorPresent) {
						Text("Row", modifier = Modifier.hdCoachMarkAnchor(anchors, "row"))
					}
				}
			}
		}
		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed()

		anchorPresent = false
		compose.waitForIdle()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()
	}

	@Test
	fun `a callout with no room on either side shrinks rather than cover its element`() {
		compose.setContent {
			AppTheme(settings = GlobalSettings(projectsDirectory = "/projects"), useDarkTheme = false) {
				val anchors = rememberHdCoachMarkAnchors()
				HdCoachMarkHost(
					anchors = anchors,
					activeKey = "tall",
					title = "Swipe a scene right",
					body = "Rename, move, archive, or delete it from the menu that opens.",
					dismissLabel = "Got it",
					onDismiss = {},
					modifier = Modifier.testTag(HOST).size(width = 360.dp, height = 420.dp),
				) {
					Column {
						Box(Modifier.size(width = 360.dp, height = 90.dp))
						Box(Modifier.size(width = 360.dp, height = 240.dp).hdCoachMarkAnchor(anchors, "tall").testTag("tall"))
					}
				}
			}
		}

		val host = compose.onNodeWithTag(HOST).getBoundsInRoot()
		val element = compose.onNodeWithTag("tall").getBoundsInRoot()
		val callout = compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed().getBoundsInRoot()

		assertTrue(callout.bottom <= element.top || callout.top >= element.bottom)
		assertTrue(callout.top >= host.top && callout.bottom <= host.bottom)
	}

	@Test
	fun `an element re-keyed to the active mark is pointed at without moving`() {
		var key by mutableStateOf("other")
		compose.setContent {
			AppTheme(settings = GlobalSettings(projectsDirectory = "/projects"), useDarkTheme = false) {
				val anchors = rememberHdCoachMarkAnchors()
				HdCoachMarkHost(
					anchors = anchors,
					activeKey = "row",
					title = "Title",
					body = "Body",
					dismissLabel = "Got it",
					onDismiss = {},
					modifier = Modifier.size(width = 360.dp, height = 420.dp),
				) {
					Text("Row", modifier = Modifier.hdCoachMarkAnchor(anchors, key))
				}
			}
		}
		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertDoesNotExist()

		key = "row"
		compose.waitForIdle()

		compose.onNodeWithTag(HD_COACH_MARK_TAG).assertIsDisplayed()
	}

	/** Writes a PNG for eyeballing; only when asked, so normal runs leave no files behind. */
	private fun writeRender(name: String) {
		val dir = System.getenv("HAMMER_RENDER_DIR") ?: return
		val bitmap = compose.onNodeWithTag(HOST).captureToImage().asSkiaBitmap()
		val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
		File(dir).apply { mkdirs() }.resolve("$name.png").writeBytes(png)
	}

	private companion object {
		const val HOST = "host"
	}
}
