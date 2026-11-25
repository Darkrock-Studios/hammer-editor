package components.actions

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.start
import com.arkivanov.essenty.lifecycle.stop
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.registerOverlayWhile
import com.darkrockstudios.apps.hammer.common.components.actions.registerWhileStarted
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionRegistryTest {

	private val registry = ActionRegistry()

	private fun counting(counter: IntArray, index: Int = 0) = ActionHandler { counter[index]++ }

	private val modN = KeyChord.mod(ChordKey.N)

	@Test
	fun `an unbound chord is not handled`() {
		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.J)))
	}

	@Test
	fun `a bound chord with no handler is not handled`() {
		assertFalse(registry.dispatch(modN))
	}

	@Test
	fun `a registered handler runs on its chord`() {
		val runs = IntArray(1)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs)))

		val handled = registry.dispatch(modN)

		assertTrue(handled)
		assertEquals(1, runs[0])
	}

	@Test
	fun `the higher layer wins`() {
		val runs = IntArray(2)
		registry.register(ActionLayer.Item, mapOf(HammerActions.ItemNew.id to counting(runs, 1)))
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))

		registry.dispatch(modN)

		assertEquals(listOf(0, 1), runs.toList())
	}

	@Test
	fun `within a layer the most recent registration wins`() {
		val runs = IntArray(2)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 1)))

		registry.dispatch(modN)

		assertEquals(listOf(0, 1), runs.toList())
	}

	@Test
	fun `unregistering restores the lower handler`() {
		val runs = IntArray(2)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))
		val item = registry.register(ActionLayer.Item, mapOf(HammerActions.ItemNew.id to counting(runs, 1)))

		item.unregister()
		item.unregister()
		registry.dispatch(modN)

		assertEquals(listOf(1, 0), runs.toList())
	}

	@Test
	fun `a disabled handler falls through to the next registration`() {
		val runs = IntArray(2)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))
		registry.register(
			ActionLayer.Item,
			mapOf(HammerActions.ItemNew.id to ActionHandler(isEnabled = { false }) { runs[1]++ }),
		)

		registry.dispatch(modN)

		assertEquals(listOf(1, 0), runs.toList())
	}

	@Test
	fun `a disabled handler with nothing below is not handled`() {
		registry.register(
			ActionLayer.Module,
			mapOf(HammerActions.ItemNew.id to ActionHandler(isEnabled = { false }) {}),
		)

		assertFalse(registry.dispatch(modN))
		assertFalse(registry.isLive(HammerActions.ItemNew.id))
	}

	@Test
	fun `an overlay barrier hides lower handlers but not global ones`() {
		val runs = IntArray(2)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))
		registry.register(ActionLayer.Root, mapOf(HammerActions.ProjectSaveAll.id to counting(runs, 1)))
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true)

		assertFalse(registry.dispatch(modN))
		assertTrue(registry.dispatch(KeyChord.mod(ChordKey.S, shift = true)))
		assertEquals(listOf(0, 1), runs.toList())
	}

	@Test
	fun `a barrier's own handlers still run`() {
		val runs = IntArray(2)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs, 0)))
		registry.register(ActionLayer.Overlay, mapOf(HammerActions.ItemNew.id to counting(runs, 1)), blocksBelow = true)

		assertTrue(registry.dispatch(modN))
		assertEquals(listOf(0, 1), runs.toList())
	}

	@Test
	fun `local actions are never dispatched`() {
		val runs = IntArray(1)
		registry.register(ActionLayer.Item, mapOf(HammerActions.EditSave.id to counting(runs)))

		assertFalse(registry.dispatch(KeyChord.mod(ChordKey.S)))
		assertEquals(0, runs[0])
	}

	@Test
	fun `invoke runs a live action by id`() {
		val runs = IntArray(1)
		registry.register(ActionLayer.Root, mapOf(HammerActions.ProjectGallery.id to counting(runs)))

		assertTrue(registry.invoke(HammerActions.ProjectGallery.id))
		assertFalse(registry.invoke(HammerActions.ItemNew.id))
		assertEquals(1, runs[0])
	}

	@Test
	fun `live lists reachable actions in table order`() {
		registry.register(ActionLayer.Root, mapOf(HammerActions.NavNotes.id to ActionHandler {}))
		registry.register(ActionLayer.Root, mapOf(HammerActions.NavHome.id to ActionHandler {}))

		assertEquals(
			listOf(HammerActions.NavHome.id, HammerActions.NavNotes.id),
			registry.live().map { it.id },
		)
	}

	@Test
	fun `registerWhileStarted follows the lifecycle`() {
		val lifecycle = LifecycleRegistry()
		registry.registerWhileStarted(lifecycle, ActionLayer.Module, mapOf(HammerActions.ItemNew.id to ActionHandler {}))

		lifecycle.create()
		assertFalse(registry.isLive(HammerActions.ItemNew.id))
		lifecycle.start()
		assertTrue(registry.isLive(HammerActions.ItemNew.id))
		lifecycle.stop()
		assertFalse(registry.isLive(HammerActions.ItemNew.id))
		lifecycle.start()
		assertTrue(registry.isLive(HammerActions.ItemNew.id))
		lifecycle.destroy()
		assertFalse(registry.isLive(HammerActions.ItemNew.id))
	}

	@Test
	fun `only an overlay barrier counts as an overlay`() {
		registry.register(ActionLayer.Overlay, mapOf(HammerActions.ItemNew.id to ActionHandler {}))
		registry.register(ActionLayer.Item, emptyMap(), blocksBelow = true)
		assertFalse(registry.hasOverlay())

		val barrier = registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true)
		assertTrue(registry.hasOverlay())

		barrier.unregister()
		assertFalse(registry.hasOverlay())
	}

	@Test
	fun `dismissing runs the most recent overlay's dismiss only`() {
		val dismissed = mutableListOf<String>()
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = { dismissed += "lower" })
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = { dismissed += "upper" })

		assertTrue(registry.dismissTopOverlay())
		assertEquals(listOf("upper"), dismissed)
	}

	@Test
	fun `an overlay without a dismiss is passed over when dismissing`() {
		val dismissed = IntArray(1)
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = { dismissed[0]++ })
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true)

		assertTrue(registry.dismissTopOverlay())
		assertEquals(1, dismissed[0])
	}

	@Test
	fun `dismissing with nothing dismissable does nothing`() {
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true)

		assertFalse(registry.dismissTopOverlay())
	}

	@Test
	fun `registerOverlayWhile holds a barrier while the state is open and started`() {
		val lifecycle = LifecycleRegistry()
		val open = MutableValue(false)
		val runs = IntArray(1)
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNew.id to counting(runs)))
		registry.registerOverlayWhile(lifecycle, open) { it }
		lifecycle.create()
		lifecycle.start()

		open.value = true
		val blockedWhileOpen = !registry.dispatch(modN)
		lifecycle.stop()
		val freeWhileStopped = registry.dispatch(modN)
		lifecycle.start()
		val blockedAgain = !registry.dispatch(modN)
		open.value = false

		assertTrue(blockedWhileOpen)
		assertTrue(freeWhileStopped)
		assertTrue(blockedAgain)
		assertTrue(registry.dispatch(modN))
		assertFalse(registry.hasOverlay())
	}
}
