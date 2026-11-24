import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.intro.IntroUi
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.preview.intro.FakeIntro
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.app_name
import com.darkrockstudios.apps.hammer.intro_welcome_subtitle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.test.assertTrue
import com.darkrockstudios.apps.hammer.intro_back
import com.darkrockstudios.apps.hammer.intro_get_started
import com.darkrockstudios.apps.hammer.intro_next
import com.darkrockstudios.apps.hammer.intro_skip
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IntroUiTest {
	@get:Rule
	val compose = createComposeRule()

	private val next = runBlocking { getString(Res.string.intro_next) }
	private val back = runBlocking { getString(Res.string.intro_back) }
	private val skip = runBlocking { getString(Res.string.intro_skip) }
	private val getStarted = runBlocking { getString(Res.string.intro_get_started) }

	/** Text that identifies [slide] on screen: its title, or its body when the drawing carries the heading. */
	private fun title(slide: Intro.Slide) = runBlocking { getString(slide.title ?: slide.body) }

	private fun show(intro: Intro) {
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				IntroUi(intro)
			}
		}
	}

	@Test
	fun `Next and Back move between slides`() {
		val intro = FakeIntro()
		show(intro)

		compose.onNodeWithText(next).performClick()
		compose.waitForIdle()
		compose.onNodeWithText(title(Intro.Slide.Structure)).assertIsDisplayed()

		compose.onNodeWithText(back).performClick()
		compose.waitForIdle()
		compose.onNodeWithText(title(Intro.Slide.Welcome)).assertIsDisplayed()
		assertEquals(0, intro.state.value.slide)
	}

	@Test
	fun `Skip is on every slide but the last`() {
		val intro = FakeIntro()
		show(intro)

		repeat(Intro.Slide.entries.lastIndex) {
			compose.onNodeWithText(skip).assertIsDisplayed()
			compose.onNodeWithText(next).performClick()
			compose.waitForIdle()
		}

		compose.onNodeWithText(skip).assertDoesNotExist()
	}

	@Test
	fun `Skip leaves the intro`() {
		val intro = FakeIntro(slide = 2)
		show(intro)

		compose.onNodeWithText(skip).performClick()

		assertEquals(1, intro.left)
	}

	@Test
	fun `The last slide's button finishes the intro`() {
		val intro = FakeIntro(slide = Intro.Slide.entries.lastIndex)
		show(intro)

		compose.onNodeWithText(next).assertDoesNotExist()
		compose.onNodeWithText(getStarted).performClick()

		assertEquals(1, intro.left)
	}

	@Test
	fun `Every slide's title renders`() {
		val intro = FakeIntro()
		show(intro)
		Intro.Slide.entries.forEach { slide ->
			intro.showSlide(slide.ordinal)
			compose.waitForIdle()
			compose.onNodeWithText(title(slide)).assertIsDisplayed()
		}
	}

	@Test
	fun `Two quick Next clicks land on the third slide and stay there`() {
		val intro = FakeIntro()
		show(intro)
		compose.mainClock.autoAdvance = false

		compose.onNodeWithText(next).performClick()
		compose.mainClock.advanceTimeBy(100)
		compose.onNodeWithText(next).performClick()
		compose.mainClock.autoAdvance = true
		compose.waitForIdle()

		assertEquals(2, intro.state.value.slide)
		compose.onNodeWithText(title(Intro.Slide.World)).assertIsDisplayed()
	}

	@Test
	fun `A mouse drag turns the slide and the component follows`() {
		val intro = FakeIntro()
		show(intro)

		compose.onRoot().performMouseInput {
			moveTo(center)
			press()
			moveBy(Offset(-40f, 0f))
			moveBy(Offset(-width * 0.4f, 0f))
			release()
		}
		compose.waitForIdle()

		assertEquals(1, intro.state.value.slide)
		compose.onNodeWithText(title(Intro.Slide.Structure)).assertIsDisplayed()
	}

	@Test
	fun `Swiping back while Next is still turning the page leaves the component on the first slide`() {
		val intro = FakeIntro()
		show(intro)
		compose.mainClock.autoAdvance = false

		compose.onNodeWithText(next).performClick()
		compose.mainClock.advanceTimeByFrame()
		compose.onRoot().performTouchInput {
			down(center)
			moveBy(Offset(width * 0.1f, 0f))
			moveBy(Offset(width * 0.3f, 0f))
			up()
		}
		compose.mainClock.autoAdvance = true
		compose.waitForIdle()

		assertEquals(0, intro.state.value.slide)
		compose.onNodeWithText(back).assertDoesNotExist()
	}

	@Test
	fun `The arrow keys move between slides, and Right does not leave from the last`() {
		val intro = FakeIntro()
		show(intro)

		compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
		compose.waitForIdle()
		val afterRight = intro.state.value.slide
		compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
		compose.waitForIdle()
		val afterLeft = intro.state.value.slide
		intro.showSlide(Intro.Slide.entries.lastIndex)
		compose.waitForIdle()
		compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
		compose.waitForIdle()

		assertEquals(1, afterRight)
		assertEquals(0, afterLeft)
		assertEquals(0, intro.left)
	}

	@Test
	fun `The welcome slide's drawing is its heading`() {
		show(FakeIntro())
		val appName = runBlocking { getString(Res.string.app_name) }

		compose.onNode(hasContentDescription(appName) and isHeading()).assertIsDisplayed()
	}

	@Test
	fun `The welcome subtitle drawn in the title page reaches screen readers`() {
		show(FakeIntro())
		val subtitle = runBlocking { getString(Res.string.intro_welcome_subtitle) }

		compose.onNode(hasContentDescription(subtitle)).assertExists()
	}

	@Test
	fun `The title page dominates a mid-sized desktop window`() = assertTitlePageWidth(840.dp, 670.dp, atLeast = 600.dp)

	@Test
	fun `The title page dominates a short wide window`() = assertTitlePageWidth(1024.dp, 640.dp, atLeast = 560.dp)

	@Test
	fun `The title page stands upright on a tall phone`() {
		val page = titlePageIn(412.dp, 915.dp)

		assertTrue(page.height > page.width, "title page is ${page.width} by ${page.height}")
		assertTrue(page.width <= 412.dp && page.height <= 915.dp)
		assertTrue(page.width >= 340.dp, "title page is only ${page.width} wide")
	}

	private fun titlePageIn(width: Dp, height: Dp): DpRect {
		val appName = runBlocking { getString(Res.string.app_name) }
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				Box(Modifier.requiredSize(width, height)) {
					IntroUi(FakeIntro())
				}
			}
		}
		return compose.onNode(hasContentDescription(appName) and isHeading()).getBoundsInRoot()
	}

	private fun assertTitlePageWidth(width: Dp, height: Dp, atLeast: Dp) {
		val page = titlePageIn(width, height)

		assertTrue(page.width >= atLeast, "title page is ${page.width} wide in a $width by $height window")
		assertTrue(page.width <= width && page.height <= height)
	}
}
