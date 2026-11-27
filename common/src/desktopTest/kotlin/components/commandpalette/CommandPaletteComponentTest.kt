package components.commandpalette

import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.PaletteEntry
import com.darkrockstudios.apps.hammer.common.components.commandpalette.CommandPaletteComponent
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import utils.KeyStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommandPaletteComponentTest : ComponentTest() {

	private val modN = KeyChord.mod(ChordKey.N)

	private lateinit var registry: ActionRegistry
	private val events = mutableListOf<String>()

	@BeforeEach
	override fun setup() {
		super.setup()
		setupComponentKoin(module { single<StrRes> { KeyStrRes() } })
		registry = ActionRegistry()
		events.clear()
	}

	private fun registerNotesAndNewItem() {
		registry.register(
			ActionLayer.Root,
			mapOf(
				HammerActions.NavNotes.id to ActionHandler { events += "nav.notes" },
				HammerActions.PaletteOpen.id to ActionHandler { events += "palette.open" },
			),
		)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler { events += "item.new" }))
	}

	private fun newPalette() = CommandPaletteComponent(context, registry) { events += "gone" }

	private fun CommandPaletteComponent.recordClosing() = state.subscribe { if (!it.open) events += "closing" }

	@Test
	fun `the palette is not loaded until its labels resolve`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()

		val palette = newPalette()
		val loadedAtOpen = palette.state.value.loaded
		context.resume()
		advanceUntilIdle()

		assertFalse(loadedAtOpen)
		assertTrue(palette.state.value.loaded)
		assertEquals(2, palette.state.value.entries.size)
	}

	@Test
	fun `the palette lists live actions only`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()

		val palette = newPalette()
		context.resume()
		advanceUntilIdle()

		assertEquals(
			listOf(
				PaletteEntry(
					HammerActions.NavNotes.id,
					"<action_nav_notes>",
					"<action_category_navigation>",
					KeyChord.mod(ChordKey.Digit3),
				),
				PaletteEntry(
					HammerActions.ItemNew.id,
					"<action_item_new>",
					"<action_category_items>",
					KeyChord.mod(ChordKey.N),
				),
			),
			palette.state.value.entries,
		)
	}

	@Test
	fun `the palette does not list itself`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()

		val palette = newPalette()
		context.resume()
		advanceUntilIdle()

		assertFalse(palette.state.value.entries.any { it.id == HammerActions.PaletteOpen.id })
	}

	@Test
	fun `running an action closes the palette then runs it`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()
		palette.recordClosing()

		palette.run(HammerActions.ItemNew.id)

		assertEquals(listOf("closing", "item.new"), events)
	}

	@Test
	fun `the palette leaves only once its exit has played`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()

		palette.dismiss()
		val goneBeforeExit = "gone" in events
		palette.onDismissed()

		assertFalse(goneBeforeExit)
		assertFalse(palette.state.value.open)
		assertEquals(listOf("gone"), events)
	}

	@Test
	fun `while open, chords below the palette are blocked`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		newPalette()
		context.resume()
		advanceUntilIdle()

		val handled = registry.dispatch(modN)

		assertFalse(handled)
		assertTrue(events.isEmpty())
	}

	@Test
	fun `Esc closes the palette and its barrier lifts once the exit has played`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()

		val closed = registry.dismissTopOverlay()
		val openAfterEsc = palette.state.value.open
		palette.onDismissed()

		assertTrue(closed)
		assertFalse(openAfterEsc)
		assertFalse(registry.hasOverlay())
		assertTrue(registry.dispatch(modN))
	}

	@Test
	fun `Esc during the exit is taken and does nothing more`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()
		palette.recordClosing()
		registry.dismissTopOverlay()

		val overlayDuringExit = registry.hasOverlay()
		val secondEsc = registry.dismissTopOverlay()

		assertTrue(overlayDuringExit)
		assertTrue(secondEsc)
		assertEquals(listOf("closing"), events)
	}

	@Test
	fun `closing an already closed palette does nothing more`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()
		palette.recordClosing()

		palette.run(HammerActions.NavNotes.id)
		palette.dismiss()
		palette.run(HammerActions.ItemNew.id)

		assertEquals(listOf("closing", "nav.notes"), events)
	}

	@Test
	fun `the exit is ignored while the palette is open`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()

		palette.onDismissed()

		assertTrue(events.isEmpty())
		assertTrue(registry.hasOverlay())
	}

	@Test
	fun `a destroyed palette lifts its barrier`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		newPalette()
		context.resume()
		advanceUntilIdle()

		context.destroy()

		assertFalse(registry.hasOverlay())
	}
}
