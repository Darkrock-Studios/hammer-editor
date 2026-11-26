package com.darkrockstudios.apps.hammer.common.commandpalette

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CheatSheet
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeCheatSheet(sections: List<CheatSheet.Section>, loaded: Boolean = true) : CheatSheet {
	override val state = MutableValue(CheatSheet.State(sections, loaded = loaded))
	var exits = 0

	override fun dismiss() {
		state.value = state.value.copy(open = false)
	}

	override fun onDismissed() {
		exits++
	}

	override fun setOnlyAvailable(only: Boolean) {
		state.value = state.value.copy(onlyAvailable = only)
	}
}

@OptIn(ExperimentalTestApi::class)
class CheatSheetUiTest {

	private val sections = listOf(
		CheatSheet.Section(
			"Navigation",
			listOf(
				CheatSheet.Row(ActionId("nav.notes"), "Go to Notes", listOf(KeyChord.mod(ChordKey.Digit3)), CheatSheet.Reach.Live),
				CheatSheet.Row(ActionId("nav.timeline"), "Go to Timeline", listOf(KeyChord.mod(ChordKey.Digit5)), CheatSheet.Reach.Dead),
			),
		),
		CheatSheet.Section(
			"Editing",
			listOf(CheatSheet.Row(ActionId("edit.bold"), "Bold", listOf(KeyChord.mod(ChordKey.B)), CheatSheet.Reach.Editor)),
		),
	)

	private fun SemanticsNodeInteractionsProvider.shows(text: String) =
		onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

	@Test
	fun `editor rows say they work while editing text`() = runComposeUiTest {
		setContent { CheatSheetUi(FakeCheatSheet(sections)) }
		waitForIdle()

		assertEquals(1, onAllNodesWithText("While editing text").fetchSemanticsNodes().size)
	}

	@Test
	fun `no rows show until the labels load`() = runComposeUiTest {
		setContent { CheatSheetUi(FakeCheatSheet(sections, loaded = false)) }
		waitForIdle()

		assertFalse(shows("Go to Notes"))
	}

	@Test
	fun `closing plays the exit before the sheet is told it has gone`() = runComposeUiTest {
		val fake = FakeCheatSheet(sections)
		setContent { CheatSheetUi(fake) }
		waitForIdle()
		mainClock.autoAdvance = false

		fake.dismiss()
		mainClock.advanceTimeByFrame()
		mainClock.advanceTimeByFrame()
		val shownDuringExit = shows("Keyboard shortcuts")
		val exitsDuringExit = fake.exits
		mainClock.advanceTimeBy(1_000)
		waitForIdle()

		assertTrue(shownDuringExit)
		assertEquals(0, exitsDuringExit)
		assertEquals(1, fake.exits)
		assertFalse(shows("Keyboard shortcuts"))
	}

	private val longSheet = listOf(
		CheatSheet.Section(
			"Navigation",
			(0 until 60).map { i ->
				CheatSheet.Row(ActionId("row.$i"), "Row $i", listOf(KeyChord.mod(ChordKey.Digit3)), CheatSheet.Reach.Live)
			},
		),
	)

	@Test
	fun `the sheet takes focus and the arrows scroll it`() = runComposeUiTest {
		setContent { CheatSheetUi(FakeCheatSheet(longSheet)) }
		waitForIdle()
		val topBefore = onNodeWithText("Row 0").getUnclippedBoundsInRoot().top

		onNodeWithTag(CHEAT_SHEET_SCROLL_TAG).assertIsFocused()
		onNodeWithTag(CHEAT_SHEET_SCROLL_TAG).performKeyInput { pressKey(Key.DirectionDown) }
		waitForIdle()

		assertTrue(onNodeWithText("Row 0").getUnclippedBoundsInRoot().top < topBefore)
	}

	@Test
	fun `End scrolls to the last row and Home back to the first`() = runComposeUiTest {
		setContent { CheatSheetUi(FakeCheatSheet(longSheet)) }
		waitForIdle()
		onNodeWithText("Row 59").assertIsNotDisplayed()

		onNodeWithTag(CHEAT_SHEET_SCROLL_TAG).performKeyInput { pressKey(Key.MoveEnd) }
		waitForIdle()
		onNodeWithText("Row 59").assertIsDisplayed()

		onNodeWithTag(CHEAT_SHEET_SCROLL_TAG).performKeyInput { pressKey(Key.MoveHome) }
		waitForIdle()
		onNodeWithText("Row 0").assertIsDisplayed()
	}

	@Test
	fun `the toggle hides what does nothing here`() = runComposeUiTest {
		setContent { CheatSheetUi(FakeCheatSheet(sections)) }
		waitForIdle()
		val deadShownBefore = shows("Go to Timeline")

		onNodeWithText("Only show what works here").performClick()
		waitForIdle()

		assertTrue(deadShownBefore)
		assertFalse(shows("Go to Timeline"))
		assertTrue(shows("Go to Notes"))
		assertTrue(shows("Bold"))
	}
}
