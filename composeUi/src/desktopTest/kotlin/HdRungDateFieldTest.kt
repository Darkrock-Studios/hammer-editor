import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdRungDateField
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val FIELD = "rung-date"

class HdRungDateFieldTest {

	@get:Rule
	val compose = createComposeRule()

	private var emitted: StoryDate? = null

	private fun show(calendar: CalendarDef, initial: StoryDate? = null) {
		emitted = initial
		compose.setContent {
			var value by remember { mutableStateOf(initial) }
			Box(modifier = Modifier.width(600.dp)) {
				HdRungDateField(
					label = "Start",
					calendar = calendar,
					value = value,
					onValueChange = {
						value = it
						emitted = it
					},
					testTag = FIELD,
				)
			}
		}
	}

	@Test
	fun `typing a year and a day emits the date`() {
		show(CalendarDef())

		compose.onNodeWithTag("$FIELD-year").performTextInput("12")
		compose.onNodeWithTag("$FIELD-day").performTextInput("40")
		compose.waitForIdle()

		assertEquals(StoryDate(mapOf("year" to 12L, "day" to 40L)), emitted)
		compose.onNodeWithTag("$FIELD-shows").assertTextEquals("Year 12 · Day 40")
	}

	@Test
	fun `a day out of range shows the problem and emits nothing`() {
		show(CalendarDef())

		compose.onNodeWithTag("$FIELD-year").performTextInput("12")
		compose.onNodeWithTag("$FIELD-day").performTextInput("400")
		compose.waitForIdle()

		assertNull(emitted)
		compose.onNodeWithText("DAY 400 IS OUT OF RANGE", substring = true, ignoreCase = true).assertExists()
	}

	@Test
	fun `a gap between rungs is not a date`() {
		show(CalendarDef())

		compose.onNodeWithTag("$FIELD-year").performTextInput("12")
		compose.onNodeWithTag("$FIELD-hour").performTextInput("6")
		compose.waitForIdle()

		assertNull(emitted)
		compose.onNodeWithText("without gaps", substring = true, ignoreCase = true).assertExists()
	}

	@Test
	fun `the before-epoch toggle negates the coarsest rung`() {
		show(CalendarDef(epochName = "Landing"))

		compose.onNodeWithTag("$FIELD-year").performTextInput("3")
		compose.onNodeWithTag("$FIELD-before").performClick()
		compose.waitForIdle()

		assertEquals(StoryDate(mapOf("year" to -3L)), emitted)
		compose.onNodeWithTag("$FIELD-shows").assertTextEquals("Year 3 before Landing")

		compose.onNodeWithTag("$FIELD-before").performClick()
		compose.waitForIdle()
		assertEquals(StoryDate(mapOf("year" to 3L)), emitted)
	}

	@Test
	fun `a stored negative date shows its magnitude with the toggle on`() {
		show(CalendarDef(epochName = "Landing"), StoryDate(mapOf("year" to -3L, "day" to 10L)))

		compose.onNodeWithTag("$FIELD-year").assertTextEquals("3")
		compose.onNodeWithTag("$FIELD-shows").assertTextEquals("Year 3 before Landing · Day 10")
	}

	@Test
	fun `a story told in hours starts at the hour`() {
		show(CalendarDef(coarsestUnit = "hour"))

		compose.onNodeWithTag("$FIELD-year").assertDoesNotExist()
		compose.onNodeWithTag("$FIELD-hour").performTextInput("30")
		compose.waitForIdle()

		assertEquals(StoryDate(mapOf("hour" to 30L)), emitted)
	}

	@Test
	fun `a gregorian date shows its month by name`() {
		show(CalendarDef(CalendarPreset.GREGORIAN))

		compose.onNodeWithTag("$FIELD-year").performTextInput("1802")
		compose.onNodeWithTag("$FIELD-month").performTextInput("5")
		compose.onNodeWithTag("$FIELD-day").performTextInput("12")
		compose.waitForIdle()

		compose.onNodeWithTag("$FIELD-shows").assertTextEquals("12 May 1802")
	}

	@Test
	fun `clearing every rung emits null`() {
		show(CalendarDef(), StoryDate(mapOf("year" to 5L)))

		compose.onNodeWithTag("$FIELD-year").performTextClearance()
		compose.waitForIdle()

		assertNull(emitted)
	}
}
