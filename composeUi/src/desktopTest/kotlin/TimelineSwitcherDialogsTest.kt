import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.StrResImpl
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_DELETE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_NEW_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_NEW_NAME_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_NEW_PRESET_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_RENAME_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_RENAME_NAME_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_SWITCHER_ERROR_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TimelineSwitcherDialogs
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TimelineSwitcherDialogsTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private data class Created(val name: String, val mode: TimelineMode, val preset: CalendarPreset)

	private val created = mutableListOf<Created>()
	private val renamed = mutableListOf<String>()
	private var deleteConfirmed = 0
	private var dismissed = 0
	private var switcher by mutableStateOf(TimelineSwitcher())

	private fun show(initial: TimelineSwitcher) {
		switcher = initial
		compose.setContent {
			// The real strings, so a refusal's reason can be read.
			KoinApplication(application = { modules(module { single<StrRes> { StrResImpl() } }) }) {
				TimelineSwitcherDialogs(
					switcher = switcher,
					onCreate = { name, mode, preset -> created += Created(name, mode, preset) },
					onRename = { renamed += it },
					onConfirmDelete = { deleteConfirmed++ },
					onDismiss = {
						dismissed++
						switcher = TimelineSwitcher()
					},
				)
			}
		}
		compose.waitForIdle()
	}

	@Test
	fun `A new timeline is manual until Dated is picked, which offers the calendar`() {
		show(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Create()))

		compose.onNodeWithText("New timeline").assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_NEW_CONFIRM_TAG).assertIsNotEnabled()
		compose.onNodeWithTag(TIME_LINE_NEW_PRESET_TAG).assertDoesNotExist()

		compose.onNodeWithTag(TIME_LINE_NEW_NAME_TAG).performTextInput("Before the evening")
		compose.onNodeWithText("DATED").performClick()
		compose.onNodeWithTag(TIME_LINE_NEW_PRESET_TAG).assertIsDisplayed()
		compose.onNodeWithText("GREGORIAN").performClick()
		compose.onNodeWithTag(TIME_LINE_NEW_CONFIRM_TAG).assertIsEnabled().performClick()

		assertEquals(listOf(Created("Before the evening", TimelineMode.DATED, CalendarPreset.GREGORIAN)), created)
	}

	@Test
	fun `A new manual timeline is created manual`() {
		show(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Create()))

		compose.onNodeWithTag(TIME_LINE_NEW_NAME_TAG).performTextInput("Scratch")
		compose.onNodeWithTag(TIME_LINE_NEW_CONFIRM_TAG).performClick()

		assertEquals(TimelineMode.MANUAL, created.single().mode)
	}

	@Test
	fun `A refused name says why under the field`() {
		show(
			TimelineSwitcher(
				dialog = TimelineSwitcher.Dialog.Create(name = "Main"),
				error = TimeLineEventError.TIMELINE_NAME_INVALID,
			)
		)

		compose.onNodeWithText("A timeline needs a name of up to 64 characters that no other timeline has", ignoreCase = true)
			.assertIsDisplayed()
	}

	@Test
	fun `Rename starts from the current name`() {
		show(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(timelineId = "backstory", name = "Backstory")))

		compose.onNodeWithText("Rename timeline").assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_RENAME_NAME_TAG).performTextClearance()
		compose.onNodeWithTag(TIME_LINE_RENAME_CONFIRM_TAG).assertIsNotEnabled()
		compose.onNodeWithTag(TIME_LINE_RENAME_NAME_TAG).performTextInput("Before the evening")
		compose.onNodeWithTag(TIME_LINE_RENAME_CONFIRM_TAG).performClick()

		assertEquals(listOf("Before the evening"), renamed)
	}

	@Test
	fun `Delete names the timeline and how many events go with it on every device`() {
		show(
			TimelineSwitcher(
				dialog = TimelineSwitcher.Dialog.Delete(timelineId = "backstory", name = "Before the evening", eventCount = 13)
			)
		)

		compose.onNodeWithText("Delete “Before the evening”?").assertIsDisplayed()
		compose.onNodeWithText("Its 13 events are deleted with it, on every synced device.").assertIsDisplayed()
		compose.onNodeWithTag(TIME_LINE_DELETE_CONFIRM_TAG).performClick()

		assertEquals(1, deleteConfirmed)
	}

	@Test
	fun `A refused delete says why`() {
		show(
			TimelineSwitcher(
				dialog = TimelineSwitcher.Dialog.Delete(timelineId = "gone", name = "Gone", eventCount = 1),
				error = TimeLineEventError.TIMELINE_NOT_FOUND,
			)
		)

		compose.onNodeWithTag(TIME_LINE_SWITCHER_ERROR_TAG).assertIsDisplayed()
		compose.onNodeWithText("Its 1 event is deleted with it, on every synced device.").assertIsDisplayed()
	}

	@Test
	fun `Cancel dismisses the dialog`() {
		show(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(timelineId = "backstory", name = "Backstory")))

		compose.onNodeWithText("Cancel").performClick()
		compose.waitForIdle()

		assertEquals(1, dismissed)
		compose.onNodeWithText("Rename timeline").assertDoesNotExist()
	}
}
