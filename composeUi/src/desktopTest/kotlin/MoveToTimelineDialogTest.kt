import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.timeline.MOVE_TO_TIMELINE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.MOVE_TO_TIMELINE_ERROR_TAG
import com.darkrockstudios.apps.hammer.common.timeline.MOVE_TO_TIMELINE_NEEDS_DATE_TAG
import com.darkrockstudios.apps.hammer.common.timeline.MOVE_TO_TIMELINE_START_TAG
import com.darkrockstudios.apps.hammer.common.timeline.MoveToTimelineDialog
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.StrResImpl
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveToTimelineDialogTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private data class Move(val target: String, val start: StoryDate?, val end: StoryDate?)

	private val backstory = TimelineDef(
		id = "backstory",
		name = "Before the evening",
		mode = TimelineMode.DATED,
		calendar = CalendarDef(CalendarPreset.GREGORIAN),
	)
	private val scratch = TimelineDef(id = "scratch", name = "Scratch")

	private val moves = mutableListOf<Move>()

	/** Answers each move with the next of [answers], then with success. */
	private fun show(vararg answers: TimeLineEventError) {
		val pending = answers.toMutableList()
		compose.setContent {
			// The real strings, so a refusal's reason can be read.
			KoinApplication(application = { modules(module { single<StrRes> { StrResImpl() } }) }) {
				MoveToTimelineDialog(
					visible = true,
					targets = listOf(backstory, scratch),
					onMove = { target, start, end ->
						moves += Move(target, start, end)
						pending.removeFirstOrNull() ?: TimeLineEventError.NONE
					},
					onDismiss = {},
				)
			}
		}
		compose.waitForIdle()
	}

	private fun pickTarget(name: String) {
		compose.onNodeWithText(backstory.name).performClick()
		compose.onAllNodesWithText(name).onLast().performClick()
		compose.waitForIdle()
	}

	@Test
	fun `The picked target is moved to with the event's own dates`() {
		show()

		pickTarget(scratch.name)
		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		assertEquals(listOf(Move("scratch", null, null)), moves)
		compose.onNodeWithTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG).assertDoesNotExist()
	}

	@Test
	fun `An event without a date is asked for one on the dated target, and the retry carries it`() {
		assertDateAskedThenCarried(TimeLineEventError.DATE_REQUIRED)
	}

	@Test
	fun `An event whose date does not fit the target is asked for one there, and the retry carries it`() {
		assertDateAskedThenCarried(TimeLineEventError.DATE_INVALID)
	}

	private fun assertDateAskedThenCarried(refusal: TimeLineEventError) {
		show(refusal)
		compose.onNodeWithTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG).assertDoesNotExist()

		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		compose.onNodeWithTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG).assertIsDisplayed()
		compose.onNodeWithTag(MOVE_TO_TIMELINE_ERROR_TAG).assertDoesNotExist()
		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).assertIsNotEnabled()

		compose.onNodeWithTag("$MOVE_TO_TIMELINE_START_TAG-year").performTextInput("1893")
		compose.waitForIdle()
		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).assertIsEnabled().performClick()
		compose.waitForIdle()

		assertEquals(2, moves.size)
		assertEquals(Move("backstory", null, null), moves[0])
		assertEquals("backstory", moves[1].target)
		assertEquals(1893L, moves[1].start?.get("year"))
		assertNull(moves[1].end)
	}

	@Test
	fun `Picking another target drops the date asked for the first`() {
		show(TimeLineEventError.DATE_REQUIRED)
		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).performClick()
		compose.waitForIdle()
		compose.onNodeWithTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG).assertIsDisplayed()

		pickTarget(scratch.name)

		compose.onNodeWithTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG).assertDoesNotExist()
		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).assertIsEnabled()
	}

	@Test
	fun `A refusal other than the date says why without asking for one`() {
		show(TimeLineEventError.DUPLICATE_PLACEMENT)

		compose.onNodeWithTag(MOVE_TO_TIMELINE_CONFIRM_TAG).performClick()
		compose.waitUntil { compose.onAllNodesWithTag(MOVE_TO_TIMELINE_ERROR_TAG).fetchSemanticsNodes().isNotEmpty() }

		compose.onNodeWithTag(MOVE_TO_TIMELINE_ERROR_TAG).assertTextEquals("THAT IS ALREADY ON THIS TIMELINE")
		compose.onNodeWithTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG).assertDoesNotExist()
	}
}
