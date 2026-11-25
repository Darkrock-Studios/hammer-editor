package com.darkrockstudios.apps.hammer.common.commandpalette

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionId
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistration
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.PaletteEntry
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CommandPalette
import com.darkrockstudios.apps.hammer.common.compose.actions.ProvideActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Holds a barrier on [registry] through its exit and runs past it, as the real palette does. */
private class FakeCommandPalette(
	entries: List<PaletteEntry>,
	private val registry: ActionRegistry = ActionRegistry(),
	loaded: Boolean = true,
) : CommandPalette {
	override val state = MutableValue(CommandPalette.State(entries, loaded = loaded))
	val ran = mutableListOf<ActionId>()
	var dismissed = 0
	var exits = 0

	private var barrier: ActionRegistration? =
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = ::dismiss)

	override fun run(id: ActionId) {
		if (!state.value.open) return
		dismiss()
		barrier?.unregister()
		ran += id
		registry.invoke(id)
		barrier = registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = {})
	}

	override fun dismiss() {
		if (!state.value.open) return
		state.value = state.value.copy(open = false)
		dismissed++
	}

	override fun onDismissed() {
		barrier?.unregister()
		barrier = null
		exits++
	}
}

@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {

	private val entries = listOf(
		PaletteEntry(ActionId("nav.notes"), "Go to Notes", "Navigation", KeyChord.mod(ChordKey.Digit3)),
		PaletteEntry(ActionId("item.new"), "New item", "Items", KeyChord.mod(ChordKey.N)),
		PaletteEntry(ActionId("project.saveAll"), "Save all", "Project", null),
	)

	private fun SemanticsNodeInteractionsProvider.searchField() = onNode(hasSetTextAction())

	private fun SemanticsNodeInteractionsProvider.rowLabels(): List<String> =
		entries.map { it.label }.filter { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

	@Test
	fun `the search field has focus on open`() = runComposeUiTest {
		val fake = FakeCommandPalette(entries)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		onNode(isFocused() and hasSetTextAction()).performTextInput("save")
		waitForIdle()

		searchField().assertIsFocused()
		assertEquals(listOf("Save all"), rowLabels())
	}

	@Test
	fun `Down then Enter runs the second result`() = runComposeUiTest {
		val fake = FakeCommandPalette(entries)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		searchField().performKeyInput {
			pressKey(Key.DirectionDown)
			pressKey(Key.Enter)
		}
		waitForIdle()

		assertEquals(listOf(entries[1].id), fake.ran)
	}

	@Test
	fun `Enter runs the top result`() = runComposeUiTest {
		val fake = FakeCommandPalette(entries)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		searchField().performTextInput("no")
		searchField().performKeyInput { pressKey(Key.Enter) }
		waitForIdle()

		assertEquals(listOf(entries[0].id), fake.ran)
	}

	@Test
	fun `typing ranks with the fuzzy filter`() = runComposeUiTest {
		val fake = FakeCommandPalette(
			listOf(
				PaletteEntry(ActionId("a"), "New outline item", "Items", null),
				PaletteEntry(ActionId("b"), "Go to Notes", "Navigation", null),
			)
		)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		searchField().performTextInput("no")
		searchField().performKeyInput { pressKey(Key.Enter) }
		waitForIdle()

		assertEquals(listOf(ActionId("b")), fake.ran)
	}

	@Test
	fun `clicking a row runs it`() = runComposeUiTest {
		val fake = FakeCommandPalette(entries)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		onNode(hasText("Save all") and hasClickAction()).performClick()
		waitForIdle()

		assertEquals(listOf(entries[2].id), fake.ran)
	}

	@Test
	fun `nothing shows until the commands load`() = runComposeUiTest {
		val fake = FakeCommandPalette(emptyList(), loaded = false)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		val emptyShown = onAllNodesWithText("No matching commands").fetchSemanticsNodes().isNotEmpty()

		assertFalse(emptyShown)
	}

	@Test
	fun `once loaded with nothing live, the empty label shows`() = runComposeUiTest {
		val fake = FakeCommandPalette(emptyList())
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		val emptyShown = onAllNodesWithText("No matching commands").fetchSemanticsNodes().isNotEmpty()

		assertTrue(emptyShown)
	}

	@Test
	fun `Enter before the commands load does nothing`() = runComposeUiTest {
		val fake = FakeCommandPalette(emptyList(), loaded = false)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()

		searchField().performKeyInput { pressKey(Key.Enter) }
		waitForIdle()

		assertTrue(fake.ran.isEmpty())
		assertEquals(0, fake.dismissed)
	}

	@Test
	fun `closing plays the exit before the palette is told it has gone`() = runComposeUiTest {
		val fake = FakeCommandPalette(entries)
		setContent { CommandPaletteUi(fake) }
		waitForIdle()
		mainClock.autoAdvance = false

		fake.dismiss()
		mainClock.advanceTimeByFrame()
		mainClock.advanceTimeByFrame()
		val shownDuringExit = onAllNodesWithText("Commands").fetchSemanticsNodes().isNotEmpty()
		val exitsDuringExit = fake.exits
		mainClock.advanceTimeBy(1_000)
		waitForIdle()

		assertTrue(shownDuringExit)
		assertEquals(0, exitsDuringExit)
		assertEquals(1, fake.exits)
		assertTrue(onAllNodesWithText("Commands").fetchSemanticsNodes().isEmpty())
	}

	@Test
	fun `Esc dismisses`() = runComposeUiTest {
		val registry = ActionRegistry()
		val fake = FakeCommandPalette(entries, registry)
		setContent { ProvideActions(registry) { CommandPaletteUi(fake) } }
		waitForIdle()

		val closed = registry.dismissTopOverlay()
		waitForIdle()

		assertTrue(closed)
		assertEquals(1, fake.dismissed)
		assertEquals(1, fake.exits)
		assertTrue(fake.ran.isEmpty())
		assertFalse(registry.hasOverlay())
	}

	@Test
	fun `the dialog does not block the action it runs`() = runComposeUiTest {
		val registry = ActionRegistry()
		var created = 0
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler { created++ }))
		val fake = FakeCommandPalette(entries, registry)
		setContent { ProvideActions(registry) { CommandPaletteUi(fake) } }
		waitForIdle()

		searchField().performTextInput("new")
		searchField().performKeyInput { pressKey(Key.Enter) }
		waitForIdle()

		assertEquals(1, created)
	}
}
