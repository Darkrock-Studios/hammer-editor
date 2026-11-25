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

	private fun newPalette() = CommandPaletteComponent(context, registry) { events += "dismissed" }

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

		palette.run(HammerActions.ItemNew.id)

		assertEquals(listOf("dismissed", "item.new"), events)
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
	fun `Esc closes the palette and lifts its barrier`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		newPalette()
		context.resume()
		advanceUntilIdle()

		val closed = registry.dismissTopOverlay()

		assertTrue(closed)
		assertEquals(listOf("dismissed"), events)
		assertFalse(registry.hasOverlay())
		assertTrue(registry.dispatch(modN))
	}

	@Test
	fun `closing an already closed palette does nothing more`() = runTest(mainTestDispatcher) {
		registerNotesAndNewItem()
		val palette = newPalette()
		context.resume()
		advanceUntilIdle()

		palette.run(HammerActions.NavNotes.id)
		palette.dismiss()

		assertEquals(listOf("dismissed", "nav.notes"), events)
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
