package components.actions

import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistration
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.ChordRepeats
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordRepeatsTest {

	private val registry = ActionRegistry()
	private val repeats = ChordRepeats()

	private val modK = KeyChord.mod(ChordKey.K)
	private val modW = KeyChord.mod(ChordKey.W)
	private val modPageDown = KeyChord.mod(ChordKey.PageDown)

	private fun press(key: Any, chord: KeyChord?, isRepeat: Boolean = false) =
		repeats.press(registry, key, chord, isRepeat)

	@Test
	fun `a held toggle runs once and its repeats are taken`() {
		var toggles = 0
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler { toggles++ }))

		assertTrue(press("K", modK))
		assertTrue(press("K", modK, isRepeat = true))
		assertTrue(press("K", modK, isRepeat = true))

		assertEquals(1, toggles)
	}

	@Test
	fun `a repeat is taken as its press was, after the press took its handler away`() {
		var closes = 0
		lateinit var registration: ActionRegistration
		registration = registry.register(
			ActionLayer.Item,
			mapOf(HammerActions.ItemClose.id to ActionHandler { closes++; registration.unregister() }),
		)

		assertTrue(press("W", modW))
		assertTrue(press("W", modW, isRepeat = true))

		assertEquals(1, closes)
	}

	@Test
	fun `a repeat of a press nothing took is passed on, even once something would take it`() {
		assertFalse(press("K", modK))
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler {}))

		assertFalse(press("K", modK, isRepeat = true))
	}

	@Test
	fun `a held step runs on every repeat`() {
		var steps = 0
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNext.id to ActionHandler { steps++ }))

		press("PageDown", modPageDown)
		press("PageDown", modPageDown, isRepeat = true)
		press("PageDown", modPageDown, isRepeat = true)

		assertEquals(3, steps)
	}

	@Test
	fun `a fresh press of the same key runs again`() {
		var toggles = 0
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler { toggles++ }))

		press("K", modK)
		press("K", modK)

		assertEquals(2, toggles)
	}

	@Test
	fun `a repeat of a key whose press was missed runs as a press`() {
		var toggles = 0
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler { toggles++ }))

		press("Ctrl", null)
		assertTrue(press("K", modK, isRepeat = true))

		assertEquals(1, toggles)
	}

	@Test
	fun `only the step actions repeat`() {
		val repeating = HammerActions.all.filter { it.repeats }.map { it.id }.toSet()

		assertEquals(
			setOf(
				HammerActions.PaneNext.id,
				HammerActions.PanePrev.id,
				HammerActions.ItemNext.id,
				HammerActions.ItemPrev.id,
				HammerActions.StoryNextGroup.id,
				HammerActions.StoryPrevGroup.id,
			),
			repeating,
		)
	}
}
